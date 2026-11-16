package co.daniel.mercado.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Por qué no se pudo abrir un respaldo. */
enum class BackupCryptoError {
    TOO_LARGE,
    NOT_A_BACKUP,
    BAD_ITERATIONS,

    /** Contraseña incorrecta o archivo alterado: GCM no distingue una cosa de la otra. */
    WRONG_PASSWORD_OR_TAMPERED,
}

class BackupCryptoException(val error: BackupCryptoError) : Exception(error.name)

/**
 * Formato `MRC1` (03-datos, S-08):
 * `MRC1` · iteraciones (int32 BE) · sal (16) · IV (12) · AES-256-GCM(datos) con etiqueta de 16 al
 * final; AAD = bytes 0–23. Clave = PBKDF2WithHmacSHA256(contraseña, sal, iteraciones, 256 bits).
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('M'.code.toByte(), 'R'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    const val ITERATIONS = 600_000
    val ITERATION_RANGE = 100_000..5_000_000
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
    const val MIN_PASSWORD = 8
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 4 + SALT_BYTES
    private const val PREFIX_BYTES = HEADER_BYTES + IV_BYTES

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(iterations in ITERATION_RANGE) { "iteraciones fuera de rango" }
        require(password.size >= MIN_PASSWORD) { "contraseña corta" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putInt(iterations).put(salt).array()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        val body = cipher.doFinal(plain)
        return header + iv + body
    }

    /** Valida cabecera y límites antes de derivar la clave; falla con [BackupCryptoException]. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size > MAX_FILE_BYTES) throw BackupCryptoException(BackupCryptoError.TOO_LARGE)
        if (file.size < PREFIX_BYTES + TAG_BITS / 8 || !file.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw BackupCryptoException(BackupCryptoError.NOT_A_BACKUP)
        }
        val iterations = ByteBuffer.wrap(file, 4, 4).int
        if (iterations !in ITERATION_RANGE) throw BackupCryptoException(BackupCryptoError.BAD_ITERATIONS)
        val salt = file.copyOfRange(8, HEADER_BYTES)
        val iv = file.copyOfRange(HEADER_BYTES, PREFIX_BYTES)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(file, 0, HEADER_BYTES)
            cipher.doFinal(file, PREFIX_BYTES, file.size - PREFIX_BYTES)
        } catch (_: AEADBadTagException) {
            throw BackupCryptoException(BackupCryptoError.WRONG_PASSWORD_OR_TAMPERED)
        } catch (_: GeneralSecurityException) {
            throw BackupCryptoException(BackupCryptoError.WRONG_PASSWORD_OR_TAMPERED)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
