package co.daniel.mercado.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.components.KeypadState
import co.daniel.mercado.ui.components.PriceKeypad
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle

/**
 * Inicio provisional (L4-T9): estado del mes en una línea y el botón grande abajo, "Ir a mercar"
 * o "Continuar compra · $X". El Inicio real llega en L5. En debug, acceso al diagnóstico.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartOrContinue: () -> Unit,
    onStartTrip: (Pesos) -> Unit,
    onDismissLimit: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.month?.let { month ->
                val budget = month.budget
                Text(
                    text = if (budget != null) {
                        stringResource(R.string.home_month_spent_of, MoneyFormat.format(month.spent), MoneyFormat.format(budget))
                    } else {
                        stringResource(R.string.home_month_spent, MoneyFormat.format(month.spent))
                    },
                    style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (onOpenDiagnostics != null) {
                TextButton(onClick = onOpenDiagnostics, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.diag_open))
                }
            }
            Button(
                onClick = onStartOrContinue,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                Text(
                    text = state.openTripTotal?.let { stringResource(R.string.home_continue_trip, MoneyFormat.format(it)) }
                        ?: stringResource(R.string.home_start_trip),
                    fontSize = 22.sp,
                )
            }
        }
    }
    if (state.limitSheet) LimitSheet(state.suggestedLimit, onStartTrip, onDismissLimit)
}

/** Límite de esta compra: prellenado con lo que queda del mes; un toque para aceptar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(suggested: Pesos?, onStart: (Pesos) -> Unit, onDismiss: () -> Unit) {
    var keypad by remember(suggested) { mutableStateOf(KeypadState.prefilled(suggested)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(if (suggested != null) R.string.home_limit_hint else R.string.home_limit_hint_none),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PriceKeypad(
                state = keypad,
                onStateChange = { keypad = it },
                onSubmit = onStart,
                title = stringResource(R.string.home_limit_title),
                actionLabel = stringResource(R.string.home_limit_start),
                allowZero = true,
            )
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    MercadoTheme {
        HomeScreen(HomeUiState(loading = false, openTripTotal = Pesos(187_400)), {}, {}, {}, onOpenDiagnostics = {})
    }
}
