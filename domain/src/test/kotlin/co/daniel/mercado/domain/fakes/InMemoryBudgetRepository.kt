package co.daniel.mercado.domain.fakes

import co.daniel.mercado.domain.model.Budget
import co.daniel.mercado.domain.port.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

class InMemoryBudgetRepository : BudgetRepository {

    val budgets = sortedMapOf<YearMonth, Budget>()
    private val version = MutableStateFlow(0)

    override suspend fun get(month: YearMonth): Budget? = budgets[month]

    override suspend fun latestBefore(month: YearMonth): Budget? = budgets.headMap(month).values.lastOrNull()

    override suspend fun set(budget: Budget) {
        budgets[budget.yearMonth] = budget
        version.value++
    }

    override fun observeAll(): Flow<List<Budget>> = version.map { budgets.values.toList() }
}
