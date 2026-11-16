package co.daniel.mercado

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import co.daniel.mercado.ui.nav.LaunchAction
import co.daniel.mercado.ui.nav.MercadoNavHost
import co.daniel.mercado.ui.theme.MercadoTheme

class MainActivity : ComponentActivity() {

    private val launchAction = mutableStateOf(LaunchAction.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) launchAction.value = intent.toLaunchAction()
        val container = (application as MercadoApp).container
        setContent {
            MercadoTheme {
                MercadoNavHost(
                    container = container,
                    launchAction = launchAction.value,
                    onLaunchActionHandled = { launchAction.value = LaunchAction.NONE },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toLaunchAction().takeIf { it != LaunchAction.NONE }?.let { launchAction.value = it }
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
