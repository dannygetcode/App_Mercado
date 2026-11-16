package co.daniel.mercado.ui.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.scanner.BarcodeDecoder
import co.daniel.mercado.scanner.FakeBarcodeSource
import co.daniel.mercado.scanner.ScanFeedback
import co.daniel.mercado.ui.theme.MercadoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Sin permiso de cámara (Robolectric) la pantalla sigue usable: los códigos llegan por
 * [FakeBarcodeSource], pasan el antirrebote y aparecen en la lista.
 */
@RunWith(RobolectricTestRunner::class)
class ScanDiagnosticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val source = FakeBarcodeSource()
    private var now = 10_000L
    private val clock = Clock { now }
    private var accepted = 0
    private val feedback = object : ScanFeedback {
        override fun accepted() { accepted++ }
        override fun unknown() = Unit
        override fun release() = Unit
    }

    private fun show() {
        compose.setContent {
            MercadoTheme {
                ScanDiagnosticsScreen(
                    source = source,
                    decoderFactory = { error("Sin cámara en la prueba") as BarcodeDecoder },
                    feedback = feedback,
                    clock = clock,
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `sin permiso muestra el panel y los codigos falsos llegan a la lista`() {
        show()
        compose.onNodeWithText("Sin acceso a la cámara").assertIsDisplayed()
        compose.onNodeWithText("Apunta la cámara a un código de barras").assertIsDisplayed()

        runBlocking { source.emit("7702004003508") }
        compose.waitForIdle()

        compose.onNodeWithText("7702004003508").assertIsDisplayed()
        compose.onNodeWithText("Leídas: 1 · Rebotes ignorados: 0").assertIsDisplayed()
        assertEquals(1, accepted)
    }

    @Test
    fun `el mismo codigo dentro de 1,5 s cuenta como rebote`() {
        show()
        runBlocking { source.emit("7702004003508") }
        now += 500
        runBlocking { source.emit("7702004003508") }
        now += 1_500
        runBlocking { source.emit("7702004003508") }
        compose.waitForIdle()

        compose.onNodeWithText("Leídas: 2 · Rebotes ignorados: 1").assertIsDisplayed()
        compose.onNodeWithText("Lecturas en el último minuto: 2").assertIsDisplayed()
        assertEquals(2, accepted)
    }
}
