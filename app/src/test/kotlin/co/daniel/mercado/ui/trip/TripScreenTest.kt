package co.daniel.mercado.ui.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.scanner.FakeBarcodeSource
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.trip.TripGraph.Companion.ARROZ
import co.daniel.mercado.ui.trip.TripGraph.Companion.DESCONOCIDO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Flujos de la pantalla "Compra en curso" sin cámara (los códigos llegan por la fuente falsa). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TripScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val t = TripGraph()
    private val source = FakeBarcodeSource()
    private val feedback = RecordingFeedback()
    private lateinit var vm: TripViewModel

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private fun show(limit: TripLimit = TripLimit.None) {
        runBlocking { t.graph.startTrip(limit) }
        vm = TripViewModel({ t.graph }, source, feedback, t.clock)
        compose.setContent {
            MercadoTheme {
                TripScreen(
                    viewModel = vm,
                    cameraSource = source,
                    decoderFactory = { error("sin cámara") },
                    onBack = {},
                    onClose = {},
                    onNoActiveTrip = {},
                    showCamera = false,
                )
            }
        }
        compose.waitUntil(5_000) { !vm.state.value.loading }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `compra vacia invita a escanear o usar el teclado`() {
        show()
        compose.onNodeWithText("Apunta la cámara a un código de barras o usa el teclado").assertIsDisplayed()
        compose.onNodeWithTag("trip_total").assertTextEquals("$0")
    }

    @Test
    fun `teclado, sumar, aparece la linea y el total`() {
        show()
        compose.onNodeWithText("$ Teclado").performClick()
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("000").performClick()
        compose.onNodeWithText("Sumar").performClick()

        waitForText("Artículo sin nombre")
        compose.onNodeWithTag("trip_total").assertTextEquals("$12.000")
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }

    @Test
    fun `menos hasta cero elimina la linea y deshacer la devuelve`() {
        runBlocking { t.product("Arroz Diana 1 kg", 5_900, ARROZ) }
        show()
        runBlocking { source.emit(ARROZ) }
        waitForText("Arroz Diana 1 kg")

        compose.onNodeWithTag("decrement").performClick()

        waitForText("Eliminado: Arroz Diana 1 kg")
        compose.onNodeWithTag("trip_total").assertTextEquals("$0")
        compose.onNodeWithText("DESHACER").performClick()
        compose.waitUntil(5_000) { vm.state.value.trip?.total == Pesos(5_900) }
        waitForText("Arroz Diana 1 kg")
    }

    @Test
    fun `codigo desconocido abre Producto nuevo y con solo el precio se suma`() {
        show()
        runBlocking { source.emit(DESCONOCIDO) }
        waitForText("Producto nuevo")
        compose.onNodeWithText(DESCONOCIDO).assertIsDisplayed()

        compose.onNodeWithText("7").performClick()
        compose.onNodeWithText("8").performClick()
        compose.onNodeWithText("00").performClick()
        compose.onNodeWithText("Sumar").performClick()

        waitForText("Producto ····7897")
        compose.onNodeWithTag("trip_total").assertTextEquals("$7.800")
    }

    @Test
    fun `buscar sin resultados ofrece crear el producto`() {
        show()
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithTag("search_field").performTextInput("Cilantro")
        waitForText("Crear «Cilantro»")
        compose.onNodeWithText("Crear «Cilantro»").performClick()
        waitForText("Precio de «Cilantro»")
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("5").performClick()
        compose.onNodeWithText("00").performClick()
        compose.onNodeWithText("Sumar").performClick()

        waitForText("Cilantro")
        compose.onNodeWithTag("trip_total").assertTextEquals("$1.500")
    }

    @Test
    fun `pasar el limite muestra te pasaste en rojo`() {
        show(TripLimit.Custom(Pesos(10_000)))
        compose.onNodeWithText("$ Teclado").performClick()
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("000").performClick()
        compose.onNodeWithText("Sumar").performClick()

        waitForText("Te pasaste por $2.000")
        compose.onNodeWithTag("traffic_RED").assertIsDisplayed()
    }
}
