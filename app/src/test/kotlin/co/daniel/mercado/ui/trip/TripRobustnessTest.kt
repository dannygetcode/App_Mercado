package co.daniel.mercado.ui.trip

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.daniel.mercado.data.db.MercadoDatabase
import co.daniel.mercado.data.db.MercadoDatabaseCallback
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.scanner.FakeBarcodeSource
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RNF-09 (L4-T10): matar el proceso a mitad de compra no pierde nada. Se simula cerrando la base
 * y el ViewModel, y abriendo otra base sobre el mismo archivo con un ViewModel nuevo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripRobustnessTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = ManualClock()
    private val feedback = RecordingFeedback()
    private val dbName = "robustez.db"

    private fun open(): DataGraph = DataGraph(
        Room.databaseBuilder(context, MercadoDatabase::class.java, dbName).addCallback(MercadoDatabaseCallback).build(),
        clock,
    ) { "Producto ····$it" }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context.deleteDatabase(dbName)
    }

    private fun TripViewModel.await(pick: (TripUiState) -> Boolean): TripUiState =
        runBlocking { withTimeout(5_000) { state.mapNotNull { s -> s.takeIf(pick) }.first() } }

    @Test
    fun `matar y recrear a mitad de compra conserva lineas, cantidades, precios y limite`() {
        val first = open()
        val source = FakeBarcodeSource()
        runBlocking {
            val cat = first.products.categoryByName("Otros")!!.id
            val id = first.products.insert(Product(name = "Arroz Diana 1 kg", categoryId = cat, refPrice = Pesos(5_900)))
            first.products.linkBarcode("7702004003508", id, 0)
            first.startTrip(TripLimit.Custom(Pesos(50_000)))
        }
        val vm1 = TripViewModel({ first }, source, feedback, clock)
        vm1.await { it.trip != null }
        runBlocking { source.emit("7702004003508") }
        vm1.await { it.trip?.total == Pesos(5_900) }
        val arrozLine = vm1.state.value.trip!!.items.single().id
        vm1.increment(arrozLine)
        vm1.await { it.trip?.total == Pesos(11_800) }
        vm1.openLooseKeypad()
        vm1.submitKeypad(Pesos(3_200))
        vm1.await { it.trip?.total == Pesos(15_000) }
        vm1.openFixPrice(arrozLine)
        vm1.submitKeypad(Pesos(6_000))
        val before = vm1.await { it.trip?.total == Pesos(15_200) }.trip!!

        // "Muerte" del proceso: se cancela el ViewModel y se cierra la base.
        vm1.viewModelScope.cancel()
        first.db.close()

        val second = open()
        val vm2 = TripViewModel({ second }, FakeBarcodeSource(), feedback, clock)
        val after = vm2.await { it.trip != null }.trip!!

        assertEquals(before.trip.id, after.trip.id)
        assertEquals(Pesos(15_200), after.total)
        assertEquals(Pesos(50_000), after.limit)
        assertEquals(before.items.map { Triple(it.nameSnapshot, it.quantity, it.unitPrice) },
            after.items.map { Triple(it.nameSnapshot, it.quantity, it.unitPrice) })
        assertEquals(3, after.articleCount)
        vm2.viewModelScope.cancel()
        second.db.close()
    }
}
