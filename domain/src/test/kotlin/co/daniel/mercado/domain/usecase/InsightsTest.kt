package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.FakeClock
import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.ListSource
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.rules.SuggestionRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {

    private val f = TripFixture()
    private val day = SuggestionRules.DAY_MS
    private val build = BuildSuggestedList(f.trips, f.shoppingList, f.tx, f.clock)
    private val observe = ObserveShoppingList(f.trips, f.products, f.shoppingList, f.clock)
    private val remove = RemoveListItem(f.trips, f.shoppingList, f.clock)
    private val priceChanges = GetPriceChanges(f.trips, f.products)
    private val summary = GetMonthSummary(f.trips, f.products, f.monthStatus, priceChanges)

    /** Compra cerrada en el instante actual del reloj con esos productos (id → cantidad). */
    private suspend fun trip(vararg lines: Pair<Product, Int>, loose: Long? = null) {
        f.startTrip()
        lines.forEach { (p, q) -> repeat(q) { f.addProduct(p.id) } }
        loose?.let { f.addLoose(Pesos(it)) }
        f.closeTrip()
    }

    @Test
    fun `la lista sugiere lo que toca, se marca al comprar y se limpia en la siguiente`() = runTest {
        val arroz = f.product("Arroz", 5_000)
        val leche = f.product("Leche", 4_000)
        trip(arroz to 2, leche to 1)
        f.clock.advance(14 * day)
        trip(arroz to 2)
        f.clock.advance(12 * day)

        assertEquals(1, build())
        val entry = observe().first().single()
        assertEquals("Arroz", entry.product.name)
        assertEquals(2, entry.item.suggestedQty)
        assertEquals(14, entry.reason?.avgIntervalDays)
        assertEquals(12, entry.reason?.daysSinceLast)

        trip(arroz to 2)
        assertTrue(f.shoppingList.getAll().single().checked)
        build()
        assertTrue(f.shoppingList.getAll().isEmpty())
    }

    @Test
    fun `quitar un sugerido lo silencia hasta su proximo ciclo y uno manual se borra`() = runTest {
        val arroz = f.product("Arroz", 5_000)
        val pan = f.product("Pan", 3_000)
        trip(arroz to 1)
        f.clock.advance(10 * day)
        trip(arroz to 1)
        f.clock.advance(9 * day)
        build()
        AddListItem(f.shoppingList)(pan.id)
        assertEquals(2, observe().first().size)

        remove(arroz.id)
        remove(pan.id)
        assertEquals(emptyList(), observe().first())
        build()
        assertEquals(emptyList(), observe().first())

        f.clock.advance(11 * day)
        build()
        assertEquals(listOf("Arroz"), observe().first().map { it.product.name })
        assertEquals(ListSource.AUTO, observe().first().single().item.source)
    }

    @Test
    fun `pendientes de la compra excluye lo que ya esta en el carrito`() = runTest {
        val arroz = f.product("Arroz", 5_000)
        val pan = f.product("Pan", 3_000)
        AddListItem(f.shoppingList)(arroz.id)
        AddListItem(f.shoppingList)(pan.id)
        f.startTrip()
        f.addProduct(arroz.id)

        assertEquals(listOf("Pan"), observe.pendingForTrip().first().map { it.product.name })
    }

    @Test
    fun `resumen del mes por categoria con Otros, top de productos, topes y variaciones`() = runTest {
        f.clock.now = FakeClock.bogota(2026, 10, 20)
        val arroz = f.product("Arroz", 5_000)
        trip(arroz to 1)
        f.clock.now = FakeClock.bogota(2026, 11, 5)
        f.products.update(arroz.copy(refPrice = Pesos(5_500)))
        trip(arroz to 2, loose = 7_000)
        SetCategoryBudget(f.products)(1, Pesos(20_000))

        val s = summary(YearMonth.of(2026, 11))

        assertEquals(Pesos(18_000), s.status.spent)
        val granos = s.categories.first { it.categoryId == 1L }
        assertEquals(Pesos(11_000), granos.spent)
        assertEquals(Pesos(20_000), granos.cap)
        assertEquals(Pesos(7_000), s.categories.first { it.name == Category.OTHER }.spent)
        assertEquals(listOf("Arroz"), s.topProducts.map { it.name })
        assertEquals(2, s.topProducts.single().quantity)
        val up = s.priceUps.single()
        assertEquals(Pesos(5_000), up.previous)
        assertEquals(Pesos(5_500), up.current)
        assertEquals(1_000L, up.basisPoints)
        assertTrue(s.priceDowns.isEmpty())
    }

    @Test
    fun `comparacion de seis meses termina en el mes pedido`() = runTest {
        f.setMonthlyBudget(Pesos(500_000), YearMonth.of(2026, 9))
        val months = GetMonthsComparison(f.monthStatus)(YearMonth.of(2026, 11))
        assertEquals(6, months.size)
        assertEquals(YearMonth.of(2026, 6), months.first().month)
        assertEquals(YearMonth.of(2026, 11), months.last().month)
        assertNull(months.first().budget)
        assertEquals(Pesos(500_000), months.last().budget)
    }

    @Test
    fun `aviso del mercado con sugeridos y no durante una compra`() = runTest {
        val remind = ShouldRemindShopping(f.trips, build, f.clock)
        val arroz = f.product("Arroz", 5_000)
        trip(arroz to 1)
        f.clock.advance(15 * day)
        trip(arroz to 1)
        f.clock.advance(14 * day)

        val r = assertNotNull(remind(frequencyDays = 15, lastReminderCycle = 0))
        assertEquals(1, r.suggestedCount)
        assertNull(remind(frequencyDays = 15, lastReminderCycle = r.cycle))
        f.startTrip()
        assertNull(remind(frequencyDays = 15, lastReminderCycle = 0))
    }
}
