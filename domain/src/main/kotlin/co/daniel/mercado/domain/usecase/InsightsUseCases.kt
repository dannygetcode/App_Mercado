package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.CategorySpend
import co.daniel.mercado.domain.model.ClosedLine
import co.daniel.mercado.domain.model.ListSource
import co.daniel.mercado.domain.model.MonthSummary
import co.daniel.mercado.domain.model.MonthTotal
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceChange
import co.daniel.mercado.domain.model.ProductSpend
import co.daniel.mercado.domain.model.ShoppingListEntry
import co.daniel.mercado.domain.model.ShoppingListItem
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.model.sum
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.ShoppingListRepository
import co.daniel.mercado.domain.port.TransactionRunner
import co.daniel.mercado.domain.port.TripRepository
import co.daniel.mercado.domain.rules.Months
import co.daniel.mercado.domain.rules.ReminderRules
import co.daniel.mercado.domain.rules.SuggestionRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/**
 * RF-09: recalcula los sugeridos. Agrega o actualiza los que tocan (salvo los silenciados), quita
 * los automáticos que ya no tocan y los ítems marcados de compras ya cerradas.
 */
class BuildSuggestedList(
    private val trips: TripRepository,
    private val list: ShoppingListRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Int = transactions.inTransaction {
        val now = clock.nowMillis()
        val suggestions = SuggestionRules.suggest(trips.closedLines(0, now + 1), now).associateBy { it.productId }
        val current = list.getAll().associateBy { it.productId }
        current.values.forEach { item ->
            val stillSuggested = item.productId in suggestions
            when {
                item.checked -> list.delete(item.productId)
                item.source == ListSource.AUTO && !stillSuggested && (item.dismissedUntil ?: 0) <= now -> list.delete(item.productId)
            }
        }
        suggestions.values.forEach { s ->
            val existing = current[s.productId]
            val silenced = existing?.dismissedUntil?.let { now < it } == true
            when {
                silenced -> Unit
                existing == null || existing.checked || existing.dismissedUntil != null ->
                    list.upsert(ShoppingListItem(s.productId, s.quantity, checked = false, source = ListSource.AUTO))
                existing.source == ListSource.AUTO && existing.suggestedQty != s.quantity ->
                    list.upsert(existing.copy(suggestedQty = s.quantity))
            }
        }
        list.getAll().count { !it.checked && it.dismissedUntil == null }
    }
}

/** Lista para mostrar: activos primero sin marcar, con el motivo de cada sugerido. Sin los silenciados. */
class ObserveShoppingList(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val list: ShoppingListRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<List<ShoppingListEntry>> = list.observeAll().map { items ->
        val visible = items.filter { it.dismissedUntil == null }
        val now = clock.nowMillis()
        val reasons = if (visible.any { it.source == ListSource.AUTO }) {
            SuggestionRules.suggest(trips.closedLines(0, now + 1), now).associateBy { it.productId }
        } else {
            emptyMap()
        }
        val byId = products.getByIds(visible.map { it.productId }).associateBy { it.id }
        visible.mapNotNull { item ->
            val product = byId[item.productId] ?: return@mapNotNull null
            ShoppingListEntry(item, product, reasons[item.productId].takeIf { item.source == ListSource.AUTO })
        }.sortedWith(compareBy<ShoppingListEntry>({ it.item.checked }, { it.item.source != ListSource.AUTO }, { it.product.name }))
    }

    /** Pendientes de la lista que aún no están en la compra abierta (pestaña Lista de la compra). */
    fun pendingForTrip(): Flow<List<ShoppingListEntry>> =
        combine(invoke(), trips.observeActive()) { entries, trip ->
            val inTrip = trip?.items?.mapNotNull { it.productId }?.toSet().orEmpty()
            entries.filter { !it.item.checked && it.product.id !in inTrip && it.product.active }
        }
}

/** Marca o desmarca un ítem a mano (comprado por fuera de la app, por ejemplo). */
class ToggleListItem(private val list: ShoppingListRepository) {
    suspend operator fun invoke(productId: Long) {
        val item = list.getAll().firstOrNull { it.productId == productId } ?: return
        list.upsert(item.copy(checked = !item.checked))
    }
}

/** "Agregar producto" a la lista a mano. */
class AddListItem(private val list: ShoppingListRepository) {
    suspend operator fun invoke(productId: Long, quantity: Int = 1) {
        require(quantity >= 1) { "cantidad inválida" }
        list.upsert(ShoppingListItem(productId, quantity, checked = false, source = ListSource.MANUAL))
    }
}

/**
 * Quitar de la lista. Un sugerido se silencia hasta su próximo ciclo (hoy + intervalo medio);
 * uno agregado a mano se borra.
 */
class RemoveListItem(
    private val trips: TripRepository,
    private val list: ShoppingListRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(productId: Long) {
        val item = list.getAll().firstOrNull { it.productId == productId } ?: return
        if (item.source == ListSource.MANUAL) {
            list.delete(productId)
            return
        }
        val now = clock.nowMillis()
        val interval = SuggestionRules.suggest(trips.closedLines(0, now + 1), now)
            .firstOrNull { it.productId == productId }?.avgIntervalMillis ?: SuggestionRules.DAY_MS
        list.upsert(item.copy(dismissedUntil = now + interval))
    }
}

/** RF-13: tope mensual por categoría (nulo lo quita). */
class SetCategoryBudget(private val products: ProductRepository) {
    suspend operator fun invoke(categoryId: Long, amount: Pesos?) {
        amount?.let { require(it.isPositive && it <= Pesos.KEYPAD_MAX) { "tope fuera de rango" } }
        products.setCategoryBudget(categoryId, amount)
    }
}

/** Mayores subidas y bajadas de precio de productos comprados en el mes, frente a su compra anterior. */
class GetPriceChanges(
    private val trips: TripRepository,
    private val products: ProductRepository,
) {
    data class Changes(val ups: List<PriceChange>, val downs: List<PriceChange>)

    suspend operator fun invoke(month: YearMonth, limit: Int = 5): Changes {
        val end = Months.endMillisExclusive(month)
        val start = Months.startMillis(month)
        val lines = trips.closedLines(0, end).filter { it.productId != null && it.unitType == UnitType.UNIT }
        val changes = lines.groupBy { it.productId!! }.mapNotNull { (productId, l) ->
            val byTrip = l.groupBy { it.tripId }.map { (_, t) -> t.maxBy { it.unitPrice } }.sortedBy { it.closedAt }
            val current = byTrip.lastOrNull { it.closedAt >= start } ?: return@mapNotNull null
            val previous = byTrip.lastOrNull { it.closedAt < current.closedAt } ?: return@mapNotNull null
            if (previous.unitPrice == current.unitPrice || !previous.unitPrice.isPositive) return@mapNotNull null
            Triple(productId, previous.unitPrice, current.unitPrice)
        }
        val names = products.getByIds(changes.map { it.first }).associate { it.id to it.name }
        val all = changes.map { (id, prev, cur) -> PriceChange(id, names[id].orEmpty(), prev, cur) }
        return Changes(
            ups = all.filter { it.basisPoints > 0 }.sortedByDescending { it.basisPoints }.take(limit),
            downs = all.filter { it.basisPoints < 0 }.sortedBy { it.basisPoints }.take(limit),
        )
    }
}

/** RF-10 / RF-13: resumen del mes. Lo de categorías y productos sale de las líneas (estimado). */
class GetMonthSummary(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val getMonthStatus: GetMonthStatus,
    private val getPriceChanges: GetPriceChanges,
) {
    suspend operator fun invoke(month: YearMonth): MonthSummary {
        val lines = trips.closedLines(Months.startMillis(month), Months.endMillisExclusive(month))
        val byId = products.getByIds(lines.mapNotNull { it.productId }.toSet()).associateBy { it.id }
        val categories = products.categories()
        val other = categories.firstOrNull { it.name == Category.OTHER }
        val spentByCategory = lines.groupBy { line -> line.productId?.let { byId[it]?.categoryId } ?: other?.id }
            .mapValues { (_, l) -> l.map(ClosedLine::total).sum() }
        val categorySpend = categories.mapNotNull { c ->
            val spent = spentByCategory[c.id] ?: Pesos.ZERO
            if (!spent.isPositive && c.monthlyBudget == null) null else CategorySpend(c.id, c.name, spent, c.monthlyBudget)
        }.sortedByDescending { it.spent }
        val top = lines.filter { it.productId != null }.groupBy { it.productId!! }
            .map { (id, l) ->
                ProductSpend(id, byId[id]?.name.orEmpty(), l.map(ClosedLine::total).sum(), l.sumOf { if (it.unitType == UnitType.KG) 1 else it.quantity })
            }
            .sortedByDescending { it.spent }
            .take(TOP)
        val changes = getPriceChanges(month)
        return MonthSummary(getMonthStatus(month), categorySpend, top, changes.ups, changes.downs)
    }

    private companion object {
        const val TOP = 10
    }
}

/** Barras de los últimos [count] meses (el más viejo primero), terminando en [month]. */
class GetMonthsComparison(private val getMonthStatus: GetMonthStatus) {
    suspend operator fun invoke(month: YearMonth, count: Int = 6): List<MonthTotal> =
        (count - 1 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            val status = getMonthStatus(m)
            MonthTotal(m, status.spent, status.budget)
        }
}

/** RF-11: decide si toca el aviso del mercado y cuántos sugeridos hay. */
class ShouldRemindShopping(
    private val trips: TripRepository,
    private val buildSuggestedList: BuildSuggestedList,
    private val clock: Clock,
) {
    data class Reminder(val cycle: Long, val suggestedCount: Int)

    suspend operator fun invoke(frequencyDays: Int, lastReminderCycle: Long): Reminder? {
        val lastClosed = trips.lastClosedAt() ?: return null
        val remind = ReminderRules.shouldRemindShopping(lastClosed, frequencyDays, lastReminderCycle, clock.nowMillis())
        if (!remind || trips.getActive() != null) return null
        return Reminder(lastClosed, buildSuggestedList())
    }
}
