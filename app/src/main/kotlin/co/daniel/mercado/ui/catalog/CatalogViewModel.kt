package co.daniel.mercado.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.ui.nav.CatalogRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogRow(val product: Product, val categoryName: String)

data class CatalogUiState(
    val loading: Boolean = true,
    val filter: CatalogFilter = CatalogFilter(),
    val categories: List<Category> = emptyList(),
    val rows: List<CatalogRow> = emptyList(),
    val needsReviewCount: Int = 0,
) {
    val isFiltered: Boolean
        get() = filter.query.isNotBlank() || filter.categoryId != null || filter.favoritesOnly || filter.needsReviewOnly
}

sealed interface CatalogEvent {
    data class Query(val text: String) : CatalogEvent

    /** Nulo = todas las categorías. Tocar la categoría elegida la quita. */
    data class SelectCategory(val categoryId: Long?) : CatalogEvent
    data object ToggleFavorites : CatalogEvent
    data object ToggleNeedsReview : CatalogEvent
    data class MoveFavorite(val productId: Long, val up: Boolean) : CatalogEvent
}

/** Catálogo (RF-01): buscador, filtros por categoría, favoritos (con su orden) y por revisar. */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    private val graph: suspend () -> DataGraph,
    needsReviewOnly: Boolean = false,
) : ViewModel() {

    private val filter = MutableStateFlow(CatalogFilter(needsReviewOnly = needsReviewOnly))
    private val _state = MutableStateFlow(CatalogUiState(filter = filter.value))
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val g = graph()
            val categories = g.products.categories()
            val names = categories.associate { it.id to it.name }
            _state.update { it.copy(categories = categories) }
            launch { g.observeNeedsReviewCount().collect { n -> _state.update { it.copy(needsReviewCount = n) } } }
            filter.flatMapLatest { f -> g.observeCatalog(f) }.collect { products ->
                _state.update { s ->
                    s.copy(
                        loading = false,
                        filter = filter.value,
                        rows = products.map { CatalogRow(it, names[it.categoryId].orEmpty()) },
                    )
                }
            }
        }
    }

    fun onEvent(event: CatalogEvent) {
        when (event) {
            is CatalogEvent.Query -> setFilter { it.copy(query = event.text.take(MAX_QUERY)) }
            is CatalogEvent.SelectCategory -> setFilter {
                it.copy(categoryId = event.categoryId.takeIf { id -> id != it.categoryId })
            }
            CatalogEvent.ToggleFavorites -> setFilter { it.copy(favoritesOnly = !it.favoritesOnly, needsReviewOnly = false) }
            CatalogEvent.ToggleNeedsReview -> setFilter { it.copy(needsReviewOnly = !it.needsReviewOnly, favoritesOnly = false) }
            is CatalogEvent.MoveFavorite -> viewModelScope.launch { graph().moveFavorite(event.productId, event.up) }
        }
    }

    private fun setFilter(change: (CatalogFilter) -> CatalogFilter) {
        filter.update(change)
        _state.update { it.copy(filter = filter.value) }
    }

    companion object {
        private const val MAX_QUERY = 80

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                val route = createSavedStateHandle().toRoute<CatalogRoute>()
                CatalogViewModel(app.container::data, route.needsReviewOnly)
            }
        }
    }
}
