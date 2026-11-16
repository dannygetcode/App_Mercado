package co.daniel.mercado.data.settings

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    private fun store(secureDefault: Boolean) =
        SettingsStore(InMemoryPreferencesDataStore(), secureScreenDefault = secureDefault)

    @Test
    fun `los valores por defecto son los de 03-datos`() = runTest {
        val s = store(secureDefault = true).current()

        assertEquals(Settings(), s)
        assertEquals(5, s.lockGraceMinutes)
        assertEquals(15, s.shoppingFrequencyDays)
        assertFalse(s.seedImported)
    }

    @Test
    fun `pantalla segura apagada por defecto en debug`() = runTest {
        assertFalse(store(secureDefault = false).current().secureScreen)
    }

    @Test
    fun `guarda y lee cada ajuste`() = runTest {
        val store = store(secureDefault = true)
        store.setLockEnabled(false)
        store.setLockGraceMinutes(15)
        store.setScanSound(false)
        store.setShoppingFrequencyDays(8)
        store.setSeedImported(true)
        store.setLastBackupAt(123L)

        val s = store.current()
        assertFalse(s.lockEnabled)
        assertEquals(15, s.lockGraceMinutes)
        assertFalse(s.scanSound)
        assertTrue(s.scanVibration)
        assertEquals(8, s.shoppingFrequencyDays)
        assertTrue(s.seedImported)
        assertEquals(123L, s.lastBackupAt)
    }

    @Test
    fun `rechaza valores fuera de las opciones`() = runTest {
        val store = store(secureDefault = true)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.setLockGraceMinutes(7) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.setShoppingFrequencyDays(0) } }
    }
}
