package co.daniel.mercado.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Lógica de archivo de [DbKeyManager] con un envoltorio AES-GCM por software (sin Keystore). */
class DbKeyManagerJvmTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val wrapper = SoftwareKeyWrapper()

    private fun manager() = DbKeyManager(
        keyFile = tmp.root.resolve("no_backup/db.key"),
        databaseFile = tmp.root.resolve("databases/mercado.db"),
        wrapper = wrapper,
    )

    @Test
    fun `genera una clave de 32 bytes y la recupera igual tras reiniciar el objeto`() {
        val first = manager().getOrCreateKey()
        val second = manager().getOrCreateKey()

        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }

    @Test
    fun `el archivo no contiene la clave en claro`() {
        val key = manager().getOrCreateKey()
        val blob = tmp.root.resolve("no_backup/db.key").readBytes()

        assertFalse(blob.toList().windowed(key.size).any { it == key.toList() })
    }

    @Test
    fun `si existe la base sin su clave no genera una nueva`() {
        tmp.root.resolve("databases").mkdirs()
        tmp.root.resolve("databases/mercado.db").writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(DbKeyException::class.java) { manager().getOrCreateKey() }
        assertFalse(tmp.root.resolve("no_backup/db.key").exists())
    }

    @Test
    fun `un archivo de clave alterado falla en vez de devolver basura`() {
        manager().getOrCreateKey()
        val file = tmp.root.resolve("no_backup/db.key")
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)

        assertThrows(DbKeyException::class.java) { manager().getOrCreateKey() }
    }

    /** Proveedor del JDK explícito: Robolectric registra otro en la misma JVM. */
    private class SoftwareKeyWrapper : KeyWrapper {
        private val kek = KeyGenerator.getInstance("AES", JDK_PROVIDER).apply { init(256) }.generateKey()

        override fun wrap(plain: ByteArray): WrappedKey {
            val c = Cipher.getInstance("AES/GCM/NoPadding", JDK_PROVIDER).apply { init(Cipher.ENCRYPT_MODE, kek) }
            return WrappedKey(c.iv, c.doFinal(plain))
        }

        override fun unwrap(wrapped: WrappedKey): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding", JDK_PROVIDER)
                .apply { init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, wrapped.iv)) }
                .doFinal(wrapped.ciphertext)
    }
}

private const val JDK_PROVIDER = "SunJCE"
