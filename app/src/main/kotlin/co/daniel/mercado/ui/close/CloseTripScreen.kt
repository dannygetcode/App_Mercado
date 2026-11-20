package co.daniel.mercado.ui.close

import co.daniel.mercado.ui.components.MercadoTextField
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.ApplyReceiptResult
import co.daniel.mercado.scanner.BarcodeDecoder
import co.daniel.mercado.scanner.ReceiptReader
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.components.KeypadState
import co.daniel.mercado.ui.components.PriceKeypad
import co.daniel.mercado.ui.components.trafficLightColor
import co.daniel.mercado.ui.theme.MoneyTextStyle

/**
 * Cierre de compra (04-ux): resumen, total pagado opcional, tienda con chips, productos por
 * nombrar (omitible), "Guardar compra" y "Descartar compra" (con la única confirmación).
 */
@Composable
fun CloseTripScreen(
    viewModel: CloseTripViewModel,
    onBackToTrip: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    receiptReaderFactory: (() -> ReceiptReader)? = null,
    decoderFactory: (() -> BarcodeDecoder)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    BackHandler(onBack = onBackToTrip)

    val receipt = state.receipt
    if (receipt != null && receiptReaderFactory != null && decoderFactory != null) {
        ReceiptScanContent(receipt, viewModel, receiptReaderFactory, decoderFactory, modifier)
        return
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextButton(onClick = onBackToTrip, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("← " + stringResource(R.string.close_back_to_trip))
                }
                Text(stringResource(R.string.close_title), style = MaterialTheme.typography.headlineSmall)
                Summary(state)
                if (receiptReaderFactory != null) ReceiptCard(state.receiptResult, viewModel::openReceipt)
                PaidRow(state.totalPaid, viewModel::openPaidSheet)
                StoreField(state, viewModel)
                if (state.review.isNotEmpty()) ReviewSection(state, viewModel)
            }
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.loading && !state.saving && state.trip != null,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) { Text(stringResource(R.string.save_trip), style = MaterialTheme.typography.titleMedium) }
                TextButton(
                    onClick = viewModel::askDiscard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.discard_trip), color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (state.paidSheet) PaidSheet(state.totalPaid, viewModel::setTotalPaid, viewModel::dismissPaidSheet)
    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDiscard,
            title = { Text(stringResource(R.string.discard_confirm_title)) },
            text = { Text(stringResource(R.string.discard_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::discard) {
                    Text(stringResource(R.string.discard_confirm_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDiscard) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Summary(state: CloseTripUiState) {
    val trip = state.trip ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryRow(stringResource(R.string.close_items), pluralStringResource(R.plurals.trip_items, trip.articleCount, trip.articleCount))
            SummaryRow(stringResource(R.string.close_estimated), MoneyFormat.format(trip.total), strong = true)
            val limit = trip.limit
            val remaining = trip.remaining
            if (limit != null && remaining != null) {
                SummaryRow(stringResource(R.string.close_limit), MoneyFormat.format(limit))
                SummaryRow(
                    label = stringResource(R.string.close_difference),
                    value = if (remaining.value < 0) {
                        stringResource(R.string.trip_over, MoneyFormat.format(Pesos(-remaining.value)))
                    } else {
                        stringResource(R.string.close_under, MoneyFormat.format(remaining))
                    },
                    color = trafficLightColor(trip.trafficLight),
                )
            }
        }
    }
}

/** "Escanear recibo": corrige precios y agrega lo que faltó con lo que dice el recibo. */
@Composable
private fun ReceiptCard(result: ApplyReceiptResult?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("receipt_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_receipt), contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(R.string.receipt_scan), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (result == null) {
                        stringResource(R.string.receipt_scan_hint)
                    } else {
                        stringResource(R.string.receipt_result, result.corrected, result.added, result.notOnReceipt)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false, color: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = (if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge).merge(MoneyTextStyle),
            color = color,
        )
    }
}

@Composable
private fun PaidRow(totalPaid: Pesos?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).testTag("paid_row")) {
        Text(stringResource(R.string.close_paid_label), style = MaterialTheme.typography.titleMedium)
        Text(
            text = totalPaid?.let(MoneyFormat::format) ?: stringResource(R.string.close_paid_empty),
            style = MaterialTheme.typography.bodyLarge.merge(MoneyTextStyle),
            color = if (totalPaid != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreField(state: CloseTripUiState, viewModel: CloseTripViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MercadoTextField(
            value = state.store,
            onValueChange = viewModel::setStore,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.close_store_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        if (state.recentStores.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recentStores.forEach { store ->
                    FilterChip(
                        selected = state.store == store,
                        onClick = { viewModel.setStore(if (state.store == store) "" else store) },
                        label = { Text(store) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewSection(state: CloseTripUiState, viewModel: CloseTripViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.close_review_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.close_review_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.review.forEach { item ->
            HorizontalDivider()
            Text(
                text = stringResource(R.string.close_review_item, item.product.name, MoneyFormat.format(item.product.refPrice)),
                style = MaterialTheme.typography.bodyMedium.merge(MoneyTextStyle),
            )
            MercadoTextField(
                value = item.name,
                onValueChange = { viewModel.setReviewName(item.product.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.edit_line_name),
                singleLine = true,
                isError = item.duplicate,
                supportingText = if (item.duplicate) stringResource(R.string.close_review_duplicate) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = item.categoryId == category.id,
                        onClick = { viewModel.setReviewCategory(item.product.id, category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaidSheet(current: Pesos?, onSubmit: (Pesos) -> Unit, onDismiss: () -> Unit) {
    var keypad by remember { mutableStateOf(KeypadState.prefilled(current)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            PriceKeypad(
                state = keypad,
                onStateChange = { keypad = it },
                onSubmit = onSubmit,
                title = stringResource(R.string.close_paid_label),
                actionLabel = stringResource(R.string.close_paid_ok),
                allowZero = true,
            )
        }
    }
}
