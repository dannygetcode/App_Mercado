package co.daniel.mercado.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.ListSource
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.ShoppingListEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingListUiState(
    val loading: Boolean = true,
    val suggested: List<ShoppingListEntry> = emptyList(),
    val added: List<ShoppingListEntry> = emptyList(),
    val addSheet: Boolean = false,
    val query: String = "",
    val results: List<Product> = emptyList(),
)

sealed interface ShoppingListEvent {
    data class Toggle(val productId: Long) : ShoppingListEvent
    data class Remove(val productId: Long) : ShoppingListEvent
    data object OpenAdd : ShoppingListEvent
    data object DismissAdd : ShoppingListEvent
    data class Query(val text: String) : ShoppingListEvent
    data class Add(val productId: Long) : ShoppingListEvent
}

/** Lista de mercado (RF-09): sugeridos con su motivo y agregados a mano. */
class ShoppingListViewModel(private val graph: suspend () -> DataGraph) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingListUiState())
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val g = graph()
            g.buildSuggestedList()
            g.observeShoppingList().collect { entries ->
                _state.update {
                    it.copy(
                        loading = false,
                        suggested = entries.filter { e -> e.item.source == ListSource.AUTO },
                        added = entries.filter { e -> e.item.source == ListSource.MANUAL },
                    )
                }
            }
        }
    }

    fun onEvent(event: ShoppingListEvent) {
        when (event) {
            is ShoppingListEvent.Toggle -> viewModelScope.launch { graph().toggleListItem(event.productId) }
            is ShoppingListEvent.Remove -> viewModelScope.launch { graph().removeListItem(event.productId) }
            ShoppingListEvent.OpenAdd -> _state.update { it.copy(addSheet = true, query = "", results = emptyList()) }
            ShoppingListEvent.DismissAdd -> _state.update { it.copy(addSheet = false, query = "", results = emptyList()) }
            is ShoppingListEvent.Query -> search(event.text)
            is ShoppingListEvent.Add -> {
                _state.update { it.copy(addSheet = false, query = "", results = emptyList()) }
                viewModelScope.launch { graph().addListItem(event.productId) }
            }
        }
    }

    private fun search(text: String) {
        _state.update { it.copy(query = text.take(MAX_QUERY)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = graph().searchProducts(text)
            _state.update { it.copy(results = results) }
        }
    }

    companion object {
        private const val MAX_QUERY = 80

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                ShoppingListViewModel(app.container::data)
            }
        }
    }
}
