package co.daniel.mercado.util

import android.util.Log
import co.daniel.mercado.BuildConfig

/**
 * Único punto de registro de la app (S-10). En release no hace nada.
 * Nunca registrar nombres de productos, precios ni totales.
 */
object AppLog {
    private const val TAG = "Mercado"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, error)
    }
}
