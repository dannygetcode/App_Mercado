package co.daniel.mercado.scanner

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.daniel.mercado.R
import co.daniel.mercado.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/**
 * Visor de cámara con lectura continua (sin botón de disparo).
 *
 * Solo `Preview` + `ImageAnalysis` (S-07): no hay `ImageCapture` y ningún cuadro va a disco.
 * Emite por [onCode] cada código con dígito de control válido; el antirrebote y la decisión sobre
 * productos son del dominio (`HandleScan`). La cámara se suelta al pausar o al salir de la
 * composición, y CameraX la detiene sola cuando la actividad pasa a segundo plano.
 */
@Composable
fun ScannerView(
    decoderFactory: () -> BarcodeDecoder,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    torchOn: Boolean = false,
    onResumeRequest: () -> Unit = {},
    onCameraStarted: () -> Unit = {},
    onTorchAvailable: (Boolean) -> Unit = {},
    labelMode: Boolean = false,
    labelReaderFactory: (() -> LabelReader)? = null,
    onLabel: (LabelRead) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnLabel by rememberUpdatedState(onLabel)
    val currentOnStarted by rememberUpdatedState(onCameraStarted)
    val currentOnTorch by rememberUpdatedState(onTorchAvailable)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var failed by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE (TextureView): Compose sí lo recorta y respeta el orden de capas. Con
            // PERFORMANCE (SurfaceView) y FILL_CENTER el visor se desbordaba por encima de la barra
            // y del resto de la pantalla en el Poco X8 Pro.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(previewView, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { state ->
            if (state == PreviewView.StreamState.STREAMING) currentOnStarted()
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }

    LaunchedEffect(paused, lifecycleOwner, labelMode) {
        if (paused) return@LaunchedEffect
        // Un hilo de análisis por enlace: se apaga solo después de soltar la cámara, para que
        // CameraX nunca entregue un cuadro a un ejecutor ya cerrado.
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        var decoder: BarcodeDecoder? = null
        var labelReader: LabelReader? = null
        val preview = Preview.Builder()
            .setResolutionSelector(RESOLUTION)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(RESOLUTION)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        try {
            provider = ProcessCameraProvider.awaitInstance(context)
            val readerFactory = labelReaderFactory
            if (labelMode && readerFactory != null) {
                // Etiqueta del estante: texto y código en el mismo cuadro.
                val activeReader = readerFactory().also { labelReader = it }
                analysis.setAnalyzer(analysisExecutor) { image -> activeReader.analyze(image) { currentOnLabel(it) } }
            } else {
                val activeDecoder = decoderFactory().also { decoder = it }
                analysis.setAnalyzer(analysisExecutor) { image ->
                    activeDecoder.analyze(image) { codes ->
                        ScanFilter.firstValid(codes)?.let(currentOnCode)
                    }
                }
            }
            val bound = provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
            )
            camera = bound
            failed = false
            currentOnTorch(bound.cameraInfo.hasFlashUnit())
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin cámara trasera, cámara ocupada por otra app o fallo al iniciar CameraX:
            // se muestra el aviso y la pantalla sigue usable (teclado, favoritos, búsqueda).
            AppLog.w("ScannerView: no se pudo abrir la cámara", e)
            failed = true
        } finally {
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            decoder?.close()
            labelReader?.close()
            analysisExecutor.shutdown()
            camera = null
        }
    }

    LaunchedEffect(camera, torchOn) {
        val cam = camera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(torchOn)
    }

    Box(modifier = modifier.clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            paused -> CameraMessage(
                text = stringResource(R.string.camera_paused),
                modifier = Modifier.fillMaxSize().clickable(onClick = onResumeRequest),
            )
            failed -> CameraMessage(stringResource(R.string.camera_error), Modifier.fillMaxSize())
            else -> AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CameraMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}


/** 1280×720 (02-arquitectura): suficiente para EAN a distancia de brazo y rápido de analizar. */
private val RESOLUTION: ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(
            Size(1280, 720),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        ),
    )
    .build()
