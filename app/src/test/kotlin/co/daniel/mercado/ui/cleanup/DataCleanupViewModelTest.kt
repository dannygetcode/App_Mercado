package co.daniel.mercado.ui.cleanup

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.domain.usecase.TripsInRange
import co.daniel.mercado.ui.catalog.await
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataCleanupViewModelTest {

    private val t = TripGraph()
    private val g get() = t.graph
    private var resets = 0
    private lateinit var vm: DataCleanupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking {
            t.clock.setBogota(2026, 10, 10)
            closed(3_000)
            closed(4_000)
            t.clock.setBogota(2026, 11, 5)
            closed(5_000)
        }
        vm = DataCleanupViewModel({ g }, t.clock) { resets++ }
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private suspend fun closed(price: Long) {
        g.startTrip(TripLimit.None)
        g.addLoosePrice(Pesos(price))
        g.closeTrip()
    }

    @Test
    fun `mes anterior muestra cuantas compras hay y borrarlas pide confirmacion`() {
        vm.onEvent(DataCleanupEvent.LastMonth)
        val s = vm.state.await { it.preview != null }
        assertEquals(LocalDate.of(2026, 10, 1), s.from)
        assertEquals(LocalDate.of(2026, 10, 31), s.to)
        assertEquals(TripsInRange(2, Pesos(7_000)), s.preview)

        vm.onEvent(DataCleanupEvent.Ask(CleanupConfirm.TRIPS))
        assertEquals(CleanupConfirm.TRIPS, vm.state.value.confirm)
        vm.onEvent(DataCleanupEvent.Confirm)

        assertEquals(CleanupMessage.TripsDeleted(2), vm.state.await { it.message != null }.message)
        assertEquals(1, runBlocking { g.trips.observeClosed().first() }.size)
        assertEquals(0, vm.state.await { it.preview?.count == 0 }.preview?.count)
    }

    @Test
    fun `rango elegido con fechas en cualquier orden`() {
        vm.onEvent(DataCleanupEvent.SetRange(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 10, 1)))
        val s = vm.state.await { it.preview != null }
        assertEquals(LocalDate.of(2026, 10, 1), s.from)
        assertEquals(3, s.preview?.count)
    }

    @Test
    fun `elegir y borrar productos con busqueda`() {
        val arroz = runBlocking { t.product("Arroz Diana", 5_900) }
        runBlocking { t.product("Leche", 4_300) }
        vm.onEvent(DataCleanupEvent.OpenProducts)
        vm.state.await { it.products.size == 2 }
        vm.onEvent(DataCleanupEvent.Query("arroz"))
        assertEquals(listOf("Arroz Diana"), vm.state.value.visibleProducts.map { it.name })
        vm.onEvent(DataCleanupEvent.ToggleProduct(arroz))

        vm.onEvent(DataCleanupEvent.Ask(CleanupConfirm.PRODUCTS))
        vm.onEvent(DataCleanupEvent.Confirm)

        assertEquals(CleanupMessage.ProductsDeleted(1), vm.state.await { it.message != null }.message)
        assertNull(runBlocking { g.products.getById(arroz) })
        assertTrue(vm.state.value.selected.isEmpty())
    }

    @Test
    fun `borrar todo llama al reinicio solo al confirmar`() {
        vm.onEvent(DataCleanupEvent.Ask(CleanupConfirm.ALL))
        vm.onEvent(DataCleanupEvent.Dismiss)
        assertEquals(0, resets)
        vm.onEvent(DataCleanupEvent.Ask(CleanupConfirm.ALL))
        vm.onEvent(DataCleanupEvent.Confirm)
        assertEquals(CleanupMessage.AllDeleted, vm.state.await { it.message != null }.message)
        assertEquals(1, resets)
    }
}
