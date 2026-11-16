package co.daniel.mercado.ui.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanDiagnosticsTest {

    @Test
    fun `mide de la camara arrancada a la primera lectura`() {
        val d = ScanDiagnostics().cameraStarted(1_000).read("A", 1_850, accepted = true)
        assertEquals(850L, d.firstReadLatencyMs)
        assertEquals(850L, d.reads.single().elapsedMs)
    }

    @Test
    fun `las siguientes lecturas miden desde la anterior y no cambian la primera`() {
        val d = ScanDiagnostics().cameraStarted(0)
            .read("A", 500, accepted = true)
            .read("B", 2_100, accepted = true)
        assertEquals(500L, d.firstReadLatencyMs)
        assertEquals(listOf("B", "A"), d.reads.map { it.code })
        assertEquals(1_600L, d.reads.first().elapsedMs)
    }

    @Test
    fun `reanudar la camara reinicia la medicion de primera lectura`() {
        val d = ScanDiagnostics().cameraStarted(0).read("A", 400, accepted = true)
            .cameraStopped().cameraStarted(10_000).read("B", 10_300, accepted = true)
        assertEquals(300L, d.firstReadLatencyMs)
        assertEquals(300L, d.reads.first().elapsedMs)
    }

    @Test
    fun `los rebotes se cuentan aparte`() {
        val d = ScanDiagnostics().cameraStarted(0)
            .read("A", 100, accepted = true)
            .read("A", 300, accepted = false)
        assertEquals(1, d.accepted)
        assertEquals(1, d.bounced)
        assertEquals(1, d.reads.size)
    }

    @Test
    fun `lecturas por minuto cuentan solo los ultimos 60 s`() {
        var d = ScanDiagnostics().cameraStarted(0)
        listOf(1_000L, 30_000L, 59_000L, 70_000L).forEachIndexed { i, t ->
            d = d.read("C$i", t, accepted = true)
        }
        assertEquals(3, d.readsPerMinute(75_000))
        assertEquals(1, d.readsPerMinute(129_000))
    }

    @Test
    fun `sin camara arrancada no hay tiempo`() {
        val d = ScanDiagnostics().read("A", 100, accepted = true)
        assertNull(d.firstReadLatencyMs)
        assertNull(d.reads.single().elapsedMs)
    }

    @Test
    fun `formatea segundos con coma y dos decimales`() {
        assertEquals("1,23 s", formatSeconds(1_234))
        assertEquals("0,05 s", formatSeconds(48))
        assertEquals("12,00 s", formatSeconds(11_999))
        assertEquals("0,00 s", formatSeconds(-5))
    }
}
