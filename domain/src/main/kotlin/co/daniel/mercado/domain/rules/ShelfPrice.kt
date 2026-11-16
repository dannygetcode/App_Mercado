package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.port.Clock

/** Una línea de texto leída en la etiqueta del estante, con su alto en píxeles (tamaño de letra). */
data class TextLine(val text: String, val height: Int)

/**
 * Precio de una etiqueta de estante leída por la cámara. En la etiqueta el precio de venta es el
 * número más grande; el precio por unidad de medida ("$24,95 x g") y las cantidades ("500 g") son
 * pequeños o llevan unidad, y se descartan. Sin centavos (COP).
 */
object ShelfPrice {

    val MIN = Pesos(50)
    val MAX = Pesos(5_000_000)

    private val NUMBER = Regex("""(\$\s?)?(\d{1,3}(?:[.,]\d{3})+|\d+)([.,]\d{1,2})?(?!\d)""")
    private val UNIT_AFTER = Regex("""^\s?(%|g\b|gr|grs|kg|kl|ml|mL|l\b|lt|lts|cc|und|un\b|uds|x\b|oz|lb|cm|m\b|mts|hojas|pza)""", RegexOption.IGNORE_CASE)

    private data class Candidate(val value: Long, val height: Int, val hasSign: Boolean)

    fun find(lines: List<TextLine>): Pesos? =
        lines.flatMap { candidates(it) }
            .maxWithOrNull(compareBy<Candidate>({ it.height }, { it.hasSign }, { it.value }))
            ?.let { Pesos(it.value) }

    private fun candidates(line: TextLine): List<Candidate> = NUMBER.findAll(line.text).mapNotNull { m ->
        val before = line.text.getOrNull(m.range.first - 1)
        if (before != null && (before.isLetterOrDigit())) return@mapNotNull null
        val after = line.text.substring(m.range.last + 1)
        if (UNIT_AFTER.containsMatchIn(after)) return@mapNotNull null
        val sign = m.groupValues[1].isNotEmpty()
        val integer = m.groupValues[2]
        val decimals = m.groupValues[3]
        // Centavos distintos de cero: es un precio por unidad de medida, no el de venta.
        if (decimals.isNotEmpty() && decimals.drop(1).any { it != '0' }) return@mapNotNull null
        val grouped = integer.length > 3 && (integer.contains('.') || integer.contains(','))
        // Un número suelto sin "$" ni separador de miles es ambiguo (pasillo, peso, fecha...).
        if (!sign && !grouped) return@mapNotNull null
        val value = integer.filter(Char::isDigit).toLongOrNull() ?: return@mapNotNull null
        if (value !in MIN.value..MAX.value) return@mapNotNull null
        Candidate(value, line.height, sign)
    }.toList()
}

/**
 * Evita sumar por una lectura suelta: una etiqueta cuenta cuando el mismo código y precio se leen
 * dos veces seguidas dentro de [windowMs]. La que ya se aceptó no vuelve a contar mientras siga en
 * cuadro; tras [forgetMs] sin verla, sí (para echar otra igual más adelante).
 */
class LabelStabilizer(
    private val clock: Clock,
    private val windowMs: Long = 2_000,
    private val forgetMs: Long = 3_000,
) {
    private data class Seen(val code: String?, val price: Pesos, val at: Long)

    private var pending: Seen? = null
    private var accepted: Seen? = null

    fun offer(code: String?, price: Pesos): Boolean {
        val now = clock.nowMillis()
        accepted?.let { last ->
            if (last.code == code && last.price == price && now - last.at < forgetMs) {
                accepted = last.copy(at = now)
                return false
            }
        }
        val previous = pending
        if (previous != null && previous.code == code && previous.price == price && now - previous.at <= windowMs) {
            pending = null
            accepted = Seen(code, price, now)
            return true
        }
        pending = Seen(code, price, now)
        return false
    }

    /** Al cambiar de modo o pausar: empieza de cero. */
    fun reset() {
        pending = null
        accepted = null
    }
}
