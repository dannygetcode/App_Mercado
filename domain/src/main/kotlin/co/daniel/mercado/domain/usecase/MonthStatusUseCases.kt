package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Budget
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.port.BudgetRepository
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.TripRepository
import co.daniel.mercado.domain.rules.BudgetRules
import co.daniel.mercado.domain.rules.Months
import java.time.YearMonth

/** Presupuesto (propio o heredado), gastado y saldo de un mes. */
class GetMonthStatus(
    private val budgets: BudgetRepository,
    private val trips: TripRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(month: YearMonth = Months.monthOf(clock.nowMillis())): MonthStatus {
        val budget = (budgets.get(month) ?: budgets.latestBefore(month))?.amount
        val spent = trips.spentBetween(Months.startMillis(month), Months.endMillisExclusive(month))
        return MonthStatus(
            yearMonth = month,
            budget = budget,
            spent = spent,
            remaining = budget?.let { it - spent },
            trafficLight = BudgetRules.trafficLight(spent, budget),
        )
    }
}

class SetMonthlyBudget(
    private val budgets: BudgetRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(amount: Pesos, month: YearMonth = Months.monthOf(clock.nowMillis())) {
        require(amount.isPositive && amount <= Pesos.KEYPAD_MAX) { "presupuesto fuera de rango" }
        budgets.set(Budget(month, amount))
    }
}
