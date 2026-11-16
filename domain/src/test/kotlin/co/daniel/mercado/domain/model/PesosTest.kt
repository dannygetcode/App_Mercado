package co.daniel.mercado.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PesosTest {

    @Test
    fun `suma, resta y multiplica en pesos enteros`() {
        assertEquals(Pesos(10_200), Pesos(5_900) + Pesos(4_300))
        assertEquals(Pesos(-300), Pesos(1_000) - Pesos(1_300))
        assertEquals(Pesos(17_700), Pesos(5_900) * 3)
        assertEquals(Pesos(10_200), listOf(Pesos(5_900), Pesos(4_300)).sum())
    }

    @Test
    fun `sumar detecta desbordamiento`() {
        assertFailsWith<ArithmeticException> { Pesos(Long.MAX_VALUE) + Pesos(1) }
        assertFailsWith<ArithmeticException> { Pesos(Long.MIN_VALUE) - Pesos(1) }
    }

    @Test
    fun `multiplicar detecta desbordamiento`() {
        assertFailsWith<ArithmeticException> { Pesos(Long.MAX_VALUE / 2 + 1) * 2 }
    }
}
