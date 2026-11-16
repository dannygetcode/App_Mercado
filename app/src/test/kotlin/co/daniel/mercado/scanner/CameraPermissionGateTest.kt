package co.daniel.mercado.scanner

import android.Manifest
import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import co.daniel.mercado.ui.theme.MercadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CameraPermissionGateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `sin permiso muestra el panel y no el visor`() {
        compose.setContent {
            MercadoTheme { CameraPermissionGate(autoRequest = false) { Text("VISOR") } }
        }
        compose.onNodeWithText("Sin acceso a la cámara").assertIsDisplayed()
        compose.onNodeWithText("Permitir").assertIsDisplayed()
        compose.onNodeWithText("VISOR").assertDoesNotExist()
    }

    @Test
    fun `con permiso muestra el visor`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.CAMERA)
        compose.setContent {
            MercadoTheme { CameraPermissionGate { Text("VISOR") } }
        }
        compose.onNodeWithText("VISOR").assertIsDisplayed()
        compose.onNodeWithText("Sin acceso a la cámara").assertDoesNotExist()
    }

    @Test
    fun `el boton Permitir llama a la accion`() {
        var clicks = 0
        compose.setContent { MercadoTheme { CameraDeniedPanel(onAllow = { clicks++ }) } }
        compose.onNodeWithText("Permitir").performClick()
        assertEquals(1, clicks)
    }
}
