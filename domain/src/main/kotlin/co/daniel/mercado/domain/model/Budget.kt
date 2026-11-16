package co.daniel.mercado.domain.model

import java.time.YearMonth

data class Budget(val yearMonth: YearMonth, val amount: Pesos)

enum class TrafficLight { NONE, GREEN, YELLOW, RED }

data class MonthStatus(
    val yearMonth: YearMonth,
    /** Presupuesto del mes o heredado del mes anterior más reciente. Nulo si nunca se definió. */
    val budget: Pesos?,
    val spent: Pesos,
    val remaining: Pesos?,
    val trafficLight: TrafficLight,
    /** El presupuesto viene de un mes anterior (el mes no tiene uno propio). */
    val inherited: Boolean = false,
)
