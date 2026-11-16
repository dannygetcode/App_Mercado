package co.daniel.mercado.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle

/**
 * Una línea de la compra (04-ux): nombre (una línea), precio unitario tocable, `−` cantidad `+` y
 * total de línea. [stalePrice]: precio de referencia de hace más de 60 días, atenuado con un punto.
 * Una línea por peso (KG) no cambia cantidad: cada bolsa es su propia línea.
 */
@Composable
fun LineRow(
    item: TripItem,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onPriceClick: () -> Unit,
    onNameClick: () -> Unit,
    modifier: Modifier = Modifier,
    stalePrice: Boolean = false,
    highlighted: Boolean = false,
) {
    val background by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 250),
        label = "highlight",
    )
    val name = item.nameSnapshot.ifBlank { stringResource(R.string.loose_item_name) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onNameClick)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.unitType == UnitType.UNIT) {
                val lessLabel = stringResource(R.string.quantity_less, name)
                val moreLabel = stringResource(R.string.quantity_more, name)
                FilledTonalIconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(48.dp).testTag("decrement").semantics { contentDescription = lessLabel },
                ) { Text("−", fontSize = 22.sp) }
                Text(
                    text = item.quantity.toString(),
                    modifier = Modifier.widthIn(min = 40.dp),
                    style = MaterialTheme.typography.titleLarge.merge(MoneyTextStyle),
                    textAlign = TextAlign.Center,
                )
                FilledTonalIconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(48.dp).testTag("increment").semantics { contentDescription = moreLabel },
                ) { Text("+", fontSize = 22.sp) }
            } else {
                Text(
                    text = item.weightGrams?.let { stringResource(R.string.line_weight_grams, it) }
                        ?: stringResource(R.string.line_by_weight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onPriceClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (stalePrice) {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
                Text(
                    text = stringResource(R.string.line_unit_price, MoneyFormat.format(item.unitPrice)),
                    style = MaterialTheme.typography.bodyLarge.merge(MoneyTextStyle),
                    color = if (stalePrice) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                )
                if (stalePrice) {
                    Text(
                        text = stringResource(R.string.line_old_price),
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Text(
                text = MoneyFormat.format(item.lineTotal),
                style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LineRowPreview() {
    MercadoTheme {
        Column {
            LineRow(
                TripItem(1, 1, 1, "Arroz Diana 1 kg", UnitType.UNIT, 2, Pesos(5_900), null, 0, 0),
                {}, {}, {}, {}, highlighted = true,
            )
            LineRow(
                TripItem(2, 1, 2, "Leche Alquería 1 L", UnitType.UNIT, 1, Pesos(4_300), null, 0, 1),
                {}, {}, {}, {}, stalePrice = true,
            )
            LineRow(TripItem(3, 1, 3, "Tomate", UnitType.KG, 1, Pesos(3_450), 820, 0, 2), {}, {}, {}, {})
            LineRow(TripItem(4, 1, null, "", UnitType.UNIT, 1, Pesos(12_000), null, 0, 3), {}, {}, {}, {})
        }
    }
}
