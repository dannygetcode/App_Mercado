package co.daniel.mercado.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import co.daniel.mercado.R

/** El teléfono tiene huella, PIN, patrón o contraseña configurados. */
fun Context.isDeviceSecure(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * D-10: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. En Android 9 y 10 esa combinación no existe;
 * ahí se usa `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, que también acepta PIN.
 */
private fun authenticators(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Resultado del diálogo del sistema. */
enum class UnlockResult { SUCCESS, CANCELED, UNAVAILABLE }

/** Muestra el diálogo del sistema (huella o PIN). La app no maneja contraseñas propias. */
fun FragmentActivity.showUnlockPrompt(onResult: (UnlockResult) -> Unit) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onResult(UnlockResult.SUCCESS)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    -> if (isDeviceSecure()) UnlockResult.CANCELED else UnlockResult.UNAVAILABLE
                    else -> UnlockResult.CANCELED
                },
            )
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.lock_prompt_title))
        .setAllowedAuthenticators(authenticators())
        .build()
    prompt.authenticate(info)
}
