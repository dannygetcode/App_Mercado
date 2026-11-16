package co.daniel.mercado.security

import co.daniel.mercado.domain.port.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockState {
    /** Todavía no se leyeron los ajustes: no se muestra nada con datos. */
    CHECKING,
    LOCKED,
    UNLOCKED,
}

/** Lo que decide si hay que bloquear, leído en cada evento. */
data class LockPolicy(
    val enabled: Boolean,
    val graceMinutes: Int,
    /** El teléfono tiene huella, PIN, patrón o contraseña. Sin eso no hay con qué desbloquear. */
    val deviceSecure: Boolean,
) {
    val active: Boolean get() = enabled && deviceSecure
}

/**
 * Compuerta de la app (S-05, D-10). Bloquea al abrir el proceso y al volver de segundo plano
 * pasada la gracia. Vive en el `AppContainer` (una por proceso): recrear la actividad no bloquea.
 * El bloqueo es solo de interfaz (D-09): la base no depende de él.
 */
class AppLock(private val clock: Clock) {

    private val _state = MutableStateFlow(LockState.CHECKING)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var backgroundAt: Long? = null

    /** Primer evento del proceso: bloquea si el bloqueo está activo. */
    fun onAppStart(policy: LockPolicy) {
        if (_state.value != LockState.CHECKING) return
        _state.value = if (policy.active) LockState.LOCKED else LockState.UNLOCKED
    }

    /** La app pasó a segundo plano. */
    fun onBackground() {
        if (_state.value == LockState.UNLOCKED) backgroundAt = clock.nowMillis()
    }

    /** La app volvió. Bloquea si pasó la gracia (o más) desde que se fue. */
    fun onForeground(policy: LockPolicy) {
        val since = backgroundAt ?: return
        backgroundAt = null
        if (_state.value != LockState.UNLOCKED) return
        if (!policy.active) return
        val graceMillis = policy.graceMinutes * 60_000L
        if (clock.nowMillis() - since >= graceMillis) _state.value = LockState.LOCKED
    }

    /** Desbloqueado con éxito, o el bloqueo dejó de aplicar (desactivado o sin bloqueo del teléfono). */
    fun unlock() {
        backgroundAt = null
        _state.value = LockState.UNLOCKED
    }
}
