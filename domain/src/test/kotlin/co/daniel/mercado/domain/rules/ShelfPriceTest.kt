package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.fakes.FakeClock
import co.daniel.mercado.domain.model.Pesos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelfPriceTest {

    private fun price(vararg lines: Pair<String, Int>) = ShelfPrice.find(lines.map { TextLine(it.first, it.second) })

    @Test
    fun `el precio grande de la etiqueta gana sobre el precio por unidad de medida`() {
        assertEquals(
            Pesos(12_500),
            price("ARROZ DIANA X 1000 G" to 20, "$ 12.500" to 90, "Precio x g $12,50" to 14, "7702004003508" to 18),
        )
    }

    @Test
    fun `formatos comunes del precio`() {
        assertEquals(Pesos(4_990), price("$4.990" to 50))
        assertEquals(Pesos(4_990), price("$ 4,990" to 50))
        assertEquals(Pesos(4_990), price("4.990" to 50))
        assertEquals(Pesos(4_990), price("$4990" to 50))
        assertEquals(Pesos(1_250_000), price("$1.250.000" to 50))
        assertEquals(Pesos(12_500), price("$12.500,00" to 50))
        assertEquals(Pesos(850), price("$850" to 50))
    }

    @Test
    fun `cantidades, porcentajes y numeros sueltos no son precios`() {
        assertNull(price("1.000 ml" to 80, "500 g" to 70, "20%" to 60, "Pasillo 12" to 40, "2026" to 30))
        assertNull(price("x 1.000 g" to 80))
        assertNull(price("7702004003508" to 80))
    }

    @Test
    fun `precio por unidad de medida con centavos se descarta aunque sea grande`() {
        assertNull(price("$24,95" to 90))
        assertEquals(Pesos(9_980), price("$24,95" to 30, "$9.980" to 60))
    }

    @Test
    fun `fuera de rango no cuenta`() {
        assertNull(price("$10" to 80))
        assertNull(price("$9.999.999" to 80))
    }

    @Test
    fun `a igual alto gana el que trae signo`() {
        assertEquals(Pesos(3_200), price("1.500" to 50, "$3.200" to 50))
    }

    @Test
    fun `la etiqueta cuenta con dos lecturas iguales y no se repite mientras sigue en cuadro`() {
        val clock = FakeClock()
        val s = LabelStabilizer(clock)
        assertFalse(s.offer("7702004003508", Pesos(5_900)))
        clock.advance(300)
        assertTrue(s.offer("7702004003508", Pesos(5_900)))
        clock.advance(300)
        assertFalse(s.offer("7702004003508", Pesos(5_900)))
        clock.advance(2_900)
        assertFalse(s.offer("7702004003508", Pesos(5_900)))

        clock.advance(3_100)
        assertFalse(s.offer("7702004003508", Pesos(5_900)))
        clock.advance(200)
        assertTrue(s.offer("7702004003508", Pesos(5_900)))
    }

    @Test
    fun `lecturas distintas o muy separadas no cuentan`() {
        val clock = FakeClock()
        val s = LabelStabilizer(clock)
        assertFalse(s.offer(null, Pesos(5_900)))
        clock.advance(200)
        assertFalse(s.offer(null, Pesos(5_000)))
        clock.advance(2_500)
        assertFalse(s.offer(null, Pesos(5_000)))
        clock.advance(100)
        assertTrue(s.offer(null, Pesos(5_000)))
    }
}
