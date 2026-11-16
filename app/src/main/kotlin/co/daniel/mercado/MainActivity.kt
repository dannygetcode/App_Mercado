package co.daniel.mercado

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import co.daniel.mercado.security.LockPolicy
import co.daniel.mercado.security.LockState
import co.daniel.mercado.security.UnlockResult
import co.daniel.mercado.security.isDeviceSecure
import co.daniel.mercado.security.showUnlockPrompt
import co.daniel.mercado.ui.lock.LockScreen
import co.daniel.mercado.ui.nav.LaunchAction
import co.daniel.mercado.ui.nav.MercadoNavHost
import co.daniel.mercado.ui.theme.MercadoTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** `FragmentActivity` porque `BiometricPrompt` lo exige. */
class MainActivity : FragmentActivity() {

    private val launchAction = mutableStateOf(LaunchAction.NONE)
    private val container get() = (application as MercadoApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) launchAction.value = intent.toLaunchAction()
        applySecureScreen()
        setContent {
            MercadoTheme {
                val lockState by container.appLock.state.collectAsStateWithLifecycle()
                // S-05: mientras no esté desbloqueada no se compone ninguna pantalla con datos.
                when (lockState) {
                    LockState.CHECKING -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                    LockState.LOCKED -> LockScreen(onUnlock = ::promptUnlock)
                    LockState.UNLOCKED -> MercadoNavHost(
                        container = container,
                        launchAction = launchAction.value,
                        onLaunchActionHandled = { launchAction.value = LaunchAction.NONE },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val policy = lockPolicy()
            container.appLock.onAppStart(policy)
            container.appLock.onForeground(policy)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.appLock.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toLaunchAction().takeIf { it != LaunchAction.NONE }?.let { launchAction.value = it }
    }

    private suspend fun lockPolicy(): LockPolicy {
        val s = container.settings.current()
        return LockPolicy(enabled = s.lockEnabled, graceMinutes = s.lockGraceMinutes, deviceSecure = isDeviceSecure())
    }

    private fun promptUnlock() {
        if (!isDeviceSecure()) {
            container.appLock.unlock()
            return
        }
        showUnlockPrompt { result ->
            if (result != UnlockResult.CANCELED) container.appLock.unlock()
        }
    }

    /** S-06: `FLAG_SECURE` según el ajuste (por defecto sí en release, no en debug). */
    private fun applySecureScreen() {
        // En release se pone de una vez, antes del primer cuadro; luego manda el ajuste.
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            container.settings.settings.map { it.secureScreen }.distinctUntilChanged().collect { secure ->
                if (secure) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    private fun Intent.toLaunchAction(): LaunchAction = when {
        action == ACTION_NEW_TRIP -> LaunchAction.NEW_TRIP
        // Solo debug: `adb shell am start ... --ez open_diagnostics true` abre el diagnóstico sin
        // tocar la pantalla (HyperOS no deja inyectar toques por adb).
        BuildConfig.DEBUG && getBooleanExtra(EXTRA_OPEN_DIAGNOSTICS, false) -> LaunchAction.DIAGNOSTICS
        else -> LaunchAction.NONE
    }

    companion object {
        const val ACTION_NEW_TRIP = "co.daniel.mercado.action.NEW_TRIP"
        private const val EXTRA_OPEN_DIAGNOSTICS = "open_diagnostics"
    }
}
