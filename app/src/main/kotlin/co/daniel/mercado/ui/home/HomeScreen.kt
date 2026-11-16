package co.daniel.mercado.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import co.daniel.mercado.R
import co.daniel.mercado.ui.theme.MercadoTheme

/**
 * Inicio provisional: el nombre de la app y, solo en debug, el acceso al diagnóstico del escáner
 * ([onOpenDiagnostics] nulo en release). El Inicio real llega en L5.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenDiagnostics: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (onOpenDiagnostics != null) {
                TextButton(onClick = onOpenDiagnostics) { Text(stringResource(R.string.diag_open)) }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    MercadoTheme { HomeScreen(onOpenDiagnostics = {}) }
}
