package co.daniel.mercado.scanner

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Fuente de códigos sin cámara para pruebas JVM e instrumentadas (L3-T5). Vive en `src/debug`
 * para que la compartan `test` y `androidTest` sin llegar a release.
 */
class FakeBarcodeSource : BarcodeSource {
    private val flow = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /** Todo lo que se ofreció, en orden, para verificar en las pruebas. */
    val offered: List<String> get() = synchronized(log) { log.toList() }
    private val log = mutableListOf<String>()

    override val codes: Flow<String> = flow.asSharedFlow()

    override fun offer(code: String) {
        synchronized(log) { log += code }
        flow.tryEmit(code)
    }

    /** Espera a que alguien esté escuchando y entrega el código (no se pierde como en la cámara). */
    suspend fun emit(code: String) {
        flow.subscriptionCount.first { it > 0 }
        synchronized(log) { log += code }
        flow.emit(code)
    }
}
