package co.daniel.mercado.scanner

import androidx.camera.core.ImageProxy
import co.daniel.mercado.domain.rules.Ean

/**
 * Decodificador de códigos de barras detrás de una interfaz (D-07): ML Kit hoy, zxing-cpp si
 * ML Kit fallara sin red. Nada más en la app conoce la librería concreta.
 */
interface BarcodeDecoder {
    /** Analiza un cuadro y devuelve los códigos crudos encontrados. Debe cerrar el [ImageProxy]. */
    fun analyze(image: ImageProxy, onResult: (List<String>) -> Unit)

    fun close()
}

/** Se acepta la primera lectura con dígito de control válido; el resto del cuadro se descarta. */
object ScanFilter {
    fun firstValid(rawCodes: List<String>): String? = rawCodes.firstNotNullOfOrNull(Ean::normalize)
}
