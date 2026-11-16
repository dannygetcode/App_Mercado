package co.daniel.mercado.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.data.seed.SeedError
import co.daniel.mercado.data.seed.SeedResult
import co.daniel.mercado.data.settings.Settings
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

sealed interface ImportMessage {
    data class Imported(val created: Int, val updated: Int) : ImportMessage

    /** [index] desde 0, como lo da el importador. */
    data class Invalid(val error: SeedError, val index: Int?) : ImportMessage
    data object Unreadable : ImportMessage
}

data class SettingsUiState(
    val settings: Settings = Settings(),
    val month: MonthStatus? = null,
    val budgetSheet: Boolean = false,
    val importing: Boolean = false,
    val importMessage: ImportMessage? = null,
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
}

/**
 * Ajustes (04-ux). Presupuesto, mercado, escáner e importar catálogo funcionan; seguridad y
 * respaldo se muestran y se conectan en L6.
 */
class SettingsViewModel(
    private val graph: suspend () -> DataGraph,
    private val store: SettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

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
            is SettingsEvent.Reminders -> viewModelScope.launch { store.setRemindersEnabled(event.on) }
            is SettingsEvent.ImportCatalog -> importCatalog(event.open)
            SettingsEvent.DismissImportMessage -> _state.update { it.copy(importMessage = null) }
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
                SettingsViewModel(app.container::data, app.container.settings)
            }
        }
    }
}
