package co.daniel.mercado.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import co.daniel.mercado.domain.model.Pesos

/**
 * Barra horizontal de magnitud (D-14, Canvas sin librería): un solo tono, extremo redondeado de
 * 4 dp anclado a la izquierda, carril tenue y, si hay tope, una marca vertical. Es decorativa para
 * el lector de pantalla: el valor va siempre en texto al lado.
 */
@Composable
fun ValueBar(value: Pesos, scale: Pesos, modifier: Modifier = Modifier, cap: Pesos? = null) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics { }) {
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(color = track, size = size, cornerRadius = radius)
        val fraction = fractionOf(value, scale)
        if (fraction > 0) {
            val w = (size.width * fraction).coerceAtLeast(radius.x * 2)
            drawRoundRect(color = fill, size = Size(w, size.height), cornerRadius = radius)
        }
        cap?.let {
            val x = size.width * fractionOf(it, scale)
            val stroke = 2.dp.toPx()
            drawLine(tick, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), strokeWidth = stroke)
        }
    }
}

/** Fracción para dibujar (no es dinero: el cálculo de montos sigue en `Pesos`). */
private fun fractionOf(value: Pesos, scale: Pesos): Float =
    if (scale.value <= 0) 0f else (value.value.toDouble() / scale.value).coerceIn(0.0, 1.0).toFloat()
