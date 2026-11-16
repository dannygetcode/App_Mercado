package co.daniel.mercado.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.components.Dates
import co.daniel.mercado.ui.components.KeypadBottomSheet
import co.daniel.mercado.ui.components.ScreenTitle
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle

/** Historial (04-ux): compras por mes con fecha, tienda, total y artículos. */
@Composable
fun HistoryScreen(state: HistoryUiState, onOpenTrip: (Long) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            ScreenTitle(stringResource(R.string.nav_history))
            if (!state.loading && state.months.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                state.months.forEach { month ->
                    item(key = "m-${month.month}") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                        ) {
                            Text(Dates.monthYear(month.month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(MoneyFormat.format(month.total), style = MaterialTheme.typography.titleSmall.merge(MoneyTextStyle))
                        }
                    }
                    items(month.trips, key = { it.trip.id }) { summary ->
                        TripRow(summary, onClick = { onOpenTrip(summary.trip.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TripRow(summary: TripSummary, onClick: () -> Unit) {
    val trip = summary.trip
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                listOfNotNull(trip.closedAt?.let(Dates::dayMonth), trip.store).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(pluralStringResource(R.plurals.articles, summary.articleCount, summary.articleCount)) },
        trailingContent = {
            Text(MoneyFormat.format(trip.effectiveTotal), style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle))
        },
    )
}

/** Detalle de una compra cerrada; el total pagado se edita con el teclado (0 lo quita). */
@Composable
fun TripDetailScreen(
    state: TripDetailUiState,
    onEvent: (TripDetailEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                Text(
                    text = stringResource(R.string.trip_detail_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (state.notFound) {
                Text(stringResource(R.string.trip_detail_not_found), modifier = Modifier.padding(16.dp))
            }
            val detail = state.detail ?: return@Column
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item { DetailSummary(detail, state.articleCount, onEditPaid = { onEvent(TripDetailEvent.EditPaid) }) }
                items(detail.items, key = { it.id }) { DetailLine(it) }
            }
        }
    }
    if (state.paidSheet) {
        KeypadBottomSheet(
            title = stringResource(R.string.close_paid_label),
            actionLabel = stringResource(R.string.save),
            initial = state.detail?.trip?.totalPaid,
            hint = stringResource(R.string.trip_detail_paid_hint),
            allowZero = true,
            onSubmit = { onEvent(TripDetailEvent.SetPaid(it)) },
            onDismiss = { onEvent(TripDetailEvent.DismissSheet) },
        )
    }
}

@Composable
private fun DetailSummary(detail: TripWithItems, articleCount: Int, onEditPaid: () -> Unit) {
    val trip = detail.trip
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            listOfNotNull(trip.closedAt?.let(Dates::dayAndTime), trip.store).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(pluralStringResource(R.plurals.articles, articleCount, articleCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SummaryLine(stringResource(R.string.close_estimated), MoneyFormat.format(trip.totalEstimated))
        trip.budgetLimit?.let { SummaryLine(stringResource(R.string.close_limit), MoneyFormat.format(it)) }
        Card(onClick = onEditPaid, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.trip_detail_paid), style = MaterialTheme.typography.labelLarge)
                    if (trip.totalPaid == null) {
                        Text(stringResource(R.string.close_paid_empty), style = MaterialTheme.typography.bodySmall)
                    }
                }
                trip.totalPaid?.let {
                    Text(MoneyFormat.format(it), style = MaterialTheme.typography.titleLarge.merge(MoneyTextStyle), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge.merge(MoneyTextStyle))
    }
}

@Composable
private fun DetailLine(item: TripItem) {
    val name = item.nameSnapshot.ifBlank { stringResource(R.string.loose_item_name) }
    val unit = MoneyFormat.format(item.unitPrice)
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = if (item.unitType == UnitType.KG) {
                    stringResource(R.string.line_by_weight)
                } else {
                    stringResource(R.string.trip_detail_qty, item.quantity, unit)
                },
                style = MaterialTheme.typography.bodyMedium.merge(MoneyTextStyle),
            )
        },
        trailingContent = { Text(MoneyFormat.format(item.lineTotal), style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle)) },
    )
}

private val previewTrip = Trip(id = 1, startedAt = 0, closedAt = 1_763_300_000_000, store = "La Granja", budgetLimit = Pesos(500_000), totalEstimated = Pesos(17_700))

@Preview
@Composable
private fun HistoryPreview() {
    MercadoTheme {
        HistoryScreen(
            HistoryUiState(loading = false, months = HistoryViewModel.group(listOf(TripSummary(previewTrip, 3), TripSummary(previewTrip.copy(id = 2, store = null), 12)))),
            {},
        )
    }
}

@Preview
@Composable
private fun TripDetailPreview() {
    MercadoTheme(darkTheme = true) {
        TripDetailScreen(
            TripDetailUiState(
                loading = false,
                articleCount = 3,
                detail = TripWithItems(
                    previewTrip,
                    listOf(
                        TripItem(1, 1, 1, "Arroz Diana 1 kg", quantity = 2, unitPrice = Pesos(5_900), addedAt = 0, position = 1),
                        TripItem(2, 1, null, "", quantity = 1, unitPrice = Pesos(5_900), addedAt = 0, position = 0),
                    ),
                ),
            ),
            {}, {},
        )
    }
}
