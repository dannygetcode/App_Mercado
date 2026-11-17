package co.daniel.mercado.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val LightColors = with(Light) {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        outline = outline, outlineVariant = outlineVariant,
        surfaceContainerLowest = containerLowest, surfaceContainerLow = containerLow,
        surfaceContainer = container, surfaceContainerHigh = containerHigh,
        surfaceContainerHighest = containerHighest,
        surfaceBright = containerLowest, surfaceDim = containerHighest,
        error = error, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    )
}

private val DarkColors = with(Dark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        outline = outline, outlineVariant = outlineVariant,
        surfaceContainerLowest = containerLowest, surfaceContainerLow = containerLow,
        surfaceContainer = container, surfaceContainerHigh = containerHigh,
        surfaceContainerHighest = containerHighest,
        surfaceBright = containerHighest, surfaceDim = containerLowest,
        error = error, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    )
}

/** Esquinas: chips y campos 12–16 dp, tarjetas 20 dp, hojas 28 dp. */
private val MercadoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
            shapes = MercadoShapes,
            content = content,
        )
    }
}
