package co.daniel.mercado.ui.cleanup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.domain.usecase.TripsInRange
import co.daniel.mercado.ui.components.SearchField
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Borrar datos: compras de un período, productos elegidos o todo, siempre con confirmación. */
@Composable
fun DataCleanupScreen(state: DataCleanupUiState, onEvent: (DataCleanupEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.cleanup_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (state.busy) CircularProgressIndicator(Modifier.padding(end = 16.dp).height(24.dp))
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(stringResource(R.string.cleanup_backup_hint), modifier = Modifier.padding(16.dp))
                }
                state.message?.let { Message(it, onDismiss = { onEvent(DataCleanupEvent.DismissMessage) }) }
                TripsCard(state, onEvent)
                CleanupCard(R.string.cleanup_products_title) {
                    Text(stringResource(R.string.cleanup_products_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { onEvent(DataCleanupEvent.OpenProducts) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.cleanup_products_pick)) }
                }
                CleanupCard(R.string.cleanup_all_title) {
                    Text(stringResource(R.string.cleanup_all_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DangerButton(stringResource(R.string.cleanup_all_button), enabled = !state.busy) {
                        onEvent(DataCleanupEvent.Ask(CleanupConfirm.ALL))
                    }
                }
            }
        }
    }
    if (state.datePicker) RangePickerDialog(onEvent)
    if (state.productsSheet) ProductsSheet(state, onEvent)
    state.confirm?.let { ConfirmDialog(it, state, onEvent) }
}

@Composable
private fun CleanupCard(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripsCard(state: DataCleanupUiState, onEvent: (DataCleanupEvent) -> Unit) {
    CleanupCard(R.string.cleanup_trips_title) {
        Text(stringResource(R.string.cleanup_trips_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onEvent(DataCleanupEvent.ThisMonth) }, label = { Text(stringResource(R.string.cleanup_this_month)) })
            AssistChip(onClick = { onEvent(DataCleanupEvent.LastMonth) }, label = { Text(stringResource(R.string.cleanup_last_month)) })
            AssistChip(onClick = { onEvent(DataCleanupEvent.PickDates) }, label = { Text(stringResource(R.string.cleanup_pick_dates)) })
        }
        val from = state.from
        val to = state.to
        if (from != null && to != null) {
            Text(stringResource(R.string.cleanup_range, DAY.format(from), DAY.format(to)), style = MaterialTheme.typography.titleSmall)
            val preview = state.preview
            Text(
                text = when {
                    preview == null -> stringResource(R.string.cleanup_counting)
                    preview.count == 0 -> stringResource(R.string.cleanup_no_trips)
                    else -> pluralStringResource(R.plurals.cleanup_trips_count, preview.count, preview.count, MoneyFormat.format(preview.total))
                },
                style = MaterialTheme.typography.bodyLarge.merge(MoneyTextStyle),
            )
            DangerButton(stringResource(R.string.cleanup_trips_button), enabled = (preview?.count ?: 0) > 0 && !state.busy) {
                onEvent(DataCleanupEvent.Ask(CleanupConfirm.TRIPS))
            }
        }
    }
}

@Composable
private fun DangerButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) { Text(text) }
}

@Composable
private fun Message(message: CleanupMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        is CleanupMessage.TripsDeleted -> pluralStringResource(R.plurals.cleanup_trips_deleted, message.count, message.count)
        is CleanupMessage.ProductsDeleted -> pluralStringResource(R.plurals.cleanup_products_deleted, message.count, message.count)
        CleanupMessage.AllDeleted -> stringResource(R.string.cleanup_all_deleted)
        CleanupMessage.Failed -> stringResource(R.string.notice_error)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(start = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.keypad_ok)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(onEvent: (DataCleanupEvent) -> Unit) {
    val picker = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = { onEvent(DataCleanupEvent.Dismiss) },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = picker.selectedStartDateMillis
                    val end = picker.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        onEvent(DataCleanupEvent.SetRange(DataCleanupViewModel.utcMillisToDate(start), DataCleanupViewModel.utcMillisToDate(end)))
                    }
                },
                enabled = picker.selectedStartDateMillis != null,
            ) { Text(stringResource(R.string.keypad_ok)) }
        },
        dismissButton = { TextButton(onClick = { onEvent(DataCleanupEvent.Dismiss) }) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(state = picker, modifier = Modifier.weight(1f), title = {
            Text(stringResource(R.string.cleanup_pick_dates), modifier = Modifier.padding(start = 24.dp, top = 16.dp))
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductsSheet(state: DataCleanupUiState, onEvent: (DataCleanupEvent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(DataCleanupEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            SearchField(
                value = state.query,
                onValueChange = { onEvent(DataCleanupEvent.Query(it)) },
                placeholder = stringResource(R.string.catalog_search_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(state.visibleProducts, key = { it.id }) { product ->
                    ProductRow(product, product.id in state.selected) { onEvent(DataCleanupEvent.ToggleProduct(product.id)) }
                }
            }
            DangerButton(
                pluralStringResource(R.plurals.cleanup_products_button, state.selected.size, state.selected.size),
                enabled = state.selected.isNotEmpty(),
                modifier = Modifier.padding(16.dp),
            ) { onEvent(DataCleanupEvent.Ask(CleanupConfirm.PRODUCTS)) }
        }
    }
}

@Composable
private fun ProductRow(product: Product, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!product.active) {
                Text(stringResource(R.string.cleanup_inactive), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(MoneyFormat.format(product.refPrice), style = MoneyTextStyle)
    }
}

@Composable
private fun ConfirmDialog(what: CleanupConfirm, state: DataCleanupUiState, onEvent: (DataCleanupEvent) -> Unit) {
    val (title, text, action) = when (what) {
        CleanupConfirm.TRIPS -> Triple(
            stringResource(R.string.cleanup_confirm_trips_title),
            pluralStringResource(R.plurals.cleanup_trips_count, state.preview?.count ?: 0, state.preview?.count ?: 0, MoneyFormat.format(state.preview?.total ?: Pesos.ZERO)) +
                " " + stringResource(R.string.cleanup_no_undo),
            stringResource(R.string.cleanup_trips_button),
        )
        CleanupConfirm.PRODUCTS -> Triple(
            stringResource(R.string.cleanup_confirm_products_title),
            pluralStringResource(R.plurals.cleanup_products_button, state.selected.size, state.selected.size) +
                ". " + stringResource(R.string.cleanup_products_text) + " " + stringResource(R.string.cleanup_no_undo),
            stringResource(R.string.delete),
        )
        CleanupConfirm.ALL -> Triple(
            stringResource(R.string.cleanup_confirm_all_title),
            stringResource(R.string.cleanup_all_text) + " " + stringResource(R.string.cleanup_no_undo),
            stringResource(R.string.cleanup_all_button),
        )
    }
    AlertDialog(
        onDismissRequest = { onEvent(DataCleanupEvent.Dismiss) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onEvent(DataCleanupEvent.Confirm) }) { Text(action, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { onEvent(DataCleanupEvent.Dismiss) }) { Text(stringResource(R.string.cancel)) } },
    )
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))

@Preview(heightDp = 1100)
@Composable
private fun DataCleanupPreview() {
    MercadoTheme {
        DataCleanupScreen(
            DataCleanupUiState(
                from = LocalDate.of(2026, 10, 1),
                to = LocalDate.of(2026, 10, 31),
                preview = TripsInRange(2, Pesos(569_700)),
                message = CleanupMessage.TripsDeleted(3),
            ),
            {}, {},
        )
    }
}
