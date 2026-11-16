package co.daniel.mercado.ui.settings

import androidx.lifecycle.viewModelScope
import co.daniel.mercado.data.seed.SeedError
import co.daniel.mercado.data.settings.InMemoryPreferencesDataStore
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.security.BackupError
import co.daniel.mercado.ui.catalog.await
import co.daniel.mercado.ui.catalog.next
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SettingsViewModelTest {

    private val t = TripGraph()
    private val store = SettingsStore(InMemoryPreferencesDataStore(), secureScreenDefault = false)
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        vm = SettingsViewModel({ t.graph }, store, t.clock, io = Dispatchers.Unconfined)
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

    @Test
    fun `seguridad se guarda en ajustes`() {
        vm.onEvent(SettingsEvent.LockEnabled(false))
        vm.onEvent(SettingsEvent.GraceMinutes(15))
        vm.onEvent(SettingsEvent.SecureScreen(true))

        val s = vm.state.await { it.settings.lockGraceMinutes == 15 && it.settings.secureScreen }.settings
        assertFalse(s.lockEnabled)
    }

    @Test
    fun `exportar pide contrasena dos veces de al menos 8 y luego el archivo`() {
        vm.onEvent(SettingsEvent.ExportBackup)
        assertEquals(BackupDialog.ExportPassword, vm.state.value.backupDialog)

        vm.onEvent(SettingsEvent.ExportPassword("corta", "corta"))
        assertEquals(BackupMessage.PasswordTooShort, vm.state.value.backupMessage)
        vm.onEvent(SettingsEvent.ExportPassword("contraseña1", "contraseña2"))
        assertEquals(BackupMessage.PasswordMismatch, vm.state.value.backupMessage)

        vm.onEvent(SettingsEvent.ExportPassword("contraseña1", "contraseña1"))
        val effect = vm.effects.next() as SettingsEffect.PickExportFile
        assertTrue(effect.suggestedName.startsWith("mercado-") && effect.suggestedName.endsWith(".mercado"))
        assertNull(vm.state.value.backupDialog)
    }

    @Test
    fun `exportar y restaurar con confirmacion devuelve los datos`() {
        runBlocking {
            t.product("Arroz Diana", 5_900)
            t.graph.setMonthlyBudget(Pesos(900_000))
        }
        val out = ByteArrayOutputStream()
        vm.onEvent(SettingsEvent.ExportBackup)
        vm.onEvent(SettingsEvent.ExportPassword("contraseña1", "contraseña1"))
        vm.effects.next()
        vm.onEvent(SettingsEvent.ExportTo { out })
        assertEquals(BackupMessage.Exported, vm.state.await { it.backupMessage != null }.backupMessage)
        assertTrue(vm.state.value.settings.lastBackupAt > 0 || runBlocking { store.current().lastBackupAt } > 0)

        runBlocking { t.product("Leche", 4_300) }
        assertEquals(listOf("Arroz Diana", "Leche"), catalogNames())

        vm.onEvent(SettingsEvent.RestoreBackup)
        assertEquals(SettingsEffect.PickRestoreFile, vm.effects.next())
        vm.onEvent(SettingsEvent.RestoreFrom { ByteArrayInputStream(out.toByteArray()) })
        assertEquals(BackupDialog.RestorePassword, vm.state.await { it.backupDialog != null }.backupDialog)

        vm.onEvent(SettingsEvent.RestorePassword("otra-contraseña"))
        val wrong = vm.state.await { it.backupMessage != null }
        assertEquals(BackupMessage.Failed(BackupError.WRONG_PASSWORD_OR_TAMPERED), wrong.backupMessage)
        assertEquals(BackupDialog.RestorePassword, wrong.backupDialog)

        vm.onEvent(SettingsEvent.RestorePassword("contraseña1"))
        val confirm = vm.state.await { it.backupDialog is BackupDialog.ConfirmRestore }.backupDialog as BackupDialog.ConfirmRestore
        assertEquals(1, confirm.products)
        assertEquals(listOf("Arroz Diana", "Leche"), catalogNames())

        vm.onEvent(SettingsEvent.ConfirmRestore)
        assertEquals(BackupMessage.Restored, vm.state.await { it.backupMessage != null }.backupMessage)
        assertEquals(listOf("Arroz Diana"), catalogNames())
    }

    @Test
    fun `un archivo que no es respaldo se informa sin tocar nada`() {
        runBlocking { t.product("Arroz Diana", 5_900) }
        vm.onEvent(SettingsEvent.RestoreFrom { ByteArrayInputStream("hola".toByteArray()) })
        vm.state.await { it.backupDialog == BackupDialog.RestorePassword }
        vm.onEvent(SettingsEvent.RestorePassword("contraseña1"))

        val s = vm.state.await { it.backupMessage != null }
        assertEquals(BackupMessage.Failed(BackupError.NOT_A_BACKUP), s.backupMessage)
        assertNull(s.backupDialog)
        assertEquals(listOf("Arroz Diana"), catalogNames())
    }
}
