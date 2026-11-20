package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceiptAccumulatorTest {

    private fun line(name: String, amount: Long) = ReceiptLine(name, 1, Pesos(amount))

    @Test
    fun `un renglon cuenta cuando se ve en dos cuadros y el ruido de uno solo no`() {
        val acc = ReceiptAccumulator()
        acc.add(ReceiptScan(listOf(line("ARROZ DIANA", 5_900), line("XQ7 RUIDO", 1_234)), null))
        assertEquals(emptyList(), acc.lines())

        acc.add(ReceiptScan(listOf(line("ARROZ DIANA", 5_900)), Pesos(5_900)))
        assertEquals(listOf(line("ARROZ DIANA", 5_900)), acc.lines())
        assertNull(acc.total())
    }

    @Test
    fun `variantes de lectura del mismo renglon se funden y gana la mas vista`() {
        val acc = ReceiptAccumulator()
        acc.add(ReceiptScan(listOf(line("LECHE ALQUERIA", 4_300)), null))
        acc.add(ReceiptScan(listOf(line("LECHE ALQUERlA", 4_300)), null))
        acc.add(ReceiptScan(listOf(line("LECHE ALQUERIA", 4_300)), null))

        assertEquals(listOf(line("LECHE ALQUERIA", 4_300)), acc.lines())
    }

    @Test
    fun `el mismo producto dos veces en el recibo se conserva dos veces y en orden`() {
        val acc = ReceiptAccumulator()
        // Al bajar la cámara, el primer cuadro ve la parte de arriba y el segundo la de abajo.
        acc.add(ReceiptScan(listOf(line("PAN BIMBO", 7_250), line("GASEOSA", 2_500), line("GASEOSA", 2_500)), Pesos(12_250)))
        acc.add(ReceiptScan(listOf(line("GASEOSA", 2_500), line("GASEOSA", 2_500), line("HUEVOS", 16_900)), Pesos(12_250)))
        acc.add(ReceiptScan(listOf(line("PAN BIMBO", 7_250), line("HUEVOS", 16_900)), null))

        assertEquals(
            listOf(line("PAN BIMBO", 7_250), line("GASEOSA", 2_500), line("GASEOSA", 2_500), line("HUEVOS", 16_900)),
            acc.lines(),
        )
        assertEquals(Pesos(12_250), acc.total())
    }
}
