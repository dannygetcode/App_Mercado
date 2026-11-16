package co.daniel.mercado.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import co.daniel.mercado.ui.theme.MercadoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `muestra el nombre de la app`() {
        compose.setContent { MercadoTheme { HomeScreen() } }
        compose.onNodeWithText("Mercado").assertIsDisplayed()
    }
}
