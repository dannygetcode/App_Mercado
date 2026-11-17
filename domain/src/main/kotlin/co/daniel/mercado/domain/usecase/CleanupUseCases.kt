package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.sum
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.TransactionRunner
import co.daniel.mercado.domain.port.TripRepository
import co.daniel.mercado.domain.rules.Months
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Cuántas compras cerradas hay en un rango de días y cuánto suman (para confirmar antes de borrar). */
data class TripsInRange(val count: Int, val total: Pesos)

/**
 * Borrar compras cerradas entre dos días (inclusive, en hora de Colombia). La compra abierta no se
 * toca: se descarta desde la compra.
 */
class DeleteTripsInRange(
    private val trips: TripRepository,
    private val transactions: TransactionRunner,
) {
    suspend fun preview(from: LocalDate, to: LocalDate): TripsInRange {
        val (start, end) = bounds(from, to)
        val inRange = trips.observeClosed().first().filter { (it.trip.closedAt ?: 0) in start until end }
        return TripsInRange(inRange.size, inRange.map { it.trip.effectiveTotal }.sum())
    }

    suspend operator fun invoke(from: LocalDate, to: LocalDate): Int {
        val (start, end) = bounds(from, to)
        return transactions.inTransaction { trips.deleteClosedBetween(start, end) }
    }

    private fun bounds(from: LocalDate, to: LocalDate): Pair<Long, Long> {
        require(!to.isBefore(from)) { "rango invertido" }
        return Months.dayStartMillis(from) to Months.dayStartMillis(to.plusDays(1))
    }
}

/** Borrar productos del catálogo; el historial de compras conserva sus renglones. */
class DeleteProducts(
    private val products: ProductRepository,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transactions.inTransaction { products.deleteProducts(ids.toSet()) }
    }
}
