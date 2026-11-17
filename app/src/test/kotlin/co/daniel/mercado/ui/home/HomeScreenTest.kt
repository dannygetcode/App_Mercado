package co.daniel.mercado.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.ui.theme.MercadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val nov = YearMonth.of(2026, 11)
    private val events = mutableListOf<HomeEvent>()

    private fun show(state: HomeUiState, onOpenTrip: (Long) -> Unit = {}, onOpenReview: () -> Unit = {}) =
        compose.setContent { MercadoTheme { HomeScreen(state, { events += it }, onOpenTrip, onOpenReview) } }

    @Test
    fun `muestra el mes con gastado, presupuesto y saldo, e Ir a mercar`() {
        show(HomeUiState(loading = false, month = MonthStatus(nov, Pesos(900_000), Pesos(612_000), Pesos(288_000), TrafficLight.GREEN)))

        compose.onNodeWithText("Gastado en Noviembre").assertIsDisplayed()
        compose.onNodeWithText("$612.000").assertIsDisplayed()
        compose.onNodeWithText("de $900.000 de presupuesto").assertIsDisplayed()
        compose.onNodeWithText("Quedan $288.000").assertIsDisplayed()
        compose.onNodeWithText("Ir a mercar").performClick()
        assertEquals(listOf<HomeEvent>(HomeEvent.StartOrContinue), events)
    }

    @Test
    fun `con compra abierta ofrece continuarla con su total`() {
        show(HomeUiState(loading = false, openTripTotal = Pesos(187_400)))
        compose.onNodeWithText("Continuar compra · $187.400").assertIsDisplayed()
    }

    @Test
    fun `primer uso sin presupuesto muestra la tarjeta omitible`() {
        show(HomeUiState(loading = false, month = MonthStatus(nov, null, Pesos.ZERO, null, TrafficLight.NONE), budgetHintDismissed = false))

        compose.onNodeWithText("Define tu presupuesto del mes").assertIsDisplayed()
        compose.onNodeWithText("Ahora no").performClick()
        compose.onNodeWithText("Definir").performClick()
        assertEquals(listOf(HomeEvent.DismissBudgetHint, HomeEvent.EditBudget), events)
    }

    @Test
    fun `tarjetas de ultima compra y productos por revisar navegan`() {
        var openedTrip: Long? = null
        var openedReview = false
        val trip = Trip(id = 7, startedAt = 0, closedAt = 1_763_300_000_000, store = "La Granja", totalEstimated = Pesos(497_870))
        show(
            HomeUiState(loading = false, lastTrip = TripSummary(trip, 57), needsReviewCount = 2),
            onOpenTrip = { openedTrip = it },
            onOpenReview = { openedReview = true },
        )

        compose.onNodeWithText("$497.870").performClick()
        compose.onNodeWithText("Productos por revisar · 2").performClick()
        assertEquals(7L, openedTrip)
        assertEquals(true, openedReview)
    }

    @Test
    fun `la hoja de limite muestra lo que queda y Empezar lo entrega`() {
        show(HomeUiState(loading = false, sheet = HomeSheet.Limit(Pesos(250_000))))

        compose.onNodeWithText("Límite de esta compra").assertIsDisplayed()
        compose.onNodeWithText("Empezar").performClick()
        assertEquals(listOf<HomeEvent>(HomeEvent.StartTrip(Pesos(250_000))), events)
    }
}
