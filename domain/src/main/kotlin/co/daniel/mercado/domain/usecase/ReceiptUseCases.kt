package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.TripRepository
import co.daniel.mercado.domain.rules.NameNormalizer
import co.daniel.mercado.domain.rules.NameSimilarity
import co.daniel.mercado.domain.rules.ReceiptLine
import java.util.Locale

/** Qué cambió en la compra al aplicar el recibo. */
data class ApplyReceiptResult(
    /** Líneas de la compra cuyo precio o cantidad se corrigió con el recibo. */
    val corrected: Int,
    /** Renglones del recibo que no estaban en la compra y se agregaron. */
    val added: Int,
    /** De esos, cuántos crearon un producto nuevo (queda "por revisar" para nombrarlo). */
    val createdProducts: Int,
    /** Líneas de la compra que no aparecen en el recibo (no se tocan). */
    val notOnReceipt: Int,
)

/**
 * Aplica los renglones leídos del recibo a la compra abierta, al cerrarla (pedido de Daniel,
 * 2026-11-20; RF-15 en su versión de cierre). Una transacción:
 *
 * - Renglones iguales del recibo (mismo nombre y precio) se suman en uno.
 * - Cada renglón busca su línea en la compra: primero por código de barras, luego por nombre
 *   parecido (con un empujón si el precio se parece). Si la encuentra, la línea queda con el precio
 *   y la cantidad del recibo.
 * - Si no, se agrega una línea: con el producto del catálogo de ese código o nombre, o con uno nuevo
 *   en "Otros" marcado por revisar (se nombra en la misma pantalla de cierre).
 * - Las líneas de la compra que no salen en el recibo se dejan como están.
 *
 * El recibo no se guarda: solo sus valores pasan a la compra.
 */
class ApplyReceipt(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val session: TripSession,
    private val clock: Clock,
) {
    suspend operator fun invoke(lines: List<ReceiptLine>): ApplyReceiptResult? {
        val merged = merge(lines)
        return session.mutate {
            val active = trips.getActive() ?: return@mutate null
            session.undo.clear()
            val now = clock.nowMillis()
            var catalog: List<Product>? = null
            val unmatched = active.items.toMutableList()
            var corrected = 0
            var added = 0
            var created = 0
            val pending = mutableListOf<ReceiptLine>()

            for (line in merged) {
                val codeOwner = line.code?.let { products.barcodeOwner(it) }
                val match = codeOwner?.let { owner -> unmatched.firstOrNull { it.productId == owner } }
                    ?: bestTripMatch(line, unmatched)
                if (match != null) {
                    unmatched.remove(match)
                    val quantity = if (match.unitType == UnitType.KG) 1 else line.effectiveQuantity
                    val price = if (match.unitType == UnitType.KG) line.amount else line.unitPrice
                    if (match.quantity != quantity || match.unitPrice != price) {
                        trips.updateItem(match.copy(quantity = quantity, unitPrice = price))
                        corrected++
                    }
                } else {
                    pending += line
                }
            }

            for (line in pending) {
                val product = line.code?.let { code -> products.barcodeOwner(code)?.let { products.getById(it) } }
                    ?: products.findByNormalizedName(NameNormalizer.normalize(line.name))
                    ?: bestCatalogMatch(line, catalog ?: products.all().also { catalog = it })
                    ?: createProduct(line, now).also { created++ }
                if (line.code != null && products.barcodeOwner(line.code) == null) {
                    products.linkBarcode(line.code, product.id, now)
                }
                val kg = product.unitType == UnitType.KG
                trips.insertItem(
                    TripItem(
                        tripId = active.trip.id,
                        productId = product.id,
                        nameSnapshot = product.name,
                        unitType = product.unitType,
                        quantity = if (kg) 1 else line.effectiveQuantity,
                        unitPrice = if (kg) line.amount else line.unitPrice,
                        addedAt = now,
                        position = trips.nextPosition(active.trip.id),
                    ),
                )
                added++
            }
            ApplyReceiptResult(corrected, added, created, unmatched.size)
        }
    }

    private fun bestTripMatch(line: ReceiptLine, candidates: List<TripItem>): TripItem? =
        candidates
            .map { item -> item to score(line, item.nameSnapshot, if (item.unitType == UnitType.KG) line.amount else line.unitPrice, item.unitPrice) }
            .filter { it.second >= MATCH }
            .maxByOrNull { it.second }
            ?.first

    private fun bestCatalogMatch(line: ReceiptLine, catalog: List<Product>): Product? =
        catalog
            .filter { it.active }
            .map { it to score(line, it.name, line.unitPrice, it.refPrice) }
            .filter { it.second >= CATALOG_MATCH }
            .maxByOrNull { it.second }
            ?.first

    /** Parecido del nombre, con un empujón si el precio está a menos de 30 %. */
    private fun score(line: ReceiptLine, name: String, receiptPrice: Pesos, knownPrice: Pesos): Int {
        val similarity = NameSimilarity.of(line.name, name)
        val close = knownPrice.isPositive &&
            kotlin.math.abs(receiptPrice.value - knownPrice.value) * 100 <= knownPrice.value * 30
        return similarity + if (close) PRICE_BONUS else 0
    }

    private suspend fun createProduct(line: ReceiptLine, now: Long): Product {
        val categoryId = products.categoryByName(Category.OTHER)?.id
            ?: products.insertCategory(Category(id = 0, name = Category.OTHER, sortOrder = Int.MAX_VALUE))
        val draft = Product(
            name = uniqueName(prettify(line.name)),
            categoryId = categoryId,
            unitType = UnitType.UNIT,
            refPrice = line.unitPrice,
            needsReview = true,
            createdAt = now,
            updatedAt = now,
        )
        return draft.copy(id = products.insert(draft))
    }

    private suspend fun uniqueName(base: String): String {
        if (products.findByNormalizedName(NameNormalizer.normalize(base)) == null) return base
        var n = 2
        while (products.findByNormalizedName(NameNormalizer.normalize("$base ($n)")) != null) n++
        return "$base ($n)"
    }

    private companion object {
        /** Umbrales en por ciento de parecido de nombre. */
        const val MATCH = 55
        const val CATALOG_MATCH = 70
        const val PRICE_BONUS = 15
        const val MAX_NAME = 80

        /** "LECHE ALQUERIA ENT 1100ML" → "Leche alqueria ent 1100ml". */
        fun prettify(name: String): String =
            name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }.take(MAX_NAME)

        /** Mismo nombre y mismo precio unitario = el mismo producto pasado dos veces por la caja. */
        fun merge(lines: List<ReceiptLine>): List<ReceiptLine> =
            lines.groupBy { NameSimilarity.compact(it.name) to it.unitPrice }
                .values
                .map { group ->
                    val first = group.first()
                    if (group.size == 1) {
                        first
                    } else {
                        val qty = group.sumOf { it.effectiveQuantity }
                        ReceiptLine(first.name, qty, Pesos(first.unitPrice.value * qty), group.firstNotNullOfOrNull { it.code })
                    }
                }
    }
}
