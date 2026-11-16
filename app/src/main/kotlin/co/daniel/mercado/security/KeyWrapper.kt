package co.daniel.mercado.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Clave cifrada: vector de inicialización + texto cifrado con su etiqueta GCM. */
class WrappedKey(val iv: ByteArray, val ciphertext: ByteArray)

/** Envuelve y desenvuelve la clave de la base con una clave que nunca sale del hardware. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): WrappedKey
    fun unwrap(wrapped: WrappedKey): ByteArray
}

/**
 * S-03 / D-09: clave AES-256-GCM del Android Keystore (alias `mercado_db_kek`), no exportable,
 * en StrongBox si existe y si no en el TEE, **no ligada a autenticación** (si se ligara, cambiar las
 * huellas o el bloqueo de pantalla la invalidaría y se perdería la base).
 */
class KeystoreKeyWrapper(private val alias: String = ALIAS) : KeyWrapper {

    override fun wrap(plain: ByteArray): WrappedKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyEncryptionKey())
        return WrappedKey(cipher.iv, cipher.doFinal(plain))
    }

    override fun unwrap(wrapped: WrappedKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyEncryptionKey(), GCMParameterSpec(TAG_BITS, wrapped.iv))
        return cipher.doFinal(wrapped.ciphertext)
    }

    private fun keyEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return generate(strongBox = false)
        return try {
            generate(strongBox = true)
        } catch (_: ProviderException) {
            // StrongBoxUnavailableException (API 28) hereda de ProviderException: sin StrongBox, TEE.
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        const val ALIAS = "mercado_db_kek"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
