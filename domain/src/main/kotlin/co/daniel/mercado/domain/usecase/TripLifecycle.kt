package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.ActiveTrip
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.TripRepository
import co.daniel.mercado.domain.rules.BudgetRules
import co.daniel.mercado.domain.rules.LineMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface TripLimit {
    /** Lo que queda del mes (nulo si no hay presupuesto). */
    data object Default : TripLimit
    data object None : TripLimit
    data class Custom(val amount: Pesos) : TripLimit
}

sealed interface StartTripResult {
    data class Started(val tripId: Long, val limit: Pesos?) : StartTripResult
    data class AlreadyOpen(val tripId: Long) : StartTripResult
}

/** Abre una compra. Garantiza la invariante de una sola compra abierta (D-19). */
class StartTrip(
    private val trips: TripRepository,
    private val monthStatus: GetMonthStatus,
    private val session: TripSession,
    private val clock: Clock,
) {
    /** Límite por defecto para prellenar la hoja de inicio: lo que queda del mes. */
    suspend fun defaultLimit(): Pesos? {
        val status = monthStatus()
        return BudgetRules.defaultLimit(status.budget, status.spent)
    }

    suspend operator fun invoke(limit: TripLimit = TripLimit.Default): StartTripResult {
        val resolved = when (limit) {
            TripLimit.Default -> defaultLimit()
            TripLimit.None -> null
            is TripLimit.Custom -> limit.amount.also { require(it.value >= 0) { "límite negativo" } }
        }
        return session.mutate {
            trips.getActive()?.let { return@mutate StartTripResult.AlreadyOpen(it.trip.id) }
            val id = trips.insertTrip(Trip(startedAt = clock.nowMillis(), budgetLimit = resolved))
            session.undo.clear()
            StartTripResult.Started(id, resolved)
        }
    }
}

/** La compra abierta con total, límite y semáforo, recalculada con cada cambio. */
class ObserveActiveTrip(private val trips: TripRepository) {

    operator fun invoke(): Flow<ActiveTrip?> = trips.observeActive().map { it?.toActiveTrip() }
}

internal fun TripWithItems.toActiveTrip(): ActiveTrip {
    val sorted = items.sortedByDescending { it.position }
    val total = LineMath.total(sorted)
    val limit = trip.budgetLimit
    return ActiveTrip(
        trip = trip,
        items = sorted,
        total = total,
        articleCount = LineMath.articleCount(sorted),
        limit = limit,
        remaining = limit?.let { it - total },
        trafficLight = BudgetRules.trafficLight(total, limit),
        percentOfLimit = BudgetRules.percentOf(total, limit),
    )
}

sealed interface CloseTripResult {
    data class Closed(val tripId: Long, val total: Pesos, val needsReview: List<Product>) : CloseTripResult
    /** La compra no tenía líneas: no se puede cerrar, se descartó. */
    data object DiscardedEmpty : CloseTripResult
    data object NoActiveTrip : CloseTripResult
}

/**
 * RF-08. En una transacción: fija total y fecha, actualiza el precio de referencia de cada producto
 * con el precio usado y registra el historial (solo `UNIT` y solo si cambió).
 */
class CloseTrip(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val session: TripSession,
    private val clock: Clock,
) {
    suspend operator fun invoke(totalPaid: Pesos? = null, store: String? = null): CloseTripResult {
        totalPaid?.let { require(it.value >= 0) { "total pagado negativo" } }
        return session.mutate {
            val active = trips.getActive() ?: return@mutate CloseTripResult.NoActiveTrip
            session.undo.clear()
            if (active.items.isEmpty()) {
                trips.deleteTrip(active.trip.id)
                return@mutate CloseTripResult.DiscardedEmpty
            }
            val now = clock.nowMillis()
            val total = LineMath.total(active.items)
            val needsReview = mutableListOf<Product>()
            active.items
                .filter { it.productId != null }
                .groupBy { it.productId }
                .forEach { (productId, lines) ->
                    val product = productId?.let { products.getById(it) } ?: return@forEach
                    val price = lines.maxBy { it.position }.unitPrice
                    updateReference(product, price, now)
                    if (product.needsReview) needsReview += product
                }
            trips.updateTrip(
                active.trip.copy(
                    closedAt = now,
                    totalEstimated = total,
                    totalPaid = totalPaid,
                    store = store?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
            CloseTripResult.Closed(active.trip.id, total, needsReview)
        }
    }

    private suspend fun updateReference(product: Product, price: Pesos, now: Long) {
        if (product.refPrice != price) {
            products.update(product.copy(refPrice = price, updatedAt = now))
        }
        if (product.unitType == UnitType.UNIT && products.lastPricePoint(product.id)?.price != price) {
            products.addPricePoint(PricePoint(product.id, price, now, PriceSource.TRIP))
        }
    }
}

/** Descarta la compra abierta y sus líneas. La UI pide confirmación antes. */
class DiscardTrip(
    private val trips: TripRepository,
    private val session: TripSession,
) {
    suspend operator fun invoke(): Boolean = session.mutate {
        val active = trips.getActive() ?: return@mutate false
        trips.deleteTrip(active.trip.id)
        session.undo.clear()
        true
    }
}

/** Fija o borra el total pagado según el recibo; es lo único editable de una compra cerrada. */
class SetPaidTotal(private val trips: TripRepository) {

    suspend operator fun invoke(tripId: Long, totalPaid: Pesos?): Boolean {
        totalPaid?.let { require(it.value >= 0 && it <= Pesos.KEYPAD_MAX) { "total pagado fuera de rango" } }
        val trip = trips.getTrip(tripId) ?: return false
        trips.updateTrip(trip.copy(totalPaid = totalPaid))
        return true
    }
}
