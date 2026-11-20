package co.daniel.mercado.ui.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).semantics { heading() },
                )
                state.month?.let {
                    MonthHeader(
                        month = it,
                        openTripTotal = state.openTripTotal,
                        openTripLimit = state.openTripLimit,
                        onClick = { onEvent(HomeEvent.EditBudget) },
                        onTripBudget = { onEvent(HomeEvent.EditTripBudget) },
                    )
                }
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
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(64.dp),
                shape = MaterialTheme.shapes.extraLarge,
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
            hint = stringResource(if (sheet.current != null) R.string.budget_hint_remove else R.string.budget_hint),
            allowZero = sheet.current != null,
            onSubmit = { onEvent(HomeEvent.SaveBudget(it)) },
            onDismiss = { onEvent(HomeEvent.DismissSheet) },
        )
        is HomeSheet.TripBudget -> KeypadBottomSheet(
            title = stringResource(R.string.trip_budget_title),
            actionLabel = stringResource(if (sheet.tripOpen) R.string.save else R.string.home_limit_start),
            initial = sheet.current,
            hint = stringResource(R.string.trip_budget_hint),
            allowZero = true,
            onSubmit = { onEvent(HomeEvent.SaveTripBudget(it)) },
            onDismiss = { onEvent(HomeEvent.DismissSheet) },
        )
        null -> Unit
    }
}

@Composable
private fun MonthHeader(
    month: MonthStatus,
    openTripTotal: Pesos?,
    openTripLimit: Pesos?,
    onClick: () -> Unit,
    onTripBudget: () -> Unit,
) {
    val budget = month.budget
    val remaining = month.remaining
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("home_month"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.home_month_label, Dates.monthName(month.yearMonth)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                MoneyFormat.format(month.spent),
                style = MaterialTheme.typography.displaySmall.merge(MoneyTextStyle),
                fontWeight = FontWeight.Bold,
            )
            if (budget != null && remaining != null) {
                Text(
                    stringResource(R.string.home_month_of, MoneyFormat.format(budget)),
                    style = MaterialTheme.typography.bodyLarge.merge(MoneyTextStyle),
                )
                val color = trafficLightColor(month.trafficLight)
                LinearProgressIndicator(
                    progress = { (BudgetRules.percentOf(month.spent, budget) ?: 0).coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(12.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (remaining.value >= 0) {
                            stringResource(R.string.home_month_remaining, MoneyFormat.format(remaining))
                        } else {
                            stringResource(R.string.trip_over, MoneyFormat.format(Pesos(-remaining.value)))
                        },
                        style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = stringResource(if (month.inherited) R.string.home_budget_inherited else R.string.home_budget_edit),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(stringResource(R.string.home_month_spent_label), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.home_budget_optional), style = MaterialTheme.typography.bodySmall)
            }
            TripBudgetButton(openTripTotal, openTripLimit, onTripBudget)
        }
    }
}

/**
 * Presupuesto de una salida (ej. $100.000 en el D1) sin tocar el del mes: lo gastado en esa compra
 * igual suma a la barra del mes.
 */
@Composable
private fun TripBudgetButton(openTripTotal: Pesos?, openTripLimit: Pesos?, onClick: () -> Unit) {
    val label = when {
        openTripTotal == null -> stringResource(R.string.home_trip_budget_new)
        openTripLimit == null -> stringResource(R.string.home_trip_budget_none, MoneyFormat.format(openTripTotal))
        else -> stringResource(R.string.home_trip_budget_of, MoneyFormat.format(openTripTotal), MoneyFormat.format(openTripLimit))
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).testTag("home_trip_budget"),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge.merge(MoneyTextStyle),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BudgetHintCard(onEvent: (HomeEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_budget_hint_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_budget_hint_text), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEvent(HomeEvent.DismissBudgetHint) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.not_now))
                }
                Button(onClick = { onEvent(HomeEvent.EditBudget) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_budget_hint_define))
                }
            }
        }
    }
}

/** Tarjeta de acceso: ícono en un círculo tonal, título, detalle y (opcional) un monto a la derecha. */
@Composable
private fun ActionCard(icon: Int, title: String, detail: String?, onClick: () -> Unit, trailing: String? = null) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle))
            } else {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LastTripCard(summary: TripSummary, onClick: () -> Unit) {
    val trip = summary.trip
    val where = listOfNotNull(trip.closedAt?.let(Dates::dayMonth), trip.store).joinToString(" · ")
    ActionCard(
        icon = R.drawable.ic_receipt,
        title = stringResource(R.string.home_last_trip),
        detail = "$where · ${pluralStringResource(R.plurals.articles, summary.articleCount, summary.articleCount)}",
        onClick = onClick,
        trailing = MoneyFormat.format(trip.effectiveTotal),
    )
}

@Composable
private fun ListCard(pending: Int, onClick: () -> Unit) {
    ActionCard(
        icon = R.drawable.ic_checklist,
        title = stringResource(R.string.home_list),
        detail = if (pending > 0) pluralStringResource(R.plurals.home_list_pending, pending, pending) else stringResource(R.string.home_list_empty),
        onClick = onClick,
    )
}

@Composable
private fun SummaryCard(onClick: () -> Unit) {
    ActionCard(R.drawable.ic_bar_chart, stringResource(R.string.home_summary), stringResource(R.string.home_summary_hint), onClick)
}

@Composable
private fun NeedsReviewCard(count: Int, onClick: () -> Unit) {
    ActionCard(R.drawable.ic_edit, stringResource(R.string.home_needs_review, count), stringResource(R.string.home_needs_review_hint), onClick)
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
