package co.daniel.mercado.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.rules.BudgetRules
import co.daniel.mercado.ui.theme.MercadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `teclado 1 2 000 muestra 12000 y sumar entrega el valor`() {
        var submitted: Pesos? = null
        compose.setContent {
            MercadoTheme {
                var state by remember { mutableStateOf(KeypadState()) }
                PriceKeypad(state, { state = it }, { submitted = it })
            }
        }
        compose.onNodeWithText("Sumar").assertIsNotEnabled()
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("000").performClick()
        compose.onNodeWithTag("keypad_value").assertTextEquals("$12.000")
        compose.onNodeWithText("Sumar").assertIsEnabled().performClick()
        assertEquals(Pesos(12_000), submitted)
    }

    @Test
    fun `teclado borrar`() {
        compose.setContent {
            MercadoTheme {
                var state by remember { mutableStateOf(KeypadState("125")) }
                PriceKeypad(state, { state = it }, {})
            }
        }
        compose.onNodeWithText("⌫").performClick()
        compose.onNodeWithTag("keypad_value").assertTextEquals("$12")
    }

    private fun model(total: Long, limit: Long?): TotalCardModel {
        val t = Pesos(total)
        val l = limit?.let(::Pesos)
        return TotalCardModel(
            total = t,
            articleCount = 3,
            limit = l,
            remaining = l?.let { it - t },
            trafficLight = BudgetRules.trafficLight(t, l),
            percentOfLimit = BudgetRules.percentOf(t, l),
        )
    }

    @Test
    fun `semaforo cambia de color al cruzar 80 y 100 por ciento`() {
        var m by mutableStateOf(model(79_000, 100_000))
        compose.setContent { MercadoTheme { TotalCard(m) } }
        compose.onNodeWithTag("traffic_GREEN").assertIsDisplayed()
        compose.onNodeWithText("Quedan $21.000 de $100.000").assertIsDisplayed()

        m = model(80_000, 100_000)
        compose.onNodeWithTag("traffic_YELLOW").assertIsDisplayed()

        m = model(100_000, 100_000)
        compose.onNodeWithTag("traffic_YELLOW").assertIsDisplayed()

        m = model(112_000, 100_000)
        compose.onNodeWithTag("traffic_RED").assertIsDisplayed()
        compose.onNodeWithText("Te pasaste por $12.000").assertIsDisplayed()
    }

    @Test
    fun `sin limite solo total y articulos`() {
        compose.setContent { MercadoTheme { TotalCard(model(5_900, null)) } }
        compose.onNodeWithTag("trip_total").assertTextEquals("$5.900")
        compose.onNodeWithText("3 artíc.").assertIsDisplayed()
        compose.onNodeWithTag("traffic_" + TrafficLight.NONE.name).assertDoesNotExist()
    }

    @Test
    fun `linea suelta se muestra como articulo sin nombre y sus botones responden`() {
        var plus = 0
        var minus = 0
        compose.setContent {
            MercadoTheme {
                LineRow(
                    TripItem(1, 1, null, "", quantity = 2, unitPrice = Pesos(1_500), addedAt = 0, position = 0),
                    onDecrement = { minus++ }, onIncrement = { plus++ }, onPriceClick = {}, onNameClick = {},
                )
            }
        }
        compose.onNodeWithText("Artículo sin nombre").assertIsDisplayed()
        compose.onNodeWithText("$1.500 c/u").assertIsDisplayed()
        compose.onNodeWithText("$3.000").assertIsDisplayed()
        compose.onNodeWithTag("increment").performClick()
        compose.onNodeWithTag("decrement").performClick()
        assertEquals(1, plus)
        assertEquals(1, minus)
    }
}
