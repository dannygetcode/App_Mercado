package co.daniel.mercado.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

/** S-08. Con 100.000 iteraciones (el mínimo aceptado) para que corran rápido. */
class BackupCryptoTest {

    private val password = "clave-del-mercado".toCharArray()
    private val plain = "{\"hola\":\"mercado\"}".repeat(50).toByteArray()
    private val fast = 100_000

    private fun encrypted() = BackupCrypto.encrypt(plain, password, iterations = fast)

    private fun failure(file: ByteArray, pass: CharArray = password): BackupCryptoError =
        assertThrows(BackupCryptoException::class.java) { BackupCrypto.decrypt(file, pass) }.error

    @Test
    fun `ida y vuelta`() {
        val file = encrypted()
        assertEquals("MRC1", String(file, 0, 4, Charsets.US_ASCII))
        assertEquals(fast, ByteBuffer.wrap(file, 4, 4).int)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("mercado"))
        assertArrayEquals(plain, BackupCrypto.decrypt(file, password))
    }

    @Test
    fun `la iteracion por defecto es 600000`() {
        val file = BackupCrypto.encrypt(plain, password)
        assertEquals(600_000, ByteBuffer.wrap(file, 4, 4).int)
    }

    @Test
    fun `contrasena incorrecta`() {
        assertEquals(BackupCryptoError.WRONG_PASSWORD_OR_TAMPERED, failure(encrypted(), "otra-clave-123".toCharArray()))
    }

    @Test
    fun `un byte alterado del contenido`() {
        val file = encrypted()
        file[file.size - 20] = (file[file.size - 20].toInt() xor 1).toByte()
        assertEquals(BackupCryptoError.WRONG_PASSWORD_OR_TAMPERED, failure(file))
    }

    @Test
    fun `cabecera alterada falla por el AAD`() {
        val file = encrypted()
        file[10] = (file[10].toInt() xor 1).toByte() // dentro de la sal
        assertEquals(BackupCryptoError.WRONG_PASSWORD_OR_TAMPERED, failure(file))
        val iv = encrypted()
        iv[30] = (iv[30].toInt() xor 1).toByte()
        assertEquals(BackupCryptoError.WRONG_PASSWORD_OR_TAMPERED, failure(iv))
    }

    @Test
    fun `iteraciones fuera de rango no se intentan`() {
        val low = encrypted().also { ByteBuffer.wrap(it).putInt(4, 99_999) }
        val high = encrypted().also { ByteBuffer.wrap(it).putInt(4, 5_000_001) }
        assertEquals(BackupCryptoError.BAD_ITERATIONS, failure(low))
        assertEquals(BackupCryptoError.BAD_ITERATIONS, failure(high))
    }

    @Test
    fun `magia equivocada, archivo corto o gigante`() {
        val file = encrypted().also { it[0] = 'X'.code.toByte() }
        assertEquals(BackupCryptoError.NOT_A_BACKUP, failure(file))
        assertEquals(BackupCryptoError.NOT_A_BACKUP, failure("MRC1".toByteArray()))
        assertEquals(BackupCryptoError.TOO_LARGE, failure(ByteArray(BackupCrypto.MAX_FILE_BYTES + 1)))
    }

    @Test
    fun `cada respaldo usa sal e IV nuevos`() {
        val a = encrypted()
        val b = encrypted()
        assertFalse(a.copyOfRange(8, 36).contentEquals(b.copyOfRange(8, 36)))
    }
}
