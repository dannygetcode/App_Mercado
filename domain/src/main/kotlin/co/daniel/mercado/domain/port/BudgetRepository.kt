package co.daniel.mercado.domain.port

import co.daniel.mercado.domain.model.Budget
import java.time.YearMonth

/** Presupuestos mensuales. */
interface BudgetRepository {

    suspend fun get(month: YearMonth): Budget?

    /** El presupuesto más reciente de un mes anterior a [month], para heredarlo. */
    suspend fun latestBefore(month: YearMonth): Budget?

    suspend fun set(budget: Budget)
}
