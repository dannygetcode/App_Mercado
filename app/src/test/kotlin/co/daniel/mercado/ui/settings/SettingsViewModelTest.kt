package co.daniel.mercado.ui.settings

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.data.seed.SeedError
import co.daniel.mercado.data.settings.InMemoryPreferencesDataStore
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.ui.catalog.await
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val t = TripGraph()
    private val store = SettingsStore(InMemoryPreferencesDataStore(), secureScreenDefault = false)
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        vm = SettingsViewModel({ t.graph }, store, io = Dispatchers.Unconfined)
        vm.state.await { it.month != null }
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        t.close()
        Dispatchers.resetMain()
    }

    private fun product(name: String, price: Long, codes: String = "") =
        """{"name":"$name","category":"Lácteos","unit":"UNIT","price":$price,"barcodes":[$codes]}"""

    private fun file(vararg products: String) = """{"version":1,"products":[${products.joinToString(",")}]}"""

    private fun import(json: String?) =
        vm.onEvent(SettingsEvent.ImportCatalog { json?.let { ByteArrayInputStream(it.toByteArray()) } })

    private fun catalogNames() = runBlocking { t.graph.observeCatalog(CatalogFilter()).first().map { it.name } }

    @Test
    fun `presupuesto del mes desde ajustes`() {
        vm.onEvent(SettingsEvent.EditBudget)
        vm.onEvent(SettingsEvent.SaveBudget(Pesos(800_000)))

        assertEquals(Pesos(800_000), vm.state.await { it.month?.budget != null }.month?.budget)
        assertFalse(vm.state.value.budgetSheet)
    }

    @Test
    fun `escaner, frecuencia y recordatorios se guardan y la frecuencia queda en su rango`() {
        vm.onEvent(SettingsEvent.ScanSound(false))
        vm.onEvent(SettingsEvent.ScanVibration(false))
        vm.onEvent(SettingsEvent.Reminders(false))
        vm.onEvent(SettingsEvent.FrequencyDays(16))
        vm.state.await { it.settings.shoppingFrequencyDays == 16 }
        vm.onEvent(SettingsEvent.FrequencyDays(500))

        val s = vm.state.await { it.settings.shoppingFrequencyDays == 90 }.settings
        assertFalse(s.scanSound)
        assertFalse(s.scanVibration)
        assertFalse(s.remindersEnabled)
    }

    @Test
    fun `importar un catalogo valido agrega productos y lo dice`() {
        import(file(product("Leche entera", 4_300, "\"96385074\""), product("Kumis", 3_000)))

        assertEquals(ImportMessage.Imported(2, 0), vm.state.await { it.importMessage != null }.importMessage)
        assertEquals(listOf("Kumis", "Leche entera"), catalogNames())
    }

    @Test
    fun `un catalogo con un producto invalido no escribe nada y dice cual`() {
        import(file(product("Leche entera", 4_300), product("Kumis", -5)))

        assertEquals(ImportMessage.Invalid(SeedError.INVALID_PRICE, 1), vm.state.await { it.importMessage != null }.importMessage)
        assertEquals(emptyList<String>(), catalogNames())
    }

    @Test
    fun `un archivo que no es json o que no se puede abrir se informa`() {
        import("esto no es json")
        assertEquals(ImportMessage.Invalid(SeedError.MALFORMED, null), vm.state.await { it.importMessage != null }.importMessage)

        vm.onEvent(SettingsEvent.DismissImportMessage)
        assertNull(vm.state.value.importMessage)
        import(null)
        assertEquals(ImportMessage.Unreadable, vm.state.await { it.importMessage != null }.importMessage)
    }
}
