package co.daniel.mercado.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.domain.rules.BudgetRules
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.components.Dates
import co.daniel.mercado.ui.components.KeypadBottomSheet
import co.daniel.mercado.ui.components.trafficLightColor
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle
import java.time.YearMonth

/**
 * Inicio (04-ux): el mes con su barra arriba, tarjetas en el medio y el botón grande abajo, donde
 * llega el pulgar. Tocar el encabezado del mes edita el presupuesto.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenNeedsReview: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenList: () -> Unit = {},
    onOpenSummary: () -> Unit = {},
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.month?.let { MonthHeader(it, onClick = { onEvent(HomeEvent.EditBudget) }) }
                if (state.showBudgetHint) BudgetHintCard(onEvent)
                state.lastTrip?.let { LastTripCard(it, onClick = { onOpenTrip(it.trip.id) }) }
                if (!state.loading) ListCard(state.listPending, onOpenList)
                if (state.lastTrip != null) SummaryCard(onOpenSummary)
                if (state.needsReviewCount > 0) NeedsReviewCard(state.needsReviewCount, onOpenNeedsReview)
                if (onOpenDiagnostics != null) {
                    TextButton(onClick = onOpenDiagnostics, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.diag_open))
                    }
                }
            }
            Button(
                onClick = { onEvent(HomeEvent.StartOrContinue) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(72.dp).padding(top = 8.dp),
            ) {
                Text(
                    text = state.openTripTotal?.let { stringResource(R.string.home_continue_trip, MoneyFormat.format(it)) }
                        ?: stringResource(R.string.home_start_trip),
                    fontSize = 22.sp,
                )
            }
        }
    }
    when (val sheet = state.sheet) {
        is HomeSheet.Limit -> KeypadBottomSheet(
            title = stringResource(R.string.home_limit_title),
            actionLabel = stringResource(R.string.home_limit_start),
            initial = sheet.suggested,
            hint = stringResource(if (sheet.suggested != null) R.string.home_limit_hint else R.string.home_limit_hint_none),
            allowZero = true,
            onSubmit = { onEvent(HomeEvent.StartTrip(it)) },
            onDismiss = { onEvent(HomeEvent.DismissSheet) },
        )
        is HomeSheet.Budget -> KeypadBottomSheet(
            title = stringResource(R.string.budget_title, state.month?.let { Dates.monthName(it.yearMonth) }.orEmpty()),
            actionLabel = stringResource(R.string.save),
            initial = sheet.current,
            hint = stringResource(R.string.budget_hint),
            onSubmit = { onEvent(HomeEvent.SaveBudget(it)) },
            onDismiss = { onEvent(HomeEvent.DismissSheet) },
        )
        null -> Unit
    }
}

@Composable
private fun MonthHeader(month: MonthStatus, onClick: () -> Unit) {
    val budget = month.budget
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("home_month")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (budget != null) {
                    stringResource(R.string.home_month_spent_of, Dates.monthName(month.yearMonth), MoneyFormat.format(month.spent), MoneyFormat.format(budget))
                } else {
                    stringResource(R.string.home_month_spent, Dates.monthName(month.yearMonth), MoneyFormat.format(month.spent))
                },
                style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
            )
            val remaining = month.remaining
            if (budget != null && remaining != null) {
                val color = trafficLightColor(month.trafficLight)
                LinearProgressIndicator(
                    progress = { (BudgetRules.percentOf(month.spent, budget) ?: 0).coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = color,
                    drawStopIndicator = {},
                )
                Text(
                    text = if (remaining.value >= 0) {
                        stringResource(R.string.home_month_remaining, MoneyFormat.format(remaining))
                    } else {
                        stringResource(R.string.trip_over, MoneyFormat.format(Pesos(-remaining.value)))
                    },
                    style = MaterialTheme.typography.bodyLarge.merge(MoneyTextStyle),
                    color = color,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(if (month.inherited) R.string.home_budget_inherited else R.string.home_budget_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetHintCard(onEvent: (HomeEvent) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_budget_hint_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_budget_hint_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEvent(HomeEvent.DismissBudgetHint) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.not_now))
                }
                TextButton(onClick = { onEvent(HomeEvent.EditBudget) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_budget_hint_define))
                }
            }
        }
    }
}

@Composable
private fun LastTripCard(summary: TripSummary, onClick: () -> Unit) {
    val trip = summary.trip
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_last_trip), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = listOfNotNull(trip.closedAt?.let(Dates::dayMonth), trip.store).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.articles, summary.articleCount, summary.articleCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(MoneyFormat.format(trip.effectiveTotal), style = MaterialTheme.typography.titleLarge.merge(MoneyTextStyle))
        }
    }
}

@Composable
private fun ListCard(pending: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_list), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (pending > 0) pluralStringResource(R.plurals.home_list_pending, pending, pending) else stringResource(R.string.home_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_summary), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_summary_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NeedsReviewCard(count: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_needs_review, count), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_needs_review_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val previewMonth = MonthStatus(YearMonth.of(2026, 11), Pesos(900_000), Pesos(612_000), Pesos(288_000), TrafficLight.GREEN)

@Preview
@Composable
private fun HomeScreenPreview() {
    MercadoTheme {
        HomeScreen(
            HomeUiState(
                loading = false,
                openTripTotal = Pesos(187_400),
                month = previewMonth,
                lastTrip = TripSummary(Trip(id = 1, startedAt = 0, closedAt = 1_763_300_000_000, store = "La Granja", totalEstimated = Pesos(497_870)), 57),
                needsReviewCount = 2,
                budgetHintDismissed = false,
            ),
            {}, {}, {}, onOpenDiagnostics = {},
        )
    }
}

@Preview
@Composable
private fun HomeFirstUsePreview() {
    MercadoTheme(darkTheme = true) {
        HomeScreen(
            HomeUiState(loading = false, month = previewMonth.copy(budget = null, remaining = null, spent = Pesos.ZERO), budgetHintDismissed = false),
            {}, {}, {},
        )
    }
}
