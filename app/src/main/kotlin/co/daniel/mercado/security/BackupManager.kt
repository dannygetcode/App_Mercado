package co.daniel.mercado.security

import androidx.room.withTransaction
import co.daniel.mercado.data.backup.BackupFileDto
import co.daniel.mercado.data.backup.toDto
import co.daniel.mercado.data.backup.toEntity
import co.daniel.mercado.data.db.MercadoDatabase
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.Months
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class BackupError {
    TOO_LARGE,
    NOT_A_BACKUP,
    WRONG_PASSWORD_OR_TAMPERED,

    /** Hecho con una versión más nueva de la app (esquema mayor que el actual). */
    NEWER_VERSION,

    /** Descifra bien pero el contenido no es válido (referencias rotas, valores fuera de rango). */
    INVALID_CONTENT,
}

/** Respaldo abierto y validado, listo para reemplazar la base. */
class OpenedBackup internal constructor(internal val content: BackupFileDto) {
    val exportedAt: Long get() = content.exportedAt
    val productCount: Int get() = content.products.size
    val tripCount: Int get() = content.trips.size
}

sealed interface BackupOpenResult {
    data class Ok(val backup: OpenedBackup) : BackupOpenResult
    data class Failed(val error: BackupError) : BackupOpenResult
}

/**
 * RF-12 / S-08: exporta todas las tablas a `.mercado` (JSON → GZIP → `BackupCrypto`) y restaura
 * reemplazando todo en una transacción. Abrir valida por completo antes de tocar la base.
 */
class BackupManager(
    private val db: MercadoDatabase,
    private val clock: Clock,
    private val iterations: Int = BackupCrypto.ITERATIONS,
) {
    private val dao = db.backupDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(password: CharArray): ByteArray {
        val content = db.withTransaction {
            BackupFileDto(
                schemaVersion = SCHEMA_VERSION,
                exportedAt = clock.nowMillis(),
                categories = dao.categories().map { it.toDto() },
                products = dao.products().map { it.toDto() },
                barcodes = dao.barcodes().map { it.toDto() },
                trips = dao.trips().map { it.toDto() },
                tripItems = dao.tripItems().map { it.toDto() },
                priceHistory = dao.priceHistory().map { it.toDto() },
                budgets = dao.budgets().map { it.toDto() },
                shoppingList = dao.shoppingList().map { it.toDto() },
            )
        }
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.encodeToString(BackupFileDto.serializer(), content).toByteArray()) }
        }.toByteArray()
        return BackupCrypto.encrypt(gzipped, password, iterations)
    }

    /** Descifra, descomprime y valida. No escribe nada. */
    fun open(file: ByteArray, password: CharArray): BackupOpenResult {
        val gzipped = try {
            BackupCrypto.decrypt(file, password)
        } catch (e: BackupCryptoException) {
            return BackupOpenResult.Failed(
                when (e.error) {
                    BackupCryptoError.TOO_LARGE -> BackupError.TOO_LARGE
                    BackupCryptoError.NOT_A_BACKUP, BackupCryptoError.BAD_ITERATIONS -> BackupError.NOT_A_BACKUP
                    BackupCryptoError.WRONG_PASSWORD_OR_TAMPERED -> BackupError.WRONG_PASSWORD_OR_TAMPERED
                },
            )
        }
        val text = gunzipLimited(gzipped) ?: return BackupOpenResult.Failed(BackupError.INVALID_CONTENT)
        val content = try {
            json.decodeFromString(BackupFileDto.serializer(), text)
        } catch (_: SerializationException) {
            return BackupOpenResult.Failed(BackupError.INVALID_CONTENT)
        } catch (_: IllegalArgumentException) {
            return BackupOpenResult.Failed(BackupError.INVALID_CONTENT)
        }
        if (content.schemaVersion > SCHEMA_VERSION) return BackupOpenResult.Failed(BackupError.NEWER_VERSION)
        if (!isValid(content)) return BackupOpenResult.Failed(BackupError.INVALID_CONTENT)
        return BackupOpenResult.Ok(OpenedBackup(content))
    }

    /** Reemplaza todos los datos por los del respaldo: todo o nada. */
    suspend fun restore(backup: OpenedBackup) {
        val c = backup.content
        db.withTransaction {
            dao.deleteTripItems()
            dao.deleteShoppingList()
            dao.deletePriceHistory()
            dao.deleteBarcodes()
            dao.deleteTrips()
            dao.deleteProducts()
            dao.deleteBudgets()
            dao.deleteCategories()
            dao.insertCategories(c.categories.map { it.toEntity() })
            dao.insertProducts(c.products.map { it.toEntity() })
            dao.insertBarcodes(c.barcodes.map { it.toEntity() })
            dao.insertTrips(c.trips.map { it.toEntity() })
            dao.insertTripItems(c.tripItems.map { it.toEntity() })
            dao.insertPriceHistory(c.priceHistory.map { it.toEntity() })
            dao.insertBudgets(c.budgets.map { it.toEntity() })
            dao.insertShoppingList(c.shoppingList.map { it.toEntity() })
        }
    }

    private fun gunzipLimited(bytes: ByteArray): String? = try {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_JSON_BYTES) return null
            }
            out.toString(Charsets.UTF_8.name())
        }
    } catch (_: IOException) {
        null
    }

    /** Integridad completa antes de escribir: claves únicas, referencias y rangos. */
    private fun isValid(c: BackupFileDto): Boolean {
        val categoryIds = c.categories.map { it.id }
        val productIds = c.products.map { it.id }
        val tripIds = c.trips.map { it.id }
        val validMoney = 0L..MAX_MONEY
        return c.schemaVersion >= 1 &&
            categoryIds.allDistinct() && categoryIds.all { it > 0 } &&
            c.categories.map { it.name }.allDistinct() && c.categories.all { it.name.isNotBlank() } &&
            productIds.allDistinct() && productIds.all { it > 0 } &&
            c.products.all { p ->
                p.categoryId in categoryIds && p.name.length in 1..MAX_NAME &&
                    p.unitType in UNIT_TYPES && p.refPrice in validMoney
            } &&
            c.barcodes.map { it.code }.allDistinct() &&
            c.barcodes.all { it.productId in productIds && it.code.length in 1..MAX_CODE } &&
            tripIds.allDistinct() && tripIds.all { it > 0 } &&
            c.trips.count { it.closedAt == null } <= 1 &&
            c.trips.all { t ->
                t.totalEstimated in validMoney && (t.totalPaid ?: 0) in validMoney && (t.budgetLimit ?: 0) in validMoney
            } &&
            c.tripItems.map { it.id }.allDistinct() &&
            c.tripItems.all { i ->
                i.tripId in tripIds && (i.productId == null || i.productId in productIds) &&
                    i.quantity >= 1 && i.unitPrice in validMoney
            } &&
            c.priceHistory.map { it.id }.allDistinct() &&
            c.priceHistory.all { it.productId in productIds && it.price in validMoney && it.source in PRICE_SOURCES } &&
            c.budgets.map { it.yearMonth }.allDistinct() &&
            c.budgets.all { it.amount in validMoney && it.yearMonth.isYearMonth() } &&
            c.shoppingList.map { it.productId }.allDistinct() &&
            c.shoppingList.all { it.productId in productIds && it.suggestedQty >= 1 }
    }

    private fun <T> List<T>.allDistinct(): Boolean = toSet().size == size

    private fun String.isYearMonth(): Boolean = try {
        YearMonth.parse(this)
        true
    } catch (_: DateTimeParseException) {
        false
    }

    companion object {
        /** Versión del esquema de Room que escribe este respaldo. */
        const val SCHEMA_VERSION = MercadoDatabase.VERSION
        private const val MAX_JSON_BYTES = 64 * 1024 * 1024
        private const val BUFFER = 64 * 1024
        private const val MAX_NAME = 80
        private const val MAX_CODE = 64
        private const val MAX_MONEY = 999_999_999_999L
        private val UNIT_TYPES = UnitType.entries.map { it.name }.toSet()
        private val PRICE_SOURCES = PriceSource.entries.map { it.name }.toSet()

        /** "mercado-AAAA-MM-DD.mercado" en la fecha de Bogotá. */
        fun suggestedFileName(epochMillis: Long): String =
            "mercado-" + Instant.ofEpochMilli(epochMillis).atZone(Months.ZONE).toLocalDate() + ".mercado"
    }
}
