package co.daniel.mercado.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.CategorySpend
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.MonthSummary
import co.daniel.mercado.domain.model.MonthTotal
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceChange
import co.daniel.mercado.domain.model.ProductSpend
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.rules.BudgetRules
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.components.Dates
import co.daniel.mercado.ui.components.KeypadBottomSheet
import co.daniel.mercado.ui.components.ValueBar
import co.daniel.mercado.ui.components.trafficLightColor
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle
import java.time.YearMonth
import kotlin.math.abs

/**
 * Resumen del mes (04-ux): gastado vs presupuesto, categorías (con tope), top 10, subidas y bajadas
 * de precio y los últimos 6 meses. Barras horizontales con el valor siempre en texto.
 */
@Composable
fun SummaryScreen(state: SummaryUiState, onEvent: (SummaryEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { onEvent(SummaryEvent.PreviousMonth) }) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.summary_prev), Modifier.rotate(-90f))
                }
                Text(
                    Dates.monthYear(state.month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onEvent(SummaryEvent.NextMonth) }, enabled = state.canGoNext) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.summary_next), Modifier.rotate(90f))
                }
            }
            val summary = state.summary ?: return@Column
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item { StatusCard(summary.status) }
                section(R.string.summary_categories, summary.categories.isEmpty()) {
                    val scale = summary.categories.maxOf { maxOf(it.spent.value, it.cap?.value ?: 0) }
                    items(summary.categories, key = { "c${it.categoryId}" }) { CategoryRow(it, Pesos(scale)) }
                }
                section(R.string.summary_top, summary.topProducts.isEmpty()) {
                    val scale = summary.topProducts.first().spent
                    items(summary.topProducts, key = { "p${it.productId}" }) { ProductRow(it, scale) }
                }
                if (summary.priceUps.isNotEmpty()) {
                    item { Title(R.string.summary_ups) }
                    items(summary.priceUps, key = { "u${it.productId}" }) { PriceRow(it) }
                }
                if (summary.priceDowns.isNotEmpty()) {
                    item { Title(R.string.summary_downs) }
                    items(summary.priceDowns, key = { "d${it.productId}" }) { PriceRow(it) }
                }
                item { Title(R.string.summary_months) }
                val monthsScale = Pesos(state.lastMonths.maxOfOrNull { maxOf(it.spent.value, it.budget?.value ?: 0) } ?: 0)
                items(state.lastMonths, key = { "m${it.month}" }) { MonthRow(it, monthsScale) }
            }
        }
    }
}

private fun LazyListScope.section(
    title: Int,
    empty: Boolean,
    content: LazyListScope.() -> Unit,
) {
    item { Title(title) }
    if (empty) {
        item {
            Text(
                stringResource(R.string.summary_nothing),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        content()
    }
}

@Composable
private fun Title(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun StatusCard(status: MonthStatus) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val budget = status.budget
            Text(
                text = if (budget != null) {
                    stringResource(R.string.summary_spent_of, MoneyFormat.format(status.spent), MoneyFormat.format(budget))
                } else {
                    stringResource(R.string.summary_spent, MoneyFormat.format(status.spent))
                },
                style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
            )
            val remaining = status.remaining
            if (budget != null && remaining != null) {
                // El semáforo es del presupuesto: aquí sí aplica (04-ux).
                val color = trafficLightColor(status.trafficLight)
                LinearProgressIndicator(
                    progress = { (BudgetRules.percentOf(status.spent, budget) ?: 0).coerceIn(0, 100) / 100f },
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
                    color = color,
                    style = MoneyTextStyle,
                )
            }
        }
    }
}

@Composable
private fun AmountRow(name: String, detail: String?, amount: String, bar: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(amount, style = MaterialTheme.typography.titleSmall.merge(MoneyTextStyle))
        }
        bar()
    }
}

@Composable
private fun CategoryRow(c: CategorySpend, scale: Pesos) {
    val cap = c.cap
    val detail = when {
        cap == null -> null
        c.spent > cap -> stringResource(R.string.summary_cap_over, MoneyFormat.format(cap), MoneyFormat.format(c.spent - cap))
        else -> stringResource(R.string.summary_cap_left, MoneyFormat.format(cap), MoneyFormat.format(cap - c.spent))
    }
    AmountRow(c.name, detail, MoneyFormat.format(c.spent)) { ValueBar(c.spent, scale, cap = cap) }
}

@Composable
private fun ProductRow(p: ProductSpend, scale: Pesos) {
    AmountRow(p.name, stringResource(R.string.list_qty, p.quantity), MoneyFormat.format(p.spent)) { ValueBar(p.spent, scale) }
}

@Composable
private fun PriceRow(change: PriceChange) {
    val bp = change.basisPoints
    val percent = (if (bp >= 0) bp + 50 else bp - 50) / 100
    ListItem(
        headlineContent = { Text(change.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                stringResource(R.string.summary_price_change, MoneyFormat.format(change.previous), MoneyFormat.format(change.current)),
                style = MoneyTextStyle,
            )
        },
        trailingContent = {
            Text(
                stringResource(if (percent >= 0) R.string.summary_percent_up else R.string.summary_percent_down, abs(percent)),
                style = MaterialTheme.typography.titleSmall.merge(MoneyTextStyle),
            )
        },
    )
}

@Composable
private fun MonthRow(m: MonthTotal, scale: Pesos) {
    val budget = m.budget
    AmountRow(
        Dates.monthYear(m.month),
        budget?.let { stringResource(R.string.summary_budget, MoneyFormat.format(it)) },
        MoneyFormat.format(m.spent),
    ) { ValueBar(m.spent, scale, cap = budget) }
}

/** Topes mensuales por categoría (RF-13): tocar abre el teclado; $0 quita el tope. */
@Composable
fun CategoryCapsScreen(state: CategoryCapsUiState, onEvent: (CategoryCapsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                Text(stringResource(R.string.settings_category_caps), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Text(
                stringResource(R.string.caps_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn {
                items(state.categories, key = { it.id }) { c ->
                    ListItem(
                        modifier = Modifier.clickable { onEvent(CategoryCapsEvent.Edit(c)) },
                        headlineContent = { Text(c.name) },
                        trailingContent = {
                            Text(c.monthlyBudget?.let(MoneyFormat::format) ?: stringResource(R.string.caps_none), style = MoneyTextStyle)
                        },
                    )
                }
            }
        }
    }
    state.editing?.let { c ->
        KeypadBottomSheet(
            title = stringResource(R.string.caps_title, c.name),
            actionLabel = stringResource(R.string.save),
            initial = c.monthlyBudget,
            hint = stringResource(R.string.caps_zero_hint),
            allowZero = true,
            onSubmit = { onEvent(CategoryCapsEvent.Save(it)) },
            onDismiss = { onEvent(CategoryCapsEvent.Dismiss) },
        )
    }
}

@Preview(heightDp = 1400)
@Composable
private fun SummaryPreview() {
    val nov = YearMonth.of(2026, 11)
    MercadoTheme {
        SummaryScreen(
            SummaryUiState(
                month = nov,
                currentMonth = nov,
                loading = false,
                summary = MonthSummary(
                    MonthStatus(nov, Pesos(900_000), Pesos(612_000), Pesos(288_000), TrafficLight.GREEN),
                    listOf(CategorySpend(1, "Granos y despensa", Pesos(210_000), Pesos(200_000)), CategorySpend(2, "Lácteos y huevos", Pesos(95_000), null)),
                    listOf(ProductSpend(1, "Arroz Diana 1 kg", Pesos(23_600), 4)),
                    listOf(PriceChange(1, "Arroz Diana 1 kg", Pesos(5_600), Pesos(5_900))),
                    emptyList(),
                ),
                lastMonths = (5 downTo 0).map { MonthTotal(nov.minusMonths(it.toLong()), Pesos(500_000L + it * 40_000), Pesos(900_000)) },
            ),
            {}, {},
        )
    }
}

@Preview
@Composable
private fun CapsPreview() {
    MercadoTheme(darkTheme = true) {
        CategoryCapsScreen(CategoryCapsUiState(listOf(Category(1, "Carnes", monthlyBudget = Pesos(150_000)), Category(2, "Aseo del hogar"))), {}, {})
    }
}
