package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos

/** Un trozo de texto leído por la cámara con su caja (coordenadas de la imagen ya derecha). */
data class TextBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/** Un renglón de producto del recibo: nombre como viene impreso, cantidad y valor total del renglón. */
data class ReceiptLine(
    val name: String,
    val quantity: Int,
    val amount: Pesos,
    /** Código de barras impreso en el renglón (ya normalizado por `Ean`), si trae. */
    val code: String? = null,
) {
    /** Precio por unidad si la división es exacta; si no, el renglón se trata como 1 unidad. */
    val unitPrice: Pesos get() = if (quantity > 1 && amount.value % quantity == 0L) Pesos(amount.value / quantity) else amount

    val effectiveQuantity: Int get() = if (quantity > 1 && amount.value % quantity == 0L) quantity else 1
}

/** Lo que se sacó de un cuadro (o de todo el recibo): renglones de producto y el total a pagar. */
data class ReceiptScan(val lines: List<ReceiptLine>, val total: Pesos?)

/**
 * Arma renglones con los trozos de texto de un cuadro: en los recibos el nombre y el valor suelen
 * llegar como bloques separados a la misma altura. Se agrupan por solapamiento vertical y se leen
 * de izquierda a derecha.
 */
object ReceiptRows {

    fun group(boxes: List<TextBox>): List<String> {
        val rows = mutableListOf<MutableList<TextBox>>()
        boxes.filter { it.text.isNotBlank() && it.height > 0 }.sortedBy { it.centerY }.forEach { box ->
            val row = rows.lastOrNull()
            val ref = row?.let { r -> r.sumOf { it.centerY } / r.size }
            val tolerance = row?.let { r -> (r.sumOf { it.height } / r.size) / 2 }
            if (row != null && ref != null && tolerance != null && kotlin.math.abs(box.centerY - ref) <= maxOf(tolerance, box.height / 2)) {
                row += box
            } else {
                rows += mutableListOf(box)
            }
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }
}

/**
 * Lee los renglones de un recibo de supermercado colombiano (Éxito, D1, Ara, Olímpica…): nombre a
 * la izquierda y valor a la derecha, a veces con el código delante, la letra del impuesto detrás y
 * la cantidad en un renglón aparte ("2 X 5.900"). Descarta subtotales, impuestos, pagos y
 * encabezados. Los descuentos se restan al producto anterior.
 */
object ReceiptParser {

    const val MIN_AMOUNT = 50L
    const val MAX_AMOUNT = 5_000_000L

    /** Valor al final del renglón con separador de miles: "$ 5.900", "11,800 A", "-1.200", "5.900,00". */
    private val SEPARATED_AMOUNT = Regex(
        """(-)?\s*\$?\s*(\d{1,3}(?:[.,]\d{3})+)(?:[.,]0{2})?\s*[A-Za-z*]{0,2}\s*(-)?$""",
    )

    /**
     * Valor sin separador ("5900"): solo si viene en su propia columna (separado por dos espacios o
     * con "$"), para no confundir "ARROZ 500" o "LECHE 1100ML" con un precio.
     */
    private val PLAIN_AMOUNT = Regex("""(?:\s{2,}|\$\s*)(-)?(\d{3,7})(?:\s+[A-Za-z*]{1,2})?\s*(-)?$""")
    private val QTY_PRICE = Regex("""(?:^|\s)(\d{1,3})\s*(?:un|und|u|uds)?\s*[x*]\s*\$?\s*(\d{1,3}(?:[.,]\d{3})+|\d{3,7})""", RegexOption.IGNORE_CASE)
    private val WEIGHT = Regex("""\d+[.,]\d{1,3}\s*(?:kg|kl|k)\b""", RegexOption.IGNORE_CASE)
    private val LETTERS = Regex("""\p{L}""")
    private val DIGIT_TOKEN = Regex("""^\d+$""")
    private val SPACES = Regex("""\s+""")

    /** Primera palabra de renglones que no son productos (normalizada, sin tildes). */
    private val SKIP_FIRST = setOf(
        "subtotal", "sub", "iva", "impuesto", "impuestos", "impoconsumo", "base", "cambio", "efectivo", "tarjeta",
        "recibido", "debito", "credito", "nit", "factura", "tel", "telefono", "cajero", "resolucion", "items",
        "articulos", "redondeo", "propina", "pago", "vueltas", "fecha", "hora", "cufe", "datafono", "nequi",
        "daviplata", "puntos", "cliente", "regimen", "cc", "saldo", "forma", "medio", "nro", "no", "total",
    )

    /** En cualquier parte del renglón: datos legales o de pago, nunca productos. */
    private val SKIP_ANYWHERE = listOf("resolucion", "autoriz", "cufe", "nit:", "nit ", "www.", "gracias")
    private val DISCOUNT_FIRST = setOf("descuento", "dcto", "desc", "rebaja", "ahorro", "dto")
    private val TOTAL_KEYS = listOf("total a pagar", "valor a pagar", "neto a pagar", "total pagar", "total")

    fun parse(rows: List<String>): ReceiptScan {
        val lines = mutableListOf<ReceiptLine>()
        var pendingName: String? = null
        var pendingQty: Int? = null
        val totals = mutableListOf<Pair<Pesos, Boolean>>()

        for (raw in rows) {
            val row = raw.trim()
            if (row.isEmpty()) continue
            val norm = NameNormalizer.normalize(row)
            val first = norm.substringBefore(' ').trim(':', '.', '-', '*')
            val amountMatch = SEPARATED_AMOUNT.find(row) ?: PLAIN_AMOUNT.find(row)
            val amount = amountMatch?.let { parseAmount(it.groupValues[2]) }
            val negative = amountMatch != null && (amountMatch.groupValues[1].isNotEmpty() || amountMatch.groupValues[3].isNotEmpty())

            if (isTotal(norm)) {
                if (amount != null) totals += amount to norm.contains("pagar")
                pendingName = null
                continue
            }
            if (first in DISCOUNT_FIRST || (negative && amount != null)) {
                if (amount != null) {
                    lines.lastOrNull()?.takeIf { it.amount.value > amount.value }?.let {
                        lines[lines.lastIndex] = it.copy(amount = Pesos(it.amount.value - amount.value))
                    }
                }
                pendingName = null
                continue
            }
            if (first in SKIP_FIRST || SKIP_ANYWHERE.any { norm.contains(it) }) {
                pendingName = null
                continue
            }

            val head = (if (amountMatch != null) row.substring(0, amountMatch.range.first) else row).replace(SPACES, " ")
            val qtyMatch = QTY_PRICE.find(head)
            val qty = qtyMatch?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..99 }
            val nameText = head
                .let { h -> qtyMatch?.let { h.removeRange(it.range) } ?: h }
                .replace(WEIGHT, " ")
            val (code, name) = cleanName(nameText)

            when {
                amount == null -> {
                    // Renglón sin valor: nombre de un producto cuyo valor viene en el siguiente
                    // ("2 X 5.900   11.800"), o una cantidad suelta.
                    if (name != null) {
                        pendingName = name
                        pendingQty = qty
                    } else if (qty != null) {
                        pendingQty = qty
                    }
                }
                name != null -> {
                    lines += ReceiptLine(name, qty ?: 1, amount, code)
                    pendingName = null
                    pendingQty = null
                }
                pendingName != null -> {
                    lines += ReceiptLine(pendingName, qty ?: pendingQty ?: 1, amount, code)
                    pendingName = null
                    pendingQty = null
                }
            }
        }
        val total = totals.lastOrNull { it.second }?.first ?: totals.maxByOrNull { it.first.value }?.first
        return ReceiptScan(lines, total)
    }

    private fun isTotal(norm: String): Boolean =
        TOTAL_KEYS.any { norm.startsWith(it) } && !norm.startsWith("total items") && !norm.startsWith("total articulos") &&
            !norm.startsWith("total iva") && !norm.startsWith("total ahorro") && !norm.startsWith("total descuento")

    /** Quita códigos y números sueltos del inicio; devuelve el código EAN válido (si hay) y el nombre. */
    private fun cleanName(text: String): Pair<String?, String?> {
        val tokens = text.trim().split(' ').filter { it.isNotBlank() }.toMutableList()
        var code: String? = null
        while (tokens.isNotEmpty() && DIGIT_TOKEN.matches(tokens.first())) {
            val t = tokens.removeAt(0)
            if (t.length >= 8) code = code ?: Ean.normalize(t)
        }
        val name = tokens.joinToString(" ").trim(' ', '-', '.', '*', ':', '$')
        val letters = LETTERS.findAll(name).count()
        return code to name.takeIf { letters >= 3 }
    }

    private fun parseAmount(text: String): Pesos? {
        val digits = text.replace(".", "").replace(",", "")
        val value = digits.toLongOrNull() ?: return null
        return Pesos(value).takeIf { value in MIN_AMOUNT..MAX_AMOUNT }
    }
}

/** Parecido entre dos nombres en por ciento (0..100): coeficiente de Dice sobre pares de letras, sin tildes ni espacios. */
object NameSimilarity {

    private val NON_ALNUM = Regex("""[^a-z0-9]""")

    fun of(a: String, b: String): Int {
        val x = bigrams(a)
        val y = bigrams(b)
        if (x.isEmpty() || y.isEmpty()) return 0
        val remaining = y.toMutableList()
        var shared = 0
        x.forEach { if (remaining.remove(it)) shared++ }
        return 200 * shared / (x.size + y.size)
    }

    fun compact(name: String): String = NameNormalizer.normalize(name).replace(NON_ALNUM, "")

    private fun bigrams(s: String): List<String> = compact(s).windowed(2)
}

/**
 * Junta lo leído en muchos cuadros mientras la cámara recorre el recibo. Un renglón cuenta cuando se
 * vio en al menos [minFrames] cuadros (el ruido del OCR rara vez se repite). Las variantes de un mismo
 * renglón (mismo valor, nombre parecido) se funden; si un renglón aparece repetido en el mismo cuadro
 * se conserva cuántas veces.
 */
class ReceiptAccumulator(private val minFrames: Int = 2) {

    private class Entry(var name: String, val amount: Pesos, var quantity: Int, var code: String?, val order: Long) {
        val names = mutableMapOf<String, Int>()
        var frames = 0
        var maxPerFrame = 0
    }

    private val entries = mutableListOf<Entry>()
    private val totals = mutableMapOf<Pesos, Int>()
    private var frame = 0L

    @Synchronized
    fun add(scan: ReceiptScan) {
        frame++
        val seenThisFrame = mutableMapOf<Entry, Int>()
        scan.lines.forEachIndexed { i, line ->
            val entry = entries.firstOrNull { e ->
                e.amount == line.amount && (seenThisFrame[e] ?: 0) < e.maxPerFrame.coerceAtLeast(1) &&
                    NameSimilarity.of(e.name, line.name) >= SAME_LINE
            } ?: entries.firstOrNull { e ->
                e.amount == line.amount && NameSimilarity.of(e.name, line.name) >= SAME_LINE
            } ?: Entry(line.name, line.amount, line.quantity, line.code, frame * 1000 + i).also { entries += it }
            entry.names.merge(line.name, 1, Int::plus)
            entry.name = entry.names.maxBy { it.value }.key
            if (line.code != null) entry.code = line.code
            if (line.quantity > entry.quantity) entry.quantity = line.quantity
            seenThisFrame.merge(entry, 1, Int::plus)
        }
        seenThisFrame.forEach { (e, n) ->
            e.frames++
            if (n > e.maxPerFrame) e.maxPerFrame = n
        }
        scan.total?.let { totals.merge(it, 1, Int::plus) }
    }

    /** Renglones confirmados, en el orden en que aparecieron (de arriba abajo del recibo). */
    @Synchronized
    fun lines(): List<ReceiptLine> = entries
        .filter { it.frames >= minFrames }
        .sortedBy { it.order }
        .flatMap { e -> List(e.maxPerFrame.coerceAtLeast(1)) { ReceiptLine(e.name, e.quantity, e.amount, e.code) } }

    /** El total más visto (al menos [minFrames] veces). */
    @Synchronized
    fun total(): Pesos? = totals.filter { it.value >= minFrames }.maxByOrNull { it.value }?.key

    @Synchronized
    fun reset() {
        entries.clear()
        totals.clear()
        frame = 0
    }

    private companion object {
        const val SAME_LINE = 60
    }
}
