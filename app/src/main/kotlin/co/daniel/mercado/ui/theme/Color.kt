package co.daniel.mercado.ui.theme

import androidx.compose.ui.graphics.Color

// Esquema propio (D-22): verdes y neutros cálidos, sin el lila por defecto de Material 3.
internal val Green900 = Color(0xFF1B5E20)
internal val Green200 = Color(0xFFA5D6A7)
internal val Green50 = Color(0xFFE8F5E9)

internal object Light {
    val primary = Green900
    val onPrimary = Color.White
    val primaryContainer = Color(0xFFC6EBC5)
    val onPrimaryContainer = Color(0xFF07390D)
    val secondary = Color(0xFF4D6350)
    val onSecondary = Color.White
    val secondaryContainer = Color(0xFFDCEBD9)
    val onSecondaryContainer = Color(0xFF13261A)
    val tertiary = Color(0xFF2E6A66)
    val onTertiary = Color.White
    val tertiaryContainer = Color(0xFFCDEBE7)
    val onTertiaryContainer = Color(0xFF002020)
    val background = Color(0xFFFAFAF7)
    val onBackground = Color(0xFF1A1C19)
    val surface = Color(0xFFFAFAF7)
    val onSurface = Color(0xFF1A1C19)
    val surfaceVariant = Color(0xFFDEE5D9)
    val onSurfaceVariant = Color(0xFF424940)
    val outline = Color(0xFF72796F)
    val outlineVariant = Color(0xFFC2C9BD)
    val containerLowest = Color.White
    val containerLow = Color(0xFFF4F5F0)
    val container = Color(0xFFEEF0EA)
    val containerHigh = Color(0xFFE8EAE4)
    val containerHighest = Color(0xFFE2E4DE)
    val error = Color(0xFFB3261E)
    val errorContainer = Color(0xFFF9DEDC)
    val onErrorContainer = Color(0xFF410E0B)
    // Avisos con "Deshacer": fondo oscuro con la acción en verde claro (sin el lila por defecto).
    val inverseSurface = Color(0xFF2F312D)
    val inverseOnSurface = Color(0xFFF0F1EB)
    val inversePrimary = Green200
}

internal object Dark {
    val primary = Green200
    val onPrimary = Color(0xFF0A3912)
    val primaryContainer = Color(0xFF1F5226)
    val onPrimaryContainer = Color(0xFFC6EBC5)
    val secondary = Color(0xFFB8CCB4)
    val onSecondary = Color(0xFF243525)
    val secondaryContainer = Color(0xFF3A4C3B)
    val onSecondaryContainer = Color(0xFFD4E8D1)
    val tertiary = Color(0xFF9FD0CB)
    val onTertiary = Color(0xFF003735)
    val tertiaryContainer = Color(0xFF14504D)
    val onTertiaryContainer = Color(0xFFBBECE7)
    val background = Color(0xFF121412)
    val onBackground = Color(0xFFE2E3DE)
    val surface = Color(0xFF121412)
    val onSurface = Color(0xFFE2E3DE)
    val surfaceVariant = Color(0xFF424940)
    val onSurfaceVariant = Color(0xFFC2C9BD)
    val outline = Color(0xFF8C9388)
    val outlineVariant = Color(0xFF424940)
    val containerLowest = Color(0xFF0D0F0D)
    val containerLow = Color(0xFF1A1C19)
    val container = Color(0xFF1E201D)
    val containerHigh = Color(0xFF282B27)
    val containerHighest = Color(0xFF333631)
    val error = Color(0xFFF2B8B5)
    val errorContainer = Color(0xFF8C1D18)
    val onErrorContainer = Color(0xFFF9DEDC)
    val inverseSurface = Color(0xFFE2E3DE)
    val inverseOnSurface = Color(0xFF2F312D)
    val inversePrimary = Green900
}

/** Colores del semáforo (04-ux.md). No se usan para nada más. */
data class TrafficLightColors(val green: Color, val yellow: Color, val red: Color)

internal val TrafficLightLight = TrafficLightColors(
    green = Color(0xFF2E7D32),
    yellow = Color(0xFFF9A825),
    red = Color(0xFFC62828),
)

internal val TrafficLightDark = TrafficLightColors(
    green = Color(0xFF66BB6A),
    yellow = Color(0xFFFDD835),
    red = Color(0xFFEF5350),
)
