package co.daniel.mercado.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom

/** La clave de la base no se puede recuperar: la base existente queda ilegible. */
class DbKeyException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * S-03: clave de la base de 32 bytes de [SecureRandom], guardada solo envuelta por [wrapper] en
 * `noBackupFilesDir/db.key`. Formato del archivo: `MDK1` + largo del IV (1 byte) + IV + cifrado.
 */
class DbKeyManager(
    private val keyFile: File,
    private val databaseFile: File,
    private val wrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) {
    /** Devuelve la clave (una copia nueva en cada llamada; quien la usa la borra). */
    @Synchronized
    fun getOrCreateKey(): ByteArray {
        if (keyFile.exists()) return readKey()
        if (databaseFile.exists()) {
            throw DbKeyException("Existe la base pero no su clave")
        }
        val key = ByteArray(KEY_SIZE).also(random::nextBytes)
        writeAtomically(wrapper.wrap(key))
        return key
    }

    private fun readKey(): ByteArray {
        val wrapped = try {
            DataInputStream(keyFile.inputStream().buffered()).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                if (!magic.contentEquals(MAGIC)) throw DbKeyException("Archivo de clave con formato desconocido")
                val iv = ByteArray(input.readUnsignedByte()).also(input::readFully)
                val ciphertext = input.readBytes()
                WrappedKey(iv, ciphertext)
            }
        } catch (e: DbKeyException) {
            throw e
        } catch (e: IOException) {
            throw DbKeyException("No se pudo leer el archivo de clave", e)
        }
        val key = try {
            wrapper.unwrap(wrapped)
        } catch (e: Exception) {
            throw DbKeyException("No se pudo descifrar la clave de la base", e)
        }
        if (key.size != KEY_SIZE) throw DbKeyException("La clave de la base tiene un tamaño inválido")
        return key
    }

    private fun writeAtomically(wrapped: WrappedKey) {
        keyFile.parentFile?.mkdirs()
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        FileOutputStream(tmp).use { fos ->
            DataOutputStream(fos).apply {
                write(MAGIC)
                writeByte(wrapped.iv.size)
                write(wrapped.iv)
                write(wrapped.ciphertext)
                flush()
            }
            fos.fd.sync()
        }
        if (!tmp.renameTo(keyFile)) {
            tmp.delete()
            throw DbKeyException("No se pudo guardar el archivo de clave")
        }
    }

    companion object {
        const val KEY_SIZE = 32
        const val FILE_NAME = "db.key"
        private val MAGIC = "MDK1".toByteArray(Charsets.US_ASCII)
    }
}
