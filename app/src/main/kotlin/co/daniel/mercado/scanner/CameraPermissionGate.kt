package co.daniel.mercado.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.daniel.mercado.R
import co.daniel.mercado.ui.theme.MercadoTheme

/**
 * Muestra [content] (el visor) solo con permiso de cámara. Pide el permiso una vez al entrar;
 * si se niega, deja un panel "Sin acceso a la cámara" con "Permitir" y la pantalla sigue usable.
 * Si el sistema ya no muestra el diálogo (negado para siempre), "Permitir" abre los ajustes de
 * la app. El permiso se revisa de nuevo al volver a la app.
 */
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    autoRequest: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        permanentlyDenied = !ok && context.findActivity()
            ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }

    LifecycleResumeEffect(Unit) {
        granted = context.hasCameraPermission()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        if (autoRequest && !granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (granted) {
        content()
    } else {
        CameraDeniedPanel(
            modifier = modifier,
            onAllow = {
                if (permanentlyDenied) {
                    context.openAppSettings()
                } else {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            },
        )
    }
}

@Composable
fun CameraDeniedPanel(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Color.Black).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.camera_denied),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.camera_allow))
        }
    }
}

fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview
@Composable
private fun CameraDeniedPanelPreview() {
    MercadoTheme { CameraDeniedPanel(onAllow = {}) }
}
