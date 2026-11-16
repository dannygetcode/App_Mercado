package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.TransactionRunner
import co.daniel.mercado.domain.rules.Ean
import co.daniel.mercado.domain.rules.NameNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Búsqueda sin tildes ni mayúsculas ("limon" encuentra "Limón"). Activos, favoritos primero. */
class SearchProducts(private val products: ProductRepository) {

    suspend operator fun invoke(query: String, limit: Int = DEFAULT_LIMIT): List<Product> {
        val normalized = NameNormalizer.normalize(query)
        if (normalized.isEmpty()) return emptyList()
        return products.search(normalized, limit)
    }

    companion object {
        const val DEFAULT_LIMIT = 30
    }
}

class ObserveFavorites(private val products: ProductRepository) {
    operator fun invoke(): Flow<List<Product>> = products.observeFavorites()
}

/** Datos editables de un producto. `id == 0` crea uno nuevo. */
data class ProductDraft(
    val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val unitType: UnitType,
    val refPrice: Pesos,
    val favorite: Boolean = false,
)

sealed interface SaveProductResult {
    data class Saved(val productId: Long) : SaveProductResult
    data object InvalidName : SaveProductResult
    data object InvalidPrice : SaveProductResult
    data object UnknownCategory : SaveProductResult
    data object NotFound : SaveProductResult
    data class DuplicateName(val existingId: Long) : SaveProductResult
}

/**
 * Crea o edita un producto. Guardarlo desde el editor lo da por revisado (`needsReview = false`).
 * Un cambio de precio manual queda en el historial (`MANUAL`, solo `UNIT`).
 */
class SaveProduct(
    private val products: ProductRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(draft: ProductDraft): SaveProductResult {
        val name = draft.name.trim().replace(WHITESPACE, " ")
        if (name.length !in 1..MAX_NAME_LENGTH) return SaveProductResult.InvalidName
        if (!draft.refPrice.isPositive || draft.refPrice > MAX_PRICE) return SaveProductResult.InvalidPrice
        return transactions.inTransaction {
            if (products.categories().none { it.id == draft.categoryId }) {
                return@inTransaction SaveProductResult.UnknownCategory
            }
            products.findByNormalizedName(NameNormalizer.normalize(name))
                ?.takeIf { it.id != draft.id }
                ?.let { return@inTransaction SaveProductResult.DuplicateName(it.id) }
            val now = clock.nowMillis()
            val existing = if (draft.id != 0L) {
                products.getById(draft.id) ?: return@inTransaction SaveProductResult.NotFound
            } else {
                null
            }
            val favoriteOrder = when {
                !draft.favorite -> 0
                existing?.favorite == true -> existing.favoriteOrder
                else -> (products.observeFavorites().first().maxOfOrNull { it.favoriteOrder } ?: -1) + 1
            }
            val product = (existing ?: Product(name = name, categoryId = draft.categoryId, refPrice = draft.refPrice, createdAt = now))
                .copy(
                    name = name,
                    categoryId = draft.categoryId,
                    unitType = draft.unitType,
                    refPrice = draft.refPrice,
                    favorite = draft.favorite,
                    favoriteOrder = favoriteOrder,
                    needsReview = false,
                    updatedAt = now,
                )
            val id = if (existing == null) products.insert(product) else product.id.also { products.update(product) }
            if (product.unitType == UnitType.UNIT && products.lastPricePoint(id)?.price != product.refPrice) {
                products.addPricePoint(PricePoint(id, product.refPrice, now, PriceSource.MANUAL))
            }
            SaveProductResult.Saved(id)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
        val MAX_PRICE = Pesos(50_000_000)
        private val WHITESPACE = Regex("\\s+")
    }
}

/** Un producto no se borra (puede tener historial): se desactiva y sale de búsquedas y favoritos. */
class DeactivateProduct(
    private val products: ProductRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(productId: Long): Boolean {
        val product = products.getById(productId) ?: return false
        if (product.active) products.update(product.copy(active = false, updatedAt = clock.nowMillis()))
        return true
    }
}

/** Deshace una desactivación: el producto vuelve a búsquedas y favoritos. */
class ReactivateProduct(
    private val products: ProductRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(productId: Long): Boolean {
        val product = products.getById(productId) ?: return false
        if (!product.active) products.update(product.copy(active = true, updatedAt = clock.nowMillis()))
        return true
    }
}

sealed interface LinkBarcodeResult {
    data object Linked : LinkBarcodeResult
    data object AlreadyLinked : LinkBarcodeResult
    /** El código era de otro producto; ahora es de este (la última asociación gana). */
    data class Moved(val fromProductId: Long) : LinkBarcodeResult
    data object InvalidCode : LinkBarcodeResult
    data object ProductNotFound : LinkBarcodeResult
}

/** Asocia un código leído a un producto del catálogo. */
class LinkBarcode(
    private val products: ProductRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(productId: Long, rawCode: String): LinkBarcodeResult {
        val code = Ean.normalize(rawCode) ?: return LinkBarcodeResult.InvalidCode
        return transactions.inTransaction {
            products.getById(productId) ?: return@inTransaction LinkBarcodeResult.ProductNotFound
            val owner = products.barcodeOwner(code)
            if (owner == productId) return@inTransaction LinkBarcodeResult.AlreadyLinked
            products.linkBarcode(code, productId, clock.nowMillis())
            if (owner == null) LinkBarcodeResult.Linked else LinkBarcodeResult.Moved(owner)
        }
    }
}
