package co.daniel.mercado.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.ui.catalog.await
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SummaryViewModelTest {

    private val t = TripGraph()
    private val g get() = t.graph
    private val vms = mutableListOf<ViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        t.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `resumen del mes actual y navegacion entre meses`() {
        t.clock.setBogota(2026, 10, 10)
        runBlocking {
            val arroz = t.product("Arroz", 5_000)
            g.setMonthlyBudget(Pesos(400_000))
            g.startTrip(TripLimit.None)
            g.addProductToTrip(arroz)
            g.closeTrip()
        }
        t.clock.setBogota(2026, 11, 16)
        val vm = SummaryViewModel({ g }, t.clock).also { vms += it }

        val nov = vm.state.await { !it.loading }
        assertEquals(YearMonth.of(2026, 11), nov.month)
        assertFalse(nov.canGoNext)
        assertEquals(Pesos.ZERO, nov.summary!!.status.spent)
        assertEquals(6, nov.lastMonths.size)
        assertEquals(Pesos(5_000), nov.lastMonths[4].spent)

        vm.onEvent(SummaryEvent.PreviousMonth)
        val oct = vm.state.await { it.month == YearMonth.of(2026, 10) && !it.loading }
        assertTrue(oct.canGoNext)
        assertEquals(listOf("Arroz"), oct.summary!!.topProducts.map { it.name })
        vm.onEvent(SummaryEvent.NextMonth)
        vm.state.await { it.month == YearMonth.of(2026, 11) && !it.loading }
    }

    @Test
    fun `topes por categoria se definen y se quitan con cero`() {
        val vm = CategoryCapsViewModel { g }.also { vms += it }
        val carnes = vm.state.await { it.categories.isNotEmpty() }.categories.first()

        vm.onEvent(CategoryCapsEvent.Edit(carnes))
        vm.onEvent(CategoryCapsEvent.Save(Pesos(150_000)))
        assertEquals(Pesos(150_000), vm.state.await { s -> s.categories.first().monthlyBudget != null }.categories.first().monthlyBudget)

        vm.onEvent(CategoryCapsEvent.Edit(vm.state.value.categories.first()))
        vm.onEvent(CategoryCapsEvent.Save(Pesos.ZERO))
        assertNull(vm.state.await { s -> s.categories.first().monthlyBudget == null }.categories.first().monthlyBudget)
    }
}
