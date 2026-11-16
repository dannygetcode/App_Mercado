package co.daniel.mercado.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.StartTripResult
import co.daniel.mercado.domain.usecase.TripLimit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    /** Total de la compra abierta, si hay una. */
    val openTripTotal: Pesos? = null,
    val month: MonthStatus? = null,
    /** Hoja "límite de esta compra" abierta, prellenada con lo que queda del mes. */
    val limitSheet: Boolean = false,
    val suggestedLimit: Pesos? = null,
)

/** Inicio provisional (L4-T9): lo mínimo para entrar y salir del flujo de compra. */
class HomeViewModel(private val graph: suspend () -> DataGraph) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val g = graph()
            launch { g.observeActiveTrip().collect { trip -> _state.update { it.copy(loading = false, openTripTotal = trip?.total) } } }
            refreshMonth()
        }
    }

    fun refreshMonth() {
        viewModelScope.launch {
            val month = graph().getMonthStatus()
            _state.update { it.copy(month = month) }
        }
    }

    fun openLimitSheet() {
        viewModelScope.launch {
            val suggested = graph().startTrip.defaultLimit()
            _state.update { it.copy(limitSheet = true, suggestedLimit = suggested) }
        }
    }

    fun dismissLimitSheet() = _state.update { it.copy(limitSheet = false) }

    /** "Empezar": 0 o vacío = sin límite. Llama a [onStarted] con la compra lista. */
    fun startTrip(limit: Pesos, onStarted: () -> Unit) {
        _state.update { it.copy(limitSheet = false) }
        viewModelScope.launch {
            val result = graph().startTrip(if (limit.isPositive) TripLimit.Custom(limit) else TripLimit.None)
            if (result is StartTripResult.Started || result is StartTripResult.AlreadyOpen) onStarted()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                HomeViewModel(app.container::data)
            }
        }
    }
}
