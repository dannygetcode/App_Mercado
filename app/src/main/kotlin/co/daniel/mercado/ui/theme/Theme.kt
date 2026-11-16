package co.daniel.mercado.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green900,
    onPrimary = Color.White,
    primaryContainer = Green50,
    onPrimaryContainer = Green900,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    error = ErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = Green200,
    onPrimary = Green900,
    primaryContainer = Green900,
    onPrimaryContainer = Green50,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    error = ErrorDark,
)

val LocalTrafficLightColors = staticCompositionLocalOf { TrafficLightLight }

/** Tema propio sin color dinámico (D-22). */
@Composable
fun MercadoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTrafficLightColors provides if (darkTheme) TrafficLightDark else TrafficLightLight,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MercadoTypography,
            content = content,
        )
    }
}
