package co.daniel.mercado.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.model.sum
import co.daniel.mercado.domain.rules.LineMath
import co.daniel.mercado.domain.rules.Months
import co.daniel.mercado.ui.nav.TripDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Compras de un mes (por fecha de cierre) y lo que suman. */
data class HistoryMonth(val month: YearMonth, val total: Pesos, val trips: List<TripSummary>)

data class HistoryUiState(val loading: Boolean = true, val months: List<HistoryMonth> = emptyList())

/** Historial (RF-10): compras cerradas agrupadas por mes, la más reciente primero. */
class HistoryViewModel(private val graph: suspend () -> DataGraph) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph().observeClosedTrips().collect { trips ->
                _state.value = HistoryUiState(loading = false, months = group(trips))
            }
        }
    }

    companion object {
        fun group(trips: List<TripSummary>): List<HistoryMonth> =
            trips.groupBy { Months.monthOf(it.trip.closedAt ?: it.trip.startedAt) }
                .map { (month, list) -> HistoryMonth(month, list.map { it.trip.effectiveTotal }.sum(), list) }
                .sortedByDescending { it.month }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                HistoryViewModel(app.container::data)
            }
        }
    }
}

data class TripDetailUiState(
    val loading: Boolean = true,
    val detail: TripWithItems? = null,
    val articleCount: Int = 0,
    val paidSheet: Boolean = false,
    val notFound: Boolean = false,
)

sealed interface TripDetailEvent {
    data object EditPaid : TripDetailEvent

    /** 0 quita el total pagado (vuelve a contar el estimado). */
    data class SetPaid(val value: Pesos) : TripDetailEvent
    data object DismissSheet : TripDetailEvent
}

/** Detalle de una compra cerrada: solo lectura, salvo el total pagado (RF-08). */
class TripDetailViewModel(
    private val graph: suspend () -> DataGraph,
    private val tripId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(TripDetailUiState())
    val state: StateFlow<TripDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val detail = graph().getTripDetail(tripId)
        _state.update {
            it.copy(
                loading = false,
                detail = detail,
                articleCount = detail?.let { d -> LineMath.articleCount(d.items) } ?: 0,
                notFound = detail == null,
            )
        }
    }

    fun onEvent(event: TripDetailEvent) {
        when (event) {
            TripDetailEvent.EditPaid -> _state.update { it.copy(paidSheet = true) }
            TripDetailEvent.DismissSheet -> _state.update { it.copy(paidSheet = false) }
            is TripDetailEvent.SetPaid -> {
                _state.update { it.copy(paidSheet = false) }
                viewModelScope.launch {
                    graph().setPaidTotal(tripId, event.value.takeIf { it.isPositive })
                    reload()
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                val route = createSavedStateHandle().toRoute<TripDetailRoute>()
                TripDetailViewModel(app.container::data, route.tripId)
            }
        }
    }
}
