package co.daniel.mercado.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.rules.SuggestionRules
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.scanner.FakeBarcodeSource
import co.daniel.mercado.ui.catalog.await
import co.daniel.mercado.ui.trip.PickerTab
import co.daniel.mercado.ui.trip.RecordingFeedback
import co.daniel.mercado.ui.trip.TripGraph
import co.daniel.mercado.ui.trip.TripViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingListViewModelTest {

    private val t = TripGraph()
    private val g get() = t.graph
    private val vms = mutableListOf<ViewModel>()
    private var arroz = 0L
    private var pan = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking {
            arroz = t.product("Arroz", 5_000)
            pan = t.product("Pan tajado", 6_000)
            repeat(2) {
                g.startTrip(TripLimit.None)
                g.addProductToTrip(arroz)
                g.closeTrip()
                t.clock.advance(14 * SuggestionRules.DAY_MS)
            }
        }
    }

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        t.close()
        Dispatchers.resetMain()
    }

    private fun list() = ShoppingListViewModel { g }.also { vms += it }

    @Test
    fun `al abrir arma los sugeridos con su motivo y se puede agregar, marcar y quitar`() {
        val vm = list()
        val s = vm.state.await { !it.loading && it.suggested.isNotEmpty() }
        assertEquals("Arroz", s.suggested.single().product.name)
        assertEquals(14, s.suggested.single().reason?.avgIntervalDays)

        vm.onEvent(ShoppingListEvent.OpenAdd)
        vm.onEvent(ShoppingListEvent.Query("pan"))
        val found = vm.state.await { it.results.isNotEmpty() }.results.single()
        vm.onEvent(ShoppingListEvent.Add(found.id))
        assertEquals("Pan tajado", vm.state.await { it.added.isNotEmpty() }.added.single().product.name)

        vm.onEvent(ShoppingListEvent.Toggle(pan))
        assertTrue(vm.state.await { it.added.singleOrNull()?.item?.checked == true }.added.single().item.checked)

        vm.onEvent(ShoppingListEvent.Remove(arroz))
        vm.onEvent(ShoppingListEvent.Remove(pan))
        vm.state.await { it.suggested.isEmpty() && it.added.isEmpty() }
    }

    @Test
    fun `la pestana Lista de la compra muestra lo que falta y desaparece al echarlo`() {
        runBlocking { g.addListItem(pan) }
        list().state.await { !it.loading && it.suggested.isNotEmpty() }
        runBlocking { g.startTrip(TripLimit.None) }
        val trip = TripViewModel({ g }, FakeBarcodeSource(), RecordingFeedback(), t.clock).also { vms += it }
        trip.state.await { it.listPending.size == 2 }

        trip.openPicker(PickerTab.LIST)
        trip.pickProduct(arroz)

        assertEquals(listOf("Pan tajado"), trip.state.await { it.listPending.size == 1 }.listPending.map { it.product.name })
        runBlocking { g.closeTrip(totalPaid = Pesos(5_000)) }
        assertTrue(runBlocking { g.shoppingList.getAll() }.first { it.productId == arroz }.checked)
    }
}
