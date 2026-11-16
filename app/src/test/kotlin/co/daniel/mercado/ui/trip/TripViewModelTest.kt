package co.daniel.mercado.ui.trip

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.ScanDebouncer
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.scanner.FakeBarcodeSource
import co.daniel.mercado.ui.trip.TripGraph.Companion.ARROZ
import co.daniel.mercado.ui.trip.TripGraph.Companion.DESCONOCIDO
import co.daniel.mercado.ui.trip.TripGraph.Companion.LECHE
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
class TripViewModelTest {

    private val t = TripGraph()
    private val g get() = t.graph
    private val source = FakeBarcodeSource()
    private val feedback = RecordingFeedback()
    private lateinit var vm: TripViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private fun start(limit: TripLimit = TripLimit.None, timing: TripViewModel.Timing = TripViewModel.Timing()) {
        runBlocking { g.startTrip(limit) }
        vm = TripViewModel({ g }, source, feedback, t.clock, timing)
        await { it.trip }
    }

    private fun <T : Any> await(pick: (TripUiState) -> T?): T =
        runBlocking { withTimeout(5_000) { vm.state.mapNotNull(pick).first() } }

    private fun scan(code: String) {
        runBlocking { source.emit(code) }
    }

    private fun totalIs(value: Long) = await { s -> s.trip?.takeIf { it.total == Pesos(value) } }

    @Test
    fun `escanear conocido actualiza total, vibra, resalta y avisa con deshacer`() {
        runBlocking { t.product("Arroz Diana 1 kg", 5_900, ARROZ) }
        start()

        scan(ARROZ)

        val trip = totalIs(5_900)
        val notice = await { it.notice }
        assertEquals(NoticeKind.ADDED, notice.kind)
        assertEquals("Arroz Diana 1 kg", notice.name)
        assertEquals(Pesos(5_900), notice.amount)
        assertTrue(notice.undoable)
        assertEquals(trip.items.single().id, vm.state.value.highlightLineId)
        assertEquals(1, feedback.accepted)
    }

    @Test
    fun `escanear de nuevo tras el antirrebote incrementa la misma linea`() {
        runBlocking { t.product("Arroz Diana 1 kg", 5_900, ARROZ) }
        start()
        scan(ARROZ)
        totalIs(5_900)
        t.clock.advance(ScanDebouncer.DEFAULT_WINDOW_MS + 1)

        scan(ARROZ)

        val trip = totalIs(11_800)
        assertEquals(2, trip.items.single().quantity)
        assertEquals(NoticeKind.INCREMENTED, await { s -> s.notice?.takeIf { it.kind == NoticeKind.INCREMENTED } }.kind)
    }

    @Test
    fun `desconocido abre la hoja Producto nuevo, pausa la camara y vibra doble`() {
        start()

        scan(DESCONOCIDO)

        val sheet = await { it.sheet as? TripSheet.NewProduct }
        assertEquals(DESCONOCIDO, sheet.code)
        assertTrue(vm.state.value.cameraPaused)
        assertEquals(1, feedback.unknown)
    }

    @Test
    fun `producto nuevo solo con precio crea la linea y cierra la hoja`() {
        start()
        scan(DESCONOCIDO)
        await { it.sheet as? TripSheet.NewProduct }

        vm.submitNewProduct(Pesos(7_800))

        val trip = totalIs(7_800)
        assertEquals("Producto ····7897", trip.items.single().nameSnapshot)
        assertNull(vm.state.value.sheet)
        assertFalse(vm.state.value.cameraPaused)
    }

    @Test
    fun `producto nuevo asociado a uno existente del autocompletado`() {
        val arroz = runBlocking { t.product("Arroz Diana 1 kg", 5_900) }
        start()
        scan(DESCONOCIDO)
        await { it.sheet as? TripSheet.NewProduct }

        vm.setQuery("arroz")
        await { s -> s.results.takeIf { it.isNotEmpty() } }
        vm.selectSuggestion(arroz)
        assertEquals(arroz, (vm.state.value.sheet as TripSheet.NewProduct).selected?.id)
        vm.submitNewProduct(Pesos(5_900))

        val trip = totalIs(5_900)
        assertEquals(arroz, trip.items.single().productId)
        assertEquals(arroz, runBlocking { g.products.barcodeOwner(DESCONOCIDO) })
    }

    @Test
    fun `deshacer restaura el total`() {
        runBlocking {
            t.product("Arroz Diana 1 kg", 5_900, ARROZ)
            t.product("Leche Alquería 1 L", 4_300, LECHE)
        }
        start()
        scan(ARROZ)
        totalIs(5_900)
        scan(LECHE)
        totalIs(10_200)

        vm.undo()

        totalIs(5_900)
        assertEquals(NoticeKind.UNDONE, await { s -> s.notice?.takeIf { it.kind == NoticeKind.UNDONE } }.kind)
    }

    @Test
    fun `precio suelto por teclado agrega articulo sin nombre`() {
        start()
        vm.openLooseKeypad()
        assertEquals(KeypadPurpose.LoosePrice, (vm.state.value.sheet as TripSheet.Keypad).purpose)

        vm.submitKeypad(Pesos(12_000))

        val trip = totalIs(12_000)
        assertTrue(trip.items.single().isLoose)
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun `producto KG escaneado pide el precio de la etiqueta`() {
        val tomate = runBlocking { t.product("Tomate chonto", 4_000, ARROZ, unit = UnitType.KG) }
        start()

        scan(ARROZ)

        val sheet = await { it.sheet as? TripSheet.Keypad }
        assertEquals(KeypadPurpose.LabelPrice(tomate, "Tomate chonto"), sheet.purpose)
        vm.submitKeypad(Pesos(3_450))
        val trip = totalIs(3_450)
        assertEquals(UnitType.KG, trip.items.single().unitType)
    }

    @Test
    fun `menos hasta cero elimina la linea con deshacer`() {
        runBlocking { t.product("Arroz Diana 1 kg", 5_900, ARROZ) }
        start()
        scan(ARROZ)
        val line = totalIs(5_900).items.single()

        vm.decrement(line.id)

        await { s -> s.trip?.takeIf { it.items.isEmpty() } }
        val notice = await { s -> s.notice?.takeIf { it.kind == NoticeKind.REMOVED } }
        assertTrue(notice.undoable)
        vm.undo()
        totalIs(5_900)
    }

    @Test
    fun `corregir precio cambia la linea`() {
        runBlocking { t.product("Arroz Diana 1 kg", 5_900, ARROZ) }
        start()
        scan(ARROZ)
        val line = totalIs(5_900).items.single()

        vm.openFixPrice(line.id)
        val sheet = vm.state.value.sheet as TripSheet.Keypad
        assertEquals(Pesos(5_900), sheet.initial)
        vm.submitKeypad(Pesos(6_200))

        totalIs(6_200)
    }

    @Test
    fun `lecturas con el teclado abierto no cuentan`() {
        runBlocking { t.product("Arroz Diana 1 kg", 5_900, ARROZ) }
        start()
        vm.openLooseKeypad()

        scan(ARROZ)
        runBlocking { kotlinx.coroutines.delay(200) }

        assertEquals(Pesos.ZERO, vm.state.value.trip?.total)
        assertEquals(0, feedback.accepted)
    }

    @Test
    fun `cruzar 80 y 100 por ciento vibra una vez por cruce`() {
        runBlocking { t.product("Arroz Diana 1 kg", 5_000, ARROZ) }
        start(limit = TripLimit.Custom(Pesos(10_000)))
        val arroz = runBlocking { g.products.findByBarcode(ARROZ)!!.id }

        vm.pickProduct(arroz) // 5.000 → 50 %
        totalIs(5_000)
        assertTrue(feedback.warnings.isEmpty())
        vm.openLooseKeypad()
        vm.submitKeypad(Pesos(3_500)) // 8.500 → 85 %
        totalIs(8_500)
        vm.openLooseKeypad()
        vm.submitKeypad(Pesos(500)) // 9.000 → sigue amarillo
        totalIs(9_000)
        vm.pickProduct(arroz) // 14.000 → rojo
        totalIs(14_000)

        assertEquals(listOf(false, true), feedback.warnings)
    }

    @Test
    fun `favorito UNIT se agrega y la hoja sigue abierta`() {
        val arroz = runBlocking { t.product("Arroz Diana 1 kg", 5_900, favorite = true) }
        start()
        await { s -> s.favorites.takeIf { it.isNotEmpty() } }
        vm.openPicker()

        vm.pickProduct(arroz)

        totalIs(5_900)
        assertTrue(vm.state.value.sheet is TripSheet.Picker)
        assertEquals(1, feedback.accepted)
    }

    @Test
    fun `buscar sin resultados y crear el producto con el teclado`() {
        start()
        vm.openPicker(PickerTab.SEARCH)
        vm.setQuery("Cilantro")

        vm.createFromSearch()
        assertEquals(KeypadPurpose.CreateProduct("Cilantro"), (vm.state.value.sheet as TripSheet.Keypad).purpose)
        vm.submitKeypad(Pesos(1_500))

        val trip = totalIs(1_500)
        assertEquals("Cilantro", trip.items.single().nameSnapshot)
    }

    @Test
    fun `la camara se pausa sola tras el tiempo sin lecturas y se reanuda`() {
        start(timing = TripViewModel.Timing(autoPauseMs = 150))

        await { s -> s.takeIf { it.cameraAutoPaused } }
        assertTrue(vm.state.value.cameraPaused)
        vm.resumeCamera()
        assertFalse(vm.state.value.cameraPaused)
    }

    @Test
    fun `el aviso desaparece solo`() {
        start(timing = TripViewModel.Timing(noticeMs = 100))
        vm.openLooseKeypad()
        vm.submitKeypad(Pesos(1_000))
        await { it.notice }

        await { s -> s.takeIf { it.notice == null } }
    }
}
