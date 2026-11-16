package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.fakes.TripFixture.Companion.ARROZ
import co.daniel.mercado.domain.fakes.TripFixture.Companion.DESCONOCIDO
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.ScanOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddUnknownScannedTest {

    private val f = TripFixture()

    @Test
    fun `solo con precio crea producto provisional, asocia el codigo y agrega la linea`() = runTest {
        f.startTrip()
        val transactionsBefore = f.tx.transactions

        val outcome = assertIs<ScanOutcome.Added>(f.addUnknown(DESCONOCIDO, Pesos(7_800)))

        val product = f.products.all.single()
        assertEquals("Producto ····7897", product.name)
        assertTrue(product.needsReview)
        assertEquals(Pesos(7_800), product.refPrice)
        assertEquals(f.products.categoryByName(Category.OTHER)?.id, product.categoryId)
        assertEquals(product.id, f.products.barcodeOwner(DESCONOCIDO))
        val line = f.activeItems().single()
        assertEquals(outcome.lineId, line.id)
        assertEquals(product.id, line.productId)
        assertEquals(Pesos(7_800), line.unitPrice)
        assertEquals(transactionsBefore + 1, f.tx.transactions, "producto, código y línea en una sola transacción")
    }

    @Test
    fun `asociando a existente no crea producto, asocia el codigo y usa el precio tecleado`() = runTest {
        val arroz = f.product("Arroz Diana 1 kg", 5_900, ARROZ)
        f.startTrip()

        f.addUnknown(DESCONOCIDO, Pesos(6_100), existingProductId = arroz.id)

        assertEquals(1, f.products.all.size)
        assertEquals(arroz.id, f.products.barcodeOwner(DESCONOCIDO))
        val line = f.activeItems().single()
        assertEquals(arroz.id, line.productId)
        assertEquals(Pesos(6_100), line.unitPrice)
    }

    @Test
    fun `el mismo codigo escaneado despues se agrega directo`() = runTest {
        f.startTrip()
        f.addUnknown(DESCONOCIDO, Pesos(7_800))
        f.afterDebounce()

        val outcome = f.handleScan(DESCONOCIDO)

        assertEquals(ScanOutcome.Incremented(f.activeItems().single().id, "Producto ····7897", 2), outcome)
    }

    @Test
    fun `deshacer un desconocido quita la linea, el codigo y el producto provisional`() = runTest {
        f.startTrip()
        f.addUnknown(DESCONOCIDO, Pesos(7_800))

        f.undo()

        assertTrue(f.activeItems().isEmpty())
        assertTrue(f.products.all.isEmpty())
        assertNull(f.products.barcodeOwner(DESCONOCIDO))
    }
}
