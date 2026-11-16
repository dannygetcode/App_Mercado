package co.daniel.mercado.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.reminders.Reminders
import co.daniel.mercado.data.seed.SeedError
import co.daniel.mercado.data.seed.SeedResult
import co.daniel.mercado.data.settings.Settings
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.security.BackupCrypto
import co.daniel.mercado.security.BackupError
import co.daniel.mercado.security.BackupManager
import co.daniel.mercado.security.BackupOpenResult
import co.daniel.mercado.security.OpenedBackup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

sealed interface ImportMessage {
    data class Imported(val created: Int, val updated: Int) : ImportMessage

    /** [index] desde 0, como lo da el importador. */
    data class Invalid(val error: SeedError, val index: Int?) : ImportMessage
    data object Unreadable : ImportMessage
}

/** Resultado de exportar o restaurar un respaldo. */
sealed interface BackupMessage {
    data object Exported : BackupMessage
    data object Restored : BackupMessage
    data object PasswordTooShort : BackupMessage
    data object PasswordMismatch : BackupMessage
    data object WriteFailed : BackupMessage
    data object ReadFailed : BackupMessage
    data class Failed(val error: BackupError) : BackupMessage
}

/** Diálogos del respaldo, uno a la vez. */
sealed interface BackupDialog {
    /** Contraseña dos veces antes de elegir dónde guardar. */
    data object ExportPassword : BackupDialog

    /** Archivo elegido: falta la contraseña. */
    data object RestorePassword : BackupDialog

    /** Respaldo válido: la única confirmación (RF-12) antes de reemplazar todo. */
    data class ConfirmRestore(val exportedAt: Long, val products: Int, val trips: Int) : BackupDialog
}

sealed interface SettingsEffect {
    data class PickExportFile(val suggestedName: String) : SettingsEffect
    data object PickRestoreFile : SettingsEffect
}

data class SettingsUiState(
    val settings: Settings = Settings(),
    val month: MonthStatus? = null,
    val budgetSheet: Boolean = false,
    val importing: Boolean = false,
    val importMessage: ImportMessage? = null,
    val backupDialog: BackupDialog? = null,
    val backupBusy: Boolean = false,
    val backupMessage: BackupMessage? = null,
)

sealed interface SettingsEvent {
    data object EditBudget : SettingsEvent
    data class SaveBudget(val amount: Pesos) : SettingsEvent
    data object DismissSheet : SettingsEvent
    data class ScanSound(val on: Boolean) : SettingsEvent
    data class ScanVibration(val on: Boolean) : SettingsEvent
    data class FrequencyDays(val days: Int) : SettingsEvent
    data class Reminders(val on: Boolean) : SettingsEvent

    /** [open] abre el archivo elegido; nulo si el sistema no lo entrega. */
    data class ImportCatalog(val open: () -> InputStream?) : SettingsEvent
    data object DismissImportMessage : SettingsEvent

    data class LockEnabled(val on: Boolean) : SettingsEvent
    data class GraceMinutes(val minutes: Int) : SettingsEvent
    data class SecureScreen(val on: Boolean) : SettingsEvent

    data object ExportBackup : SettingsEvent
    data class ExportPassword(val password: String, val repeat: String) : SettingsEvent

    /** [open] abre el destino elegido para escribir; nulo si el sistema no lo entrega. */
    data class ExportTo(val open: () -> OutputStream?) : SettingsEvent
    data object RestoreBackup : SettingsEvent
    data class RestoreFrom(val open: () -> InputStream?) : SettingsEvent
    data class RestorePassword(val password: String) : SettingsEvent
    data object ConfirmRestore : SettingsEvent
    data object DismissBackupDialog : SettingsEvent
    data object DismissBackupMessage : SettingsEvent
}

/**
 * Ajustes (04-ux): presupuesto, mercado, escáner, seguridad (bloqueo, gracia, pantalla segura) y
 * datos (respaldo cifrado e importar catálogo).
 */
class SettingsViewModel(
    private val graph: suspend () -> DataGraph,
    private val store: SettingsStore,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Programa o cancela la revisión diaria (WorkManager). */
    private val onRemindersChanged: (Boolean) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch { store.settings.collect { s -> _state.update { it.copy(settings = s) } } }
        viewModelScope.launch { graph().observeMonthStatus().collect { m -> _state.update { it.copy(month = m) } } }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.EditBudget -> _state.update { it.copy(budgetSheet = true) }
            is SettingsEvent.SaveBudget -> {
                _state.update { it.copy(budgetSheet = false) }
                if (event.amount.isPositive) viewModelScope.launch { graph().setMonthlyBudget(event.amount) }
            }
            SettingsEvent.DismissSheet -> _state.update { it.copy(budgetSheet = false) }
            is SettingsEvent.ScanSound -> viewModelScope.launch { store.setScanSound(event.on) }
            is SettingsEvent.ScanVibration -> viewModelScope.launch { store.setScanVibration(event.on) }
            is SettingsEvent.FrequencyDays -> viewModelScope.launch {
                store.setShoppingFrequencyDays(event.days.coerceIn(SettingsStore.FREQUENCY_RANGE))
            }
            is SettingsEvent.Reminders -> viewModelScope.launch {
                store.setRemindersEnabled(event.on)
                onRemindersChanged(event.on)
            }
            is SettingsEvent.ImportCatalog -> importCatalog(event.open)
            SettingsEvent.DismissImportMessage -> _state.update { it.copy(importMessage = null) }
            is SettingsEvent.LockEnabled -> viewModelScope.launch { store.setLockEnabled(event.on) }
            is SettingsEvent.GraceMinutes -> viewModelScope.launch { store.setLockGraceMinutes(event.minutes) }
            is SettingsEvent.SecureScreen -> viewModelScope.launch { store.setSecureScreen(event.on) }
            SettingsEvent.ExportBackup -> _state.update { it.copy(backupDialog = BackupDialog.ExportPassword, backupMessage = null) }
            is SettingsEvent.ExportPassword -> exportPassword(event.password, event.repeat)
            is SettingsEvent.ExportTo -> exportTo(event.open)
            SettingsEvent.RestoreBackup -> {
                _state.update { it.copy(backupMessage = null) }
                _effects.trySend(SettingsEffect.PickRestoreFile)
            }
            is SettingsEvent.RestoreFrom -> restoreFrom(event.open)
            is SettingsEvent.RestorePassword -> restorePassword(event.password)
            SettingsEvent.ConfirmRestore -> confirmRestore()
            SettingsEvent.DismissBackupDialog -> dropPending()
            SettingsEvent.DismissBackupMessage -> _state.update { it.copy(backupMessage = null) }
        }
    }

    // La contraseña y el archivo viven solo en memoria mientras dura el flujo.
    private var pendingPassword: CharArray? = null
    private var pendingFile: ByteArray? = null
    private var pendingBackup: OpenedBackup? = null

    private fun dropPending() {
        pendingPassword?.fill(' ')
        pendingPassword = null
        pendingFile = null
        pendingBackup = null
        _state.update { it.copy(backupDialog = null) }
    }

    private fun finish(message: BackupMessage) {
        dropPending()
        _state.update { it.copy(backupBusy = false, backupMessage = message) }
    }

    private fun exportPassword(password: String, repeat: String) {
        when {
            password.length < BackupCrypto.MIN_PASSWORD -> _state.update { it.copy(backupMessage = BackupMessage.PasswordTooShort) }
            password != repeat -> _state.update { it.copy(backupMessage = BackupMessage.PasswordMismatch) }
            else -> {
                pendingPassword = password.toCharArray()
                _state.update { it.copy(backupDialog = null, backupMessage = null) }
                _effects.trySend(SettingsEffect.PickExportFile(BackupManager.suggestedFileName(clock.nowMillis())))
            }
        }
    }

    private fun exportTo(open: () -> OutputStream?) {
        val password = pendingPassword ?: return
        _state.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            val manager = graph().backupManager
            val ok = withContext(io) {
                try {
                    val bytes = manager.export(password)
                    open()?.use { it.write(bytes) } != null
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            if (ok) store.setLastBackupAt(clock.nowMillis())
            finish(if (ok) BackupMessage.Exported else BackupMessage.WriteFailed)
        }
    }

    private fun restoreFrom(open: () -> InputStream?) {
        _state.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val bytes = withContext(io) {
                try {
                    open()?.use { it.readAtMost(BackupCrypto.MAX_FILE_BYTES + 1) }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            when {
                bytes == null -> finish(BackupMessage.ReadFailed)
                bytes.size > BackupCrypto.MAX_FILE_BYTES -> finish(BackupMessage.Failed(BackupError.TOO_LARGE))
                else -> {
                    pendingFile = bytes
                    _state.update { it.copy(backupBusy = false, backupDialog = BackupDialog.RestorePassword) }
                }
            }
        }
    }

    private fun restorePassword(password: String) {
        val file = pendingFile ?: return
        _state.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val manager = graph().backupManager
            when (val result = withContext(io) { manager.open(file, password.toCharArray()) }) {
                is BackupOpenResult.Failed -> {
                    // Contraseña equivocada: se puede volver a intentar sin elegir el archivo otra vez.
                    val retry = result.error == BackupError.WRONG_PASSWORD_OR_TAMPERED
                    if (retry) {
                        _state.update { it.copy(backupBusy = false, backupMessage = BackupMessage.Failed(result.error)) }
                    } else {
                        finish(BackupMessage.Failed(result.error))
                    }
                }
                is BackupOpenResult.Ok -> {
                    pendingFile = null
                    pendingBackup = result.backup
                    val b = result.backup
                    _state.update {
                        it.copy(backupBusy = false, backupDialog = BackupDialog.ConfirmRestore(b.exportedAt, b.productCount, b.tripCount))
                    }
                }
            }
        }
    }

    private fun confirmRestore() {
        val backup = pendingBackup ?: return
        _state.update { it.copy(backupBusy = true, backupDialog = null) }
        viewModelScope.launch {
            val g = graph()
            withContext(io) { g.backupManager.restore(backup) }
            g.tripSession.undo.clear()
            finish(BackupMessage.Restored)
        }
    }

    private fun importCatalog(open: () -> InputStream?) {
        if (_state.value.importing) return
        _state.update { it.copy(importing = true, importMessage = null) }
        viewModelScope.launch {
            val importer = graph().seedImporter
            val message = withContext(io) {
                try {
                    open()?.use { input ->
                        when (val result = importer.import(input)) {
                            is SeedResult.Imported -> ImportMessage.Imported(result.created, result.updated)
                            is SeedResult.Invalid -> ImportMessage.Invalid(result.error, result.index)
                        }
                    } ?: ImportMessage.Unreadable
                } catch (_: IOException) {
                    ImportMessage.Unreadable
                } catch (_: SecurityException) {
                    ImportMessage.Unreadable
                }
            }
            _state.update { it.copy(importing = false, importMessage = message) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                SettingsViewModel(app.container::data, app.container.settings, app.container.clock) { on ->
                    Reminders.schedule(app, on)
                }
            }
        }
    }
}

/** Lee hasta [limit] bytes (para no cargar en memoria un archivo gigante). */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
