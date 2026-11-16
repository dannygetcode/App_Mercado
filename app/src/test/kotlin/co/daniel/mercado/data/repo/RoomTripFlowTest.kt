package co.daniel.mercado.data.repo

import co.daniel.mercado.data.db.Repos
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.ScanDebouncer
import co.daniel.mercado.domain.usecase.AddUnknownScanned
import co.daniel.mercado.domain.usecase.AddWeighedItem
import co.daniel.mercado.domain.usecase.CloseTrip
import co.daniel.mercado.domain.usecase.CloseTripResult
import co.daniel.mercado.domain.usecase.GetMonthStatus
import co.daniel.mercado.domain.usecase.HandleScan
import co.daniel.mercado.domain.usecase.ObserveActiveTrip
import co.daniel.mercado.domain.usecase.RemoveLine
import co.daniel.mercado.domain.usecase.StartTrip
import co.daniel.mercado.domain.usecase.StartTripResult
import co.daniel.mercado.domain.usecase.TripSession
import co.daniel.mercado.domain.usecase.UndoLastAction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Los casos de uso del dominio sobre los repositorios Room reales (base en memoria). */
@RunWith(RobolectricTestRunner::class)
class RoomTripFlowTest {

    private val r = Repos()
    private var now = 1_800_000_000_000L
    private val clock = Clock { now }
    private val session = TripSession(r.tx)
    private val monthStatus = GetMonthStatus(r.budgets, r.trips, clock)
    private val startTrip = StartTrip(r.trips, monthStatus, session, clock)
    private val handleScan = HandleScan(r.trips, r.products, ScanDebouncer(clock), session, clock)
    private val addUnknown = AddUnknownScanned(r.trips, r.products, session, clock) { "Producto ····$it" }
    private val addWeighed = AddWeighedItem(r.trips, r.products, session, clock)
    private val removeLine = RemoveLine(r.trips, session)
    private val undo = UndoLastAction(r.trips, r.products, session)
    private val closeTrip = CloseTrip(r.trips, r.products, session, clock)
    private val observe = ObserveActiveTrip(r.trips)

    @After
    fun close() = r.db.close()

    private suspend fun product(name: String, price: Long, code: String? = null, unit: UnitType = UnitType.UNIT): Long {
        val categoryId = r.products.categoryByName("Otros")!!.id
        val id = r.products.insert(Product(name = name, categoryId = categoryId, unitType = unit, refPrice = Pesos(price)))
        code?.let { r.products.linkBarcode(it, id, 0) }
        return id
    }

    @Test
    fun `invariante de una sola compra abierta aun con dos inicios simultaneos`() = runTest {
        val results = (1..5).map { async { startTrip() } }.awaitAll()

        assertEquals(1, results.count { it is StartTripResult.Started })
        assertEquals(1, r.db.tripDao().countOpen())
    }

    @Test
    fun `escanear, incrementar, borrar, deshacer y cerrar persisten en Room`() = runTest {
        val arroz = product("Arroz Diana 1 kg", 5_900, "7702004003508")
        startTrip()
        val line = (handleScan("7702004003508") as ScanOutcome.Added).lineId
        now += 2_000
        handleScan("7702004003508")
        val before = r.trips.getActive()!!.items

        removeLine(line)
        assertTrue(r.trips.getActive()!!.items.isEmpty())
        undo()
        assertEquals(before, r.trips.getActive()!!.items)

        val active = observe().first()!!
        assertEquals(Pesos(11_800), active.total)
        assertEquals(2, active.articleCount)

        val closed = closeTrip(totalPaid = Pesos(11_000)) as CloseTripResult.Closed
        assertNull(r.trips.getActive())
        assertEquals(Pesos(11_800), r.trips.getTrip(closed.tripId)!!.totalEstimated)
        assertEquals(Pesos(11_000), monthStatus().spent)
        assertEquals(PriceSource.TRIP, r.products.lastPricePoint(arroz)!!.source)
    }

    @Test
    fun `la linea de un producto KG conoce su tipo de unidad`() = runTest {
        val tomate = product("Tomate chonto", 4_200, unit = UnitType.KG)
        startTrip()

        addWeighed(tomate, Pesos(3_150))

        assertEquals(UnitType.KG, observe().first()!!.items.single().unitType)
    }

    @Test
    fun `deshacer un desconocido borra producto provisional y codigo en Room`() = runTest {
        startTrip()
        addUnknown("7701234567897", Pesos(7_800))
        assertEquals("Producto ····7897", r.products.findByBarcode("7701234567897")?.name)

        undo()

        assertNull(r.products.findByBarcode("7701234567897"))
        assertEquals(0, r.db.productDao().count())
        assertTrue(r.trips.getActive()!!.items.isEmpty())
    }
}
