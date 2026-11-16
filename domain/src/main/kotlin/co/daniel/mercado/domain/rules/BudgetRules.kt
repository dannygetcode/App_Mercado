package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.model.maxOf

/** Semáforo y límites de presupuesto. Aritmética entera. */
object BudgetRules {

    /** Verde < 80 %, amarillo 80–100 % inclusive, rojo > 100 %. Sin límite → `NONE`. */
    fun trafficLight(total: Pesos, limit: Pesos?): TrafficLight {
        if (limit == null) return TrafficLight.NONE
        val total100 = Math.multiplyExact(total.value, 100L)
        return when {
            total100 < Math.multiplyExact(limit.value, 80L) -> TrafficLight.GREEN
            total.value <= limit.value -> TrafficLight.YELLOW
            else -> TrafficLight.RED
        }
    }

    /** Límite por defecto de una compra: lo que queda del mes, nunca negativo. Sin presupuesto → nulo. */
    fun defaultLimit(budget: Pesos?, spent: Pesos): Pesos? =
        budget?.let { maxOf(it - spent, Pesos.ZERO) }

    /** Porcentaje entero del límite consumido (puede pasar de 100). Nulo sin límite o con límite 0. */
    fun percentOf(total: Pesos, limit: Pesos?): Int? {
        if (limit == null || limit.value <= 0) return null
        return (Math.multiplyExact(total.value, 100L) / limit.value).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Si al pasar de [previous] a [current] se cruzó hacia amarillo o rojo, devuelve el nuevo color
     * (para avisar una vez por cruce). Si no hubo cruce hacia arriba, nulo.
     */
    fun escalation(previous: TrafficLight, current: TrafficLight): TrafficLight? =
        current.takeIf {
            (it == TrafficLight.YELLOW || it == TrafficLight.RED) &&
                previous != TrafficLight.NONE && it.ordinal > previous.ordinal
        }
}
