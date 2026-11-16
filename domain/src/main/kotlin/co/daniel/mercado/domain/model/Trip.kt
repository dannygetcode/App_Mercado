package co.daniel.mercado.domain.model

import co.daniel.mercado.domain.rules.LineMath

data class Trip(
    val id: Long = 0,
    val startedAt: Long,
    val closedAt: Long? = null,
    val store: String? = null,
    val budgetLimit: Pesos? = null,
    val totalEstimated: Pesos = Pesos.ZERO,
    val totalPaid: Pesos? = null,
) {
    val isOpen: Boolean get() = closedAt == null

    /** Lo que cuenta como gasto del mes: el pagado si se registró, si no el estimado. */
    val effectiveTotal: Pesos get() = totalPaid ?: totalEstimated
}

/**
 * Línea de compra. Copia nombre y precio en el momento (no depende del catálogo después).
 * `productId == null` es un precio suelto (RF-17); su nombre vacío se muestra como
 * "Artículo sin nombre" en la interfaz.
 */
data class TripItem(
    val id: Long = 0,
    val tripId: Long,
    val productId: Long?,
    val nameSnapshot: String,
    val unitType: UnitType = UnitType.UNIT,
    val quantity: Int,
    val unitPrice: Pesos,
    val weightGrams: Int? = null,
    val addedAt: Long,
    val position: Int,
) {
    val lineTotal: Pesos get() = LineMath.lineTotal(unitPrice, quantity)

    val isLoose: Boolean get() = productId == null
}

data class TripWithItems(val trip: Trip, val items: List<TripItem>)

/** Fila del historial: una compra cerrada y su número de artículos (suma de cantidades; KG cuenta 1). */
data class TripSummary(val trip: Trip, val articleCount: Int)

/** Estado observable de la compra abierta: lo que pinta la tarjeta de total. */
data class ActiveTrip(
    val trip: Trip,
    /** Última línea primero. */
    val items: List<TripItem>,
    val total: Pesos,
    val articleCount: Int,
    val limit: Pesos?,
    /** `limit - total`; negativo si se pasó. Nulo sin límite. */
    val remaining: Pesos?,
    val trafficLight: TrafficLight,
    val percentOfLimit: Int?,
)
