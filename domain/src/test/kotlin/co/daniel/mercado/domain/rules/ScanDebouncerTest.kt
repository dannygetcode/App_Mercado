package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.fakes.FakeClock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanDebouncerTest {

    private val clock = FakeClock(0)
    private val debouncer = ScanDebouncer(clock)

    @Test
    fun `mismo codigo a los 500 ms se rechaza y a los 1600 ms se acepta`() {
        assertTrue(debouncer.shouldAccept("7702004003508"))
        clock.advance(500)
        assertFalse(debouncer.shouldAccept("7702004003508"))
        clock.advance(1_100)
        assertTrue(debouncer.shouldAccept("7702004003508"))
    }

    @Test
    fun `codigo distinto inmediatamente se acepta`() {
        assertTrue(debouncer.shouldAccept("7702004003508"))
        assertTrue(debouncer.shouldAccept("96385074"))
    }

    @Test
    fun `la ventana se cuenta desde la ultima aceptacion, no desde el ultimo rechazo`() {
        assertTrue(debouncer.shouldAccept("96385074"))
        clock.advance(1_000)
        assertFalse(debouncer.shouldAccept("96385074"))
        clock.advance(500)
        assertTrue(debouncer.shouldAccept("96385074"))
    }
}
