package co.daniel.mercado

import android.app.Application
import co.daniel.mercado.di.AppContainer

class MercadoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
