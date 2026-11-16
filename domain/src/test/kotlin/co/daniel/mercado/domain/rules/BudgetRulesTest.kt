package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetRulesTest {

    private val limit = Pesos(100_000)

    @Test
    fun `semaforo en los umbrales`() {
        assertEquals(TrafficLight.GREEN, BudgetRules.trafficLight(Pesos(79_000), limit))
        assertEquals(TrafficLight.GREEN, BudgetRules.trafficLight(Pesos(79_999), limit))
        assertEquals(TrafficLight.YELLOW, BudgetRules.trafficLight(Pesos(80_000), limit))
        assertEquals(TrafficLight.YELLOW, BudgetRules.trafficLight(Pesos(100_000), limit))
        assertEquals(TrafficLight.RED, BudgetRules.trafficLight(Pesos(100_001), limit))
        assertEquals(TrafficLight.NONE, BudgetRules.trafficLight(Pesos(50_000), null))
    }

    @Test
    fun `limite por defecto es presupuesto menos gastado y nunca negativo`() {
        assertEquals(Pesos(288_000), BudgetRules.defaultLimit(Pesos(900_000), Pesos(612_000)))
        assertEquals(Pesos.ZERO, BudgetRules.defaultLimit(Pesos(900_000), Pesos(950_000)))
        assertNull(BudgetRules.defaultLimit(null, Pesos(10_000)))
    }

    @Test
    fun `porcentaje entero del limite`() {
        assertEquals(75, BudgetRules.percentOf(Pesos(187_500), Pesos(250_000)))
        assertEquals(120, BudgetRules.percentOf(Pesos(120_000), limit))
        assertNull(BudgetRules.percentOf(Pesos(1), null))
        assertNull(BudgetRules.percentOf(Pesos(1), Pesos.ZERO))
    }

    @Test
    fun `avisa solo al cruzar hacia amarillo o rojo`() {
        assertEquals(TrafficLight.YELLOW, BudgetRules.escalation(TrafficLight.GREEN, TrafficLight.YELLOW))
        assertEquals(TrafficLight.RED, BudgetRules.escalation(TrafficLight.YELLOW, TrafficLight.RED))
        assertEquals(TrafficLight.RED, BudgetRules.escalation(TrafficLight.GREEN, TrafficLight.RED))
        assertNull(BudgetRules.escalation(TrafficLight.YELLOW, TrafficLight.YELLOW))
        assertNull(BudgetRules.escalation(TrafficLight.RED, TrafficLight.YELLOW))
        assertNull(BudgetRules.escalation(TrafficLight.NONE, TrafficLight.NONE))
    }
}
