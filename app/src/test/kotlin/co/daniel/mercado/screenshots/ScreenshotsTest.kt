package co.daniel.mercado.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.daniel.mercado.R
import co.daniel.mercado.data.settings.InMemoryPreferencesDataStore
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.scanner.FakeBarcodeSource
import co.daniel.mercado.ui.catalog.CatalogScreen
import co.daniel.mercado.ui.catalog.CatalogViewModel
import co.daniel.mercado.ui.catalog.ProductEditScreen
import co.daniel.mercado.ui.catalog.ProductEditViewModel
import co.daniel.mercado.ui.catalog.await
import co.daniel.mercado.ui.components.KeypadState
import co.daniel.mercado.ui.components.PriceKeypad
import co.daniel.mercado.ui.diagnostics.loadSampleMonths
import co.daniel.mercado.ui.history.HistoryScreen
import co.daniel.mercado.ui.history.HistoryViewModel
import co.daniel.mercado.ui.home.HomeScreen
import co.daniel.mercado.ui.home.HomeViewModel
import co.daniel.mercado.ui.list.ShoppingListScreen
import co.daniel.mercado.ui.list.ShoppingListViewModel
import co.daniel.mercado.ui.lock.LockScreen
import co.daniel.mercado.ui.settings.SettingsScreen
import co.daniel.mercado.ui.settings.SettingsViewModel
import co.daniel.mercado.ui.summary.SummaryScreen
import co.daniel.mercado.ui.summary.SummaryViewModel
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.trip.RecordingFeedback
import co.daniel.mercado.ui.trip.TripGraph
import co.daniel.mercado.ui.trip.TripScreen
import co.daniel.mercado.ui.trip.TripViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de pantalla para el README y la guía (no son pruebas de comportamiento). Dibujan cada
 * pantalla con datos de ejemplo, nunca con datos reales, y guardan PNG en `app/build/screenshots`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val t = TripGraph()
    private val g get() = t.graph
    private val vms = mutableListOf<ViewModel>()
    private val settings = SettingsStore(InMemoryPreferencesDataStore(), secureScreenDefault = false)
    private val products = mutableMapOf<String, Long>()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        t.clock.setBogota(2026, 11, 16, 10)
        runBlocking {
            listOf(
                "Arroz Diana 1 kg" to 5_900, "Leche entera Alquería 1 L" to 4_300, "Huevos AA x 30" to 18_900,
                "Café Sello Rojo 500 g" to 21_500, "Pan tajado Bimbo" to 7_200, "Aceite Premier 1 L" to 13_800,
                "Atún Van Camps" to 6_400, "Papel higiénico Familia x 12" to 24_900, "Jabón Rey" to 3_100,
                "Detergente Ariel 2 kg" to 28_700, "Pasta Doria 500 g" to 3_600, "Panela Kpo" to 4_200,
                "Queso campesino 500 g" to 12_500, "Avena Quaker 500 g" to 6_900, "Crema dental Colgate" to 8_900,
                "Chocolate Corona 500 g" to 9_800, "Fríjol cargamanto 500 g" to 7_400, "Lenteja 500 g" to 4_100,
            ).forEachIndexed { i, (name, price) ->
                products[name] = t.product(name, price.toLong(), favorite = i % 3 == 0)
            }
            // Categorías de la semilla para que el catálogo y el resumen se vean reales.
            val byName = g.products.categories().associateBy { it.name }
            mapOf(
                "Granos y despensa" to listOf("Arroz Diana 1 kg", "Café Sello Rojo 500 g", "Aceite Premier 1 L", "Atún Van Camps", "Pasta Doria 500 g", "Panela Kpo", "Avena Quaker 500 g", "Chocolate Corona 500 g", "Fríjol cargamanto 500 g", "Lenteja 500 g"),
                "Lácteos y huevos" to listOf("Leche entera Alquería 1 L", "Huevos AA x 30", "Queso campesino 500 g"),
                "Panadería" to listOf("Pan tajado Bimbo"),
                "Aseo del hogar" to listOf("Papel higiénico Familia x 12", "Jabón Rey", "Detergente Ariel 2 kg"),
                "Cuidado personal" to listOf("Crema dental Colgate"),
            ).forEach { (category, names) ->
                val categoryId = byName.getValue(category).id
                names.forEach { n -> g.products.getById(products.getValue(n))?.let { g.products.update(it.copy(categoryId = categoryId)) } }
            }
            products["Tomate chonto"] = t.product("Tomate chonto", 3_200, unit = UnitType.KG, favorite = true)
            products["Banano"] = t.product("Banano", 2_400, unit = UnitType.KG, favorite = true)
            val fruits = g.products.categoryByName("Frutas y verduras")!!.id
            listOf("Tomate chonto", "Banano").forEach { n ->
                g.products.getById(products.getValue(n))?.let { g.products.update(it.copy(categoryId = fruits)) }
            }
            g.setMonthlyBudget(Pesos(900_000))
            loadSampleMonths(g, t.clock.now)
        }
    }

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        t.close()
        Dispatchers.resetMain()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(dark: Boolean = false, tab: Int? = null, content: @Composable () -> Unit) {
        compose.setContent {
            MercadoTheme(darkTheme = dark) {
                if (tab == null) {
                    content()
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { content() }
                        FakeNavigationBar(tab)
                    }
                }
            }
        }
    }

    private fun openTrip() = runBlocking {
        g.startTrip(TripLimit.Custom(Pesos(250_000)))
        listOf("Huevos AA x 30", "Café Sello Rojo 500 g", "Leche entera Alquería 1 L", "Arroz Diana 1 kg").forEach {
            g.addProductToTrip(products.getValue(it))
        }
        g.addProductToTrip(products.getValue("Arroz Diana 1 kg"))
        g.addWeighedItem(products.getValue("Tomate chonto"), Pesos(4_850))
        g.addProductToTrip(products.getValue("Detergente Ariel 2 kg"))
        g.addProductToTrip(products.getValue("Papel higiénico Familia x 12"))
        g.addProductToTrip(products.getValue("Aceite Premier 1 L"))
        g.addLoosePrice(Pesos(12_000))
        g.addProductToTrip(products.getValue("Queso campesino 500 g"))
        g.addProductToTrip(products.getValue("Chocolate Corona 500 g"))
    }

    private fun tripScreen(dark: Boolean = false) {
        openTrip()
        val vm = TripViewModel({ g }, FakeBarcodeSource(), RecordingFeedback(), t.clock).also { vms += it }
        vm.state.await { it.trip != null }
        show(dark) {
            TripScreen(vm, FakeBarcodeSource(), { error("sin cámara") }, {}, {}, {}, showCamera = false)
        }
    }

    @Test
    fun `01 inicio`() {
        openTrip()
        val vm = HomeViewModel({ g }, settings).also { vms += it }
        vm.state.await { it.lastTrip != null && it.month != null && it.openTripTotal != null }
        show(tab = 0) {
            val s by vm.state.collectAsStateWithLifecycle()
            HomeScreen(s, {}, {}, {})
        }
        shot("01-inicio")
    }

    @Test
    fun `02 compra`() {
        tripScreen()
        shot("02-compra")
    }

    @Test
    fun `03 compra oscuro`() {
        tripScreen(dark = true)
        shot("03-compra-oscuro")
    }

    @Test
    fun `04 teclado`() {
        show {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.padding(top = 360.dp)) {
                    var state by remember { mutableStateOf(KeypadState("12500")) }
                    PriceKeypad(state, { state = it }, {}, title = "Precio")
                }
            }
        }
        shot("04-teclado")
    }

    @Test
    fun `05 catalogo`() {
        val vm = CatalogViewModel({ g }).also { vms += it }
        vm.state.await { it.rows.isNotEmpty() }
        show(tab = 1) {
            val s by vm.state.collectAsStateWithLifecycle()
            CatalogScreen(s, {}, {}, {})
        }
        shot("05-catalogo")
    }

    @Test
    fun `06 editar producto`() {
        val vm = ProductEditViewModel({ g }, products.getValue("Café Sello Rojo 500 g")).also { vms += it }
        vm.state.await { !it.loading }
        show {
            val s by vm.state.collectAsStateWithLifecycle()
            ProductEditScreen(s, {}, {}, { error("sin cámara") }, showCamera = false)
        }
        shot("06-editar-producto")
    }

    @Test
    fun `07 historial`() {
        val vm = HistoryViewModel { g }.also { vms += it }
        vm.state.await { it.months.isNotEmpty() }
        show(tab = 2) {
            val s by vm.state.collectAsStateWithLifecycle()
            HistoryScreen(s, {})
        }
        shot("07-historial")
    }

    @Test
    fun `08 resumen`() {
        val vm = SummaryViewModel({ g }, t.clock).also { vms += it }
        vm.state.await { !it.loading }
        vm.onEvent(co.daniel.mercado.ui.summary.SummaryEvent.PreviousMonth)
        vm.state.await { !it.loading && it.summary?.topProducts?.isNotEmpty() == true }
        show {
            val s by vm.state.collectAsStateWithLifecycle()
            SummaryScreen(s, {}, {})
        }
        shot("08-resumen")
    }

    @Test
    fun `09 lista`() {
        runBlocking { g.addListItem(products.getValue("Panela Kpo")) }
        val vm = ShoppingListViewModel { g }.also { vms += it }
        vm.state.await { it.suggested.isNotEmpty() }
        show {
            val s by vm.state.collectAsStateWithLifecycle()
            ShoppingListScreen(s, {}, {})
        }
        shot("09-lista")
    }

    @Test
    fun `10 ajustes`() {
        val vm = SettingsViewModel({ g }, settings, t.clock).also { vms += it }
        vm.state.await { it.month != null }
        show(tab = 3) {
            val s by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(s, {})
        }
        shot("10-ajustes")
    }

    @Test
    fun `12 borrar datos`() {
        val vm = co.daniel.mercado.ui.cleanup.DataCleanupViewModel({ g }, t.clock) {}.also { vms += it }
        vm.onEvent(co.daniel.mercado.ui.cleanup.DataCleanupEvent.LastMonth)
        vm.state.await { it.preview != null }
        show {
            val s by vm.state.collectAsStateWithLifecycle()
            co.daniel.mercado.ui.cleanup.DataCleanupScreen(s, {}, {})
        }
        shot("12-borrar-datos")
    }

    @Test
    fun `13 aviso deshacer`() {
        openTrip()
        val vm = TripViewModel({ g }, FakeBarcodeSource(), RecordingFeedback(), t.clock).also { vms += it }
        vm.state.await { it.trip != null }
        vm.pickProduct(products.getValue("Arroz Diana 1 kg"))
        vm.state.await { it.notice != null }
        show { TripScreen(vm, FakeBarcodeSource(), { error("sin cámara") }, {}, {}, {}, showCamera = false) }
        shot("13-aviso-deshacer")
    }

    @Test
    fun `11 bloqueo`() {
        show(dark = true) { LockScreen(onUnlock = {}, autoPrompt = false) }
        shot("11-bloqueo")
    }
}

/** La barra inferior de la app, para que las capturas de nivel superior se vean completas. */
@Composable
private fun FakeNavigationBar(selected: Int) {
    NavigationBar {
        listOf(
            R.string.nav_home to R.drawable.ic_home,
            R.string.nav_catalog to R.drawable.ic_catalog,
            R.string.nav_history to R.drawable.ic_history,
            R.string.nav_settings to R.drawable.ic_settings,
        ).forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = i == selected,
                onClick = {},
                icon = { Icon(painterResource(icon), contentDescription = null) },
                label = { Text(androidx.compose.ui.res.stringResource(label)) },
            )
        }
    }
}
