package co.daniel.mercado.data.seed

import androidx.room.withTransaction
import co.daniel.mercado.data.db.MercadoDatabase
import co.daniel.mercado.data.db.entity.BarcodeEntity
import co.daniel.mercado.data.db.entity.CategoryEntity
import co.daniel.mercado.data.db.entity.PriceHistoryEntity
import co.daniel.mercado.data.db.entity.ProductEntity
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.Ean
import co.daniel.mercado.domain.rules.NameNormalizer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

enum class SeedError {
    TOO_LARGE,
    MALFORMED,
    UNSUPPORTED_VERSION,
    TOO_MANY_PRODUCTS,
    INVALID_NAME,
    INVALID_CATEGORY,
    INVALID_UNIT,
    INVALID_PRICE,
    INVALID_BARCODE,
    DUPLICATE_BARCODE,
}

sealed interface SeedResult {
    data class Imported(val created: Int, val updated: Int) : SeedResult

    /** Nada se escribió. [index] es la posición (desde 0) del producto con problema, si aplica. */
    data class Invalid(val error: SeedError, val index: Int? = null) : SeedResult
}

/**
 * RF-02 / S-09: importa un catálogo JSON. Valida todo antes de escribir y escribe en una sola
 * transacción (todo o nada). Idempotente: un producto con el mismo nombre normalizado se actualiza
 * (precio, categoría, códigos nuevos) en vez de duplicarse.
 */
class SeedImporter(
    private val db: MercadoDatabase,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(input: InputStream): SeedResult {
        val bytes = try {
            readLimited(input) ?: return SeedResult.Invalid(SeedError.TOO_LARGE)
        } catch (_: IOException) {
            return SeedResult.Invalid(SeedError.MALFORMED)
        }
        val file = try {
            json.decodeFromString(SeedFileDto.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return SeedResult.Invalid(SeedError.MALFORMED)
        } catch (_: IllegalArgumentException) {
            return SeedResult.Invalid(SeedError.MALFORMED)
        }
        val products = when (val validated = validate(file)) {
            is Validation.Ok -> validated.products
            is Validation.Failed -> return validated.result
        }
        return write(products)
    }

    private class ValidProduct(
        val name: String,
        val category: String,
        val unit: UnitType,
        val price: Pesos,
        val barcodes: List<String>,
        val favorite: Boolean,
    )

    private sealed interface Validation {
        class Ok(val products: List<ValidProduct>) : Validation
        class Failed(val result: SeedResult.Invalid) : Validation
    }

    private fun validate(file: SeedFileDto): Validation {
        fun fail(error: SeedError, index: Int? = null) = Validation.Failed(SeedResult.Invalid(error, index))
        if (file.version != SUPPORTED_VERSION) return fail(SeedError.UNSUPPORTED_VERSION)
        if (file.products.size > MAX_PRODUCTS) return fail(SeedError.TOO_MANY_PRODUCTS)
        val codeOwners = HashMap<String, String>()
        val valid = file.products.mapIndexed { index, p ->
            val name = p.name.trim().replace(WHITESPACE, " ")
            if (name.length !in 1..MAX_NAME_LENGTH) return fail(SeedError.INVALID_NAME, index)
            val category = p.category.trim().replace(WHITESPACE, " ")
            if (category.length !in 1..MAX_CATEGORY_LENGTH) return fail(SeedError.INVALID_CATEGORY, index)
            val unit = when (p.unit) {
                "UNIT" -> UnitType.UNIT
                "KG" -> UnitType.KG
                else -> return fail(SeedError.INVALID_UNIT, index)
            }
            if (p.price !in 1..MAX_PRICE) return fail(SeedError.INVALID_PRICE, index)
            val norm = NameNormalizer.normalize(name)
            val codes = p.barcodes.map { raw ->
                val code = Ean.normalize(raw) ?: return fail(SeedError.INVALID_BARCODE, index)
                val previous = codeOwners.putIfAbsent(code, norm)
                if (previous != null && previous != norm) return fail(SeedError.DUPLICATE_BARCODE, index)
                code
            }.distinct()
            ValidProduct(name, category, unit, Pesos(p.price), codes, p.favorite)
        }
        return Validation.Ok(valid)
    }

    private suspend fun write(products: List<ValidProduct>): SeedResult = db.withTransaction {
        val productDao = db.productDao()
        val categoryDao = db.categoryDao()
        val now = clock.nowMillis()
        val categories = categoryDao.all().associateTo(HashMap()) { it.name to it.id }
        var nextCategoryOrder = (categoryDao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        var nextFavoriteOrder = 0
        var created = 0
        var updated = 0
        for (p in products) {
            val categoryId = categories.getOrPut(p.category) {
                categoryDao.insert(CategoryEntity(name = p.category, sortOrder = nextCategoryOrder++))
            }
            val norm = NameNormalizer.normalize(p.name)
            val existing = productDao.findByNameNorm(norm)
            val productId = if (existing != null) {
                if (existing.refPrice != p.price || existing.categoryId != categoryId) {
                    productDao.update(existing.copy(refPrice = p.price, categoryId = categoryId, updatedAt = now))
                    updated++
                }
                existing.id
            } else {
                created++
                productDao.insert(
                    ProductEntity(
                        name = p.name,
                        nameNorm = norm,
                        categoryId = categoryId,
                        unitType = p.unit,
                        refPrice = p.price,
                        favorite = p.favorite,
                        favoriteOrder = if (p.favorite) nextFavoriteOrder++ else 0,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            p.barcodes.forEach { productDao.upsertBarcode(BarcodeEntity(it, productId, now)) }
            val unit = existing?.unitType ?: p.unit
            if (unit == UnitType.UNIT && productDao.lastPricePoint(productId)?.price != p.price) {
                productDao.insertPricePoint(
                    PriceHistoryEntity(productId = productId, price = p.price, at = now, source = PriceSource.SEED),
                )
            }
        }
        SeedResult.Imported(created, updated)
    }

    /** Lee como máximo [MAX_BYTES]; devuelve nulo si el archivo es más grande. */
    private fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        const val SUPPORTED_VERSION = 1
        const val MAX_PRODUCTS = 5_000
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_NAME_LENGTH = 80
        const val MAX_CATEGORY_LENGTH = 40
        const val MAX_PRICE = 50_000_000L
        const val ASSET_PATH = "seed/catalogo_inicial.json"
        private val WHITESPACE = Regex("\\s+")
    }
}
