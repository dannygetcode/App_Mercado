package co.daniel.mercado.data.db

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Crea la base. Se inyecta para que las pruebas JVM usen Room en memoria sin cifrar. */
fun interface DatabaseFactory {
    fun create(context: Context): MercadoDatabase
}

/**
 * Base real (S-02): `mercado.db` cifrada con SQLCipher. La clave de 32 bytes viene de
 * [keyProvider] (DbKeyManager) y se entrega como clave cruda `x'…'`, sin derivación PBKDF2,
 * porque ya es aleatoria; así abrir la base no cuesta cientos de milisegundos en el arranque.
 */
class SqlCipherDatabaseFactory(
    private val keyProvider: () -> ByteArray,
    private val fileName: String = MercadoDatabase.FILE_NAME,
) : DatabaseFactory {

    override fun create(context: Context): MercadoDatabase {
        val key = keyProvider()
        val passphrase = rawKeyPassphrase(key)
        key.fill(0)
        return Room.databaseBuilder(context, MercadoDatabase::class.java, fileName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase, null, true))
            .addCallback(MercadoDatabaseCallback)
            .build()
    }

    companion object {
        const val LIBRARY = "sqlcipher"

        /** Formato de clave cruda de SQLCipher: `x'<64 hex>'`. */
        fun rawKeyPassphrase(key: ByteArray): ByteArray {
            require(key.size == 32) { "la clave debe tener 32 bytes" }
            val hex = StringBuilder(key.size * 2 + 3).append("x'")
            key.forEach { b -> hex.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF]) }
            hex.append('\'')
            return hex.toString().toByteArray(Charsets.US_ASCII)
        }

        private const val HEX = "0123456789ABCDEF"
    }
}
