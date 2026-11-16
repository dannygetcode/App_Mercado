package co.daniel.mercado.ui.home

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.data.settings.InMemoryPreferencesDataStore
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.TripLimit
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
import org.junit.Assert.assertFalse
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
    private val settings = SettingsStore(InMemoryPreferencesDataStore(), secureScreenDefault = false)
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private fun start() {
        vm = HomeViewModel({ t.graph }, settings)
        await { !it.loading && it.month != null }
    }

    private fun await(pick: (HomeUiState) -> Boolean): HomeUiState =
        runBlocking { withTimeout(5_000) { vm.state.mapNotNull { s -> s.takeIf(pick) }.first() } }

    private fun nextEffect(): HomeEffect = runBlocking { withTimeout(5_000) { vm.effects.first() } }

    @Test
    fun `la hoja de limite se prellena con lo que queda del mes y empezar abre la compra`() {
        runBlocking { t.graph.setMonthlyBudget(Pesos(900_000)) }
        start()

        vm.onEvent(HomeEvent.StartOrContinue)
        assertEquals(HomeSheet.Limit(Pesos(900_000)), await { it.sheet != null }.sheet)

        vm.onEvent(HomeEvent.StartTrip(Pesos(250_000)))
        assertEquals(HomeEffect.OpenTrip, nextEffect())
        assertEquals(Pesos.ZERO, await { it.openTripTotal != null }.openTripTotal)
        assertNull(vm.state.value.sheet)
        assertEquals(Pesos(250_000), runBlocking { t.graph.trips.getActive()!!.trip.budgetLimit })
    }

    @Test
    fun `sin presupuesto y con 0 la compra empieza sin limite`() {
        start()
        vm.onEvent(HomeEvent.StartOrContinue)
        assertEquals(HomeSheet.Limit(null), await { it.sheet != null }.sheet)

        vm.onEvent(HomeEvent.StartTrip(Pesos.ZERO))

        assertEquals(HomeEffect.OpenTrip, nextEffect())
        assertNull(runBlocking { t.graph.trips.getActive()!!.trip.budgetLimit })
    }

    @Test
    fun `con compra abierta el boton la continua sin hoja`() {
        runBlocking { t.graph.startTrip(TripLimit.None) }
        start()
        await { it.openTripTotal != null }

        vm.onEvent(HomeEvent.StartOrContinue)

        assertEquals(HomeEffect.OpenTrip, nextEffect())
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun `primer uso sin presupuesto muestra la tarjeta y omitirla la oculta para siempre`() {
        start()
        assertTrue(await { !it.budgetHintDismissed }.showBudgetHint)

        vm.onEvent(HomeEvent.DismissBudgetHint)

        assertFalse(await { it.budgetHintDismissed }.showBudgetHint)
        assertTrue(runBlocking { settings.current().budgetHintDismissed })
    }

    @Test
    fun `definir el presupuesto actualiza el mes y quita la tarjeta`() {
        start()
        vm.onEvent(HomeEvent.EditBudget)
        assertEquals(HomeSheet.Budget(null), await { it.sheet != null }.sheet)

        vm.onEvent(HomeEvent.SaveBudget(Pesos(900_000)))

        val s = await { it.month?.budget != null }
        assertEquals(Pesos(900_000), s.month?.budget)
        assertFalse(s.showBudgetHint)
        assertNull(s.sheet)
    }

    @Test
    fun `cerrar una compra actualiza el gastado, la ultima compra y los productos por revisar`() {
        runBlocking { t.graph.setMonthlyBudget(Pesos(100_000)) }
        start()
        runBlocking {
            t.graph.startTrip(TripLimit.None)
            t.graph.addUnknownScanned(TripGraph.DESCONOCIDO, Pesos(30_000))
            t.graph.closeTrip(store = "La Granja")
        }

        val s = await { it.lastTrip != null && it.month?.spent == Pesos(30_000) }
        assertEquals(Pesos(70_000), s.month?.remaining)
        assertEquals("La Granja", s.lastTrip?.trip?.store)
        assertEquals(1, await { it.needsReviewCount == 1 }.needsReviewCount)
        assertNull(await { it.openTripTotal == null }.openTripTotal)
    }

    @Test
    fun `la tarjeta de la lista cuenta lo pendiente`() {
        val pan = runBlocking { t.product("Pan", 3_000) }
        start()
        assertEquals(0, vm.state.value.listPending)
        runBlocking { t.graph.addListItem(pan) }
        assertEquals(1, await { it.listPending == 1 }.listPending)
    }
}
