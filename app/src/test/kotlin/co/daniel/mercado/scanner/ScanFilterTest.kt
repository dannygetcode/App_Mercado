package co.daniel.mercado.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanFilterTest {

    @Test
    fun `toma el primer codigo con digito de control valido`() {
        assertEquals("7702004003508", ScanFilter.firstValid(listOf("7702004003509", "7702004003508")))
    }

    @Test
    fun `normaliza UPC-A a EAN-13`() {
        assertEquals("0042100005264", ScanFilter.firstValid(listOf("042100005264")))
    }

    @Test
    fun `sin codigos validos no devuelve nada`() {
        assertNull(ScanFilter.firstValid(listOf("123", "7702004003509")))
        assertNull(ScanFilter.firstValid(emptyList()))
    }

    @Test
    fun `ML Kit se limita a los cinco formatos de RF-03`() {
        assertEquals(
            setOf(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128,
            ),
            MlKitBarcodeDecoder.FORMATS.toSet(),
        )
        assertEquals(5, MlKitBarcodeDecoder.FORMATS.size)
    }
}
