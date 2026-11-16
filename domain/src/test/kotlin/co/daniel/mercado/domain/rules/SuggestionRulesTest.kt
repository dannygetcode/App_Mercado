package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.ClosedLine
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionRulesTest {

    private val day = SuggestionRules.DAY_MS
    private val t0 = 1_800_000_000_000L

    private fun bought(trip: Long, atDay: Long, qty: Int = 1, product: Long = 1) =
        ClosedLine(trip, t0 + atDay * day, product, qty, Pesos(1_000), UnitType.UNIT)

    @Test
    fun `producto con una sola compra no se sugiere`() {
        assertTrue(SuggestionRules.suggest(listOf(bought(1, 0)), t0 + 100 * day).isEmpty())
    }

    @Test
    fun `compras cada 28 dias, ultima hace 24, se sugiere y hace 20 no`() {
        val lines = listOf(bought(1, 0), bought(2, 28), bought(3, 56))
        val s = SuggestionRules.suggest(lines, t0 + (56 + 24) * day).single()
        assertEquals(28, s.avgIntervalDays)
        assertEquals(24, s.daysSinceLast)
        assertTrue(SuggestionRules.suggest(lines, t0 + (56 + 20) * day).isEmpty())
    }

    @Test
    fun `cantidad sugerida es la mediana`() {
        val lines = listOf(bought(1, 0, 2), bought(2, 10, 5), bought(3, 20, 3))
        assertEquals(3, SuggestionRules.suggest(lines, t0 + 40 * day).single().quantity)
        assertEquals(2, SuggestionRules.median(listOf(1, 3)))
        assertEquals(5, SuggestionRules.median(listOf(1, 4, 6, 9)))
    }

    @Test
    fun `varias lineas de la misma compra cuentan como una compra`() {
        val lines = listOf(bought(1, 0, 1), bought(1, 0, 2), bought(2, 10, 3))
        val s = SuggestionRules.suggest(lines, t0 + 30 * day).single()
        assertEquals(10, s.avgIntervalDays)
        assertEquals(3, s.quantity)
    }

    @Test
    fun `aviso de mercado una vez por ciclo cuando falta un dia o menos`() {
        val closed = t0
        assertFalse(ReminderRules.shouldRemindShopping(closed, 15, 0, closed + 13 * day))
        assertTrue(ReminderRules.shouldRemindShopping(closed, 15, 0, closed + 14 * day))
        assertFalse(ReminderRules.shouldRemindShopping(closed, 15, closed, closed + 20 * day))
        assertFalse(ReminderRules.shouldRemindShopping(null, 15, 0, closed))
    }

    @Test
    fun `recordatorio de respaldo mensual`() {
        assertTrue(ReminderRules.shouldRemindBackup(0, 0, t0, hasData = true))
        assertFalse(ReminderRules.shouldRemindBackup(0, 0, t0, hasData = false))
        assertFalse(ReminderRules.shouldRemindBackup(t0 - 10 * day, 0, t0, hasData = true))
        assertFalse(ReminderRules.shouldRemindBackup(0, t0 - 5 * day, t0, hasData = true))
    }
}
