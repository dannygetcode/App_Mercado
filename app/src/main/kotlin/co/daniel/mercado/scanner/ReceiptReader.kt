package co.daniel.mercado.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import co.daniel.mercado.domain.rules.ReceiptParser
import co.daniel.mercado.domain.rules.ReceiptRows
import co.daniel.mercado.domain.rules.ReceiptScan
import co.daniel.mercado.domain.rules.TextBox
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/** Lee un recibo cuadro a cuadro mientras la cámara lo recorre. Debe cerrar el [ImageProxy]. */
interface ReceiptReader {
    fun analyze(image: ImageProxy, onResult: (ReceiptScan) -> Unit)

    fun close()
}

/**
 * ML Kit texto latino (bundled, sin red). Ningún cuadro va a disco ni se guarda el recibo (S-07):
 * solo salen los renglones de producto y el total. No se registra el texto leído en ningún log.
 */
class MlKitReceiptReader : ReceiptReader {

    private val closed = AtomicBoolean(false)
    private val text: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy, onResult: (ReceiptScan) -> Unit) {
        val media = image.image
        if (closed.get() || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        text.process(input).addOnCompleteListener { task ->
            try {
                if (closed.get() || !task.isSuccessful) return@addOnCompleteListener
                val scan = ReceiptParser.parse(ReceiptRows.group(boxesOf(task.result)))
                if (scan.lines.isNotEmpty() || scan.total != null) onResult(scan)
            } finally {
                image.close()
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) text.close()
    }
}

/** Cada línea de ML Kit con su caja; el armado de renglones lo hace el dominio. */
private fun boxesOf(text: Text): List<TextBox> =
    text.textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            TextBox(line.text, box.left, box.top, box.right, box.bottom)
        }
    }
