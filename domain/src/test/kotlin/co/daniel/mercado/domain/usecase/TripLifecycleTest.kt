package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.FakeClock
import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.fakes.TripFixture.Companion.ARROZ
import co.daniel.mercado.domain.fakes.TripFixture.Companion.DESCONOCIDO
import co.daniel.mercado.domain.fakes.TripFixture.Companion.LECHE
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.UnitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripLifecycleTest {

    private val f = TripFixture()

    @Test
    fun `StartTrip falla si ya hay una abierta`() = runTest {
        val first = assertIs<StartTripResult.Started>(f.startTrip())

        assertEquals(StartTripResult.AlreadyOpen(first.tripId), f.startTrip())
        assertEquals(1, f.trips.trips.count { it.isOpen })
    }

    @Test
    fun `el limite por defecto es lo que queda del mes`() = runTest {
        f.setMonthlyBudget(Pesos(900_000))
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip()
        f.handleScan(ARROZ)
        f.closeTrip(totalPaid = Pesos(612_000))

        val started = assertIs<StartTripResult.Started>(f.startTrip())

        assertEquals(Pesos(288_000), started.limit)
        assertEquals(Pesos(288_000), f.trips.getTrip(started.tripId)?.budgetLimit)
    }

    @Test
    fun `sin presupuesto la compra no tiene limite y se puede fijar uno a mano`() = runTest {
        assertNull(assertIs<StartTripResult.Started>(f.startTrip()).limit)
        f.discardTrip()

        val custom = assertIs<StartTripResult.Started>(f.startTrip(TripLimit.Custom(Pesos(150_000))))

        assertEquals(Pesos(150_000), custom.limit)
    }

    @Test
    fun `el presupuesto de la compra se cambia en curso sin tocar lo gastado del mes`() = runTest {
        val nov = YearMonth.of(2026, 11)
        f.setMonthlyBudget(Pesos(900_000), nov)
        f.startTrip()
        f.addLoose(Pesos(30_000))
        f.closeTrip()
        f.startTrip(TripLimit.None)
        f.addLoose(Pesos(40_000))

        assertTrue(f.setTripLimit(Pesos(100_000)))
        val active = assertNotNull(f.observeActiveTrip().first())
        assertEquals(Pesos(100_000), active.limit)
        assertEquals(Pesos(60_000), active.remaining)
        assertEquals(Pesos(40_000), active.total)

        f.setTripLimit(Pesos.ZERO)
        assertNull(f.observeActiveTrip().first()?.limit)
        f.closeTrip()
        assertEquals(Pesos(70_000), f.monthStatus(nov).spent)
        assertFalse(f.setTripLimit(Pesos(50_000)))
    }

    @Test
    fun `CloseTrip fija total y fecha y actualiza referencia e historial solo si cambio`() = runTest {
        val arroz = f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        val leche = f.product("Leche Alquería 1 L", 4_300, LECHE)
        f.startTrip()
        val arrozLine = assertIs<ScanOutcome.Added>(f.handleScan(ARROZ)).lineId
        f.handleScan(LECHE)
        f.changePrice(arrozLine, Pesos(6_200))
        f.clock.advance(60_000)

        val closed = assertIs<CloseTripResult.Closed>(f.closeTrip(store = "  D1  "))

        val trip = requireNotNull(f.trips.getTrip(closed.tripId))
        assertEquals(Pesos(10_500), trip.totalEstimated)
        assertEquals(Pesos(10_500), closed.total)
        assertEquals(f.clock.now, trip.closedAt)
        assertEquals("D1", trip.store)
        assertEquals(Pesos(6_200), f.products.getById(arroz.id)?.refPrice)
        assertEquals(Pesos(4_300), f.products.getById(leche.id)?.refPrice)
        val arrozHistory = f.products.priceHistory.filter { it.productId == arroz.id }
        assertEquals(listOf(PriceSource.SEED, PriceSource.TRIP), arrozHistory.map { it.source })
        assertEquals(Pesos(6_200), arrozHistory.last().price)
        assertEquals(1, f.products.priceHistory.count { it.productId == leche.id })
        assertNull(f.trips.getActive())
    }

    @Test
    fun `CloseTrip actualiza la referencia de KG sin historial`() = runTest {
        val tomate = f.product("Tomate chonto", 4_200, unit = UnitType.KG)
        f.startTrip()
        f.addWeighed(tomate.id, Pesos(3_100))
        f.addWeighed(tomate.id, Pesos(5_300))

        f.closeTrip()

        assertEquals(Pesos(5_300), f.products.getById(tomate.id)?.refPrice)
        assertTrue(f.products.priceHistory.none { it.productId == tomate.id })
    }

    @Test
    fun `CloseTrip devuelve los productos pendientes de nombrar`() = runTest {
        f.startTrip()
        f.addUnknown(DESCONOCIDO, Pesos(7_800))

        val closed = assertIs<CloseTripResult.Closed>(f.closeTrip())

        assertEquals(listOf("Producto ····7897"), closed.needsReview.map { it.name })
    }

    @Test
    fun `con total pagado el gastado del mes usa el pagado y sin el usa el estimado`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip()
        f.handleScan(ARROZ)
        f.closeTrip(totalPaid = Pesos(5_500))
        f.startTrip()
        f.afterDebounce()
        f.handleScan(ARROZ)
        f.closeTrip()

        assertEquals(Pesos(11_400), f.monthStatus().spent)
    }

    @Test
    fun `el total pagado se puede editar despues de cerrar`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip()
        f.handleScan(ARROZ)
        val closed = assertIs<CloseTripResult.Closed>(f.closeTrip())

        assertTrue(f.setPaidTotal(closed.tripId, Pesos(5_000)))

        assertEquals(Pesos(5_000), f.monthStatus().spent)
    }

    @Test
    fun `una compra vacia no se cierra, se descarta`() = runTest {
        f.startTrip()

        assertEquals(CloseTripResult.DiscardedEmpty, f.closeTrip())
        assertTrue(f.trips.trips.isEmpty())
    }

    @Test
    fun `descartar borra la compra abierta y sus lineas`() = runTest {
        f.startTrip()
        f.addLoose(Pesos(1_000))

        assertTrue(f.discardTrip())

        assertNull(f.trips.getActive())
        assertTrue(f.trips.trips.isEmpty())
        assertFalse(f.discardTrip())
    }

    @Test
    fun `una compra cerrada el 30 de noviembre a las 2330 cuenta en noviembre`() = runTest {
        f.clock.now = FakeClock.bogota(2026, 11, 30, 23, 0)
        f.startTrip()
        f.addLoose(Pesos(50_000))
        f.clock.now = FakeClock.bogota(2026, 11, 30, 23, 30)
        f.closeTrip()

        assertEquals(Pesos(50_000), f.monthStatus(YearMonth.of(2026, 11)).spent)
        assertEquals(Pesos.ZERO, f.monthStatus(YearMonth.of(2026, 12)).spent)
    }
}
