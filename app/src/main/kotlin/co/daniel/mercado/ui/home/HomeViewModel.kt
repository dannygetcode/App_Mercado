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
    /** Presupuesto de la compra abierta (nulo: sin tope o sin compra abierta). */
    val openTripLimit: Pesos? = null,
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

    /** Presupuesto del mes en curso (el propio o el heredado como punto de partida). $0 lo quita. */
    data class Budget(val current: Pesos?) : HomeSheet

    /**
     * "Presupuesto de esta compra" desde la tarjeta del mes: con una compra abierta cambia su tope;
     * sin compra abierta arranca una con ese tope. Lo gastado siempre suma al mes.
     */
    data class TripBudget(val current: Pesos?, val tripOpen: Boolean) : HomeSheet
}

sealed interface HomeEvent {
    data object StartOrContinue : HomeEvent
    data class StartTrip(val limit: Pesos) : HomeEvent
    data object EditBudget : HomeEvent
    data class SaveBudget(val amount: Pesos) : HomeEvent
    data object EditTripBudget : HomeEvent
    data class SaveTripBudget(val amount: Pesos) : HomeEvent
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
            launch { g.observeActiveTrip().collect { trip -> _state.update { it.copy(loading = false, openTripTotal = trip?.total, openTripLimit = trip?.limit) } } }
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
            HomeEvent.EditTripBudget -> _state.update {
                val open = it.openTripTotal != null
                it.copy(sheet = HomeSheet.TripBudget(if (open) it.openTripLimit else null, open))
            }
            is HomeEvent.SaveTripBudget -> saveTripBudget(event.amount)
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

    /** $0 quita el presupuesto del mes (si había uno); el presupuesto mensual es opcional. */
    private fun saveBudget(amount: Pesos) {
        val hadBudget = _state.value.month?.budget != null
        _state.update { it.copy(sheet = null) }
        if (!amount.isPositive && !hadBudget) return
        viewModelScope.launch { graph().setMonthlyBudget(amount) }
    }

    /** Con compra abierta cambia su tope; sin ella, arranca una compra con ese tope ($0 = sin tope). */
    private fun saveTripBudget(amount: Pesos) {
        if (_state.value.openTripTotal == null) {
            startTrip(amount)
            return
        }
        _state.update { it.copy(sheet = null) }
        viewModelScope.launch { graph().setTripLimit(amount.takeIf { it.isPositive }) }
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
