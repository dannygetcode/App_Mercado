package co.daniel.mercado.scanner

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.daniel.mercado.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Humo de cámara (06-pruebas): con permiso concedido, la pantalla con el visor abre, la cámara
 * arranca con ML Kit, se pausa y se reanuda sin caerse. En L3 la pantalla con visor es el
 * diagnóstico; en L4 se agrega la de compra.
 */
@RunWith(AndroidJUnit4::class)
class ScannerSmokeTest {

    private val grantCamera = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.uiAutomation.grantRuntimePermission(
                    instrumentation.targetContext.packageName,
                    Manifest.permission.CAMERA,
                )
                base.evaluate()
            }
        }
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(grantCamera).around(compose)

    @Test
    fun visorAbreConPermisoYNoSeCae() {
        compose.onNodeWithText("Diagnóstico del escáner").performClick()
        Thread.sleep(CAMERA_WARMUP_MS)
        compose.waitForIdle()
        compose.onNodeWithText("Sin acceso a la cámara").assertDoesNotExist()
        compose.onNodeWithText("No se pudo abrir la cámara").assertDoesNotExist()

        compose.onNodeWithText("Pausar cámara").performClick()
        compose.onNodeWithText("Reanudar cámara").performClick()
        Thread.sleep(CAMERA_WARMUP_MS)
        compose.waitForIdle()
        compose.onNodeWithText("No se pudo abrir la cámara").assertDoesNotExist()
        compose.onNodeWithText("Pausar cámara").assertExists()
    }

    private companion object {
        const val CAMERA_WARMUP_MS = 3_000L
    }
}
