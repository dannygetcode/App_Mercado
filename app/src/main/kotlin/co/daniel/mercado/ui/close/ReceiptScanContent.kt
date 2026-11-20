package co.daniel.mercado.ui.close

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.domain.rules.ReceiptLine
import co.daniel.mercado.scanner.BarcodeDecoder
import co.daniel.mercado.scanner.CameraPermissionGate
import co.daniel.mercado.scanner.ReceiptReader
import co.daniel.mercado.scanner.ScannerView
import co.daniel.mercado.ui.theme.MoneyTextStyle

/**
 * Recibo al cerrar la compra: la cámara lo recorre de arriba abajo y los renglones aparecen a medida
 * que se confirman; luego se revisan (se puede desmarcar lo mal leído) y se aplican a la compra.
 * No hay foto: solo análisis en vivo (S-07).
 */
@Composable
fun ReceiptScanContent(
    receipt: ReceiptUi,
    viewModel: CloseTripViewModel,
    receiptReaderFactory: () -> ReceiptReader,
    decoderFactory: () -> BarcodeDecoder,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = if (receipt.reviewing) viewModel::continueReceiptScan else viewModel::closeReceipt)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            if (receipt.reviewing) {
                ReviewStage(receipt, viewModel)
            } else {
                ScanStage(receipt, viewModel, receiptReaderFactory, decoderFactory)
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ScanStage(
    receipt: ReceiptUi,
    viewModel: CloseTripViewModel,
    receiptReaderFactory: () -> ReceiptReader,
    decoderFactory: () -> BarcodeDecoder,
) {
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    Header(stringResource(R.string.receipt_title), viewModel::closeReceipt) {
        if (hasTorch) {
            IconButton(onClick = { torch = !torch }) {
                Icon(
                    painterResource(if (torch) R.drawable.ic_flashlight_off else R.drawable.ic_flashlight_on),
                    contentDescription = stringResource(if (torch) R.string.torch_off else R.string.torch_on),
                )
            }
        }
    }
    CameraPermissionGate(modifier = Modifier.fillMaxWidth().weight(1f)) {
        ScannerView(
            decoderFactory = decoderFactory,
            onCode = {},
            modifier = Modifier.fillMaxSize(),
            torchOn = torch,
            onTorchAvailable = { hasTorch = it },
            receiptReaderFactory = receiptReaderFactory,
            onReceipt = viewModel::onReceiptScan,
        )
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.receipt_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(R.plurals.receipt_read, receipt.lines.size, receipt.lines.size) +
                (receipt.total?.let { " · " + stringResource(R.string.receipt_total, MoneyFormat.format(it)) } ?: ""),
            style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
            modifier = Modifier.testTag("receipt_status"),
        )
        receipt.lines.takeLast(PREVIEW_LINES).forEach { line ->
            Text(
                "${line.name} · ${MoneyFormat.format(line.amount)}",
                style = MaterialTheme.typography.bodySmall.merge(MoneyTextStyle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = viewModel::reviewReceipt,
            enabled = receipt.lines.isNotEmpty() || receipt.total != null,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text(stringResource(R.string.receipt_done), style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReviewStage(receipt: ReceiptUi, viewModel: CloseTripViewModel) {
    Header(stringResource(R.string.receipt_review_title), viewModel::continueReceiptScan)
    Text(
        stringResource(R.string.receipt_review_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(receipt.lines) { index, line ->
            ReceiptRow(line, checked = index in receipt.selected, onToggle = { viewModel.toggleReceiptLine(index) })
            HorizontalDivider()
        }
        if (receipt.lines.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.receipt_no_lines),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.receipt_selected_sum), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(MoneyFormat.format(receipt.selectedSum), style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle))
        }
        receipt.total?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.receipt_total_label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(MoneyFormat.format(it), style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle))
            }
        }
        Button(
            onClick = viewModel::applyReceipt,
            enabled = !receipt.applying && (receipt.selected.isNotEmpty() || receipt.total != null),
            modifier = Modifier.fillMaxWidth().height(64.dp).testTag("receipt_apply"),
        ) { Text(stringResource(R.string.receipt_apply), style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick = viewModel::continueReceiptScan,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.receipt_continue)) }
        TextButton(onClick = viewModel::closeReceipt, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun ReceiptRow(line: ReceiptLine, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.effectiveQuantity > 1) {
                Text(
                    stringResource(R.string.receipt_qty, line.effectiveQuantity, MoneyFormat.format(line.unitPrice)),
                    style = MaterialTheme.typography.bodySmall.merge(MoneyTextStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            MoneyFormat.format(line.amount),
            style = MaterialTheme.typography.titleMedium.merge(MoneyTextStyle),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}

private const val PREVIEW_LINES = 3
