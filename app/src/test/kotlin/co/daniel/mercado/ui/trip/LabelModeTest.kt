package co.daniel.mercado.ui.trip

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.scanner.FakeBarcodeSource
import co.daniel.mercado.scanner.LabelRead
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Modo etiqueta: el precio leído del estante prellena el teclado; nunca suma solo. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LabelModeTest {

    private val t = TripGraph()
    private val g get() = t.graph
    private val feedback = RecordingFeedback()
    private lateinit var vm: TripViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { g.startTrip(TripLimit.None) }
        vm = TripViewModel({ g }, FakeBarcodeSource(), feedback, t.clock)
        await { it.trip }
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private fun <T : Any> await(pick: (TripUiState) -> T?): T =
        runBlocking { withTimeout(5_000) { vm.state.mapNotNull(pick).first() } }

    /** Dos lecturas iguales seguidas, como la cámara al quedarse quieta. */
    private fun read(code: String?, price: Long) {
        vm.onLabel(LabelRead(code, Pesos(price)))
        t.clock.advance(300)
        vm.onLabel(LabelRead(code, Pesos(price)))
    }

    @Test
    fun `fuera del modo etiqueta no pasa nada`() {
        read(null, 4_990)
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun `una sola lectura no basta`() {
        vm.toggleLabelMode()
        vm.onLabel(LabelRead(null, Pesos(4_990)))
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun `etiqueta con codigo conocido prellena el teclado y Sumar agrega con ese precio`() {
        runBlocking { t.product("Arroz Diana", 5_900, ARROZ) }
        vm.toggleLabelMode()
        read(ARROZ, 6_200)

        val sheet = await { it.sheet as? TripSheet.Keypad }
        assertEquals(Pesos(6_200), sheet.initial)
        assertEquals("Arroz Diana", (sheet.purpose as KeypadPurpose.ShelfPrice).productName)
        assertEquals(1, feedback.accepted)
        assertEquals(0, vm.state.value.trip!!.items.size)

        vm.submitKeypad(Pesos(6_200))
        val trip = await { s -> s.trip?.takeIf { it.total == Pesos(6_200) } }
        assertEquals("Arroz Diana", trip.items.single().nameSnapshot)
    }

    @Test
    fun `producto por peso con etiqueta queda como bolsa con ese precio`() {
        runBlocking { t.product("Queso", 9_000, ARROZ, unit = UnitType.KG) }
        vm.toggleLabelMode()
        read(ARROZ, 12_340)
        await { it.sheet as? TripSheet.Keypad }

        vm.submitKeypad(Pesos(12_340))
        val trip = await { s -> s.trip?.takeIf { it.total == Pesos(12_340) } }
        assertEquals(UnitType.KG, trip.items.single().unitType)
    }

    @Test
    fun `codigo desconocido abre Producto nuevo con el precio leido`() {
        vm.toggleLabelMode()
        read(DESCONOCIDO, 3_500)

        val sheet = await { it.sheet as? TripSheet.NewProduct }
        assertEquals(DESCONOCIDO, sheet.code)
        assertEquals(Pesos(3_500), sheet.price)
    }

    @Test
    fun `solo precio abre el precio suelto prellenado`() {
        vm.toggleLabelMode()
        read(null, 4_990)

        val sheet = await { it.sheet as? TripSheet.Keypad }
        assertEquals(KeypadPurpose.LoosePrice, sheet.purpose)
        assertEquals(Pesos(4_990), sheet.initial)

        vm.submitKeypad(Pesos(5_000))
        await { s -> s.trip?.takeIf { it.total == Pesos(5_000) } }
    }

    @Test
    fun `con una hoja abierta las etiquetas no cuentan`() {
        vm.toggleLabelMode()
        vm.openLooseKeypad()
        read(null, 4_990)
        assertEquals(KeypadPurpose.LoosePrice, (vm.state.value.sheet as TripSheet.Keypad).purpose)
        assertNull((vm.state.value.sheet as TripSheet.Keypad).initial)
    }

    @Test
    fun `el codigo leido en un cuadro y el precio en otros se juntan`() {
        runBlocking { t.product("Arroz Diana", 5_900, ARROZ) }
        vm.toggleLabelMode()
        vm.onLabel(LabelRead(ARROZ, null))
        t.clock.advance(300)
        vm.onLabel(LabelRead(null, Pesos(6_200)))
        t.clock.advance(300)
        vm.onLabel(LabelRead(null, Pesos(6_200)))

        val sheet = await { it.sheet as? TripSheet.Keypad }
        assertEquals("Arroz Diana", (sheet.purpose as KeypadPurpose.ShelfPrice).productName)
        assertEquals(Pesos(6_200), sheet.initial)
    }

    @Test
    fun `el precio que se esta leyendo se muestra en vivo`() {
        vm.toggleLabelMode()
        vm.onLabel(LabelRead(null, Pesos(4_590)))
        assertEquals(Pesos(4_590), vm.state.value.labelPreview)
        vm.toggleLabelMode()
        assertNull(vm.state.value.labelPreview)
    }
}
