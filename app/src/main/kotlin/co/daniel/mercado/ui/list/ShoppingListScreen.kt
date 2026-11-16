package co.daniel.mercado.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.ListSource
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.ShoppingListEntry
import co.daniel.mercado.domain.model.ShoppingListItem
import co.daniel.mercado.domain.model.Suggestion
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle

/** Lista de mercado (04-ux): "Sugeridos" con el motivo y "Agregados por ti"; "Agregar producto". */
@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onEvent: (ShoppingListEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                    Text(
                        stringResource(R.string.list_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (!state.loading && state.suggested.isEmpty() && state.added.isEmpty()) {
                    Text(
                        text = stringResource(R.string.list_empty),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    if (state.suggested.isNotEmpty()) {
                        item { SectionTitle(R.string.list_suggested) }
                        items(state.suggested, key = { "s${it.product.id}" }) { ListRow(it, onEvent) }
                    }
                    if (state.added.isNotEmpty()) {
                        item { SectionTitle(R.string.list_added) }
                        items(state.added, key = { "a${it.product.id}" }) { ListRow(it, onEvent) }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { onEvent(ShoppingListEvent.OpenAdd) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.list_add)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    if (state.addSheet) AddSheet(state, onEvent)
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun ListRow(entry: ShoppingListEntry, onEvent: (ShoppingListEvent) -> Unit) {
    val item = entry.item
    val reason = entry.reason
    ListItem(
        modifier = Modifier.toggleable(value = item.checked, role = Role.Checkbox) { onEvent(ShoppingListEvent.Toggle(item.productId)) },
        leadingContent = { Checkbox(checked = item.checked, onCheckedChange = null) },
        headlineContent = {
            Text(
                text = if (item.suggestedQty > 1) "${entry.product.name} ${stringResource(R.string.list_qty, item.suggestedQty)}" else entry.product.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = reason?.let {
            {
                Text(
                    stringResource(
                        R.string.list_reason,
                        pluralStringResource(R.plurals.list_days, it.avgIntervalDays, it.avgIntervalDays),
                        pluralStringResource(R.plurals.list_days, it.daysSinceLast, it.daysSinceLast),
                    ),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { onEvent(ShoppingListEvent.Remove(item.productId)) }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.list_remove, entry.product.name))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(state: ShoppingListUiState, onEvent: (ShoppingListEvent) -> Unit) {
    val focus = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = { onEvent(ShoppingListEvent.DismissAdd) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxHeight(0.7f).navigationBarsPadding()) {
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(ShoppingListEvent.Query(it)) },
                label = { Text(stringResource(R.string.picker_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(state.results, key = { it.id }) { product ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onEvent(ShoppingListEvent.Add(product.id)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(product.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(MoneyFormat.format(product.refPrice), style = MoneyTextStyle)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ShoppingListPreview() {
    val arroz = Product(1, "Arroz Diana 1 kg", 1, refPrice = Pesos(5_900))
    val pan = Product(2, "Pan tajado", 1, refPrice = Pesos(6_200))
    MercadoTheme {
        ShoppingListScreen(
            ShoppingListUiState(
                loading = false,
                suggested = listOf(
                    ShoppingListEntry(
                        ShoppingListItem(1, 2, source = ListSource.AUTO),
                        arroz,
                        Suggestion(1, 28, 24, 2, 0, 0),
                    ),
                ),
                added = listOf(ShoppingListEntry(ShoppingListItem(2, 1, checked = true, source = ListSource.MANUAL), pan, null)),
            ),
            {}, {},
        )
    }
}
