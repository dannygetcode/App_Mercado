package co.daniel.mercado.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.rules.ShelfPrice
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * RF-18 con el motor real de ML Kit en el teléfono: una etiqueta de estante dibujada (nombre,
 * precio grande, "Und" y precio por gramo pequeños) debe dar el precio de venta.
 */
@RunWith(AndroidJUnit4::class)
class ShelfLabelOcrDeviceTest {

    private fun label(vararg lines: Pair<String, Float>): Bitmap {
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(255, 236, 64)) // amarillo de etiqueta
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD }
        var y = 40f
        lines.forEach { (text, size) ->
            paint.textSize = size
            y += size * 1.15f
            canvas.drawText(text, 60f, y, paint)
        }
        return bitmap
    }

    private fun read(bitmap: Bitmap): Pesos? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS)
            val lines = linesOf(text)
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply { putString("ocr", lines.joinToString(" | ") { "${it.text}[${it.height}]" }) },
            )
            return ShelfPrice.find(lines)
        } finally {
            recognizer.close()
        }
    }

    @Test
    fun etiquetaConSignoYUnidad() {
        assertEquals(
            Pesos(5_900),
            read(label("ARROZ DIANA X 1000 G" to 44f, "$ 5.900" to 220f, "Und" to 40f, "Precio x g $5,90" to 34f)),
        )
    }

    @Test
    fun etiquetaSinSignoConPrecioPorKilo() {
        assertEquals(
            Pesos(12_490),
            read(label("QUESO CAMPESINO 500 g" to 44f, "12.490" to 230f, "Valor kilo $24.980" to 36f)),
        )
    }
}
