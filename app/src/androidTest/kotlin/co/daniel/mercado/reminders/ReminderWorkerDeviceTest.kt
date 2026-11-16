package co.daniel.mercado.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-11: `SystemJobService` va con `exported="false"`; el sistema debe poder ejecutarlo igual.
 * Encola la revisión una vez y espera a que termine (con los ajustes del teléfono; si los
 * recordatorios están apagados, no notifica nada).
 */
@RunWith(AndroidJUnit4::class)
class ReminderWorkerDeviceTest {

    @Test
    fun elTrabajoDeRecordatoriosCorreConElServicioNoExportado() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().build()
        work.enqueue(request).result.get()

        val deadline = System.currentTimeMillis() + 90_000
        var state = work.getWorkInfoById(request.id).get()?.state
        while (state?.isFinished != true && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            state = work.getWorkInfoById(request.id).get()?.state
        }
        assertEquals(WorkInfo.State.SUCCEEDED, state)
    }
}
