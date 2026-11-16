package co.daniel.mercado.scanner

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fuente de códigos leídos hacia quien los procesa (ViewModel o diagnóstico). La cámara solo
 * llama a [offer]; en pruebas se usa `FakeBarcodeSource` sin cámara.
 */
interface BarcodeSource {
    val codes: Flow<String>

    fun offer(code: String)
}

/** Fuente en memoria. Sin nadie escuchando, el código se pierde (la cámara no está activa). */
open class SharedBarcodeSource : BarcodeSource {
    private val flow = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val codes: Flow<String> = flow.asSharedFlow()

    override fun offer(code: String) {
        flow.tryEmit(code)
    }
}
