package co.daniel.mercado.ui.close

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.ActiveTrip
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.usecase.CloseTripResult
import co.daniel.mercado.domain.usecase.ProductDraft
import co.daniel.mercado.domain.usecase.SaveProductResult
import co.daniel.mercado.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un producto provisional de esta compra para nombrarlo al cerrar. */
data class ReviewItem(
    val product: Product,
    val name: String = "",
    val categoryId: Long = product.categoryId,
    val duplicate: Boolean = false,
)

data class CloseTripUiState(
    val loading: Boolean = true,
    val trip: ActiveTrip? = null,
    val totalPaid: Pesos? = null,
    val paidSheet: Boolean = false,
    val store: String = "",
    val recentStores: List<String> = emptyList(),
    val categories: List<Category> = emptyList(),
    val review: List<ReviewItem> = emptyList(),
    val confirmDiscard: Boolean = false,
    val saving: Boolean = false,
    /** La compra se guardó o descartó: volver a Inicio. */
    val done: Boolean = false,
)

/** Cierre de compra (L4-T8, RF-08). */
class CloseTripViewModel(private val graph: suspend () -> DataGraph) : ViewModel() {

    private val _state = MutableStateFlow(CloseTripUiState())
    val state: StateFlow<CloseTripUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val g = graph()
            val trip = g.observeActiveTrip().first()
            val review = trip?.items
                ?.mapNotNull { it.productId }
                ?.distinct()
                ?.mapNotNull { g.products.getById(it) }
                ?.filter { it.needsReview }
                ?.map { ReviewItem(it) }
                .orEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    trip = trip,
                    done = trip == null,
                    recentStores = g.trips.recentStores(RECENT_STORES),
                    categories = g.products.categories(),
                    review = review,
                )
            }
        }
    }

    fun openPaidSheet() = _state.update { it.copy(paidSheet = true) }

    fun dismissPaidSheet() = _state.update { it.copy(paidSheet = false) }

    /** 0 borra el total pagado (queda el estimado). */
    fun setTotalPaid(value: Pesos) =
        _state.update { it.copy(totalPaid = value.takeIf { v -> v.isPositive }, paidSheet = false) }

    fun setStore(value: String) = _state.update { it.copy(store = value.take(MAX_STORE)) }

    fun setReviewName(productId: Long, name: String) = updateReview(productId) { it.copy(name = name.take(MAX_NAME), duplicate = false) }

    fun setReviewCategory(productId: Long, categoryId: Long) = updateReview(productId) { it.copy(categoryId = categoryId) }

    private fun updateReview(productId: Long, change: (ReviewItem) -> ReviewItem) =
        _state.update { s -> s.copy(review = s.review.map { if (it.product.id == productId) change(it) else it }) }

    /** "Guardar compra": primero nombra los productos que se llenaron, luego cierra. */
    fun save() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val g = graph()
            var blocked = false
            val updated = _state.value.review.map { item ->
                if (item.name.isBlank()) return@map item
                val result = g.saveProduct(
                    ProductDraft(
                        id = item.product.id,
                        name = item.name,
                        categoryId = item.categoryId,
                        unitType = item.product.unitType,
                        refPrice = item.product.refPrice,
                        favorite = item.product.favorite,
                    ),
                )
                if (result is SaveProductResult.DuplicateName) {
                    blocked = true
                    item.copy(duplicate = true)
                } else {
                    item
                }
            }
            if (blocked) {
                _state.update { it.copy(review = updated, saving = false) }
                return@launch
            }
            val s = _state.value
            val result = runCatching { g.closeTrip(totalPaid = s.totalPaid, store = s.store) }
                .onFailure { AppLog.w("CloseTripViewModel: no se pudo cerrar", it) }
                .getOrNull()
            _state.update {
                it.copy(saving = false, done = result is CloseTripResult.Closed || result is CloseTripResult.DiscardedEmpty || result is CloseTripResult.NoActiveTrip)
            }
        }
    }

    fun askDiscard() = _state.update { it.copy(confirmDiscard = true) }

    fun cancelDiscard() = _state.update { it.copy(confirmDiscard = false) }

    fun discard() {
        _state.update { it.copy(confirmDiscard = false) }
        viewModelScope.launch {
            graph().discardTrip()
            _state.update { it.copy(done = true) }
        }
    }

    companion object {
        const val RECENT_STORES = 6
        private const val MAX_STORE = 40
        private const val MAX_NAME = 80

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                CloseTripViewModel(app.container::data)
            }
        }
    }
}
