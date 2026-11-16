package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LineMathTest {

    private fun item(price: Long, qty: Int, type: UnitType = UnitType.UNIT) = TripItem(
        tripId = 1, productId = 1, nameSnapshot = "x", unitType = type,
        quantity = qty, unitPrice = Pesos(price), addedAt = 0, position = 0,
    )

    @Test
    fun `total de linea es precio por cantidad`() {
        assertEquals(Pesos(11_800), LineMath.lineTotal(Pesos(5_900), 2))
    }

    @Test
    fun `total de linea detecta desbordamiento`() {
        assertFailsWith<ArithmeticException> { LineMath.lineTotal(Pesos(Long.MAX_VALUE), 2) }
    }

    @Test
    fun `total y conteo de articulos de varias lineas`() {
        val items = listOf(item(5_900, 2), item(4_300, 1), item(8_750, 1, UnitType.KG))
        assertEquals(Pesos(24_850), LineMath.total(items))
        assertEquals(4, LineMath.articleCount(items))
    }
}
