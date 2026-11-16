package co.daniel.mercado.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import co.daniel.mercado.util.AppLog

/** Qué retroalimentación quiere Daniel (Ajustes → Escáner). */
data class ScanFeedbackPrefs(val sound: Boolean = true, val vibration: Boolean = true)

/**
 * Retroalimentación de una lectura (04-ux): aceptada = vibración de 30 ms + pitido corto;
 * desconocida = vibración doble, sin pitido. Cada canal respeta su ajuste.
 */
interface ScanFeedback {
    fun accepted()
    fun unknown()

    /**
     * Cruce del semáforo hacia amarillo ([red] = false) o rojo (RF-07): vibración distinta, una vez
     * por cruce. No depende del ajuste de vibración del escáner: es un aviso de presupuesto.
     */
    fun budgetWarning(red: Boolean) = Unit

    fun release()
}

/** Motor de vibración; [pattern] alterna espera y vibración en ms, como `VibrationEffect`. */
fun interface Haptics {
    fun vibrate(pattern: LongArray)
}

fun interface Beeper {
    fun beep()
}

class DefaultScanFeedback(
    private val prefs: () -> ScanFeedbackPrefs,
    private val haptics: Haptics,
    private val beeper: Beeper,
    private val onRelease: () -> Unit = {},
) : ScanFeedback {

    override fun accepted() {
        val p = prefs()
        if (p.vibration) haptics.vibrate(ACCEPTED_PATTERN)
        if (p.sound) beeper.beep()
    }

    override fun unknown() {
        if (prefs().vibration) haptics.vibrate(UNKNOWN_PATTERN)
    }

    override fun budgetWarning(red: Boolean) {
        haptics.vibrate(if (red) RED_PATTERN else YELLOW_PATTERN)
    }

    override fun release() = onRelease()

    companion object {
        val ACCEPTED_PATTERN = longArrayOf(0, 30)
        val UNKNOWN_PATTERN = longArrayOf(0, 40, 90, 40)
        val YELLOW_PATTERN = longArrayOf(0, 150, 120, 150)
        val RED_PATTERN = longArrayOf(0, 300, 120, 300, 120, 300)

        fun create(context: Context, prefs: () -> ScanFeedbackPrefs): DefaultScanFeedback {
            val beeper = ToneBeeper()
            return DefaultScanFeedback(prefs, VibratorHaptics(context), beeper, beeper::release)
        }
    }
}

private class VibratorHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override fun vibrate(pattern: LongArray) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}

/** Pitido por el canal de notificaciones: respeta el modo silencio del teléfono. */
private class ToneBeeper : Beeper {
    private var tone: ToneGenerator? = null

    @Synchronized
    override fun beep() {
        val generator = tone ?: try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME).also { tone = it }
        } catch (e: RuntimeException) {
            AppLog.w("ScanFeedback: sin generador de tonos", e)
            return
        }
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
    }

    @Synchronized
    fun release() {
        tone?.release()
        tone = null
    }

    private companion object {
        const val VOLUME = 70
        const val BEEP_MS = 80
    }
}
