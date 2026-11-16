package co.daniel.mercado.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class NameNormalizerTest {

    @Test
    fun `quita tildes, pasa a minusculas y colapsa espacios`() {
        assertEquals("limon", NameNormalizer.normalize("Limón"))
        assertEquals("cafe sello rojo 500 g", NameNormalizer.normalize("  Café   Sello  Rojo\t500 g "))
        assertEquals("pina oro miel", NameNormalizer.normalize("PIÑA Oro Miel"))
        assertEquals("yogur alpina", NameNormalizer.normalize("Yogúr ALPINA"))
    }
}
