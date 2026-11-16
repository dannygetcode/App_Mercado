package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.fakes.TripFixture.Companion.ARROZ
import co.daniel.mercado.domain.fakes.TripFixture.Companion.LECHE
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.UnitType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EditAndUndoTest {

    private val f = TripFixture()

    private suspend fun tripWithArrozYLeche(): Pair<Long, Long> {
        f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.product("Leche Alquería 1 L", 4_300, LECHE)
        f.startTrip()
        val arroz = assertIs<ScanOutcome.Added>(f.handleScan(ARROZ)).lineId
        val leche = assertIs<ScanOutcome.Added>(f.handleScan(LECHE)).lineId
        return arroz to leche
    }

    @Test
    fun `ChangeQuantity a 0 elimina la linea`() = runTest {
        val (arroz, _) = tripWithArrozYLeche()

        assertEquals(EditResult.REMOVED, f.changeQuantity(arroz, 0))

        assertEquals(listOf("Leche Alquería 1 L"), f.activeItems().map { it.nameSnapshot })
    }

    @Test
    fun `una linea KG no cambia de cantidad`() = runTest {
        val tomate = f.product("Tomate chonto", 4_200, unit = UnitType.KG)
        f.startTrip()
        val line = assertIs<ScanOutcome.Added>(f.addWeighed(tomate.id, Pesos(3_000))).lineId

        assertEquals(EditResult.NOT_ALLOWED, f.changeQuantity(line, 2))
        assertEquals(1, f.activeItems().single().quantity)
    }

    @Test
    fun `deshacer revierte agregar`() = runTest {
        tripWithArrozYLeche()

        assertTrue(f.undo())

        assertEquals(listOf("Arroz Diana 1 kg"), f.activeItems().map { it.nameSnapshot })
    }

    @Test
    fun `deshacer revierte incrementar`() = runTest {
        tripWithArrozYLeche()
        f.afterDebounce()
        assertIs<ScanOutcome.Incremented>(f.handleScan(ARROZ))

        f.undo()

        assertEquals(1, f.activeItems().first { it.nameSnapshot.startsWith("Arroz") }.quantity)
    }

    @Test
    fun `deshacer revierte eliminar restaurando posicion y cantidad`() = runTest {
        val (arroz, _) = tripWithArrozYLeche()
        f.changeQuantity(arroz, 3)
        val before = f.activeItems()
        f.removeLine(arroz)

        f.undo()

        assertEquals(before, f.activeItems())
        assertEquals(3, f.activeItems().first { it.id == arroz }.quantity)
    }

    @Test
    fun `deshacer revierte eliminar por cantidad 0`() = runTest {
        val (arroz, _) = tripWithArrozYLeche()
        val before = f.activeItems()
        f.changeQuantity(arroz, 0)

        f.undo()

        assertEquals(before, f.activeItems())
    }

    @Test
    fun `deshacer revierte cambio de precio`() = runTest {
        val (arroz, _) = tripWithArrozYLeche()
        f.changePrice(arroz, Pesos(6_200))
        assertEquals(Pesos(6_200), f.activeItems().first { it.id == arroz }.unitPrice)

        f.undo()

        assertEquals(Pesos(5_900), f.activeItems().first { it.id == arroz }.unitPrice)
    }

    @Test
    fun `deshacer dos veces seguidas no hace nada la segunda`() = runTest {
        tripWithArrozYLeche()

        assertTrue(f.undo())
        val afterFirst = f.activeItems()
        assertFalse(f.undo())

        assertEquals(afterFirst, f.activeItems())
    }

    @Test
    fun `renombrar solo aplica a precios sueltos`() = runTest {
        val (arroz, _) = tripWithArrozYLeche()
        val loose = assertIs<ScanOutcome.Added>(f.addLoose(Pesos(2_500))).lineId

        assertEquals(EditResult.UPDATED, f.renameLoose(loose, "  Bolsa   de hielo "))
        assertEquals(EditResult.NOT_ALLOWED, f.renameLoose(arroz, "Otro"))

        assertEquals("Bolsa de hielo", f.activeItems().first { it.id == loose }.nameSnapshot)
    }

    @Test
    fun `no se editan lineas de una compra cerrada`() = runTest {
        val (arroz, _) = tripWithArrozYLeche()
        f.closeTrip()

        assertEquals(EditResult.NOT_FOUND, f.changeQuantity(arroz, 5))
        assertEquals(EditResult.NOT_FOUND, f.changePrice(arroz, Pesos(1)))
        assertFalse(f.undo())
    }
}
