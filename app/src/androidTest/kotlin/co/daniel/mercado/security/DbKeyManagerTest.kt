package co.daniel.mercado.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

/** S-03 con el Android Keystore real. Usa un alias y archivos propios de la prueba. */
@RunWith(AndroidJUnit4::class)
class DbKeyManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.noBackupFilesDir, "dbkey-test").apply { deleteRecursively(); mkdirs() }
    private val keyFile = File(dir, "db.key")

    private fun manager() = DbKeyManager(keyFile, File(dir, "none.db"), KeystoreKeyWrapper(ALIAS))

    @After
    fun cleanup() {
        dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    @Test
    fun generaGuardaYRecuperaLaMismaClaveTrasReiniciarElObjeto() {
        val first = manager().getOrCreateKey()
        val second = manager().getOrCreateKey()

        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }

    @Test
    fun elBlobNoContieneLaClaveEnClaro() {
        val key = manager().getOrCreateKey()
        val blob = keyFile.readBytes()

        assertFalse(blob.toList().windowed(key.size).any { it == key.toList() })
    }

    @Test
    fun laClaveDelKeystoreNoEsExportable() {
        manager().getOrCreateKey()
        val entry = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(ALIAS, null)

        assertEquals(null, entry.encoded)
    }

    private companion object {
        const val ALIAS = "mercado_db_kek_test"
    }
}
