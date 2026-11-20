package co.daniel.mercado.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.theme.LocalTrafficLightColors
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle
import co.daniel.mercado.ui.theme.TotalTextStyle

/** Lo que pinta la tarjeta de total; se arma desde `ActiveTrip`. */
data class TotalCardModel(
    val total: Pesos,
    val articleCount: Int,
    val limit: Pesos?,
    val remaining: Pesos?,
    val trafficLight: TrafficLight,
    val percentOfLimit: Int?,
)

/**
 * Tarjeta fija de total (04-ux): total grande con cifras tabulares, artículos, barra y saldo con el
 * color del semáforo. Sin límite: solo total y artículos. El total se achica antes que cortarse.
 */
@Composable
fun TotalCard(model: TotalCardModel, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val color = trafficLightColor(model.trafficLight)
    Card(modifier = modifier.fillMaxWidth()) {
        Box {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        targetState = model.total,
                        transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
                        modifier = Modifier.weight(1f),
                        label = "total",
                    ) { total ->
                        BasicText(
                            text = MoneyFormat.format(total),
                            modifier = Modifier.testTag("trip_total"),
                            style = TotalTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 46.sp),
                        )
                    }
                    Text(
                        text = pluralStringResource(R.plurals.trip_items, model.articleCount, model.articleCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                val limit = model.limit
                val remaining = model.remaining
                if (limit != null && remaining != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { ((model.percentOfLimit ?: 0).coerceIn(0, 100)) / 100f },
                            modifier = Modifier.weight(1f).height(10.dp),
                            color = color,
                            drawStopIndicator = {},
                        )
                        model.percentOfLimit?.let {
                            Text(
                                text = stringResource(R.string.trip_percent, it),
                                modifier = Modifier.padding(start = 12.dp),
                                style = MoneyTextStyle,
                            )
                        }
                    }
                    Text(
                        text = if (remaining.value < 0) {
                            stringResource(R.string.trip_over, MoneyFormat.format(Pesos(-remaining.value)))
                        } else {
                            stringResource(R.string.trip_remaining, MoneyFormat.format(remaining), MoneyFormat.format(limit))
                        },
                        modifier = Modifier.testTag("traffic_" + model.trafficLight.name),
                        color = color,
                        style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
                    )
                } else if (onClick != null) {
                    Text(
                        text = stringResource(R.string.trip_no_limit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onClick != null) {
                // Capa táctil encima: tocar la tarjeta cambia el presupuesto de la compra sin fundir la
                // accesibilidad del total, el saldo y el semáforo (cada uno se sigue leyendo aparte).
                Box(Modifier.matchParentSize().clickable(onClickLabel = stringResource(R.string.trip_budget_title), onClick = onClick))
            }
        }
    }
}

@Composable
fun trafficLightColor(light: TrafficLight): Color {
    val colors = LocalTrafficLightColors.current
    return when (light) {
        TrafficLight.GREEN -> colors.green
        TrafficLight.YELLOW -> colors.yellow
        TrafficLight.RED -> colors.red
        TrafficLight.NONE -> MaterialTheme.colorScheme.primary
    }
}

private const val FADE_MS = 90

@Preview
@Composable
private fun TotalCardPreview() {
    MercadoTheme {
        TotalCard(
            TotalCardModel(Pesos(187_400), 12, Pesos(250_000), Pesos(62_600), TrafficLight.GREEN, 74),
        )
    }
}

@Preview
@Composable
private fun TotalCardOverPreview() {
    MercadoTheme {
        TotalCard(
            TotalCardModel(Pesos(262_000), 15, Pesos(250_000), Pesos(-12_000), TrafficLight.RED, 104),
        )
    }
}
