package co.daniel.mercado.ui.home

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private val t = TripGraph()
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private fun await(pick: (HomeUiState) -> Boolean): HomeUiState =
        runBlocking { withTimeout(5_000) { vm.state.mapNotNull { s -> s.takeIf(pick) }.first() } }

    @Test
    fun `la hoja de limite se prellena con lo que queda del mes y empezar abre la compra`() {
        runBlocking { t.graph.setMonthlyBudget(Pesos(900_000)) }
        vm = HomeViewModel({ t.graph })
        await { !it.loading }

        vm.openLimitSheet()
        val s = await { it.limitSheet }
        assertEquals(Pesos(900_000), s.suggestedLimit)

        var started = false
        vm.startTrip(Pesos(250_000)) { started = true }
        val open = await { it.openTripTotal != null }
        assertTrue(started)
        assertEquals(Pesos.ZERO, open.openTripTotal)
        assertEquals(Pesos(250_000), runBlocking { t.graph.trips.getActive()!!.trip.budgetLimit })
    }

    @Test
    fun `sin presupuesto y con 0 la compra empieza sin limite`() {
        vm = HomeViewModel({ t.graph })
        await { !it.loading }
        vm.openLimitSheet()
        assertNull(await { it.limitSheet }.suggestedLimit)

        vm.startTrip(Pesos.ZERO) {}

        await { it.openTripTotal != null }
        assertNull(runBlocking { t.graph.trips.getActive()!!.trip.budgetLimit })
    }
}
