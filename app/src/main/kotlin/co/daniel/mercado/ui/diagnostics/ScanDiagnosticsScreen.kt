package co.daniel.mercado.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.ScanDebouncer
import co.daniel.mercado.scanner.BarcodeDecoder
import co.daniel.mercado.scanner.BarcodeSource
import co.daniel.mercado.scanner.CameraPermissionGate
import co.daniel.mercado.scanner.ScanFeedback
import co.daniel.mercado.scanner.ScannerView
import co.daniel.mercado.ui.theme.MoneyTextStyle

/**
 * Diagnóstico del escáner (solo debug, L3-T4): visor real, últimos códigos leídos, tiempo de
 * cámara arrancada a primera lectura y lecturas por minuto. Usa el mismo antirrebote que la
 * compra. Sirve para la prueba en modo avión y se conserva en debug.
 */
@Composable
fun ScanDiagnosticsScreen(
    source: BarcodeSource,
    decoderFactory: () -> BarcodeDecoder,
    feedback: ScanFeedback,
    clock: Clock,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadSample: (() -> Unit)? = null,
    sampleMessage: String? = null,
) {
    var state by remember { mutableStateOf(ScanDiagnostics()) }
    val debouncer = remember { ScanDebouncer(clock) }
    var paused by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(source) {
        source.codes.collect { code ->
            val accepted = debouncer.shouldAccept(code)
            if (accepted) feedback.accepted()
            state = state.read(code, clock.nowMillis(), accepted)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                if (hasTorch) {
                    TextButton(onClick = { torchOn = !torchOn }) {
                        Text(stringResource(if (torchOn) R.string.torch_off else R.string.torch_on))
                    }
                }
                TextButton(onClick = {
                    paused = !paused
                    if (paused) state = state.cameraStopped()
                }) {
                    Text(stringResource(if (paused) R.string.camera_resume else R.string.camera_pause))
                }
            }

            if (onLoadSample != null) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLoadSample) { Text(stringResource(R.string.diag_load_sample)) }
                    sampleMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            CameraPermissionGate(modifier = Modifier.fillMaxWidth().fillMaxHeight(CAMERA_FRACTION)) {
                ScannerView(
                    decoderFactory = decoderFactory,
                    onCode = source::offer,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(CAMERA_FRACTION),
                    paused = paused,
                    torchOn = torchOn,
                    onResumeRequest = { paused = false },
                    onCameraStarted = { state = state.cameraStarted(clock.nowMillis()) },
                    onTorchAvailable = { hasTorch = it },
                )
            }

            DiagnosticsSummary(state, now = state.reads.firstOrNull()?.atMs ?: 0L)
            HorizontalDivider()
            ReadsList(state.reads, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiagnosticsSummary(state: ScanDiagnostics, now: Long) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.titleMedium)
        val first = state.firstReadLatencyMs?.let(::formatSeconds)
            ?: stringResource(R.string.diag_waiting)
        Text(stringResource(R.string.diag_first_read, first), style = MoneyTextStyle)
        Text(stringResource(R.string.diag_per_minute, state.readsPerMinute(now)), style = MoneyTextStyle)
        Text(stringResource(R.string.diag_counts, state.accepted, state.bounced), style = MoneyTextStyle)
    }
}

@Composable
private fun ReadsList(reads: List<DiagnosticRead>, modifier: Modifier = Modifier) {
    if (reads.isEmpty()) {
        Text(
            text = stringResource(R.string.diag_empty),
            modifier = modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(reads, key = { it.atMs.toString() + it.code }) { read ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(read.code, style = MoneyTextStyle, modifier = Modifier.weight(1f))
                Text(read.elapsedMs?.let { "+" + formatSeconds(it) } ?: "", style = MoneyTextStyle)
            }
        }
    }
}

/** `1234` → `1,23 s` (enteros; sin depender del Locale). */
internal fun formatSeconds(ms: Long): String {
    val centis = (ms.coerceAtLeast(0) + 5) / 10
    return "${centis / 100},${(centis % 100).toString().padStart(2, '0')} s"
}

private const val CAMERA_FRACTION = 0.32f
