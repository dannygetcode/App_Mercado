package co.daniel.mercado.reminders

import co.daniel.mercado.data.settings.InMemoryPreferencesDataStore
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.domain.rules.SuggestionRules
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderCheckTest {

    private val t = TripGraph()
    private val settings = SettingsStore(InMemoryPreferencesDataStore(), secureScreenDefault = false)
    private val shopping = mutableListOf<Int>()
    private var backups = 0
    private val notifier = object : ReminderNotifier {
        override fun shopping(suggestedCount: Int) {
            shopping += suggestedCount
        }

        override fun backup() {
            backups++
        }
    }
    private val check = ReminderCheck({ t.graph }, settings, notifier, t.clock)
    private val day = SuggestionRules.DAY_MS

    @After
    fun tearDown() = t.close()

    private fun closedTripWith(productId: Long) = runBlocking {
        t.graph.startTrip(TripLimit.None)
        t.graph.addProductToTrip(productId)
        t.graph.closeTrip()
    }

    @Test
    fun `avisa el mercado una vez por ciclo con los sugeridos, y el respaldo una vez al mes`() = runBlocking {
        settings.setLastBackupAt(t.clock.now)
        val arroz = t.product("Arroz", 5_000)
        closedTripWith(arroz)
        t.clock.advance(15 * day)
        closedTripWith(arroz)
        t.clock.advance(13 * day)
        check.run()
        assertEquals(emptyList<Int>(), shopping)

        t.clock.advance(1 * day)
        check.run()
        check.run()
        assertEquals(listOf(1), shopping)
        assertEquals(0, backups)

        t.clock.advance(2 * day)
        check.run()
        assertEquals(1, backups)
        t.clock.advance(20 * day)
        check.run()
        assertEquals(1, backups)
        t.clock.advance(11 * day)
        check.run()
        assertEquals(2, backups)
        assertEquals(listOf(1), shopping)
    }

    @Test
    fun `con los recordatorios apagados no avisa nada`() = runBlocking {
        settings.setRemindersEnabled(false)
        val arroz = t.product("Arroz", 5_000)
        closedTripWith(arroz)
        t.clock.advance(40 * day)
        check.run()
        assertEquals(emptyList<Int>(), shopping)
        assertEquals(0, backups)
    }
}
