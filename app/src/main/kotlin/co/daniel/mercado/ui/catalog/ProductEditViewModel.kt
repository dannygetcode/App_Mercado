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
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.Ean
import co.daniel.mercado.domain.usecase.LinkBarcodeResult
import co.daniel.mercado.domain.usecase.ProductDraft
import co.daniel.mercado.domain.usecase.SaveProduct
import co.daniel.mercado.domain.usecase.SaveProductResult
import co.daniel.mercado.ui.nav.ProductEditRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProductError { NAME, PRICE, DUPLICATE, CATEGORY, SAVE }

/** Avisos breves del editor. [name] es el producto del que venía un código reasignado. */
sealed interface ProductNotice {
    data object CodeLinked : ProductNotice
    data class CodeMoved(val name: String) : ProductNotice
    data object CodeAlreadyHere : ProductNotice
    data object InvalidCode : ProductNotice
}

data class ProductEditUiState(
    val loading: Boolean = true,
    val productId: Long = 0,
    val name: String = "",
    val categoryId: Long? = null,
    val unitType: UnitType = UnitType.UNIT,
    val price: Pesos? = null,
    val favorite: Boolean = false,
    val active: Boolean = true,
    val needsReview: Boolean = false,
    /** Códigos asociados; en un producto nuevo se asocian al guardar. */
    val codes: List<String> = emptyList(),
    val history: List<PricePoint> = emptyList(),
    val categories: List<Category> = emptyList(),
    val priceSheet: Boolean = false,
    val scanning: Boolean = false,
    val error: ProductError? = null,
    val notice: ProductNotice? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = productId == 0L
}

sealed interface ProductEditEvent {
    data class Name(val value: String) : ProductEditEvent
    data class SelectCategory(val categoryId: Long) : ProductEditEvent
    data class Unit(val value: UnitType) : ProductEditEvent
    data object OpenPrice : ProductEditEvent
    data class SetPrice(val value: Pesos) : ProductEditEvent
    data object DismissSheet : ProductEditEvent
    data class Favorite(val value: Boolean) : ProductEditEvent
    data object StartScan : ProductEditEvent
    data class Scanned(val code: String) : ProductEditEvent
    data class RemoveCode(val code: String) : ProductEditEvent
    data object DismissNotice : ProductEditEvent
    data object Save : ProductEditEvent
    data object Deactivate : ProductEditEvent
    data object Reactivate : ProductEditEvent
}

sealed interface ProductEditEffect {
    data object Done : ProductEditEffect
}

/**
 * Crear o editar un producto (04-ux, Catálogo). Los cambios de los campos se aplican al guardar;
 * los códigos de un producto existente se asocian o quitan al momento (no dependen del resto).
 */
class ProductEditViewModel(
    private val graph: suspend () -> DataGraph,
    productId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductEditUiState(productId = productId))
    val state: StateFlow<ProductEditUiState> = _state.asStateFlow()

    private val _effects = Channel<ProductEditEffect>(Channel.BUFFERED)
    val effects: Flow<ProductEditEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val g = graph()
            val categories = g.products.categories()
            _state.update { it.copy(categories = categories) }
            if (productId == 0L) {
                val other = categories.firstOrNull { it.name == Category.OTHER } ?: categories.lastOrNull()
                _state.update { it.copy(loading = false, categoryId = other?.id) }
            } else {
                reload(g)
            }
        }
    }

    private suspend fun reload(g: DataGraph) {
        val detail = g.getProductDetail(_state.value.productId) ?: run {
            _effects.send(ProductEditEffect.Done)
            return
        }
        val p = detail.product
        _state.update {
            it.copy(
                loading = false,
                name = p.name,
                categoryId = p.categoryId,
                unitType = p.unitType,
                price = p.refPrice,
                favorite = p.favorite,
                active = p.active,
                needsReview = p.needsReview,
                codes = detail.barcodes,
                history = detail.priceHistory,
            )
        }
    }

    fun onEvent(event: ProductEditEvent) {
        when (event) {
            is ProductEditEvent.Name -> _state.update { it.copy(name = event.value.take(SaveProduct.MAX_NAME_LENGTH), error = null) }
            is ProductEditEvent.SelectCategory -> _state.update { it.copy(categoryId = event.categoryId, error = null) }
            is ProductEditEvent.Unit -> _state.update { it.copy(unitType = event.value) }
            ProductEditEvent.OpenPrice -> _state.update { it.copy(priceSheet = true) }
            is ProductEditEvent.SetPrice -> _state.update { it.copy(price = event.value, priceSheet = false, error = null) }
            ProductEditEvent.DismissSheet -> _state.update { it.copy(priceSheet = false, scanning = false) }
            is ProductEditEvent.Favorite -> _state.update { it.copy(favorite = event.value) }
            ProductEditEvent.StartScan -> _state.update { it.copy(scanning = true, notice = null) }
            is ProductEditEvent.Scanned -> scanned(event.code)
            is ProductEditEvent.RemoveCode -> removeCode(event.code)
            ProductEditEvent.DismissNotice -> _state.update { it.copy(notice = null) }
            ProductEditEvent.Save -> save()
            ProductEditEvent.Deactivate -> setActive(false)
            ProductEditEvent.Reactivate -> setActive(true)
        }
    }

    private fun scanned(raw: String) {
        if (!_state.value.scanning) return
        _state.update { it.copy(scanning = false) }
        val code = Ean.normalize(raw) ?: return _state.update { it.copy(notice = ProductNotice.InvalidCode) }
        val s = _state.value
        if (s.isNew) {
            if (code in s.codes) return _state.update { it.copy(notice = ProductNotice.CodeAlreadyHere) }
            _state.update { it.copy(codes = it.codes + code, notice = ProductNotice.CodeLinked) }
            return
        }
        viewModelScope.launch {
            val g = graph()
            val notice = when (val result = g.linkBarcode(s.productId, code)) {
                LinkBarcodeResult.Linked -> ProductNotice.CodeLinked
                LinkBarcodeResult.AlreadyLinked -> ProductNotice.CodeAlreadyHere
                is LinkBarcodeResult.Moved ->
                    ProductNotice.CodeMoved(g.getProductDetail(result.fromProductId)?.product?.name.orEmpty())
                LinkBarcodeResult.InvalidCode, LinkBarcodeResult.ProductNotFound -> ProductNotice.InvalidCode
            }
            _state.update { it.copy(codes = g.products.barcodesOf(s.productId), notice = notice) }
        }
    }

    private fun removeCode(code: String) {
        val s = _state.value
        if (s.isNew) return _state.update { it.copy(codes = it.codes - code) }
        viewModelScope.launch {
            val g = graph()
            g.unlinkBarcode(s.productId, code)
            _state.update { it.copy(codes = g.products.barcodesOf(s.productId)) }
        }
    }

    private fun save() {
        val s = _state.value
        if (s.saving) return
        val categoryId = s.categoryId ?: return _state.update { it.copy(error = ProductError.CATEGORY) }
        val price = s.price ?: return _state.update { it.copy(error = ProductError.PRICE) }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val g = graph()
            val result = g.saveProduct(ProductDraft(s.productId, s.name, categoryId, s.unitType, price, s.favorite))
            val error = when (result) {
                is SaveProductResult.Saved -> {
                    if (s.isNew) s.codes.forEach { g.linkBarcode(result.productId, it) }
                    null
                }
                SaveProductResult.InvalidName -> ProductError.NAME
                SaveProductResult.InvalidPrice -> ProductError.PRICE
                SaveProductResult.UnknownCategory -> ProductError.CATEGORY
                is SaveProductResult.DuplicateName -> ProductError.DUPLICATE
                SaveProductResult.NotFound -> ProductError.SAVE
            }
            _state.update { it.copy(saving = false, error = error) }
            if (error == null) _effects.send(ProductEditEffect.Done)
        }
    }

    private fun setActive(active: Boolean) {
        val id = _state.value.productId
        if (id == 0L) return
        viewModelScope.launch {
            val g = graph()
            if (active) g.reactivateProduct(id) else g.deactivateProduct(id)
            _state.update { it.copy(active = active) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                val route = createSavedStateHandle().toRoute<ProductEditRoute>()
                ProductEditViewModel(app.container::data, route.productId)
            }
        }
    }
}
