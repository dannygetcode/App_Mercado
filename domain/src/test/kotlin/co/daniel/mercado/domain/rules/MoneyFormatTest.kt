package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {

    @Test
    fun `formatea con punto de miles y signo de pesos`() {
        assertEquals("$5.900", MoneyFormat.format(Pesos(5_900)))
        assertEquals("$1.250.000", MoneyFormat.format(Pesos(1_250_000)))
        assertEquals("$0", MoneyFormat.format(Pesos(0)))
        assertEquals("$999", MoneyFormat.format(Pesos(999)))
        assertEquals("$100.000", MoneyFormat.format(Pesos(100_000)))
    }

    @Test
    fun `negativos llevan el signo antes del simbolo`() {
        assertEquals("-$300", MoneyFormat.format(Pesos(-300)))
        assertEquals("-$62.600", MoneyFormat.format(Pesos(-62_600)))
    }

    @Test
    fun `no se rompe con los extremos de Long`() {
        assertEquals("-$9.223.372.036.854.775.808", MoneyFormat.format(Pesos(Long.MIN_VALUE)))
    }
}
