package co.daniel.mercado.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EanTest {

    @Test
    fun `acepta EAN-13 valido y rechaza uno con un digito cambiado`() {
        assertTrue(Ean.isValid("7702004003508"))
        assertFalse(Ean.isValid("7702004003518"))
        assertFalse(Ean.isValid("7702004003509"))
    }

    @Test
    fun `acepta EAN-8 y UPC-E`() {
        assertEquals("96385074", Ean.normalize("96385074"))
        assertEquals("04252614", Ean.normalize("04252614"))
        assertFalse(Ean.isValid("96385075"))
        assertFalse(Ean.isValid("04252615"))
    }

    @Test
    fun `normaliza UPC-A de 12 digitos a EAN-13 con cero inicial`() {
        assertEquals("0042100005264", Ean.normalize("042100005264"))
        assertNull(Ean.normalize("042100005265"))
    }

    @Test
    fun `rechaza vacio, letras en EAN y longitudes raras`() {
        assertNull(Ean.normalize(""))
        assertNull(Ean.normalize("   "))
        assertNull(Ean.normalize("77020040035A8"))
        assertNull(Ean.normalize("9638507A"))
        assertNull(Ean.normalize("12345"))
        assertNull(Ean.normalize("1234567890"))
        assertNull(Ean.normalize("77020040035081"))
    }

    @Test
    fun `acepta Code 128 alfanumerico de 4 a 32 caracteres imprimibles`() {
        assertEquals("AB-1234", Ean.normalize("AB-1234"))
        assertNull(Ean.normalize("AB1"))
        assertNull(Ean.normalize("A".repeat(33)))
        assertNull(Ean.normalize("AB\u00011234"))
    }
}
