package co.daniel.mercado

import android.app.Application
import co.daniel.mercado.data.db.SqlCipherDatabaseFactory
import co.daniel.mercado.di.AppContainer
import co.daniel.mercado.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MercadoApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary(SqlCipherDatabaseFactory.LIBRARY)
        container = AppContainer(this)
        // RF-11: la revisión diaria queda programada según el ajuste (KEEP: no se duplica).
        scope.launch { Reminders.schedule(this@MercadoApp, container.settings.current().remindersEnabled) }
    }
}
