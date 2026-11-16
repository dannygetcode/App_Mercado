package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.ClosedLine
import co.daniel.mercado.domain.model.Suggestion
import co.daniel.mercado.domain.model.UnitType

/**
 * RF-09: un producto con ≥ 2 compras entra a la lista cuando
 * `días desde la última ≥ 0,8 × intervalo medio`. Cantidad sugerida = mediana de cantidades.
 * Aritmética entera en milisegundos.
 */
object SuggestionRules {

    const val DAY_MS = 86_400_000L

    fun suggest(lines: List<ClosedLine>, now: Long): List<Suggestion> =
        lines.filter { it.productId != null }
            .groupBy { it.productId!! }
            .mapNotNull { (productId, productLines) -> suggestion(productId, productLines, now) }
            .sortedBy { it.productId }

    private fun suggestion(productId: Long, lines: List<ClosedLine>, now: Long): Suggestion? {
        // Una compra = una compra cerrada, aunque el producto esté en varias líneas.
        val purchases = lines.groupBy { it.tripId }
            .map { (_, l) -> l.first().closedAt to l.sumOf { if (it.unitType == UnitType.KG) 1 else it.quantity } }
            .sortedBy { it.first }
        if (purchases.size < 2) return null
        val first = purchases.first().first
        val last = purchases.last().first
        val avg = (last - first) / (purchases.size - 1)
        if (avg <= 0) return null
        val since = now - last
        if (Math.multiplyExact(since, 10L) < Math.multiplyExact(avg, 8L)) return null
        return Suggestion(
            productId = productId,
            avgIntervalDays = ((avg + DAY_MS / 2) / DAY_MS).toInt(),
            daysSinceLast = (since / DAY_MS).toInt(),
            quantity = median(purchases.map { it.second }),
            lastPurchaseAt = last,
            avgIntervalMillis = avg,
        )
    }

    /** Mediana entera; con un número par de datos, el promedio de los dos del medio redondeado hacia arriba. */
    fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2] + 1) / 2
    }
}

/** RF-11 y recordatorio de respaldo: cuándo avisar. Una vez por ciclo. */
object ReminderRules {

    /**
     * Avisa cuando falta ≤ 1 día para cumplir la frecuencia desde la última compra cerrada.
     * [lastReminderCycle] guarda el `lastClosedAt` del ciclo ya avisado.
     */
    fun shouldRemindShopping(lastClosedAt: Long?, frequencyDays: Int, lastReminderCycle: Long, now: Long): Boolean {
        if (lastClosedAt == null || lastClosedAt == lastReminderCycle) return false
        val due = lastClosedAt + (frequencyDays - 1) * SuggestionRules.DAY_MS
        return now >= due
    }

    /** Recordatorio mensual de respaldo: más de 30 días desde el último (o nunca) y sin avisar en 30 días. */
    fun shouldRemindBackup(lastBackupAt: Long, lastBackupReminderAt: Long, now: Long, hasData: Boolean): Boolean {
        if (!hasData) return false
        val month = 30 * SuggestionRules.DAY_MS
        return now - lastBackupAt >= month && now - lastBackupReminderAt >= month
    }
}
