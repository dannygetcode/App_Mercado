package co.daniel.mercado.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.domain.usecase.StartTripResult
import co.daniel.mercado.domain.usecase.TripLimit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    /** Total de la compra abierta, si hay una. */
    val openTripTotal: Pesos? = null,
    val month: MonthStatus? = null,
    val lastTrip: TripSummary? = null,
    val needsReviewCount: Int = 0,
    /** Ítems de la lista sin marcar (tarjeta "Lista de mercado · N"). */
    val listPending: Int = 0,
    val budgetHintDismissed: Boolean = true,
    val sheet: HomeSheet? = null,
) {
    /** Primer uso: tarjeta "Define tu presupuesto del mes" (omitible). */
    val showBudgetHint: Boolean
        get() = month != null && month.budget == null && !budgetHintDismissed
}

sealed interface HomeSheet {
    /** Límite de esta compra, prellenado con lo que queda del mes. */
    data class Limit(val suggested: Pesos?) : HomeSheet

    /** Presupuesto del mes en curso (el propio o el heredado como punto de partida). */
    data class Budget(val current: Pesos?) : HomeSheet
}

sealed interface HomeEvent {
    data object StartOrContinue : HomeEvent
    data class StartTrip(val limit: Pesos) : HomeEvent
    data object EditBudget : HomeEvent
    data class SaveBudget(val amount: Pesos) : HomeEvent
    data object DismissSheet : HomeEvent
    data object DismissBudgetHint : HomeEvent
}

sealed interface HomeEffect {
    data object OpenTrip : HomeEffect
}

/** Inicio: estado del mes, tarjetas y el botón grande "Ir a mercar" / "Continuar compra". */
class HomeViewModel(
    private val graph: suspend () -> DataGraph,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val g = graph()
            launch { g.observeActiveTrip().collect { trip -> _state.update { it.copy(loading = false, openTripTotal = trip?.total) } } }
            launch { g.observeMonthStatus().collect { month -> _state.update { it.copy(month = month) } } }
            launch { g.observeClosedTrips().collect { trips -> _state.update { it.copy(lastTrip = trips.firstOrNull()) } } }
            launch { g.observeNeedsReviewCount().collect { n -> _state.update { it.copy(needsReviewCount = n) } } }
            launch {
                g.buildSuggestedList()
                g.observeShoppingList().collect { l -> _state.update { it.copy(listPending = l.count { e -> !e.item.checked }) } }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(budgetHintDismissed = s.budgetHintDismissed) } }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.StartOrContinue -> startOrContinue()
            is HomeEvent.StartTrip -> startTrip(event.limit)
            HomeEvent.EditBudget -> _state.update { it.copy(sheet = HomeSheet.Budget(it.month?.budget)) }
            is HomeEvent.SaveBudget -> saveBudget(event.amount)
            HomeEvent.DismissSheet -> _state.update { it.copy(sheet = null) }
            HomeEvent.DismissBudgetHint -> viewModelScope.launch { settings.setBudgetHintDismissed(true) }
        }
    }

    private fun startOrContinue() {
        if (_state.value.openTripTotal != null) {
            _effects.trySend(HomeEffect.OpenTrip)
            return
        }
        viewModelScope.launch {
            val suggested = graph().startTrip.defaultLimit()
            _state.update { it.copy(sheet = HomeSheet.Limit(suggested)) }
        }
    }

    /** "Empezar": 0 = sin límite. */
    private fun startTrip(limit: Pesos) {
        _state.update { it.copy(sheet = null) }
        viewModelScope.launch {
            val result = graph().startTrip(if (limit.isPositive) TripLimit.Custom(limit) else TripLimit.None)
            if (result is StartTripResult.Started || result is StartTripResult.AlreadyOpen) _effects.send(HomeEffect.OpenTrip)
        }
    }

    private fun saveBudget(amount: Pesos) {
        _state.update { it.copy(sheet = null) }
        if (!amount.isPositive) return
        viewModelScope.launch { graph().setMonthlyBudget(amount) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                HomeViewModel(app.container::data, app.container.settings)
            }
        }
    }
}
