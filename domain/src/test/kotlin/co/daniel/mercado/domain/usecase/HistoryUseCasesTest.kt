package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.model.Pesos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryUseCasesTest {

    private val f = TripFixture()

    @Test
    fun `el historial lista solo compras cerradas, la mas reciente primero, con sus articulos`() = runTest {
        f.startTrip()
        f.addLoose(Pesos(10_000))
        f.closeTrip(store = "La Granja")
        f.clock.advance(60_000)
        f.startTrip()
        val arroz = f.product("Arroz", 5_900)
        f.addProduct(arroz.id)
        f.addProduct(arroz.id)
        f.closeTrip()
        f.startTrip()

        val history = ObserveClosedTrips(f.trips)().first()

        assertEquals(listOf(Pesos(11_800), Pesos(10_000)), history.map { it.trip.effectiveTotal })
        assertEquals(listOf(2, 1), history.map { it.articleCount })
        assertEquals("La Granja", history[1].trip.store)
    }

    @Test
    fun `el detalle es de compras cerradas y el total pagado se puede editar`() = runTest {
        f.startTrip()
        f.addLoose(Pesos(10_000))
        val tripId = f.trips.getActive()!!.trip.id
        assertNull(GetTripDetail(f.trips)(tripId))
        f.closeTrip()

        f.setPaidTotal(tripId, Pesos(9_500))
        val detail = GetTripDetail(f.trips)(tripId)!!

        assertEquals(1, detail.items.size)
        assertEquals(Pesos(9_500), detail.trip.effectiveTotal)
    }
}
