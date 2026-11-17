package co.daniel.mercado.ui.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.TotalTextStyle

/**
 * Valor del teclado de precio: solo dígitos, máximo 9, sin ceros a la izquierda.
 * [replaceOnType]: el valor vino prellenado (precio de referencia, límite) y la primera tecla lo
 * reemplaza, como una selección completa.
 */
@Immutable
data class KeypadState(val digits: String = "", val replaceOnType: Boolean = false) {

    val value: Pesos get() = Pesos(digits.toLongOrNull() ?: 0L)

    val isZero: Boolean get() = value.value == 0L

    fun press(key: String): KeypadState {
        require(key.isNotEmpty() && key.all { it in '0'..'9' }) { "tecla inválida: $key" }
        val base = if (replaceOnType) "" else digits
        val next = (base + key).trimStart('0')
        if (next.length > MAX_DIGITS) return this
        return KeypadState(next, replaceOnType = false)
    }

    fun backspace(): KeypadState = KeypadState(digits.dropLast(1), replaceOnType = false)

    companion object {
        const val MAX_DIGITS = 9

        fun prefilled(price: Pesos?): KeypadState =
            if (price == null || price.value <= 0L) KeypadState()
            else KeypadState(price.value.toString().take(MAX_DIGITS), replaceOnType = true)
    }
}

/**
 * Teclado propio de la app (RF-17): mismo componente para precio suelto, etiqueta KG, corregir
 * precio, producto nuevo y presupuesto; cambian [title] y [actionLabel]. Teclas de 64 dp.
 */
@Composable
fun PriceKeypad(
    state: KeypadState,
    onStateChange: (KeypadState) -> Unit,
    onSubmit: (Pesos) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String = stringResource(R.string.keypad_add),
    allowZero: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Monto en un recuadro: tenue mientras es el valor prellenado (la primera tecla lo reemplaza).
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            BasicText(
                text = MoneyFormat.format(state.value),
                modifier = Modifier.fillMaxWidth().testTag("keypad_value"),
                style = TotalTextStyle.copy(
                    color = if (state.replaceOnType) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 46.sp),
            )
        }
        val keyColors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        KEY_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(
                        onClick = { onStateChange(state.press(key)) },
                        modifier = Modifier.weight(1f).height(KEY_HEIGHT),
                        shape = MaterialTheme.shapes.large,
                        colors = keyColors,
                    ) { Text(key, fontSize = 26.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onStateChange(state.backspace()) },
                modifier = Modifier.weight(1f).height(KEY_HEIGHT),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.keypad_backspace_desc)) }
            Button(
                onClick = { onSubmit(state.value) },
                enabled = allowZero || !state.isZero,
                modifier = Modifier.weight(2f).height(KEY_HEIGHT),
                shape = MaterialTheme.shapes.large,
            ) { Text(actionLabel, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

private val KEY_HEIGHT = 64.dp
private val KEY_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("00", "0", "000"),
)

@Preview(showBackground = true)
@Composable
private fun PriceKeypadPreview() {
    MercadoTheme {
        var state by remember { mutableStateOf(KeypadState("12500")) }
        PriceKeypad(state, { state = it }, {}, title = "Precio")
    }
}
