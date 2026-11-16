package co.daniel.mercado.domain.port

import co.daniel.mercado.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** Presupuestos mensuales. */
interface BudgetRepository {

    suspend fun get(month: YearMonth): Budget?

    /** El presupuesto más reciente de un mes anterior a [month], para heredarlo. */
    suspend fun latestBefore(month: YearMonth): Budget?

    suspend fun set(budget: Budget)

    /** Todos los presupuestos; se emite de nuevo con cada cambio. */
    fun observeAll(): Flow<List<Budget>>
}
