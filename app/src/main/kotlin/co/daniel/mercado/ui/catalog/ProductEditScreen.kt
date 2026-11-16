package co.daniel.mercado.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.scanner.BarcodeDecoder
import co.daniel.mercado.scanner.CameraPermissionGate
import co.daniel.mercado.scanner.ScannerView
import co.daniel.mercado.ui.components.Dates
import co.daniel.mercado.ui.components.KeypadBottomSheet
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle
import kotlinx.coroutines.delay

/**
 * Editar producto (04-ux): nombre, categoría, tipo de unidad, precio, favorito, códigos (quitar y
 * "Escanear código"), historial de precios y desactivar. "Guardar" abajo, al alcance del pulgar.
 */
@Composable
fun ProductEditScreen(
    state: ProductEditUiState,
    onEvent: (ProductEditEvent) -> Unit,
    onBack: () -> Unit,
    decoderFactory: () -> BarcodeDecoder,
    modifier: Modifier = Modifier,
    onScanAccepted: () -> Unit = {},
    showCamera: Boolean = true,
) {
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(NOTICE_MS)
            onEvent(ProductEditEvent.DismissNotice)
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                Text(
                    text = stringResource(if (state.isNew) R.string.product_new_title else R.string.product_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (!state.loading) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProductFields(state, onEvent)
                    CodesSection(state, onEvent)
                    if (state.history.isNotEmpty()) PriceHistorySection(state.history)
                    if (!state.isNew && state.active) {
                        TextButton(
                            onClick = { onEvent(ProductEditEvent.Deactivate) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.product_deactivate), color = MaterialTheme.colorScheme.error) }
                    }
                }
                Button(
                    onClick = { onEvent(ProductEditEvent.Save) },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (state.priceSheet) {
        KeypadBottomSheet(
            title = stringResource(if (state.unitType == UnitType.KG) R.string.product_price_kg else R.string.product_price),
            actionLabel = stringResource(R.string.keypad_ok),
            initial = state.price,
            onSubmit = { onEvent(ProductEditEvent.SetPrice(it)) },
            onDismiss = { onEvent(ProductEditEvent.DismissSheet) },
        )
    }
    if (state.scanning) {
        ScanCodeSheet(
            decoderFactory = decoderFactory,
            showCamera = showCamera,
            onCode = {
                onScanAccepted()
                onEvent(ProductEditEvent.Scanned(it))
            },
            onDismiss = { onEvent(ProductEditEvent.DismissSheet) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductFields(state: ProductEditUiState, onEvent: (ProductEditEvent) -> Unit) {
    if (!state.active) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.product_inactive), color = MaterialTheme.colorScheme.onErrorContainer)
                Button(onClick = { onEvent(ProductEditEvent.Reactivate) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.product_reactivate))
                }
            }
        }
    }
    if (state.needsReview) {
        Text(stringResource(R.string.product_needs_review_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val nameError = when (state.error) {
        ProductError.NAME -> stringResource(R.string.product_error_name)
        ProductError.DUPLICATE -> stringResource(R.string.close_review_duplicate)
        else -> null
    }
    OutlinedTextField(
        value = state.name,
        onValueChange = { onEvent(ProductEditEvent.Name(it)) },
        label = { Text(stringResource(R.string.edit_line_name)) },
        isError = nameError != null,
        supportingText = nameError?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("product_name"),
    )

    SectionTitle(R.string.product_category)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.categories.forEach { category ->
            FilterChip(
                selected = state.categoryId == category.id,
                onClick = { onEvent(ProductEditEvent.SelectCategory(category.id)) },
                label = { Text(category.name) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }

    SectionTitle(R.string.product_unit)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.unitType == UnitType.UNIT,
            onClick = { onEvent(ProductEditEvent.Unit(UnitType.UNIT)) },
            label = { Text(stringResource(R.string.product_unit_unit)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = state.unitType == UnitType.KG,
            onClick = { onEvent(ProductEditEvent.Unit(UnitType.KG)) },
            label = { Text(stringResource(R.string.product_unit_kg)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
    if (state.unitType == UnitType.KG) {
        Text(stringResource(R.string.product_unit_kg_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    OutlinedCard(onClick = { onEvent(ProductEditEvent.OpenPrice) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (state.unitType == UnitType.KG) R.string.product_price_kg else R.string.product_price),
                style = MaterialTheme.typography.labelLarge,
                color = if (state.error == ProductError.PRICE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.price?.let(MoneyFormat::format) ?: stringResource(R.string.product_price_empty),
                style = MaterialTheme.typography.headlineSmall.merge(MoneyTextStyle),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = state.favorite, role = Role.Switch, onValueChange = { onEvent(ProductEditEvent.Favorite(it)) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.favorite), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.product_favorite_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = state.favorite, onCheckedChange = null)
    }
    if (state.error == ProductError.CATEGORY || state.error == ProductError.SAVE) {
        Text(stringResource(R.string.notice_error), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CodesSection(state: ProductEditUiState, onEvent: (ProductEditEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(R.string.product_codes)
        if (state.codes.isEmpty()) {
            Text(stringResource(R.string.product_no_codes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.codes.forEach { code ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(code, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEvent(ProductEditEvent.RemoveCode(code)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.product_remove_code))
                }
            }
        }
        OutlinedButton(onClick = { onEvent(ProductEditEvent.StartScan) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.product_scan_code))
        }
        state.notice?.let { notice ->
            Text(
                text = when (notice) {
                    ProductNotice.CodeLinked -> stringResource(R.string.product_code_linked)
                    is ProductNotice.CodeMoved -> stringResource(R.string.product_code_moved, notice.name)
                    ProductNotice.CodeAlreadyHere -> stringResource(R.string.product_code_already)
                    ProductNotice.InvalidCode -> stringResource(R.string.product_code_invalid)
                },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun PriceHistorySection(history: List<PricePoint>) {
    Column {
        SectionTitle(R.string.product_history)
        history.forEach { point ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Dates.dayMonthYear(point.at))
                    Text(
                        stringResource(
                            when (point.source) {
                                PriceSource.SEED -> R.string.price_source_seed
                                PriceSource.TRIP -> R.string.price_source_trip
                                PriceSource.MANUAL -> R.string.price_source_manual
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(MoneyFormat.format(point.price), style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle))
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

/** Visor para asociar un código: la primera lectura válida cierra la hoja. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanCodeSheet(
    decoderFactory: () -> BarcodeDecoder,
    showCamera: Boolean,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.product_scan_hint), style = MaterialTheme.typography.titleMedium)
            val frame = Modifier.fillMaxWidth().height(260.dp)
            if (showCamera) {
                CameraPermissionGate(modifier = frame) {
                    Box(frame) {
                        ScannerView(decoderFactory = decoderFactory, onCode = onCode, modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.8f)
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.7f)),
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
        }
    }
}

private const val NOTICE_MS = 4_000L

@Preview
@Composable
private fun ProductEditPreview() {
    MercadoTheme {
        ProductEditScreen(
            ProductEditUiState(
                loading = false,
                productId = 1,
                name = "Arroz Diana 1 kg",
                categoryId = 1,
                price = Pesos(5_900),
                favorite = true,
                codes = listOf("7702004003508"),
                categories = listOf(Category(1, "Granos y despensa"), Category(2, "Lácteos"), Category(10, "Otros")),
                history = listOf(PricePoint(1, Pesos(5_900), 1_763_300_000_000, PriceSource.TRIP), PricePoint(1, Pesos(5_600), 1_760_000_000_000, PriceSource.SEED)),
                notice = ProductNotice.CodeLinked,
            ),
            {}, {}, { error("preview") }, showCamera = false,
        )
    }
}
