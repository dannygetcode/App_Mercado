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
import co.daniel.mercado.domain.rules.ReceiptAccumulator
import co.daniel.mercado.domain.rules.ReceiptLine
import co.daniel.mercado.domain.rules.ReceiptScan
import co.daniel.mercado.domain.usecase.ApplyReceiptResult
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

/** Lector del recibo al cerrar: primero la cámara lo recorre, luego se revisa lo leído. */
data class ReceiptUi(
    val reviewing: Boolean = false,
    val lines: List<ReceiptLine> = emptyList(),
    val total: Pesos? = null,
    /** Índices de [lines] que se aplicarán (por defecto todos). */
    val selected: Set<Int> = emptySet(),
    val applying: Boolean = false,
) {
    val selectedSum: Pesos get() = Pesos(lines.filterIndexed { i, _ -> i in selected }.sumOf { it.amount.value })
}

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
    /** Abierto mientras se escanea o revisa el recibo. */
    val receipt: ReceiptUi? = null,
    /** Lo que hizo el último recibo aplicado (aviso en el cierre). */
    val receiptResult: ApplyReceiptResult? = null,
    val saving: Boolean = false,
    /** La compra se guardó o descartó: volver a Inicio. */
    val done: Boolean = false,
)

/** Cierre de compra (L4-T8, RF-08). */
class CloseTripViewModel(private val graph: suspend () -> DataGraph) : ViewModel() {

    private val _state = MutableStateFlow(CloseTripUiState())
    val state: StateFlow<CloseTripUiState> = _state.asStateFlow()

    private val accumulator = ReceiptAccumulator()

    init {
        viewModelScope.launch {
            val g = graph()
            _state.update {
                it.copy(recentStores = g.trips.recentStores(RECENT_STORES), categories = g.products.categories())
            }
            reload()
        }
    }

    /** Lee la compra abierta y los productos por nombrar (al entrar y después de aplicar un recibo). */
    private suspend fun reload() {
        val g = graph()
        val trip = g.observeActiveTrip().first()
        val typed = _state.value.review.associateBy { it.product.id }
        val review = trip?.items
            ?.mapNotNull { it.productId }
            ?.distinct()
            ?.mapNotNull { g.products.getById(it) }
            ?.filter { it.needsReview }
            ?.map { typed[it.id] ?: ReviewItem(it) }
            .orEmpty()
        _state.update {
            it.copy(
                loading = false,
                trip = trip,
                done = trip == null,
                categories = it.categories.ifEmpty { g.products.categories() },
                review = review,
            )
        }
    }

    // ---- Recibo ----

    fun openReceipt() {
        accumulator.reset()
        _state.update { it.copy(receipt = ReceiptUi(), receiptResult = null) }
    }

    /** Un cuadro leído por la cámara. Llega del hilo de ML Kit. */
    fun onReceiptScan(scan: ReceiptScan) {
        if (_state.value.receipt?.reviewing != false) return
        accumulator.add(scan)
        val lines = accumulator.lines()
        val total = accumulator.total()
        _state.update { s ->
            val r = s.receipt ?: return@update s
            if (r.reviewing) s else s.copy(receipt = r.copy(lines = lines, total = total))
        }
    }

    /** "Listo": pasa a revisar lo leído, todo marcado. */
    fun reviewReceipt() = _state.update { s ->
        val r = s.receipt ?: return@update s
        s.copy(receipt = r.copy(reviewing = true, selected = r.lines.indices.toSet()))
    }

    fun toggleReceiptLine(index: Int) = _state.update { s ->
        val r = s.receipt ?: return@update s
        s.copy(receipt = r.copy(selected = if (index in r.selected) r.selected - index else r.selected + index))
    }

    /** "Seguir escaneando": vuelve a la cámara sin perder lo leído. */
    fun continueReceiptScan() = _state.update { s ->
        val r = s.receipt ?: return@update s
        s.copy(receipt = r.copy(reviewing = false))
    }

    fun closeReceipt() {
        accumulator.reset()
        _state.update { it.copy(receipt = null) }
    }

    /** Aplica lo marcado a la compra; el total leído pasa a "Total pagado". */
    fun applyReceipt() {
        val r = _state.value.receipt ?: return
        if (r.applying) return
        _state.update { it.copy(receipt = r.copy(applying = true)) }
        viewModelScope.launch {
            val chosen = r.lines.filterIndexed { i, _ -> i in r.selected }
            val result = runCatching { graph().applyReceipt(chosen) }
                .onFailure { AppLog.w("CloseTripViewModel: no se pudo aplicar el recibo", it) }
                .getOrNull()
            accumulator.reset()
            _state.update {
                it.copy(
                    receipt = null,
                    receiptResult = result,
                    totalPaid = r.total?.takeIf { t -> t.isPositive && t <= Pesos.KEYPAD_MAX } ?: it.totalPaid,
                )
            }
            reload()
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
