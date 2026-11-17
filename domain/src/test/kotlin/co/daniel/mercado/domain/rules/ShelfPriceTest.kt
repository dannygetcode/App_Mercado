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
    fun `precio seguido de Und o c-u si cuenta`() {
        assertEquals(Pesos(4_590), price("$4.590 Und" to 80, "x 500 g" to 20))
        assertEquals(Pesos(4_590), price("4.590 UN" to 80))
        assertEquals(Pesos(2_750), price("$2.750 c/u" to 60))
    }

    @Test
    fun `confusiones tipicas de la lectura de texto`() {
        assertEquals(Pesos(4_590), price("S4.590" to 80))
        assertEquals(Pesos(4_590), price("$ 4 590" to 80))
        assertEquals(Pesos(10_900), price("$1O.9OO" to 80))
        assertEquals(Pesos(11_900), price("$1l.900" to 80))
    }

    @Test
    fun `numero grande solo en su linea cuenta y uno suelto en una frase no`() {
        assertEquals(Pesos(4590), price("ARROZ DIANA" to 30, "4590" to 120, "Und" to 30))
        assertNull(price("Pasillo 12 estante 3400" to 60))
    }

    @Test
    fun `la etiqueta se confirma por el precio aunque el codigo aparezca solo en algunos cuadros`() {
        val clock = FakeClock()
        val s = LabelStabilizer(clock)
        assertNull(s.offer("7702004003508", null))
        clock.advance(300)
        assertNull(s.offer(null, Pesos(5_900)))
        clock.advance(300)
        assertEquals(LabelStabilizer.Accepted("7702004003508", Pesos(5_900)), s.offer(null, Pesos(5_900)))
        clock.advance(300)
        assertNull(s.offer("7702004003508", Pesos(5_900)))
        clock.advance(2_900)
        assertNull(s.offer(null, Pesos(5_900)))

        clock.advance(3_100)
        assertNull(s.offer(null, Pesos(5_900)))
        clock.advance(200)
        assertEquals(LabelStabilizer.Accepted(null, Pesos(5_900)), s.offer(null, Pesos(5_900)))
    }

    @Test
    fun `lecturas distintas o muy separadas no cuentan`() {
        val clock = FakeClock()
        val s = LabelStabilizer(clock)
        assertNull(s.offer(null, Pesos(5_900)))
        clock.advance(200)
        assertNull(s.offer(null, Pesos(5_000)))
        clock.advance(2_600)
        assertNull(s.offer(null, Pesos(5_000)))
        clock.advance(100)
        assertEquals(Pesos(5_000), s.offer(null, Pesos(5_000))?.price)
    }
}
