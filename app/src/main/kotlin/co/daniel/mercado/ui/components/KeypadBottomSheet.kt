package co.daniel.mercado.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.daniel.mercado.domain.model.Pesos

/**
 * Hoja con el `PriceKeypad` para montos fuera de la compra: límite, presupuesto, total pagado,
 * precio de referencia. [initial] se reemplaza con la primera tecla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeypadBottomSheet(
    title: String,
    actionLabel: String,
    initial: Pesos?,
    onSubmit: (Pesos) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
    allowZero: Boolean = false,
) {
    var keypad by remember(initial) { mutableStateOf(KeypadState.prefilled(initial)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hint != null) {
                Text(
                    text = hint,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriceKeypad(
                state = keypad,
                onStateChange = { keypad = it },
                onSubmit = onSubmit,
                title = title,
                actionLabel = actionLabel,
                allowZero = allowZero,
            )
        }
    }
}
