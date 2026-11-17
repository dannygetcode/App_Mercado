package co.daniel.mercado.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.rules.ShelfPrice
import co.daniel.mercado.domain.rules.TextLine
import co.daniel.mercado.util.AppLog
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/** Lo leído en la etiqueta del estante: el código (si trae) y el precio grande (si se encontró). */
data class LabelRead(val code: String?, val price: Pesos?)

/** Lee etiquetas de estante: código de barras y texto en el mismo cuadro. Debe cerrar el [ImageProxy]. */
interface LabelReader {
    fun analyze(image: ImageProxy, onResult: (LabelRead) -> Unit)

    fun close()
}

/**
 * ML Kit bundled (texto latino + códigos), en el teléfono y sin red. Ningún cuadro va a disco
 * (S-07); solo se entrega el código y el precio.
 */
class MlKitLabelReader : LabelReader {

    private val closed = AtomicBoolean(false)
    private val text: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodes: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(MlKitBarcodeDecoder.FORMATS.first(), *MlKitBarcodeDecoder.FORMATS.drop(1).toIntArray())
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy, onResult: (LabelRead) -> Unit) {
        val media = image.image
        if (closed.get() || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        val textTask = text.process(input)
        val codeTask = barcodes.process(input)
        Tasks.whenAllComplete(textTask, codeTask).addOnCompleteListener {
            try {
                if (closed.get()) return@addOnCompleteListener
                val lines = if (textTask.isSuccessful) linesOf(textTask.result) else emptyList()
                val code = if (codeTask.isSuccessful) ScanFilter.firstValid(codeTask.result.mapNotNull { it.rawValue }) else null
                val price = ShelfPrice.find(lines)
                // Solo en debug (AppLog no escribe en release): qué vio la cámara y qué precio salió.
                AppLog.d("Etiqueta: ${lines.joinToString(" | ") { "${it.text}[${it.height}]" }} -> $price / $code")
                if (code != null || price != null) onResult(LabelRead(code, price))
            } finally {
                image.close()
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            text.close()
            barcodes.close()
        }
    }
}

/** Líneas del resultado de ML Kit con su alto (el tamaño de letra decide cuál es el precio). */
fun linesOf(text: Text): List<TextLine> =
    text.textBlocks.flatMap { block -> block.lines.map { TextLine(it.text, it.boundingBox?.height() ?: 0) } }

