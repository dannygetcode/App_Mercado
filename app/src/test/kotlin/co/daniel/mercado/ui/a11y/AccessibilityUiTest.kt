package co.daniel.mercado.ui.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.ui.catalog.CatalogRow
import co.daniel.mercado.ui.catalog.CatalogScreen
import co.daniel.mercado.ui.catalog.CatalogUiState
import co.daniel.mercado.ui.components.LineRow
import co.daniel.mercado.ui.history.HistoryScreen
import co.daniel.mercado.ui.history.HistoryUiState
import co.daniel.mercado.ui.home.HomeScreen
import co.daniel.mercado.ui.home.HomeUiState
import co.daniel.mercado.ui.settings.SettingsScreen
import co.daniel.mercado.ui.settings.SettingsUiState
import co.daniel.mercado.ui.theme.MercadoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** RNF-03 / RNF-11: tema oscuro, letra del sistema a 1,3×, descripciones y objetivos de 48 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showDarkLargeFont(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
            MercadoTheme(darkTheme = true) { content() }
        }
    }

    @Test
    fun `inicio en oscuro con letra grande deja el boton principal visible y grande`() {
        showDarkLargeFont {
            HomeScreen(
                HomeUiState(loading = false, month = MonthStatus(YearMonth.of(2026, 11), Pesos(900_000), Pesos(612_000), Pesos(288_000), TrafficLight.GREEN)),
                {}, {}, {},
            )
        }
        compose.onNodeWithText("Ir a mercar").assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Quedan $288.000").assertIsDisplayed()
    }

    @Test
    fun `los iconos del catalogo dicen lo que son`() {
        val rows = listOf(
            CatalogRow(Product(1, "Arroz", 1, refPrice = Pesos(5_900), favorite = true), "Granos"),
            CatalogRow(Product(2, "Leche", 1, refPrice = Pesos(4_300), favorite = true), "Lácteos"),
        )
        showDarkLargeFont { CatalogScreen(CatalogUiState(loading = false, rows = rows), {}, {}, {}) }
        compose.onAllNodesWithContentDescription("Favorito", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("Nuevo producto", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `ordenar favoritos tiene botones nombrados de 48 dp`() {
        val rows = listOf(CatalogRow(Product(1, "Arroz", 1, refPrice = Pesos(5_900), favorite = true), "Granos"))
        showDarkLargeFont { CatalogScreen(CatalogUiState(loading = false, filter = CatalogFilter(favoritesOnly = true), rows = rows), {}, {}, {}) }
        compose.onNodeWithContentDescription("Subir Arroz").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Bajar Arroz").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `los botones de cantidad de una linea dicen de que producto son`() {
        val item = TripItem(1, 1, 1, "Arroz Diana", quantity = 2, unitPrice = Pesos(5_900), addedAt = 0, position = 0)
        showDarkLargeFont { LineRow(item = item, onNameClick = {}, onPriceClick = {}, onIncrement = {}, onDecrement = {}) }
        compose.onNodeWithContentDescription("Agregar uno de Arroz Diana").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Quitar uno de Arroz Diana").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `estados vacios de historial y ajustes sin datos`() {
        showDarkLargeFont { HistoryScreen(HistoryUiState(loading = false), {}) }
        compose.onNodeWithText("Aún no hay compras cerradas. Cuando cierres una, aparece aquí.").assertIsDisplayed()
    }

    @Test
    fun `ajustes muestra la nota de sin internet`() {
        showDarkLargeFont { SettingsScreen(SettingsUiState(), {}) }
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
        compose.onNodeWithText("Esta app no tiene acceso a internet").assertExists()
    }
}
