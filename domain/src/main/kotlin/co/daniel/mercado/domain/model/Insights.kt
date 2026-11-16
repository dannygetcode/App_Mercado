package co.daniel.mercado.domain.model

import java.time.YearMonth

enum class ListSource { AUTO, MANUAL }

/** Ítem de la lista de mercado (RF-09). */
data class ShoppingListItem(
    val productId: Long,
    val suggestedQty: Int = 1,
    val checked: Boolean = false,
    val source: ListSource,
    /** Quitado por el usuario: no se vuelve a sugerir antes de esta fecha. */
    val dismissedUntil: Long? = null,
)

/** Una línea de una compra cerrada, la materia prima de sugerencias y resúmenes. */
data class ClosedLine(
    val tripId: Long,
    val closedAt: Long,
    /** Nulo = precio suelto (cuenta en "Otros"). */
    val productId: Long?,
    val quantity: Int,
    val unitPrice: Pesos,
    val unitType: UnitType,
) {
    val total: Pesos get() = unitPrice * quantity
}

/** Por qué se sugiere un producto: cada ~[avgIntervalDays] días, la última hace [daysSinceLast]. */
data class Suggestion(
    val productId: Long,
    val avgIntervalDays: Int,
    val daysSinceLast: Int,
    val quantity: Int,
    val lastPurchaseAt: Long,
    val avgIntervalMillis: Long,
)

/** Entrada de la lista para mostrar: ítem, producto y motivo si es sugerido. */
data class ShoppingListEntry(val item: ShoppingListItem, val product: Product, val reason: Suggestion?)

data class CategorySpend(val categoryId: Long, val name: String, val spent: Pesos, val cap: Pesos?)

data class ProductSpend(val productId: Long, val name: String, val spent: Pesos, val quantity: Int)

/** Variación de precio frente a la compra anterior, en puntos básicos (1 % = 100). */
data class PriceChange(val productId: Long, val name: String, val previous: Pesos, val current: Pesos) {
    val basisPoints: Long get() = Math.multiplyExact(current.value - previous.value, 10_000L) / previous.value
}

data class MonthSummary(
    val status: MonthStatus,
    val categories: List<CategorySpend>,
    val topProducts: List<ProductSpend>,
    val priceUps: List<PriceChange>,
    val priceDowns: List<PriceChange>,
)

data class MonthTotal(val month: YearMonth, val spent: Pesos, val budget: Pesos?)
