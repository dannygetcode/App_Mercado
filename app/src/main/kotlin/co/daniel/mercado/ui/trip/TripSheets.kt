package co.daniel.mercado.ui.trip

import co.daniel.mercado.domain.model.ShoppingListEntry
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.components.KeypadState
import co.daniel.mercado.ui.components.PriceKeypad
import co.daniel.mercado.ui.theme.MoneyTextStyle

/** Muestra la hoja que pida el estado. Cerrarla sin confirmar descarta lo tecleado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSheets(state: TripUiState, viewModel: TripViewModel) {
    val sheet = state.sheet ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissSheet,
        sheetState = sheetState,
        modifier = Modifier.testTag("sheet"),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            when (sheet) {
                is TripSheet.Keypad -> KeypadSheet(sheet, viewModel)
                is TripSheet.NewProduct -> NewProductSheet(sheet, state, viewModel)
                is TripSheet.Picker -> PickerSheet(sheet, state, viewModel)
                is TripSheet.EditLine -> state.trip?.items?.firstOrNull { it.id == sheet.lineId }
                    ?.let { EditLineSheet(it, viewModel) }
                    ?: LaunchedEffect(Unit) { viewModel.dismissSheet() }
            }
        }
    }
}

// ---- T4 · Teclado ----

@Composable
private fun KeypadSheet(sheet: TripSheet.Keypad, viewModel: TripViewModel) {
    var keypad by remember(sheet) { mutableStateOf(KeypadState.prefilled(sheet.initial)) }
    val (title, subtitle, action) = when (val p = sheet.purpose) {
        KeypadPurpose.LoosePrice -> Triple(
            stringResource(if (sheet.initial != null) R.string.shelf_price_title else R.string.keypad_price_title),
            null,
            stringResource(R.string.keypad_add),
        )
        is KeypadPurpose.LabelPrice -> Triple(stringResource(R.string.label_price_title), p.productName, stringResource(R.string.keypad_add))
        is KeypadPurpose.FixPrice -> Triple(
            stringResource(R.string.fix_price_title),
            p.name.ifBlank { stringResource(R.string.loose_item_name) },
            stringResource(R.string.keypad_change),
        )
        is KeypadPurpose.CreateProduct -> Triple(stringResource(R.string.create_price_title, p.name), null, stringResource(R.string.keypad_add))
        is KeypadPurpose.ShelfPrice -> Triple(stringResource(R.string.shelf_price_title), p.productName, stringResource(R.string.keypad_add))
    }
    if (subtitle != null) {
        Text(
            text = subtitle,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    PriceKeypad(
        state = keypad,
        onStateChange = { keypad = it },
        onSubmit = viewModel::submitKeypad,
        title = title,
        actionLabel = action,
    )
}

// ---- T5 · Producto nuevo ----

@Composable
private fun NewProductSheet(sheet: TripSheet.NewProduct, state: TripUiState, viewModel: TripViewModel) {
    var keypad by remember(sheet.selected?.id) {
        mutableStateOf(KeypadState.prefilled(sheet.price ?: sheet.selected?.refPrice))
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.new_product_title), style = MaterialTheme.typography.titleLarge)
        Text(sheet.code, style = MaterialTheme.typography.bodySmall.merge(MoneyTextStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // No toma el foco al abrir: el teclado del sistema no salta; el de precio ya está activo.
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().testTag("new_product_name"),
            label = { Text(stringResource(R.string.new_product_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
        if (sheet.selected == null) {
            state.results.take(MAX_SUGGESTIONS).forEach { product ->
                SuggestionRow(product) { viewModel.selectSuggestion(product.id) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    PriceKeypad(
        state = keypad,
        onStateChange = { keypad = it },
        onSubmit = viewModel::submitNewProduct,
        actionLabel = stringResource(if (sheet.selected != null) R.string.new_product_link_add else R.string.keypad_add),
    )
}

@Composable
private fun SuggestionRow(product: Product, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(product.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(MoneyFormat.format(product.refPrice), style = MoneyTextStyle)
    }
}

// ---- T6 · Favoritos / Buscar ----

@Composable
private fun PickerSheet(sheet: TripSheet.Picker, state: TripUiState, viewModel: TripViewModel) {
    Column(Modifier.fillMaxHeight(PICKER_HEIGHT)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryTabRow(selectedTabIndex = sheet.tab.ordinal, modifier = Modifier.weight(1f)) {
                Tab(
                    selected = sheet.tab == PickerTab.FAVORITES,
                    onClick = { viewModel.openPicker(PickerTab.FAVORITES) },
                    text = { Text(stringResource(R.string.picker_favorites)) },
                )
                Tab(
                    selected = sheet.tab == PickerTab.SEARCH,
                    onClick = { viewModel.openPicker(PickerTab.SEARCH) },
                    text = { Text(stringResource(R.string.picker_search)) },
                )
                Tab(
                    selected = sheet.tab == PickerTab.LIST,
                    onClick = { viewModel.openPicker(PickerTab.LIST) },
                    text = { Text(stringResource(R.string.picker_list, state.listPending.size)) },
                )
            }
        }
        state.trip?.let {
            Text(
                text = stringResource(R.string.picker_total, MoneyFormat.format(it.total)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge.merge(MoneyTextStyle),
            )
        }
        when (sheet.tab) {
            PickerTab.FAVORITES -> FavoritesGrid(state.favorites, viewModel::pickProduct)
            PickerTab.SEARCH -> SearchTab(state, viewModel)
            PickerTab.LIST -> ListTab(state.listPending, viewModel::pickProduct)
        }
    }
}

/** Lo que falta de la lista; tocar agrega (y sale de aquí porque ya está en el carrito). */
@Composable
private fun ListTab(pending: List<ShoppingListEntry>, onPick: (Long) -> Unit) {
    if (pending.isEmpty()) {
        Text(
            text = stringResource(R.string.picker_list_empty),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        items(pending, key = { it.product.id }) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(entry.product.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.product.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.item.suggestedQty > 1) {
                    Text(stringResource(R.string.list_qty, entry.item.suggestedQty), modifier = Modifier.padding(horizontal = 8.dp))
                }
                Text(
                    text = if (entry.product.unitType == UnitType.KG) stringResource(R.string.line_by_weight) else MoneyFormat.format(entry.product.refPrice),
                    style = MoneyTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun FavoritesGrid(favorites: List<Product>, onPick: (Long) -> Unit) {
    if (favorites.isEmpty()) {
        Text(
            text = stringResource(R.string.picker_no_favorites),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(favorites, key = { it.id }) { product ->
            Card(modifier = Modifier.heightIn(min = 88.dp).clickable { onPick(product.id) }) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 40.dp),
                    )
                    Text(
                        text = if (product.unitType == UnitType.KG) stringResource(R.string.line_by_weight) else MoneyFormat.format(product.refPrice),
                        style = MaterialTheme.typography.bodyMedium.merge(MoneyTextStyle),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTab(state: TripUiState, viewModel: TripViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::setQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag("search_field"),
        label = { Text(stringResource(R.string.picker_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            state.results.firstOrNull()?.let { viewModel.pickProduct(it.id) } ?: viewModel.createFromSearch()
        }),
    )
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        items(state.results, key = { it.id }) { product ->
            SuggestionRow(product) { viewModel.pickProduct(product.id) }
            HorizontalDivider()
        }
        if (state.query.isNotBlank() && state.results.isEmpty()) {
            item {
                OutlinedButton(
                    onClick = viewModel::createFromSearch,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.picker_create, state.query.trim())) }
            }
        }
    }
}

// ---- T7 · Editar línea ----

@Composable
private fun EditLineSheet(line: TripItem, viewModel: TripViewModel) {
    var name by rememberSaveable(line.id) { mutableStateOf(line.nameSnapshot) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.edit_line_title), style = MaterialTheme.typography.titleLarge)
        if (line.isLoose) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                modifier = Modifier.fillMaxWidth().testTag("line_name"),
                label = { Text(stringResource(R.string.edit_line_name)) },
                placeholder = { Text(stringResource(R.string.loose_item_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.renameLooseLine(line.id, name); viewModel.dismissSheet() }),
            )
            FilledTonalButton(
                onClick = { viewModel.renameLooseLine(line.id, name); viewModel.dismissSheet() },
                enabled = name.trim() != line.nameSnapshot,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.edit_line_rename)) }
        } else {
            Text(line.nameSnapshot, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(
            onClick = { viewModel.openFixPrice(line.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.edit_line_price, MoneyFormat.format(line.unitPrice)), style = MoneyTextStyle) }
        if (line.unitType == UnitType.UNIT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_line_quantity), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                val lessLabel = stringResource(R.string.quantity_less, line.nameSnapshot)
                val moreLabel = stringResource(R.string.quantity_more, line.nameSnapshot)
                FilledTonalIconButton(
                    onClick = { viewModel.setQuantity(line.id, line.quantity - 1) },
                    modifier = Modifier.size(56.dp).semantics { contentDescription = lessLabel },
                ) {
                    Text("−", fontSize = 24.sp)
                }
                Text(
                    text = line.quantity.toString(),
                    modifier = Modifier.widthIn(min = 56.dp),
                    style = MaterialTheme.typography.headlineSmall.merge(MoneyTextStyle),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                FilledTonalIconButton(
                    onClick = { viewModel.setQuantity(line.id, line.quantity + 1) },
                    modifier = Modifier.size(56.dp).semantics { contentDescription = moreLabel },
                ) {
                    Text("+", fontSize = 24.sp)
                }
            }
        }
        OutlinedButton(
            onClick = { viewModel.removeLine(line.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
    }
}

private const val MAX_SUGGESTIONS = 4
private const val MAX_NAME = 80
private const val PICKER_HEIGHT = 0.7f
