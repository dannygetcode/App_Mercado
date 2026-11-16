package co.daniel.mercado.ui.catalog

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

fun <T> StateFlow<T>.await(pick: (T) -> Boolean): T =
    runBlocking { withTimeout(5_000) { mapNotNull { s -> s.takeIf(pick) }.first() } }

fun <T> Flow<T>.next(): T = runBlocking { withTimeout(5_000) { first() } }

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogViewModelTest {

    private val t = TripGraph()
    private val vms = mutableListOf<androidx.lifecycle.ViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        t.close()
        Dispatchers.resetMain()
    }

    private fun catalog(needsReview: Boolean = false) = CatalogViewModel({ t.graph }, needsReview).also { vms += it }

    private fun edit(id: Long = 0) = ProductEditViewModel({ t.graph }, id).also { vms += it }

    private fun names(vm: CatalogViewModel) = vm.state.value.rows.map { it.product.name }

    @Test
    fun `lista el catalogo, busca sin tildes y filtra por categoria`() {
        runBlocking {
            t.product("Limón Tahití", 3_000)
            t.product("Arroz Diana", 5_900)
        }
        val vm = catalog()
        vm.state.await { it.rows.size == 2 }
        assertEquals(listOf("Arroz Diana", "Limón Tahití"), names(vm))
        assertTrue(vm.state.value.categories.size >= 10)

        vm.onEvent(CatalogEvent.Query("limon"))
        vm.state.await { it.rows.size == 1 }
        assertEquals(listOf("Limón Tahití"), names(vm))

        vm.onEvent(CatalogEvent.Query(""))
        val lacteos = vm.state.value.categories.first { it.name != "Otros" }.id
        vm.onEvent(CatalogEvent.SelectCategory(lacteos))
        assertTrue(vm.state.await { it.rows.isEmpty() }.isFiltered)
        vm.onEvent(CatalogEvent.SelectCategory(lacteos))
        assertNull(vm.state.await { it.rows.size == 2 }.filter.categoryId)
    }

    @Test
    fun `favoritos en su orden y se pueden reordenar`() {
        runBlocking {
            t.product("Arroz", 1_000, favorite = true)
            t.product("Leche", 1_000, favorite = true)
            t.product("Pan", 1_000)
        }
        val vm = catalog()
        vm.state.await { it.rows.size == 3 }
        vm.onEvent(CatalogEvent.ToggleFavorites)
        vm.state.await { it.rows.size == 2 }
        val leche = vm.state.value.rows.first { it.product.name == "Leche" }.product.id

        vm.onEvent(CatalogEvent.MoveFavorite(leche, up = true))

        vm.state.await { it.rows.firstOrNull()?.product?.name == "Leche" }
        assertEquals(listOf("Leche", "Arroz"), names(vm))
    }

    @Test
    fun `abre filtrado en por revisar desde el inicio`() {
        runBlocking {
            t.graph.startTrip()
            t.graph.addUnknownScanned(TripGraph.DESCONOCIDO, Pesos(2_000))
            t.graph.closeTrip()
            t.product("Arroz", 1_000)
        }
        val vm = catalog(needsReview = true)

        val s = vm.state.await { !it.loading && it.needsReviewCount == 1 }
        assertEquals(listOf("Producto ····7897"), s.rows.map { it.product.name })
        vm.onEvent(CatalogEvent.ToggleNeedsReview)
        assertEquals(2, vm.state.await { it.rows.size == 2 }.rows.size)
    }

    @Test
    fun `crear producto nuevo con codigo escaneado, y error sin nombre o repetido`() {
        runBlocking { t.product("Arroz", 1_000) }
        val vm = edit()
        val s0 = vm.state.await { !it.loading }
        assertTrue(s0.isNew)
        assertEquals("Otros", s0.categories.first { it.id == s0.categoryId }.name)

        vm.onEvent(ProductEditEvent.SetPrice(Pesos(4_300)))
        vm.onEvent(ProductEditEvent.Save)
        assertEquals(ProductError.NAME, vm.state.await { it.error != null }.error)

        vm.onEvent(ProductEditEvent.Name("arroz"))
        vm.onEvent(ProductEditEvent.Save)
        assertEquals(ProductError.DUPLICATE, vm.state.await { it.error != null }.error)

        vm.onEvent(ProductEditEvent.Name("Leche entera"))
        vm.onEvent(ProductEditEvent.Favorite(true))
        vm.onEvent(ProductEditEvent.StartScan)
        vm.onEvent(ProductEditEvent.Scanned(TripGraph.LECHE))
        assertEquals(listOf(TripGraph.LECHE), vm.state.value.codes)
        vm.onEvent(ProductEditEvent.Save)

        assertEquals(ProductEditEffect.Done, vm.effects.next())
        val saved = runBlocking { t.graph.products.findByBarcode(TripGraph.LECHE) }!!
        assertEquals("Leche entera", saved.name)
        assertEquals(Pesos(4_300), saved.refPrice)
        assertTrue(saved.favorite)
    }

    @Test
    fun `editar producto existente cambia precio con historial y maneja codigos al momento`() {
        val arrozId = runBlocking { t.product("Arroz", 5_900, code = TripGraph.ARROZ) }
        val lecheId = runBlocking { t.product("Leche", 4_300, code = TripGraph.LECHE) }
        val vm = edit(arrozId)
        val s0 = vm.state.await { !it.loading }
        assertEquals(listOf(TripGraph.ARROZ), s0.codes)
        assertEquals(UnitType.UNIT, s0.unitType)

        vm.onEvent(ProductEditEvent.StartScan)
        vm.onEvent(ProductEditEvent.Scanned(TripGraph.LECHE))
        val moved = vm.state.await { it.notice != null }
        assertEquals(ProductNotice.CodeMoved("Leche"), moved.notice)
        assertEquals(setOf(TripGraph.ARROZ, TripGraph.LECHE), moved.codes.toSet())
        assertTrue(runBlocking { t.graph.products.barcodesOf(lecheId) }.isEmpty())

        vm.onEvent(ProductEditEvent.RemoveCode(TripGraph.ARROZ))
        vm.state.await { it.codes == listOf(TripGraph.LECHE) }
        assertNull(runBlocking { t.graph.products.findByBarcode(TripGraph.ARROZ) })

        vm.onEvent(ProductEditEvent.SetPrice(Pesos(6_200)))
        vm.onEvent(ProductEditEvent.Save)
        assertEquals(ProductEditEffect.Done, vm.effects.next())
        val history = runBlocking { t.graph.getProductDetail(arrozId) }!!.priceHistory
        assertEquals(Pesos(6_200), history.first().price)
        assertEquals(PriceSource.MANUAL, history.first().source)
    }

    @Test
    fun `desactivar saca el producto del catalogo y reactivar lo devuelve`() {
        val arrozId = runBlocking { t.product("Arroz", 5_900) }
        val list = catalog()
        list.state.await { it.rows.size == 1 }
        val vm = edit(arrozId)
        vm.state.await { !it.loading }

        vm.onEvent(ProductEditEvent.Deactivate)
        assertFalse(vm.state.await { !it.active }.active)
        list.state.await { it.rows.isEmpty() }

        vm.onEvent(ProductEditEvent.Reactivate)
        vm.state.await { it.active }
        list.state.await { it.rows.size == 1 }
    }
}
