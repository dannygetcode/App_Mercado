package co.daniel.mercado.ui.diagnostics

import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.Budget
import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.Months
import co.daniel.mercado.domain.rules.SuggestionRules
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * Solo debug (puerta de L7): agrega 4 meses de compras de ejemplo con el catálogo actual, cada
 * ~14 días, con productos de siempre (para la lista sugerida) y precios que suben un poco cada mes
 * (para las variaciones). Agrega; no borra nada. Devuelve cuántas compras creó.
 */
suspend fun loadSampleMonths(graph: DataGraph, now: Long): Int {
    val products = graph.observeCatalog(CatalogFilter()).first()
        .filter { it.unitType == UnitType.UNIT && !it.needsReview }
    if (products.size < 6) return 0
    val random = Random(SEED)
    val staples = products.shuffled(random).take(minOf(10, products.size / 2))
    val others = products - staples.toSet()
    val stores = listOf("La Granja", "D1", "Éxito")
    var created = 0
    graph.transactions.inTransaction {
        val firstDay = now - 120 * SuggestionRules.DAY_MS
        (0 until 4).forEach { back ->
            graph.budgets.set(Budget(Months.monthOf(now).minusMonths(back.toLong()), Pesos(900_000)))
        }
        var day = 0L
        while (day < 118) {
            val closedAt = firstDay + day * SuggestionRules.DAY_MS + 11 * 3_600_000L
            val monthsAgo = ((now - closedAt) / (30 * SuggestionRules.DAY_MS)).toInt()
            val bumpPercent = (4 - monthsAgo).coerceAtLeast(0) * 3
            val picked = staples + others.shuffled(random).take(4)
            val store = stores.random(random)
            val tripId = graph.trips.insertTrip(Trip(startedAt = closedAt - 3_600_000L, closedAt = closedAt, store = store))
            var total = Pesos.ZERO
            picked.forEachIndexed { index, p ->
                val price = Pesos((p.refPrice.value * (100 + bumpPercent) / 100 + 25) / 50 * 50)
                val qty = 1 + random.nextInt(3)
                graph.trips.insertItem(
                    TripItem(tripId = tripId, productId = p.id, nameSnapshot = p.name, quantity = qty, unitPrice = price, addedAt = closedAt, position = index),
                )
                total += price * qty
            }
            graph.trips.updateTrip(Trip(id = tripId, startedAt = closedAt - 3_600_000L, closedAt = closedAt, store = store, totalEstimated = total))
            created++
            day += 12 + random.nextInt(5)
        }
    }
    return created
}

private const val SEED = 2026
