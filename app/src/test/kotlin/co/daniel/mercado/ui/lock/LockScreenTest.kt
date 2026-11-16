package co.daniel.mercado.ui.lock

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import co.daniel.mercado.ui.theme.MercadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LockScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `lanza el dialogo al entrar y deja Desbloquear si se cancela`() {
        var prompts = 0
        compose.setContent { MercadoTheme { LockScreen(onUnlock = { prompts++ }) } }
        compose.waitForIdle()
        assertEquals(1, prompts)

        compose.onNodeWithText("Mercado").assertExists()
        compose.onNodeWithText("Desbloquear").performClick()
        assertEquals(2, prompts)
    }
}
