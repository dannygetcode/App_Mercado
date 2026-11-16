package co.daniel.mercado.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.ui.theme.MercadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `muestra el nombre de la app e Ir a mercar`() {
        var taps = 0
        compose.setContent { MercadoTheme { HomeScreen(HomeUiState(loading = false), { taps++ }, {}, {}) } }
        compose.onNodeWithText("Mercado").assertIsDisplayed()
        compose.onNodeWithText("Ir a mercar").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `con compra abierta ofrece continuarla con su total`() {
        compose.setContent {
            MercadoTheme { HomeScreen(HomeUiState(loading = false, openTripTotal = Pesos(187_400)), {}, {}, {}) }
        }
        compose.onNodeWithText("Continuar compra · $187.400").assertIsDisplayed()
    }

    @Test
    fun `la hoja de limite muestra lo que queda y Empezar lo entrega`() {
        var started: Pesos? = null
        compose.setContent {
            MercadoTheme {
                HomeScreen(
                    HomeUiState(loading = false, limitSheet = true, suggestedLimit = Pesos(250_000)),
                    {}, { started = it }, {},
                )
            }
        }
        compose.onNodeWithText("Límite de esta compra").assertIsDisplayed()
        compose.onNodeWithText("Empezar").performClick()
        assertEquals(Pesos(250_000), started)
    }
}
