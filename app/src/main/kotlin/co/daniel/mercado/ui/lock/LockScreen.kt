package co.daniel.mercado.ui.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.daniel.mercado.R
import co.daniel.mercado.ui.theme.MercadoTheme

/**
 * Bloqueo (04-ux): logo, "Mercado" y el diálogo del sistema lanzado solo al entrar. Si se cancela,
 * queda "Desbloquear". Detrás no hay nada de la app.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier, autoPrompt: Boolean = true) {
    LaunchedEffect(Unit) { if (autoPrompt) onUnlock() }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(112.dp).clip(CircleShape).background(colorResource(R.color.launcher_background)),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.lock_unlock), fontSize = 18.sp)
            }
        }
    }
}

@Preview
@Composable
private fun LockScreenPreview() {
    MercadoTheme { LockScreen(onUnlock = {}, autoPrompt = false) }
}
