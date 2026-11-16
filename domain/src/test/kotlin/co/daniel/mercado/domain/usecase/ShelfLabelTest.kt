package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.UnitType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Sumar un producto con el precio leído en la etiqueta del estante. */
class ShelfLabelTest {

    private val f = TripFixture()

    @Test
    fun `producto por unidad con el precio de la etiqueta y luego otro igual incrementa con ese precio`() = runTest {
        f.startTrip()
        val arroz = f.product("Arroz", 5_900)

        assertIs<ScanOutcome.Added>(f.addProduct(arroz.id, Pesos(6_200)))
        assertIs<ScanOutcome.Incremented>(f.addProduct(arroz.id, Pesos(6_200)))

        val line = f.activeItems().single()
        assertEquals(2, line.quantity)
        assertEquals(Pesos(6_200), line.unitPrice)
    }

    @Test
    fun `producto por peso con precio se agrega como bolsa y deshacer la quita`() = runTest {
        f.startTrip()
        val papa = f.product("Papa", 2_000, unit = UnitType.KG)

        assertIs<ScanOutcome.Added>(f.addProduct(papa.id, Pesos(3_450)))
        assertEquals(Pesos(3_450), f.activeItems().single().unitPrice)
        f.undo()
        assertEquals(0, f.activeItems().size)
    }

    @Test
    fun `sin precio sigue igual que antes`() = runTest {
        f.startTrip()
        val papa = f.product("Papa", 2_000, unit = UnitType.KG)
        assertIs<ScanOutcome.NeedsLabelPrice>(f.addProduct(papa.id))
    }
}
