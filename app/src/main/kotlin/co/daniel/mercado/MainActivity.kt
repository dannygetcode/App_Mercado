package co.daniel.mercado

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.daniel.mercado.di.AppContainer
import co.daniel.mercado.scanner.SharedBarcodeSource
import co.daniel.mercado.ui.close.CloseTripScreen
import co.daniel.mercado.ui.close.CloseTripViewModel
import co.daniel.mercado.ui.diagnostics.ScanDiagnosticsScreen
import co.daniel.mercado.ui.home.HomeScreen
import co.daniel.mercado.ui.home.HomeViewModel
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.trip.TripScreen
import co.daniel.mercado.ui.trip.TripViewModel

/** Pantallas mientras llega la navegación real (L5, `MercadoNavHost`). */
enum class Screen { HOME, TRIP, CLOSE, DIAGNOSTICS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MercadoApp).container
        val openDiagnostics = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_OPEN_DIAGNOSTICS, false)
        setContent {
            MercadoTheme {
                MercadoRoot(container, if (openDiagnostics) Screen.DIAGNOSTICS else Screen.HOME)
            }
        }
    }

    private companion object {
        // Solo debug: `adb shell am start ... --ez open_diagnostics true` abre el diagnóstico sin
        // tocar la pantalla (HyperOS no deja inyectar toques por adb).
        const val EXTRA_OPEN_DIAGNOSTICS = "open_diagnostics"
    }
}

@Composable
private fun MercadoRoot(container: AppContainer, initial: Screen) {
    var screen by rememberSaveable { mutableStateOf(initial) }
    // Si hay una compra abierta al arrancar, la app abre directo en "Compra en curso" (04-ux).
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    var closeVisit by rememberSaveable { mutableStateOf(0) }
    val home: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val homeState by home.state.collectAsStateWithLifecycle()
    LaunchedEffect(homeState.loading, homeState.openTripTotal) {
        if (!autoOpened && !homeState.loading) {
            autoOpened = true
            if (homeState.openTripTotal != null && screen == Screen.HOME) screen = Screen.TRIP
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            state = homeState,
            onStartOrContinue = {
                if (homeState.openTripTotal != null) screen = Screen.TRIP else home.openLimitSheet()
            },
            onStartTrip = { limit -> home.startTrip(limit) { screen = Screen.TRIP } },
            onDismissLimit = home::dismissLimitSheet,
            onOpenDiagnostics = if (BuildConfig.DEBUG) {
                { screen = Screen.DIAGNOSTICS }
            } else {
                null
            },
        )
        Screen.TRIP -> {
            val trip: TripViewModel = viewModel(factory = TripViewModel.Factory)
            TripScreen(
                viewModel = trip,
                cameraSource = container.tripBarcodeSource,
                decoderFactory = container::newBarcodeDecoder,
                onBack = { screen = Screen.HOME },
                onClose = {
                    trip.dismissSheet()
                    closeVisit++
                    screen = Screen.CLOSE
                },
                onNoActiveTrip = { screen = Screen.HOME },
            )
        }
        Screen.CLOSE -> {
            // Uno nuevo en cada cierre: lee la compra tal como está ahora.
            val close: CloseTripViewModel = viewModel(key = "close-$closeVisit", factory = CloseTripViewModel.Factory)
            CloseTripScreen(
                viewModel = close,
                onBackToTrip = { screen = Screen.TRIP },
                onDone = {
                    home.refreshMonth()
                    screen = Screen.HOME
                },
            )
        }
        Screen.DIAGNOSTICS -> {
            val source = remember { SharedBarcodeSource() }
            ScanDiagnosticsScreen(
                source = source,
                decoderFactory = container::newBarcodeDecoder,
                feedback = container.scanFeedback,
                clock = container.clock,
                onBack = { screen = Screen.HOME },
            )
        }
    }
}
