package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.port.Clock

/**
 * Antirrebote del escáner: el mismo código aceptado hace menos de [windowMs] se ignora;
 * un código distinto se acepta de inmediato.
 */
class ScanDebouncer(
    private val clock: Clock,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {
    private val lastAccepted = HashMap<String, Long>()

    @Synchronized
    fun shouldAccept(code: String): Boolean {
        val now = clock.nowMillis()
        lastAccepted.entries.removeAll { now - it.value >= windowMs }
        if (lastAccepted.containsKey(code)) return false
        lastAccepted[code] = now
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 1_500L
    }
}
