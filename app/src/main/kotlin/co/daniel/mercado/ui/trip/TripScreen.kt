package co.daniel.mercado.ui.trip

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import co.daniel.mercado.scanner.LabelReader
import co.daniel.mercado.scanner.LabelRead
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.scanner.BarcodeDecoder
import co.daniel.mercado.scanner.BarcodeSource
import co.daniel.mercado.scanner.CameraPermissionGate
import co.daniel.mercado.scanner.ScannerView
import co.daniel.mercado.ui.components.LineRow
import co.daniel.mercado.ui.components.TotalCard
import co.daniel.mercado.ui.components.UndoBar
import co.daniel.mercado.ui.theme.LocalTrafficLightColors

/**
 * Compra en curso (04-ux): arriba lo que se mira (cámara ~32 %, total), abajo lo que se toca
 * (lista, aviso, barra). La pantalla no se apaga mientras está visible.
 */
@Composable
fun TripScreen(
    viewModel: TripViewModel,
    cameraSource: BarcodeSource,
    decoderFactory: () -> BarcodeDecoder,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onNoActiveTrip: () -> Unit,
    modifier: Modifier = Modifier,
    showCamera: Boolean = true,
    labelReaderFactory: (() -> LabelReader)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Solo si la compra desaparece estando aquí (no por un estado viejo al entrar).
    var sawTrip by remember { mutableStateOf(false) }
    if (state.trip != null) sawTrip = true
    LaunchedEffect(sawTrip, state.noActiveTrip) { if (sawTrip && state.noActiveTrip) onNoActiveTrip() }
    BackHandler(onBack = onBack)
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.safeDrawingPadding()) {
            val cameraHeight = maxHeight * CAMERA_FRACTION
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    torchOn = state.torchOn,
                    paused = state.cameraPaused,
                    onBack = onBack,
                    onTorch = viewModel::toggleTorch,
                    onPause = viewModel::toggleCameraPause,
                )
                CameraArea(
                    state = state,
                    height = cameraHeight,
                    showCamera = showCamera,
                    cameraSource = cameraSource,
                    decoderFactory = decoderFactory,
                    onResume = viewModel::resumeCamera,
                    labelReaderFactory = labelReaderFactory,
                    onLabel = viewModel::onLabel,
                    onToggleLabelMode = viewModel::toggleLabelMode,
                )
                state.totalCard?.let { TotalCard(it, Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) }
                Lines(
                    items = state.trip?.items.orEmpty(),
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                )
                state.notice?.let { notice ->
                    UndoBar(
                        message = noticeText(notice),
                        onUndo = if (notice.undoable) viewModel::undo else null,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                BottomBar(
                    onKeypad = viewModel::openLooseKeypad,
                    onFavorites = { viewModel.openPicker(PickerTab.FAVORITES) },
                    onSearch = { viewModel.openPicker(PickerTab.SEARCH) },
                    onClose = onClose,
                    closeEnabled = state.trip != null,
                )
            }
        }
    }

    TripSheets(state, viewModel)
}

@Composable
private fun TopBar(torchOn: Boolean, paused: Boolean, onBack: () -> Unit, onTorch: () -> Unit, onPause: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.trip_back)) }
        Text(
            stringResource(R.string.trip_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (!paused) {
            IconButton(onClick = onTorch) {
                Icon(
                    painterResource(if (torchOn) R.drawable.ic_flashlight_off else R.drawable.ic_flashlight_on),
                    stringResource(if (torchOn) R.string.torch_off else R.string.torch_on),
                )
            }
        }
        IconButton(onClick = onPause) {
            Icon(
                painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
                stringResource(if (paused) R.string.camera_resume else R.string.camera_pause),
            )
        }
    }
}

@Composable
private fun CameraArea(
    state: TripUiState,
    height: androidx.compose.ui.unit.Dp,
    showCamera: Boolean,
    cameraSource: BarcodeSource,
    decoderFactory: () -> BarcodeDecoder,
    onResume: () -> Unit,
    labelReaderFactory: (() -> LabelReader)?,
    onLabel: (LabelRead) -> Unit,
    onToggleLabelMode: () -> Unit,
) {
    if (state.cameraPaused) {
        // Visor colapsado a una franja: la lista gana espacio. Se reanuda tocándola.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Black)
                .clickable(enabled = state.sheet !is TripSheet.NewProduct, onClick = onResume)
                .testTag("camera_strip"),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.camera_paused), color = Color.White)
        }
        return
    }
    val flash = state.highlightLineId != null
    val flashColor = LocalTrafficLightColors.current.green
    val frame = Modifier
        .fillMaxWidth()
        .height(height)
        .border(BorderStroke(if (flash) 4.dp else 0.dp, if (flash) flashColor else Color.Transparent))
    if (!showCamera) {
        Box(frame.background(Color.Black))
        return
    }
    CameraPermissionGate(modifier = frame) {
        Box(frame) {
            ScannerView(
                decoderFactory = decoderFactory,
                onCode = cameraSource::offer,
                modifier = Modifier.fillMaxSize(),
                torchOn = state.torchOn,
                labelMode = state.labelMode && labelReaderFactory != null,
                labelReaderFactory = labelReaderFactory,
                onLabel = onLabel,
            )
            if (state.labelMode) {
                Text(
                    text = stringResource(R.string.label_mode_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            } else {
                // Línea guía horizontal.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.8f)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.7f)),
                )
            }
            if (labelReaderFactory != null) {
                ModeSwitch(
                    labelMode = state.labelMode,
                    onToggle = onToggleLabelMode,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                )
            }
        }
    }
}

/** "Código | Etiqueta" sobre el visor: qué lee la cámara. */
@Composable
private fun ModeSwitch(labelMode: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to R.string.mode_code, true to R.string.mode_label).forEach { (isLabel, label) ->
            val selected = labelMode == isLabel
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(20.dp))
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) onToggle() })
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Lines(items: List<TripItem>, state: TripUiState, viewModel: TripViewModel, modifier: Modifier) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.TopCenter) {
            if (!state.loading) {
                Text(
                    text = stringResource(R.string.trip_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth().testTag("trip_lines")) {
        items(items, key = { it.id }) { item ->
            SwipeToDeleteLine(onDelete = { viewModel.removeLine(item.id) }) {
                LineRow(
                    item = item,
                    onDecrement = { viewModel.decrement(item.id) },
                    onIncrement = { viewModel.increment(item.id) },
                    onPriceClick = { viewModel.openFixPrice(item.id) },
                    onNameClick = { viewModel.openEditLine(item.id) },
                    stalePrice = item.productId in state.staleProductIds,
                    highlighted = item.id == state.highlightLineId,
                )
            }
            HorizontalDivider()
        }
    }
}

/** Deslizar a la izquierda elimina (con "Deshacer" en el aviso). */
@Composable
private fun SwipeToDeleteLine(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
private fun BottomBar(
    onKeypad: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    closeEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BarButton(R.drawable.ic_dialpad, R.string.trip_bar_keypad, onKeypad, primary = true)
        BarButton(R.drawable.ic_star, R.string.trip_bar_favorites, onFavorites)
        BarButton(R.drawable.ic_search, R.string.trip_bar_search, onSearch)
        BarButton(R.drawable.ic_check, R.string.close_trip, onClose, enabled = closeEnabled)
    }
}

/** Botón de la barra de la compra: ícono arriba y nombre completo abajo, 64 dp de alto. */
@Composable
private fun RowScope.BarButton(icon: Int, label: Int, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    val colors = if (primary) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).height(64.dp),
        shape = MaterialTheme.shapes.large,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
fun noticeText(notice: TripNotice): String {
    val name = notice.name.ifBlank { stringResource(R.string.loose_item_name) }
    return when (notice.kind) {
        NoticeKind.ADDED -> stringResource(R.string.notice_added, name, MoneyFormat.format(notice.amount ?: co.daniel.mercado.domain.model.Pesos.ZERO))
        NoticeKind.INCREMENTED, NoticeKind.QUANTITY -> stringResource(R.string.notice_quantity, name, notice.quantity)
        NoticeKind.REMOVED -> stringResource(R.string.notice_removed, name)
        NoticeKind.PRICE_CHANGED -> stringResource(R.string.notice_price_changed, name, MoneyFormat.format(notice.amount ?: co.daniel.mercado.domain.model.Pesos.ZERO))
        NoticeKind.RENAMED -> stringResource(R.string.notice_renamed, name)
        NoticeKind.UNDONE -> stringResource(R.string.notice_undone)
        NoticeKind.INVALID_PRICE -> stringResource(R.string.notice_invalid_price)
        NoticeKind.ERROR -> stringResource(R.string.notice_error)
    }
}

private const val CAMERA_FRACTION = 0.32f
