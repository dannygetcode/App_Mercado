package co.daniel.mercado

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.daniel.mercado.scanner.SharedBarcodeSource
import co.daniel.mercado.ui.diagnostics.ScanDiagnosticsScreen
import co.daniel.mercado.ui.home.HomeScreen
import co.daniel.mercado.ui.theme.MercadoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MercadoApp).container
        setContent {
            MercadoTheme {
                // Navegación provisional hasta L5 (MercadoNavHost).
                var showDiagnostics by rememberSaveable { mutableStateOf(false) }
                if (BuildConfig.DEBUG && showDiagnostics) {
                    val source = remember { SharedBarcodeSource() }
                    ScanDiagnosticsScreen(
                        source = source,
                        decoderFactory = container::newBarcodeDecoder,
                        feedback = container.scanFeedback,
                        clock = container.clock,
                        onBack = { showDiagnostics = false },
                    )
                } else {
                    HomeScreen(
                        onOpenDiagnostics = if (BuildConfig.DEBUG) {
                            { showDiagnostics = true }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
