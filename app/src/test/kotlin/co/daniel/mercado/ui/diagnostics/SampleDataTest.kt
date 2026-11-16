package co.daniel.mercado.ui.diagnostics

import co.daniel.mercado.domain.rules.Months
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Puerta de L7: con 4 meses de ejemplo la lista sugiere y el resumen da cifras coherentes. */
@RunWith(RobolectricTestRunner::class)
class SampleDataTest {

    private val t = TripGraph()

    @After
    fun tearDown() = t.close()

    @Test
    fun `cuatro meses de ejemplo alimentan la lista y el resumen`() = runBlocking {
        t.clock.setBogota(2026, 11, 16)
        repeat(20) { t.product("Producto $it", 1_000L + it * 250) }
        val g = t.graph

        val trips = loadSampleMonths(g, t.clock.now)

        assertTrue(trips in 7..10)
        assertTrue(g.buildSuggestedList() > 0)
        assertTrue(g.observeShoppingList().first().all { it.reason != null })
        val month = Months.monthOf(t.clock.now).minusMonths(1)
        val summary = g.getMonthSummary(month)
        val linesTotal = summary.categories.fold(0L) { acc, c -> acc + c.spent.value }
        assertEquals(summary.status.spent.value, linesTotal)
        assertTrue(summary.topProducts.isNotEmpty())
        assertTrue(summary.priceUps.isNotEmpty())
        assertTrue(g.getMonthsComparison(Months.monthOf(t.clock.now)).count { it.spent.isPositive } >= 4)
    }
}
