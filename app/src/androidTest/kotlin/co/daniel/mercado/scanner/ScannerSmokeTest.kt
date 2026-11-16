package co.daniel.mercado.scanner

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.daniel.mercado.MainActivity
import co.daniel.mercado.MercadoApp
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
                val target = instrumentation.targetContext
                if (!target.hasCameraPermission()) {
                    // HyperOS niega este atajo salvo con "Depuración USB (ajustes de seguridad)";
                    // la alternativa es conceder el permiso a mano una vez desde la app.
                    try {
                        instrumentation.uiAutomation.grantRuntimePermission(
                            target.packageName,
                            Manifest.permission.CAMERA,
                        )
                    } catch (e: SecurityException) {
                        throw AssertionError(
                            "Sin permiso de cámara: ábrela una vez desde Diagnóstico y acepta, " +
                                "o activa 'Depuración USB (ajustes de seguridad)'",
                            e,
                        )
                    }
                }
                // La compuerta de bloqueo (S-05) se prueba en JVM; aquí se abre sin huella.
                (target.applicationContext as MercadoApp).container.appLock.unlock()
                base.evaluate()
            }
        }
    }

    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(grantCamera).around(compose)

    @Test
    fun visorAbreConPermisoYNoSeCae() {
        // Abre el diagnóstico directo (extra de debug), aunque haya una compra abierta.
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(target, MainActivity::class.java).putExtra("open_diagnostics", true)
        ActivityScenario.launch<MainActivity>(intent).use { runSmoke() }
    }

    private fun runSmoke() {
        compose.waitUntil(CAMERA_WARMUP_MS) {
            compose.onAllNodesWithText("Pausar cámara").fetchSemanticsNodes().isNotEmpty()
        }
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
