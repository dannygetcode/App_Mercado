package co.daniel.mercado.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Bundle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-08 con el proveedor criptográfico del teléfono y las 600.000 iteraciones reales. */
@RunWith(AndroidJUnit4::class)
class BackupCryptoDeviceTest {

    @Test
    fun idaYVueltaConIteracionesReales() {
        val plain = ByteArray(200_000) { (it % 251).toByte() }
        val password = "contraseña-del-mercado".toCharArray()

        val start = System.nanoTime()
        val file = BackupCrypto.encrypt(plain, password)
        val encryptMs = (System.nanoTime() - start) / 1_000_000
        val back = BackupCrypto.decrypt(file, password)

        assertArrayEquals(plain, back)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("cifrar_ms", encryptMs.toString()) })
        assertTrue("PBKDF2 demasiado lento: $encryptMs ms", encryptMs < 15_000)
    }
}
