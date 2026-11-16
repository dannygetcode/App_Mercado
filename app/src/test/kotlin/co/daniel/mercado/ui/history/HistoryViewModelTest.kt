package co.daniel.mercado.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.ui.catalog.await
import co.daniel.mercado.ui.trip.ManualClock
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

    private val clock = ManualClock()
    private val t = TripGraph(clock)
    private val vms = mutableListOf<ViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        t.close()
        Dispatchers.resetMain()
    }

    private fun closedTrip(price: Long, store: String? = null, paid: Long? = null): Long = runBlocking {
        t.graph.startTrip(TripLimit.None)
        t.graph.addLoosePrice(Pesos(price))
        val id = t.graph.trips.getActive()!!.trip.id
        t.graph.closeTrip(totalPaid = paid?.let(::Pesos), store = store)
        id
    }

    @Test
    fun `sin compras cerradas el historial queda vacio`() {
        val vm = HistoryViewModel { t.graph }.also { vms += it }
        assertTrue(vm.state.await { !it.loading }.months.isEmpty())
    }

    @Test
    fun `agrupa por mes con su total, el mas reciente primero`() {
        clock.setBogota(2026, 10, 20)
        closedTrip(100_000, "La Granja")
        clock.setBogota(2026, 11, 2)
        closedTrip(50_000)
        clock.setBogota(2026, 11, 16)
        closedTrip(30_000, paid = 29_000)
        val vm = HistoryViewModel { t.graph }.also { vms += it }

        val months = vm.state.await { !it.loading }.months

        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), months.map { it.month })
        assertEquals(listOf(Pesos(79_000), Pesos(100_000)), months.map { it.total })
        assertEquals(listOf(Pesos(29_000), Pesos(50_000)), months[0].trips.map { it.trip.effectiveTotal })
        assertEquals("La Granja", months[1].trips.single().trip.store)
    }

    @Test
    fun `el detalle muestra lineas y permite editar y quitar el total pagado`() {
        val id = closedTrip(12_000)
        val history = HistoryViewModel { t.graph }.also { vms += it }
        history.state.await { it.months.isNotEmpty() }
        val vm = TripDetailViewModel({ t.graph }, id).also { vms += it }
        val s = vm.state.await { !it.loading }
        assertEquals(1, s.detail!!.items.size)
        assertNull(s.detail.trip.totalPaid)

        vm.onEvent(TripDetailEvent.EditPaid)
        assertTrue(vm.state.value.paidSheet)
        vm.onEvent(TripDetailEvent.SetPaid(Pesos(11_500)))

        assertEquals(Pesos(11_500), vm.state.await { it.detail?.trip?.totalPaid != null }.detail!!.trip.totalPaid)
        assertEquals(Pesos(11_500), history.state.await { it.months.first().total == Pesos(11_500) }.months.first().total)

        vm.onEvent(TripDetailEvent.SetPaid(Pesos.ZERO))
        assertNull(vm.state.await { it.detail?.trip?.totalPaid == null }.detail!!.trip.totalPaid)
    }

    @Test
    fun `una compra abierta no tiene detalle de historial`() {
        val id = runBlocking {
            t.graph.startTrip(TripLimit.None)
            t.graph.trips.getActive()!!.trip.id
        }
        val vm = TripDetailViewModel({ t.graph }, id).also { vms += it }
        assertTrue(vm.state.await { !it.loading }.notFound)
    }
}
