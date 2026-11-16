package co.daniel.mercado.ui.components

import co.daniel.mercado.domain.model.Pesos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadStateTest {

    private fun KeypadState.type(vararg keys: String) = keys.fold(this) { s, k -> s.press(k) }

    @Test
    fun `1 2 000 da 12000`() {
        assertEquals(Pesos(12_000), KeypadState().type("1", "2", "000").value)
    }

    @Test
    fun `borrar quita el ultimo digito`() {
        val s = KeypadState().type("1", "2", "5").backspace()
        assertEquals(Pesos(12), s.value)
        assertTrue(s.backspace().backspace().backspace().isZero)
    }

    @Test
    fun `tope de 9 digitos`() {
        val s = KeypadState().type("999", "999", "999")
        assertEquals(Pesos(999_999_999), s.value)
        assertEquals(s, s.press("1"))
        assertEquals(s, s.press("000"))
        assertEquals(Pesos(99_999_999), KeypadState().type("99", "999", "999").value)
        assertEquals(Pesos(99_999_999), KeypadState().type("99", "999", "999", "00").value)
    }

    @Test
    fun `ceros a la izquierda no cuentan`() {
        assertTrue(KeypadState().type("0", "00", "000").isZero)
        assertEquals("5", KeypadState().type("00", "5").digits)
    }

    @Test
    fun `valor prellenado se reemplaza con la primera tecla`() {
        val pre = KeypadState.prefilled(Pesos(5_900))
        assertTrue(pre.replaceOnType)
        assertEquals(Pesos(5_900), pre.value)
        assertEquals(Pesos(6), pre.press("6").value)
        assertFalse(pre.press("6").replaceOnType)
        assertEquals(Pesos(590), pre.backspace().value)
        assertTrue(KeypadState.prefilled(null).isZero)
    }
}
