package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.model.Budget
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthStatusTest {

    private val f = TripFixture()
    private val nov = YearMonth.of(2026, 11)

    @Test
    fun `un mes sin presupuesto hereda el del mes anterior mas reciente`() = runTest {
        f.budgets.set(Budget(YearMonth.of(2026, 8), Pesos(700_000)))
        f.budgets.set(Budget(YearMonth.of(2026, 9), Pesos(850_000)))
        f.budgets.set(Budget(YearMonth.of(2027, 1), Pesos(999_000)))

        assertEquals(Pesos(850_000), f.monthStatus(nov).budget)
    }

    @Test
    fun `el presupuesto propio del mes gana sobre el heredado`() = runTest {
        f.budgets.set(Budget(YearMonth.of(2026, 9), Pesos(850_000)))
        f.setMonthlyBudget(Pesos(900_000), nov)

        assertEquals(Pesos(900_000), f.monthStatus(nov).budget)
    }

    @Test
    fun `estado del mes con gastado, saldo y semaforo`() = runTest {
        f.setMonthlyBudget(Pesos(100_000), nov)
        f.startTrip()
        f.addLoose(Pesos(85_000))
        f.closeTrip()

        val status = f.monthStatus(nov)

        assertEquals(Pesos(85_000), status.spent)
        assertEquals(Pesos(15_000), status.remaining)
        assertEquals(TrafficLight.YELLOW, status.trafficLight)
    }

    @Test
    fun `sin presupuesto nunca definido el estado solo trae el gastado`() = runTest {
        val status = f.monthStatus(nov)

        assertNull(status.budget)
        assertNull(status.remaining)
        assertEquals(TrafficLight.NONE, status.trafficLight)
        assertEquals(Pesos.ZERO, status.spent)
    }

    @Test
    fun `el presupuesto debe ser positivo`() = runTest {
        assertFailsWith<IllegalArgumentException> { f.setMonthlyBudget(Pesos.ZERO, nov) }
    }

    @Test
    fun `el estado dice si el presupuesto es heredado`() = runTest {
        f.budgets.set(Budget(YearMonth.of(2026, 9), Pesos(850_000)))
        assertTrue(f.monthStatus(nov).inherited)

        f.setMonthlyBudget(Pesos(900_000), nov)
        assertFalse(f.monthStatus(nov).inherited)
    }

    @Test
    fun `el estado observado se recalcula al definir presupuesto y al cerrar una compra`() = runTest {
        val observe = ObserveMonthStatus(f.budgets, f.trips, f.monthStatus)
        assertNull(observe().first().budget)

        f.setMonthlyBudget(Pesos(100_000))
        assertEquals(Pesos(100_000), observe().first().budget)

        f.startTrip()
        f.addLoose(Pesos(30_000))
        f.closeTrip()
        assertEquals(Pesos(70_000), observe().first().remaining)
    }
}
