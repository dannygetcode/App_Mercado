package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.FakeClock
import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.model.Pesos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CleanupTest {

    private val f = TripFixture()
    private val deleteTrips = DeleteTripsInRange(f.trips, f.tx)

    private suspend fun closedOn(year: Int, month: Int, day: Int, hour: Int, price: Long) {
        f.clock.now = FakeClock.bogota(year, month, day, hour)
        f.startTrip()
        f.addLoose(Pesos(price))
        f.closeTrip()
    }

    @Test
    fun `borra compras cerradas entre dos dias inclusive en hora de Colombia y no toca la abierta`() = runTest {
        closedOn(2026, 9, 30, 23, 1_000)
        closedOn(2026, 10, 1, 0, 2_000)
        closedOn(2026, 10, 31, 23, 3_000)
        closedOn(2026, 11, 1, 8, 4_000)
        f.startTrip()

        val oct1 = LocalDate.of(2026, 10, 1)
        val oct31 = LocalDate.of(2026, 10, 31)
        assertEquals(TripsInRange(2, Pesos(5_000)), deleteTrips.preview(oct1, oct31))
        assertEquals(2, deleteTrips(oct1, oct31))

        assertEquals(listOf(Pesos(4_000), Pesos(1_000)), f.trips.observeClosed().first().map { it.trip.effectiveTotal })
        assertNotNull(f.trips.getActive())
    }

    @Test
    fun `borrar productos conserva los renglones de compras pasadas`() = runTest {
        val arroz = f.product("Arroz", 5_000, TripFixture.ARROZ)
        val leche = f.product("Leche", 4_000)
        f.startTrip()
        f.addProduct(arroz.id)
        f.closeTrip()

        assertEquals(1, DeleteProducts(f.products, f.tx)(listOf(arroz.id)))

        assertNull(f.products.getById(arroz.id))
        assertNull(f.products.findByBarcode(TripFixture.ARROZ))
        assertNotNull(f.products.getById(leche.id))
        val line = f.trips.itemsOf(f.trips.trips.single().id).single()
        assertNull(line.productId)
        assertEquals("Arroz", line.nameSnapshot)
        assertEquals(Pesos(5_000), f.trips.trips.single().effectiveTotal)
    }
}
