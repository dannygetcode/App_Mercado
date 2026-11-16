package co.daniel.mercado.ui.theme

import androidx.compose.ui.graphics.Color

internal val Green900 = Color(0xFF1B5E20)
internal val Green200 = Color(0xFFA5D6A7)
internal val Green50 = Color(0xFFE8F5E9)
internal val BackgroundLight = Color(0xFFFAFAF7)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val OnBackgroundLight = Color(0xFF1A1C19)
internal val BackgroundDark = Color(0xFF121412)
internal val SurfaceDark = Color(0xFF1C1F1C)
internal val OnBackgroundDark = Color(0xFFE2E3DE)
internal val ErrorLight = Color(0xFFB3261E)
internal val ErrorDark = Color(0xFFF2B8B5)

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
