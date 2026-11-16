package co.daniel.mercado.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ML Kit Barcode Scanning, variante bundled (el modelo va dentro del APK; no necesita red).
 * Solo los formatos de RF-03, para que cada cuadro se procese rápido.
 */
class MlKitBarcodeDecoder : BarcodeDecoder {

    private val closed = AtomicBoolean(false)

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(FORMATS.first(), *FORMATS.drop(1).toIntArray())
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy, onResult: (List<String>) -> Unit) {
        val media = image.image
        if (closed.get() || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                if (!closed.get()) {
                    val codes = barcodes.mapNotNull { it.rawValue }
                    if (codes.isNotEmpty()) onResult(codes)
                }
            }
            .addOnCompleteListener { image.close() }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) scanner.close()
    }

    companion object {
        val FORMATS: List<Int> = listOf(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
        )
    }
}
