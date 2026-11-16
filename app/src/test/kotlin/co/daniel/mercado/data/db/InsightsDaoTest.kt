package co.daniel.mercado.data.db

import co.daniel.mercado.domain.model.ListSource
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.ShoppingListItem
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Consultas de L7 sobre Room: líneas cerradas, última compra, lista y topes. */
@RunWith(RobolectricTestRunner::class)
class InsightsDaoTest {

    private val t = TripGraph()
    private val g get() = t.graph

    @After
    fun tearDown() = t.close()

    @Test
    fun `lineas de compras cerradas con su tipo de unidad, sueltas como UNIT`() = runBlocking {
        val papa = t.product("Papa", 2_000, unit = UnitType.KG)
        val arroz = t.product("Arroz", 5_000)
        assertNull(g.trips.lastClosedAt())
        g.startTrip(TripLimit.None)
        g.addWeighedItem(papa, Pesos(3_100))
        g.addProductToTrip(arroz)
        g.addProductToTrip(arroz)
        g.addLoosePrice(Pesos(900))
        g.closeTrip()
        g.startTrip(TripLimit.None)
        g.addLoosePrice(Pesos(1))

        val lines = g.trips.closedLines(0, Long.MAX_VALUE)

        assertEquals(3, lines.size)
        assertEquals(UnitType.KG, lines.first { it.productId == papa }.unitType)
        assertEquals(2, lines.first { it.productId == arroz }.quantity)
        assertEquals(UnitType.UNIT, lines.first { it.productId == null }.unitType)
        assertEquals(t.clock.now, g.trips.lastClosedAt())
        assertEquals(emptyList<Any>(), g.trips.closedLines(0, t.clock.now))
    }

    @Test
    fun `lista de mercado y tope de categoria`() = runBlocking {
        val arroz = t.product("Arroz", 5_000)
        g.shoppingList.upsert(ShoppingListItem(arroz, 2, source = ListSource.AUTO))
        g.shoppingList.upsert(ShoppingListItem(arroz, 3, source = ListSource.MANUAL))
        assertEquals(listOf(ShoppingListItem(arroz, 3, source = ListSource.MANUAL)), g.shoppingList.observeAll().first())
        g.shoppingList.delete(arroz)
        assertEquals(emptyList<ShoppingListItem>(), g.shoppingList.getAll())

        val otros = g.products.categoryByName("Otros")!!.id
        g.setCategoryBudget(otros, Pesos(80_000))
        assertEquals(Pesos(80_000), g.products.categories().first { it.id == otros }.monthlyBudget)
        g.setCategoryBudget(otros, null)
        assertNull(g.products.categories().first { it.id == otros }.monthlyBudget)
        assertEquals(listOf("Arroz"), g.products.getByIds(listOf(arroz, arroz, 999)).map { it.name })
    }
}
