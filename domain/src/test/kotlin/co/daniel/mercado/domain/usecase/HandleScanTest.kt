package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.fakes.TripFixture.Companion.ARROZ
import co.daniel.mercado.domain.fakes.TripFixture.Companion.ARROZ_UPCA
import co.daniel.mercado.domain.fakes.TripFixture.Companion.DESCONOCIDO
import co.daniel.mercado.domain.fakes.TripFixture.Companion.LECHE
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.UnitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HandleScanTest {

    private val f = TripFixture()

    @Test
    fun `codigo conocido UNIT se agrega con su precio de referencia`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip()

        val outcome = f.handleScan(ARROZ)

        val added = assertIs<ScanOutcome.Added>(outcome)
        assertEquals("Arroz Diana 1 kg", added.productName)
        assertEquals(Pesos(5_900), added.unitPrice)
        val line = f.activeItems().single()
        assertEquals(1, line.quantity)
        assertEquals(Pesos(5_900), line.unitPrice)
    }

    @Test
    fun `mismo producto otra vez pasada la ventana incrementa una sola linea`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip()

        f.handleScan(ARROZ)
        f.afterDebounce()
        val outcome = f.handleScan(ARROZ)

        val inc = assertIs<ScanOutcome.Incremented>(outcome)
        assertEquals(2, inc.newQuantity)
        assertEquals(2, f.activeItems().single().quantity)
    }

    @Test
    fun `dos codigos distintos del mismo producto incrementan la misma linea`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ, "0042100005264")
        f.startTrip()

        f.handleScan(ARROZ)
        val outcome = f.handleScan(ARROZ_UPCA)

        assertIs<ScanOutcome.Incremented>(outcome)
        assertEquals(2, f.activeItems().single().quantity)
    }

    @Test
    fun `producto KG pide precio de etiqueta y no agrega linea`() = runTest {
        val tomate = f.product("Tomate chonto", 4_200, LECHE, unit = UnitType.KG)
        f.startTrip()

        val outcome = f.handleScan(LECHE)

        assertEquals(ScanOutcome.NeedsLabelPrice(tomate.id, "Tomate chonto"), outcome)
        assertTrue(f.activeItems().isEmpty())
    }

    @Test
    fun `codigo desconocido devuelve Unknown y no agrega linea`() = runTest {
        f.startTrip()

        assertEquals(ScanOutcome.Unknown(DESCONOCIDO), f.handleScan(DESCONOCIDO))
        assertTrue(f.activeItems().isEmpty())
    }

    @Test
    fun `checksum invalido o rebote se ignoran`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip()

        assertEquals(ScanOutcome.Ignored, f.handleScan("7702004003509"))
        assertIs<ScanOutcome.Added>(f.handleScan(ARROZ))
        f.clock.advance(500)
        assertEquals(ScanOutcome.Ignored, f.handleScan(ARROZ))
        assertEquals(1, f.activeItems().single().quantity)
    }

    @Test
    fun `sin compra abierta devuelve NoActiveTrip`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)

        assertEquals(ScanOutcome.NoActiveTrip, f.handleScan(ARROZ))
    }

    @Test
    fun `agregar por id desde favoritos suma o pide etiqueta`() = runTest {
        val arroz = f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        val tomate = f.product("Tomate chonto", 4_200, unit = UnitType.KG)
        f.startTrip()

        assertIs<ScanOutcome.Added>(f.addProduct(arroz.id))
        assertIs<ScanOutcome.Incremented>(f.addProduct(arroz.id))
        assertIs<ScanOutcome.NeedsLabelPrice>(f.addProduct(tomate.id))
        assertEquals(2, f.activeItems().single().quantity)
    }

    @Test
    fun `cada bolsa de un producto KG es una linea nueva con el precio de la etiqueta`() = runTest {
        val tomate = f.product("Tomate chonto", 4_200, unit = UnitType.KG)
        f.startTrip()

        f.addWeighed(tomate.id, Pesos(3_150))
        f.addWeighed(tomate.id, Pesos(5_020), weightGrams = 1_200)

        val items = f.activeItems()
        assertEquals(2, items.size)
        assertEquals(listOf(Pesos(5_020), Pesos(3_150)), items.map { it.unitPrice })
        assertTrue(items.all { it.quantity == 1 && it.unitType == UnitType.KG })
    }

    @Test
    fun `precio suelto crea una linea sin producto`() = runTest {
        f.startTrip()

        val outcome = assertIs<ScanOutcome.Added>(f.addLoose(Pesos(12_000)))

        val line = f.activeItems().single()
        assertEquals(outcome.lineId, line.id)
        assertEquals(null, line.productId)
        assertEquals("", line.nameSnapshot)
        assertEquals(Pesos(12_000), line.unitPrice)
    }

    @Test
    fun `la compra observada trae total, articulos, saldo y semaforo`() = runTest {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip(TripLimit.Custom(Pesos(10_000)))
        f.handleScan(ARROZ)
        f.afterDebounce()
        f.handleScan(ARROZ)

        val active = requireNotNull(f.observeActiveTrip().first())

        assertEquals(Pesos(11_800), active.total)
        assertEquals(2, active.articleCount)
        assertEquals(Pesos(-1_800), active.remaining)
        assertEquals(co.daniel.mercado.domain.model.TrafficLight.RED, active.trafficLight)
        assertEquals(118, active.percentOfLimit)
    }
}
