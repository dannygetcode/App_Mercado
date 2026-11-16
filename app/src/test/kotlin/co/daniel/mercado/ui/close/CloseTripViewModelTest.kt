package co.daniel.mercado.ui.close

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.ui.trip.TripGraph
import co.daniel.mercado.ui.trip.TripGraph.Companion.ARROZ
import co.daniel.mercado.ui.trip.TripGraph.Companion.DESCONOCIDO
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
class CloseTripViewModelTest {

    private val t = TripGraph()
    private val g get() = t.graph
    private lateinit var vm: CloseTripViewModel

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private fun await(pick: (CloseTripUiState) -> Boolean): CloseTripUiState =
        runBlocking { withTimeout(5_000) { vm.state.mapNotNull { s -> s.takeIf(pick) }.first() } }

    private fun tripWithNewProduct() = runBlocking {
        t.product("Arroz Diana 1 kg", 5_900, ARROZ)
        g.startTrip(TripLimit.Custom(Pesos(10_000)))
        g.handleScan(ARROZ)
        g.addUnknownScanned(DESCONOCIDO, Pesos(7_800))
    }

    @Test
    fun `resumen, productos por nombrar y guardar con pagado, tienda y nombre`() {
        tripWithNewProduct()
        vm = CloseTripViewModel({ g })
        val s = await { !it.loading }
        assertEquals(Pesos(13_700), s.trip?.total)
        val pending = s.review.single()
        assertEquals("Producto ····7897", pending.product.name)

        vm.setTotalPaid(Pesos(13_500))
        vm.setStore("La Granja")
        vm.setReviewName(pending.product.id, "Galletas de avena")
        vm.save()

        await { it.done }
        val closed = runBlocking { g.trips.getTrip(s.trip!!.trip.id)!! }
        assertEquals(Pesos(13_500), closed.totalPaid)
        assertEquals("La Granja", closed.store)
        val named = runBlocking { g.products.getById(pending.product.id)!! }
        assertEquals("Galletas de avena", named.name)
        assertFalse(named.needsReview)
        assertNull(runBlocking { g.trips.getActive() })
        assertEquals(listOf("La Granja"), runBlocking { g.trips.recentStores(5) })
    }

    @Test
    fun `nombrar se puede omitir`() {
        tripWithNewProduct()
        vm = CloseTripViewModel({ g })
        val pending = await { !it.loading }.review.single()

        vm.save()

        await { it.done }
        assertTrue(runBlocking { g.products.getById(pending.product.id)!!.needsReview })
    }

    @Test
    fun `nombre repetido bloquea y avisa sin cerrar`() {
        tripWithNewProduct()
        vm = CloseTripViewModel({ g })
        val pending = await { !it.loading }.review.single()

        vm.setReviewName(pending.product.id, "arroz diana 1 KG")
        vm.save()

        val s = await { it.review.single().duplicate }
        assertFalse(s.done)
        assertTrue(runBlocking { g.trips.getActive() } != null)
    }

    @Test
    fun `descartar pide confirmacion y borra la compra`() {
        tripWithNewProduct()
        vm = CloseTripViewModel({ g })
        await { !it.loading }

        vm.askDiscard()
        assertTrue(vm.state.value.confirmDiscard)
        vm.discard()

        await { it.done }
        assertNull(runBlocking { g.trips.getActive() })
    }
}
