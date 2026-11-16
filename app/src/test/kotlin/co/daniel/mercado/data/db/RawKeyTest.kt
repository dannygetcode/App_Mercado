package co.daniel.mercado.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class RawKeyTest {
    @Test
    fun `la clave se entrega a SQLCipher como clave cruda hexadecimal`() {
        val key = ByteArray(32) { it.toByte() }
        val passphrase = SqlCipherDatabaseFactory.rawKeyPassphrase(key).toString(Charsets.US_ASCII)

        assertEquals("x'000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F'", passphrase)
    }
}
