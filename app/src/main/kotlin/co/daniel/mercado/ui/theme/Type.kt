package co.daniel.mercado.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val MercadoTypography = Typography()

/** Total de la compra: grande, fuerte y con cifras tabulares. */
val TotalTextStyle = TextStyle(
    fontSize = 46.sp,
    lineHeight = 52.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

/** Cualquier monto en listas: cifras tabulares para que las columnas se alineen. */
val MoneyTextStyle = TextStyle(fontFeatureSettings = "tnum")
