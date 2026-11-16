package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.TripRepository
import co.daniel.mercado.domain.rules.Ean
import co.daniel.mercado.domain.rules.NameNormalizer
import co.daniel.mercado.domain.rules.ScanDebouncer

/** Código leído → resultado. Valida el código, aplica el antirrebote y agrega o incrementa. */
class HandleScan(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val debouncer: ScanDebouncer,
    private val session: TripSession,
    clock: Clock,
) {
    private val lines = TripLines(trips, clock)

    suspend operator fun invoke(rawCode: String): ScanOutcome {
        val code = Ean.normalize(rawCode) ?: return ScanOutcome.Ignored
        if (!debouncer.shouldAccept(code)) return ScanOutcome.Ignored
        return session.mutate {
            val active = trips.getActive() ?: return@mutate ScanOutcome.NoActiveTrip
            val product = products.findByBarcode(code) ?: return@mutate ScanOutcome.Unknown(code)
            if (product.unitType == UnitType.KG) {
                return@mutate ScanOutcome.NeedsLabelPrice(product.id, product.name)
            }
            val reverts = mutableListOf<Revert>()
            lines.addUnit(active, product, reverts = reverts).also { session.undo.record(reverts) }
        }
    }
}

/** Agrega por id de producto (favoritos, búsqueda, lista). `KG` pide el precio de la etiqueta. */
class AddProductToTrip(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val session: TripSession,
    clock: Clock,
) {
    private val lines = TripLines(trips, clock)

    suspend operator fun invoke(productId: Long): ScanOutcome = session.mutate {
        val active = trips.getActive() ?: return@mutate ScanOutcome.NoActiveTrip
        val product = requireNotNull(products.getById(productId)) { "producto $productId no existe" }
        if (product.unitType == UnitType.KG) {
            return@mutate ScanOutcome.NeedsLabelPrice(product.id, product.name)
        }
        val reverts = mutableListOf<Revert>()
        lines.addUnit(active, product, reverts = reverts).also { session.undo.record(reverts) }
    }
}

/**
 * RF-04: código desconocido, precio primero. Sin [existingProductId] ni nombre crea un producto
 * provisional (`needsReview`) con [provisionalName] de los últimos 4 dígitos; con [existingProductId]
 * asocia el código a ese producto. Con `name` (tecleado sin elegir del autocompletado) usa el
 * producto de ese nombre si existe o crea uno ya nombrado (sin revisión pendiente). Sin `code`
 * ("Crear «texto»" desde la búsqueda) no asocia ningún código. Una sola transacción.
 */
class AddUnknownScanned(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val session: TripSession,
    private val clock: Clock,
    private val provisionalName: (lastDigits: String) -> String,
) {
    private val lines = TripLines(trips, clock)

    suspend operator fun invoke(
        code: String?,
        price: Pesos,
        existingProductId: Long? = null,
        name: String? = null,
    ): ScanOutcome {
        requireValidPrice(price)
        val normalized = code?.let { requireNotNull(Ean.normalize(it)) { "código inválido" } }
        val cleanName = name?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }
        cleanName?.let { require(it.length <= MAX_NAME_LENGTH) { "nombre muy largo" } }
        require(normalized != null || cleanName != null || existingProductId != null) {
            "hace falta un código, un nombre o un producto"
        }
        return session.mutate {
            val active = trips.getActive() ?: return@mutate ScanOutcome.NoActiveTrip
            val reverts = mutableListOf<Revert>()
            val now = clock.nowMillis()
            val targetId = existingProductId
                ?: cleanName?.let { products.findByNormalizedName(NameNormalizer.normalize(it))?.id }
                ?: normalized?.let { products.barcodeOwner(it) }
            val product = if (targetId != null) {
                requireNotNull(products.getById(targetId)) { "producto $targetId no existe" }
            } else {
                val draft = Product(
                    name = cleanName ?: provisionalName(requireNotNull(normalized).takeLast(4)),
                    categoryId = otherCategoryId(),
                    unitType = UnitType.UNIT,
                    refPrice = price,
                    needsReview = cleanName == null,
                    createdAt = now,
                    updatedAt = now,
                )
                val id = products.insert(draft)
                reverts += Revert.ProductCreated(id)
                draft.copy(id = id)
            }
            if (normalized != null) {
                val previousOwner = products.barcodeOwner(normalized)
                if (previousOwner != product.id) {
                    products.linkBarcode(normalized, product.id, now)
                    reverts += Revert.BarcodeLinked(normalized, previousOwner)
                }
            }
            val outcome = if (product.unitType == UnitType.KG) {
                val lineId = lines.insert(active.trip.id, product.id, product.name, UnitType.KG, price, null, reverts)
                ScanOutcome.Added(lineId, product.name, price)
            } else {
                lines.addUnit(active, product, price, reverts)
            }
            session.undo.record(reverts)
            outcome
        }
    }

    private suspend fun otherCategoryId(): Long =
        products.categoryByName(Category.OTHER)?.id
            ?: products.insertCategory(Category(id = 0, name = Category.OTHER, sortOrder = Int.MAX_VALUE))

    private companion object {
        const val MAX_NAME_LENGTH = 80
        val WHITESPACE = Regex("\\s+")
    }
}

/** RF-17: precio suelto, línea sin producto (nombre vacío = "Artículo sin nombre"). */
class AddLoosePrice(
    private val trips: TripRepository,
    private val session: TripSession,
    clock: Clock,
) {
    private val lines = TripLines(trips, clock)

    suspend operator fun invoke(price: Pesos): ScanOutcome {
        requireValidPrice(price)
        return session.mutate {
            val active = trips.getActive() ?: return@mutate ScanOutcome.NoActiveTrip
            val reverts = mutableListOf<Revert>()
            val id = lines.insert(active.trip.id, null, "", UnitType.UNIT, price, null, reverts)
            session.undo.record(reverts)
            ScanOutcome.Added(id, "", price)
        }
    }
}

/** D-18: producto `KG` con el precio total de la etiqueta. Cada bolsa es una línea nueva. */
class AddWeighedItem(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val session: TripSession,
    clock: Clock,
) {
    private val lines = TripLines(trips, clock)

    suspend operator fun invoke(productId: Long, labelPrice: Pesos, weightGrams: Int? = null): ScanOutcome {
        requireValidPrice(labelPrice)
        weightGrams?.let { require(it > 0) { "peso inválido" } }
        return session.mutate {
            val active = trips.getActive() ?: return@mutate ScanOutcome.NoActiveTrip
            val product = requireNotNull(products.getById(productId)) { "producto $productId no existe" }
            val reverts = mutableListOf<Revert>()
            val id = lines.insert(active.trip.id, product.id, product.name, UnitType.KG, labelPrice, weightGrams, reverts)
            session.undo.record(reverts)
            ScanOutcome.Added(id, product.name, labelPrice)
        }
    }
}
