package co.daniel.mercado.scanner

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFeedbackTest {

    private val vibrations = mutableListOf<LongArray>()
    private var beeps = 0
    private var prefs = ScanFeedbackPrefs()
    private val feedback = DefaultScanFeedback({ prefs }, { vibrations += it }, { beeps++ })

    @Test
    fun `aceptada vibra 30 ms y pita`() {
        feedback.accepted()
        assertEquals(1, vibrations.size)
        assertArrayEquals(longArrayOf(0, 30), vibrations.single())
        assertEquals(1, beeps)
    }

    @Test
    fun `desconocida vibra doble y no pita`() {
        feedback.unknown()
        assertEquals(4, vibrations.single().size)
        assertEquals(0, beeps)
    }

    @Test
    fun `respeta el sonido apagado`() {
        prefs = ScanFeedbackPrefs(sound = false, vibration = true)
        feedback.accepted()
        assertEquals(0, beeps)
        assertEquals(1, vibrations.size)
    }

    @Test
    fun `respeta la vibracion apagada`() {
        prefs = ScanFeedbackPrefs(sound = true, vibration = false)
        feedback.accepted()
        feedback.unknown()
        assertEquals(0, vibrations.size)
        assertEquals(1, beeps)
    }

    @Test
    fun `lee los ajustes en cada lectura`() {
        feedback.accepted()
        prefs = ScanFeedbackPrefs(sound = false, vibration = false)
        feedback.accepted()
        assertEquals(1, beeps)
        assertEquals(1, vibrations.size)
    }
}
