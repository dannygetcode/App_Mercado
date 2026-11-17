package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.port.Clock

/** Una línea de texto leída en la etiqueta del estante, con su alto en píxeles (tamaño de letra). */
data class TextLine(val text: String, val height: Int)

/**
 * Precio de una etiqueta de estante leída por la cámara. En la etiqueta el precio de venta es el
 * número más grande; el precio por unidad de medida ("$24,95 x g") y las cantidades ("500 g") son
 * pequeños o llevan unidad, y se descartan. Sin centavos (COP).
 *
 * La lectura de texto confunde caracteres: "S" por "$", "O" por "0", "l" por "1", y a veces ve
 * un espacio donde va el punto de miles. Se corrigen antes de buscar el número.
 */
object ShelfPrice {

    val MIN = Pesos(50)
    val MAX = Pesos(5_000_000)

    private val NUMBER = Regex("""(\$\s?)?(\d{1,3}(?:[.,]\d{3})+|\d+)([.,]\d{1,2})?(?!\d)""")

    /** Unidades de cantidad o medida: un número seguido de esto no es un precio. "Und" sí puede serlo. */
    private val UNIT_AFTER = Regex(
        """^\s?(%|g\b|gr\b|grs\b|gramos|kg\b|kl\b|kilo|ml\b|l\b|lt\b|lts\b|litro|cc\b|oz\b|lb\b|cm\b|mts?\b|hojas|pza|pañales|tabletas|metros)""",
        RegexOption.IGNORE_CASE,
    )

    /** Solo el número (y si acaso "$" o "und"): así se ve el precio grande de la etiqueta. */
    private val ALONE = Regex("""^\s*\$?\s*\d{3,7}\s*(c/u|und\.?|un\.?|unid\.?)?\s*$""", RegexOption.IGNORE_CASE)

    private data class Candidate(val value: Long, val height: Int, val strong: Boolean)

    fun find(lines: List<TextLine>): Pesos? {
        val tallest = lines.maxOfOrNull { it.height } ?: return null
        return lines.flatMap { candidates(TextLine(normalize(it.text), it.height)) }
            // Un número sin "$" ni separador solo vale si es (casi) el texto más grande de la etiqueta.
            .filter { it.strong || it.height * 10 >= tallest * 9 }
            .maxWithOrNull(compareBy<Candidate>({ it.height }, { it.strong }, { it.value }))
            ?.let { Pesos(it.value) }
    }

    /** Corrige las confusiones típicas de la lectura de texto alrededor de los números. */
    internal fun normalize(text: String): String {
        var s = text
        s = Regex("""(?<![A-Za-z])[Ss]\s?(?=\d)""").replace(s) { "$" }
        // "$1O.9OO": cada "O" pegada a cifras es un cero (se repite porque una "O" corregida
        // vuelve cifra a la siguiente).
        val letterO = Regex("""(?<=[\d$.,])[Oo]|[Oo](?=[\d.,]*\d)""")
        while (letterO.containsMatchIn(s)) s = letterO.replace(s, "0")
        s = s.replace(Regex("""(?<=[\d$])[lI|](?=[\d.,])"""), "1")
        // "$4 590" o "4 590" sola en la línea: el espacio era el punto de miles.
        if (s.contains('$') || s.trim().matches(Regex("""\d{1,3}(\s\d{3})+"""))) {
            s = s.replace(Regex("""(?<=\d)\s(?=\d{3}(?!\d))"""), ".")
        }
        return s
    }

    private fun candidates(line: TextLine): List<Candidate> = NUMBER.findAll(line.text).mapNotNull { m ->
        val before = line.text.getOrNull(m.range.first - 1)
        if (before != null && before.isLetterOrDigit()) return@mapNotNull null
        val after = line.text.substring(m.range.last + 1)
        if (UNIT_AFTER.containsMatchIn(after)) return@mapNotNull null
        val sign = m.groupValues[1].isNotEmpty()
        val integer = m.groupValues[2]
        val decimals = m.groupValues[3]
        // Centavos distintos de cero: es un precio por unidad de medida, no el de venta.
        if (decimals.isNotEmpty() && decimals.drop(1).any { it != '0' }) return@mapNotNull null
        val grouped = integer.length > 3 && (integer.contains('.') || integer.contains(','))
        // Un número suelto sin "$" ni separador cuenta solo si está solo en su línea (el precio grande).
        val alone = ALONE.matches(line.text)
        if (!sign && !grouped && !alone) return@mapNotNull null
        val value = integer.filter(Char::isDigit).toLongOrNull() ?: return@mapNotNull null
        if (value !in MIN.value..MAX.value) return@mapNotNull null
        Candidate(value, line.height, strong = sign || grouped)
    }.toList()
}

/**
 * Evita abrir el teclado por una lectura suelta: un precio cuenta cuando se lee dos veces dentro de
 * [windowMs] (el código de barras de la etiqueta, pequeño, se lee en unos cuadros sí y en otros no,
 * así que no entra en la comparación: se usa el último visto). El precio ya aceptado no vuelve a
 * contar mientras siga en cuadro; tras [forgetMs] sin verlo, sí.
 */
class LabelStabilizer(
    private val clock: Clock,
    private val windowMs: Long = 2_500,
    private val forgetMs: Long = 3_000,
) {
    data class Accepted(val code: String?, val price: Pesos)

    private var pendingPrice: Pesos? = null
    private var pendingAt = 0L
    private var acceptedPrice: Pesos? = null
    private var acceptedSeenAt = 0L
    private var lastCode: String? = null
    private var lastCodeAt = 0L

    /** Un cuadro con código y/o precio. Devuelve la etiqueta cuando el precio se confirma. */
    fun offer(code: String?, price: Pesos?): Accepted? {
        val now = clock.nowMillis()
        if (code != null) {
            lastCode = code
            lastCodeAt = now
        }
        price ?: return null
        if (price == acceptedPrice && now - acceptedSeenAt < forgetMs) {
            acceptedSeenAt = now
            return null
        }
        val previous = pendingPrice
        if (previous == price && now - pendingAt <= windowMs) {
            pendingPrice = null
            acceptedPrice = price
            acceptedSeenAt = now
            val recentCode = lastCode.takeIf { now - lastCodeAt <= windowMs }
            return Accepted(recentCode, price)
        }
        pendingPrice = price
        pendingAt = now
        return null
    }

    /** Al cambiar de modo o pausar: empieza de cero. */
    fun reset() {
        pendingPrice = null
        acceptedPrice = null
        lastCode = null
    }
}
