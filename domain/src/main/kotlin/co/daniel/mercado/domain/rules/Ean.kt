package co.daniel.mercado.domain.rules

/**
 * Validación y normalización de códigos leídos por el escáner.
 *
 * - Solo dígitos: debe ser EAN-13, UPC-A (12), EAN-8 o UPC-E (8) con dígito de control correcto.
 *   UPC-A se normaliza a EAN-13 anteponiendo `0`.
 * - Con algún carácter no numérico: Code 128 si tiene 4–32 caracteres ASCII imprimibles y no tiene
 *   longitud de EAN/UPC (8, 12 o 13), para no aceptar un EAN con letras.
 */
object Ean {

    private val EAN_LENGTHS = setOf(8, 12, 13)

    fun isValid(code: String): Boolean = normalize(code) != null

    /** Devuelve el código normalizado o `null` si no es aceptable. */
    fun normalize(code: String): String? {
        val c = code.trim()
        if (c.isEmpty()) return null
        return if (c.all { it in '0'..'9' }) normalizeNumeric(c) else normalizeCode128(c)
    }

    private fun normalizeNumeric(c: String): String? = when (c.length) {
        13 -> c.takeIf { hasValidCheckDigit(it) }
        12 -> "0$c".takeIf { hasValidCheckDigit(it) }
        8 -> c.takeIf { hasValidCheckDigit(it) || isValidUpcE(it) }
        else -> null
    }

    private fun normalizeCode128(c: String): String? {
        if (c.length !in 4..32 || c.length in EAN_LENGTHS) return null
        return c.takeIf { s -> s.all { it.code in 0x20..0x7E } }
    }

    /** Dígito de control GTIN (EAN-8, EAN-13, UPC-A): pesos 3 y 1 desde la derecha. */
    private fun hasValidCheckDigit(digits: String): Boolean {
        val body = digits.dropLast(1)
        var sum = 0
        body.reversed().forEachIndexed { i, ch -> sum += (ch - '0') * if (i % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }

    private fun isValidUpcE(code: String): Boolean {
        val numberSystem = code[0]
        if (numberSystem != '0' && numberSystem != '1') return false
        val d = code.substring(1, 7)
        val (manufacturer, product) = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}00" to "00${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00" to "000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}0" to "0000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}" to "0000${d[5]}"
        }
        return hasValidCheckDigit("$numberSystem$manufacturer$product${code[7]}")
    }
}
