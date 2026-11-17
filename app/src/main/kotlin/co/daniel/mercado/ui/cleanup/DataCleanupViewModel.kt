package co.daniel.mercado.ui.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.Months
import co.daniel.mercado.domain.rules.NameNormalizer
import co.daniel.mercado.domain.usecase.TripsInRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class CleanupConfirm { TRIPS, PRODUCTS, ALL }

sealed interface CleanupMessage {
    data class TripsDeleted(val count: Int) : CleanupMessage
    data class ProductsDeleted(val count: Int) : CleanupMessage
    data object AllDeleted : CleanupMessage
    data object Failed : CleanupMessage
}

data class DataCleanupUiState(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val preview: TripsInRange? = null,
    val products: List<Product> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val query: String = "",
    val productsSheet: Boolean = false,
    val datePicker: Boolean = false,
    val confirm: CleanupConfirm? = null,
    val busy: Boolean = false,
    val message: CleanupMessage? = null,
) {
    val visibleProducts: List<Product>
        get() {
            val q = NameNormalizer.normalize(query)
            return if (q.isEmpty()) products else products.filter { NameNormalizer.normalize(it.name).contains(q) }
        }
}

sealed interface DataCleanupEvent {
    data object ThisMonth : DataCleanupEvent
    data object LastMonth : DataCleanupEvent
    data object PickDates : DataCleanupEvent
    data class SetRange(val from: LocalDate, val to: LocalDate) : DataCleanupEvent
    data object OpenProducts : DataCleanupEvent
    data class Query(val text: String) : DataCleanupEvent
    data class ToggleProduct(val id: Long) : DataCleanupEvent
    data class Ask(val what: CleanupConfirm) : DataCleanupEvent
    data object Confirm : DataCleanupEvent
    data object Dismiss : DataCleanupEvent
    data object DismissMessage : DataCleanupEvent
}

/**
 * Borrar datos (pedido de Daniel): compras de un período, productos elegidos o todo. Cada borrado
 * se confirma; la pantalla recuerda exportar un respaldo antes.
 */
class DataCleanupViewModel(
    private val graph: suspend () -> DataGraph,
    private val clock: Clock,
    private val resetAll: suspend () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(DataCleanupUiState())
    val state: StateFlow<DataCleanupUiState> = _state.asStateFlow()

    fun onEvent(event: DataCleanupEvent) {
        when (event) {
            DataCleanupEvent.ThisMonth -> month(0)
            DataCleanupEvent.LastMonth -> month(1)
            DataCleanupEvent.PickDates -> _state.update { it.copy(datePicker = true) }
            is DataCleanupEvent.SetRange -> setRange(minOf(event.from, event.to), maxOf(event.from, event.to))
            DataCleanupEvent.OpenProducts -> openProducts()
            is DataCleanupEvent.Query -> _state.update { it.copy(query = event.text.take(MAX_QUERY)) }
            is DataCleanupEvent.ToggleProduct -> _state.update {
                it.copy(selected = if (event.id in it.selected) it.selected - event.id else it.selected + event.id)
            }
            is DataCleanupEvent.Ask -> _state.update { it.copy(confirm = event.what) }
            DataCleanupEvent.Confirm -> confirm()
            DataCleanupEvent.Dismiss -> _state.update { it.copy(confirm = null, productsSheet = false, datePicker = false) }
            DataCleanupEvent.DismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun month(back: Long) {
        val m = Months.monthOf(clock.nowMillis()).minusMonths(back)
        setRange(m.atDay(1), m.atEndOfMonth())
    }

    private fun setRange(from: LocalDate, to: LocalDate) {
        _state.update { it.copy(from = from, to = to, datePicker = false, preview = null) }
        viewModelScope.launch {
            val preview = graph().deleteTripsInRange.preview(from, to)
            _state.update { if (it.from == from && it.to == to) it.copy(preview = preview) else it }
        }
    }

    private fun openProducts() {
        _state.update { it.copy(productsSheet = true, query = "", selected = emptySet()) }
        viewModelScope.launch {
            val all = graph().products.all()
            _state.update { it.copy(products = all) }
        }
    }

    private fun confirm() {
        val s = _state.value
        val what = s.confirm ?: return
        _state.update { it.copy(confirm = null, busy = true, productsSheet = false) }
        viewModelScope.launch {
            val message = try {
                val g = graph()
                when (what) {
                    CleanupConfirm.TRIPS -> {
                        val from = s.from ?: return@launch
                        val to = s.to ?: return@launch
                        CleanupMessage.TripsDeleted(g.deleteTripsInRange(from, to))
                    }
                    CleanupConfirm.PRODUCTS -> CleanupMessage.ProductsDeleted(g.deleteProducts(s.selected))
                    CleanupConfirm.ALL -> {
                        resetAll()
                        CleanupMessage.AllDeleted
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                CleanupMessage.Failed
            }
            _state.update { it.copy(busy = false, message = message, selected = emptySet()) }
            s.from?.let { from -> s.to?.let { to -> setRange(from, to) } }
        }
    }

    companion object {
        private const val MAX_QUERY = 80

        /** Fecha elegida en el calendario de Material (medianoche UTC) como día local. */
        fun utcMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                DataCleanupViewModel(app.container::data, app.container.clock, app.container::resetAllData)
            }
        }
    }
}
