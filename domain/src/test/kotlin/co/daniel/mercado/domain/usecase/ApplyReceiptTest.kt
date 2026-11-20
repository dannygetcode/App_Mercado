package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.ReceiptLine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplyReceiptTest {

    private val f = TripFixture()
    private val apply = ApplyReceipt(f.trips, f.products, f.session, f.clock)

    @Test
    fun `corrige precios y cantidades de lo que ya estaba y agrega lo que faltaba`() = runTest {
        val arroz = f.product("Arroz Diana 1 kg", 5_500, "7702001041183")
        val leche = f.product("Leche Alquería entera", 4_100)
        val banano = f.product("Banano", 2_400, unit = UnitType.KG)
        f.startTrip(TripLimit.None)
        f.addProduct(arroz.id)
        f.addProduct(leche.id)
        f.addWeighed(banano.id, Pesos(3_000))
        f.addLoose(Pesos(1_000))

        val result = apply(
            listOf(
                ReceiptLine("ARROZ DIANA 1000G", 1, Pesos(5_900), "7702001041183"),
                ReceiptLine("LECHE ALQUERIA ENT 1100ML", 2, Pesos(8_600)),
                ReceiptLine("BANANO URABA KG", 1, Pesos(3_120)),
                ReceiptLine("DETERGENTE FAB 1KG", 1, Pesos(12_900)),
            ),
        )

        assertEquals(ApplyReceiptResult(corrected = 3, added = 1, createdProducts = 1, notOnReceipt = 1), result)
        val items = f.activeItems().associateBy { it.nameSnapshot }
        assertEquals(Pesos(5_900), items.getValue("Arroz Diana 1 kg").unitPrice)
        assertEquals(2, items.getValue("Leche Alquería entera").quantity)
        assertEquals(Pesos(4_300), items.getValue("Leche Alquería entera").unitPrice)
        assertEquals(Pesos(3_120), items.getValue("Banano").unitPrice)
        val nuevo = items.getValue("Detergente fab 1kg")
        assertEquals(Pesos(12_900), nuevo.unitPrice)
        assertTrue(f.products.getById(nuevo.productId!!)!!.needsReview)
        // El precio suelto que no sale en el recibo se queda.
        assertTrue(f.activeItems().any { it.isLoose })
    }

    @Test
    fun `un producto del catalogo que no estaba en la compra se agrega con ese producto`() = runTest {
        val cafe = f.product("Café Sello Rojo 500 g", 20_000)
        f.startTrip(TripLimit.None)

        val result = apply(listOf(ReceiptLine("CAFE SELLO ROJO 500G", 1, Pesos(21_500))))

        assertEquals(0, result?.createdProducts)
        val line = f.activeItems().single()
        assertEquals(cafe.id, line.productId)
        assertEquals(Pesos(21_500), line.unitPrice)
    }

    @Test
    fun `el mismo producto dos veces en el recibo queda como una linea con cantidad 2`() = runTest {
        val gaseosa = f.product("Gaseosa Postobón 400 ml", 2_500)
        f.startTrip(TripLimit.None)
        f.addProduct(gaseosa.id)

        val result = apply(
            listOf(
                ReceiptLine("GASEOSA POSTOBON 400ML", 1, Pesos(2_500)),
                ReceiptLine("GASEOSA POSTOBON 400ML", 1, Pesos(2_500)),
            ),
        )

        assertEquals(1, result?.corrected)
        assertEquals(2, f.activeItems().single().quantity)
    }

    @Test
    fun `sin compra abierta no hace nada`() = runTest {
        assertNull(apply(listOf(ReceiptLine("ARROZ", 1, Pesos(5_900)))))
    }
}
