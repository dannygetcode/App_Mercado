package co.daniel.mercado.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.MonthSummary
import co.daniel.mercado.domain.model.MonthTotal
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.Months
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class SummaryUiState(
    val month: YearMonth,
    val currentMonth: YearMonth,
    val loading: Boolean = true,
    val summary: MonthSummary? = null,
    val lastMonths: List<MonthTotal> = emptyList(),
) {
    val canGoNext: Boolean get() = month < currentMonth
}

sealed interface SummaryEvent {
    data object PreviousMonth : SummaryEvent
    data object NextMonth : SummaryEvent
}

/** Resumen del mes (RF-10, RF-13) con selector de mes. */
class SummaryViewModel(private val graph: suspend () -> DataGraph, clock: Clock) : ViewModel() {

    private val now = Months.monthOf(clock.nowMillis())
    private val _state = MutableStateFlow(SummaryUiState(month = now, currentMonth = now))
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(now)
    }

    fun onEvent(event: SummaryEvent) {
        val month = _state.value.month
        when (event) {
            SummaryEvent.PreviousMonth -> load(month.minusMonths(1))
            SummaryEvent.NextMonth -> if (_state.value.canGoNext) load(month.plusMonths(1))
        }
    }

    private fun load(month: YearMonth) {
        _state.update { it.copy(month = month, loading = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val g = graph()
            val summary = g.getMonthSummary(month)
            val months = g.getMonthsComparison(month)
            _state.update { it.copy(loading = false, summary = summary, lastMonths = months) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                SummaryViewModel(app.container::data, app.container.clock)
            }
        }
    }
}

data class CategoryCapsUiState(val categories: List<Category> = emptyList(), val editing: Category? = null)

sealed interface CategoryCapsEvent {
    data class Edit(val category: Category) : CategoryCapsEvent

    /** 0 quita el tope. */
    data class Save(val amount: Pesos) : CategoryCapsEvent
    data object Dismiss : CategoryCapsEvent
}

/** Topes mensuales por categoría (RF-13), desde Ajustes. */
class CategoryCapsViewModel(private val graph: suspend () -> DataGraph) : ViewModel() {

    private val _state = MutableStateFlow(CategoryCapsUiState())
    val state: StateFlow<CategoryCapsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val categories = graph().products.categories()
        _state.update { it.copy(categories = categories) }
    }

    fun onEvent(event: CategoryCapsEvent) {
        when (event) {
            is CategoryCapsEvent.Edit -> _state.update { it.copy(editing = event.category) }
            CategoryCapsEvent.Dismiss -> _state.update { it.copy(editing = null) }
            is CategoryCapsEvent.Save -> {
                val category = _state.value.editing ?: return
                _state.update { it.copy(editing = null) }
                viewModelScope.launch {
                    graph().setCategoryBudget(category.id, event.amount.takeIf { it.isPositive })
                    reload()
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                CategoryCapsViewModel(app.container::data)
            }
        }
    }
}
