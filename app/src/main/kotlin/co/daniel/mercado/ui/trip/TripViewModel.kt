package co.daniel.mercado.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.ActiveTrip
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.BudgetRules
import co.daniel.mercado.domain.rules.LabelStabilizer
import co.daniel.mercado.domain.usecase.EditResult
import co.daniel.mercado.scanner.BarcodeSource
import co.daniel.mercado.scanner.LabelRead
import co.daniel.mercado.scanner.ScanFeedback
import co.daniel.mercado.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Compra en curso (L4-T2): conecta el escáner con `HandleScan`, las hojas con sus casos de uso,
 * avisos con "Deshacer", resaltado, cruces del semáforo y pausa automática de la cámara.
 * El estado de la compra vive en la base (RNF-09): el ViewModel solo lo observa.
 */
class TripViewModel(
    private val graph: suspend () -> DataGraph,
    private val source: BarcodeSource,
    private val feedback: ScanFeedback,
    private val clock: Clock,
    private val timing: Timing = Timing(),
) : ViewModel() {

    data class Timing(
        val highlightMs: Long = 1_000,
        val noticeMs: Long = 4_000,
        val autoPauseMs: Long = 120_000,
        val stalePriceMs: Long = 60L * 24 * 60 * 60 * 1_000,
        val labelPreviewMs: Long = 1_500,
    )

    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private val noticeIds = AtomicLong()
    private var noticeJob: Job? = null
    private var highlightJob: Job? = null
    private var autoPauseJob: Job? = null
    private var searchJob: Job? = null
    private var lastLight: TrafficLight? = null
    private val staleCache = HashMap<Long, StalePrice?>()
    private val labelStabilizer = LabelStabilizer(clock)

    private data class StalePrice(val price: Pesos, val at: Long)

    init {
        viewModelScope.launch {
            val g = graph()
            launch { g.observeActiveTrip().collect(::onTrip) }
            launch { g.observeFavorites().collect { favs -> _state.update { it.copy(favorites = favs) } } }
            launch { source.codes.collect(::onCode) }
            launch { g.observeShoppingList.pendingForTrip().collect { l -> _state.update { it.copy(listPending = l) } } }
            restartAutoPause()
        }
    }

    // ---- Compra observada ----

    private suspend fun onTrip(trip: ActiveTrip?) {
        val previous = lastLight
        lastLight = trip?.trafficLight
        if (trip != null && previous != null) {
            when (BudgetRules.escalation(previous, trip.trafficLight)) {
                TrafficLight.YELLOW -> feedback.budgetWarning(red = false)
                TrafficLight.RED -> feedback.budgetWarning(red = true)
                else -> Unit
            }
        }
        val stale = trip?.let { staleProducts(it) }.orEmpty()
        _state.update { it.copy(loading = false, trip = trip, staleProductIds = stale) }
    }

    private suspend fun staleProducts(trip: ActiveTrip): Set<Long> {
        val g = graph()
        val now = clock.nowMillis()
        return trip.items.mapNotNull { line ->
            val productId = line.productId ?: return@mapNotNull null
            val point = staleCache.getOrPut(productId) {
                g.products.lastPricePoint(productId)?.let { StalePrice(it.price, it.at) }
            }
            productId.takeIf {
                point != null && point.price == line.unitPrice && now - point.at > timing.stalePriceMs
            }
        }.toSet()
    }

    // ---- Escáner ----

    fun onCode(code: String) {
        val current = _state.value
        if (current.trip == null) return
        // Con una hoja que pide datos abierta, las lecturas no cuentan (la cámara sigue viva).
        val sheet = current.sheet
        if (sheet != null && sheet !is TripSheet.Picker) return
        viewModelScope.launch {
            val outcome = guarded { graph().handleScan(code) } ?: return@launch
            if (outcome !is ScanOutcome.Ignored) restartAutoPause()
            handleOutcome(outcome, fromScan = true)
        }
    }

    private var previewJob: Job? = null

    /** "Precio detectado: $X" sobre el visor; se apaga si deja de verse. */
    private fun showLabelPreview(price: Pesos) {
        _state.update { it.copy(labelPreview = price) }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(timing.labelPreviewMs)
            _state.update { it.copy(labelPreview = null) }
        }
    }

    /** Cambia entre leer códigos y leer la etiqueta del estante. */
    fun toggleLabelMode() {
        labelStabilizer.reset()
        previewJob?.cancel()
        _state.update { it.copy(labelMode = !it.labelMode, labelPreview = null) }
        restartAutoPause()
    }

    /**
     * Etiqueta del estante leída (modo etiqueta). Nunca suma sola: abre el teclado con el precio
     * leído para confirmar con "Sumar" (la lectura de texto puede equivocarse).
     */
    fun onLabel(read: LabelRead) {
        val current = _state.value
        if (current.trip == null || !current.labelMode || current.sheet != null) return
        read.price?.let { showLabelPreview(it) }
        val accepted = labelStabilizer.offer(read.code, read.price) ?: return
        val price = accepted.price
        restartAutoPause()
        viewModelScope.launch {
            val code = accepted.code
            val product = code?.let { guarded { graph().products.findByBarcode(it) } }
            if (_state.value.sheet != null) return@launch
            feedback.accepted()
            when {
                product != null -> openSheet(TripSheet.Keypad(KeypadPurpose.ShelfPrice(product.id, product.name), initial = price))
                code != null -> {
                    _state.update { it.copy(query = "", results = emptyList()) }
                    openSheet(TripSheet.NewProduct(code, price = price))
                }
                else -> openSheet(TripSheet.Keypad(KeypadPurpose.LoosePrice, initial = price))
            }
        }
    }

    private fun handleOutcome(outcome: ScanOutcome, fromScan: Boolean) {
        when (outcome) {
            is ScanOutcome.Added -> {
                feedback.accepted()
                highlight(outcome.lineId)
                notice(NoticeKind.ADDED, outcome.productName, amount = outcome.unitPrice)
            }
            is ScanOutcome.Incremented -> {
                feedback.accepted()
                highlight(outcome.lineId)
                notice(NoticeKind.INCREMENTED, outcome.productName, quantity = outcome.newQuantity)
            }
            is ScanOutcome.NeedsLabelPrice -> {
                if (fromScan) feedback.accepted()
                openSheet(TripSheet.Keypad(KeypadPurpose.LabelPrice(outcome.productId, outcome.productName)))
            }
            is ScanOutcome.Unknown -> {
                feedback.unknown()
                _state.update { it.copy(query = "", results = emptyList()) }
                openSheet(TripSheet.NewProduct(outcome.code))
            }
            ScanOutcome.Ignored -> Unit
            ScanOutcome.NoActiveTrip -> _state.update { it.copy(trip = null, loading = false) }
        }
    }

    // ---- Hojas ----

    fun openLooseKeypad() = openSheet(TripSheet.Keypad(KeypadPurpose.LoosePrice))

    /** Tocar la tarjeta del total: cambiar el presupuesto de esta compra. */
    fun openLimitKeypad() = openSheet(TripSheet.Keypad(KeypadPurpose.TripLimit, initial = _state.value.trip?.limit))

    fun openPicker(tab: PickerTab = PickerTab.FAVORITES) {
        if (tab == PickerTab.SEARCH) _state.update { it.copy(query = "", results = emptyList()) }
        openSheet(TripSheet.Picker(tab))
    }

    fun openEditLine(lineId: Long) = openSheet(TripSheet.EditLine(lineId))

    fun openFixPrice(lineId: Long) {
        val line = _state.value.trip?.items?.firstOrNull { it.id == lineId } ?: return
        openSheet(TripSheet.Keypad(KeypadPurpose.FixPrice(lineId, line.nameSnapshot), initial = line.unitPrice))
    }

    fun dismissSheet() {
        _state.update { it.copy(sheet = null, query = "", results = emptyList()) }
        restartAutoPause()
    }

    private fun openSheet(sheet: TripSheet) {
        _state.update { it.copy(sheet = sheet) }
    }

    /** "Sumar" del teclado; el uso depende de para qué se abrió. La hoja se cierra sola. */
    fun submitKeypad(price: Pesos) {
        val sheet = _state.value.sheet as? TripSheet.Keypad ?: return
        if (sheet.purpose == KeypadPurpose.TripLimit) {
            dismissSheet()
            viewModelScope.launch { guarded { graph().setTripLimit(price.takeIf { it.isPositive }) } }
            return
        }
        if (!validPrice(price)) return
        dismissSheet()
        viewModelScope.launch {
            val g = graph()
            when (val purpose = sheet.purpose) {
                KeypadPurpose.TripLimit -> Unit
                KeypadPurpose.LoosePrice -> guarded { g.addLoosePrice(price) }?.let { handleOutcome(it, false) }
                is KeypadPurpose.LabelPrice ->
                    guarded { g.addWeighedItem(purpose.productId, price) }?.let { handleOutcome(it, false) }
                is KeypadPurpose.ShelfPrice ->
                    guarded { g.addProductToTrip(purpose.productId, price) }?.let { handleOutcome(it, false) }
                is KeypadPurpose.CreateProduct ->
                    guarded { g.addUnknownScanned(null, price, name = purpose.name) }?.let { handleOutcome(it, false) }
                is KeypadPurpose.FixPrice -> {
                    val result = guarded { g.changeLinePrice(purpose.lineId, price) }
                    if (result == EditResult.UPDATED) notice(NoticeKind.PRICE_CHANGED, purpose.name, amount = price)
                }
            }
        }
    }

    /** "Sumar" / "Asociar y sumar" de la hoja Producto nuevo. */
    fun submitNewProduct(price: Pesos) {
        val sheet = _state.value.sheet as? TripSheet.NewProduct ?: return
        if (!validPrice(price)) return
        val typedName = _state.value.query.trim().takeIf { sheet.selected == null && it.isNotEmpty() }
        dismissSheet()
        viewModelScope.launch {
            guarded { graph().addUnknownScanned(sheet.code, price, sheet.selected?.id, typedName) }
                ?.let { handleOutcome(it, false) }
        }
    }

    /** Elegir un producto del autocompletado en "Producto nuevo": precio de su referencia. */
    fun selectSuggestion(productId: Long) {
        val sheet = _state.value.sheet as? TripSheet.NewProduct ?: return
        val product = _state.value.results.firstOrNull { it.id == productId } ?: return
        _state.update { it.copy(sheet = sheet.copy(selected = product), query = product.name, results = emptyList()) }
    }

    fun clearSuggestion() {
        val sheet = _state.value.sheet as? TripSheet.NewProduct ?: return
        _state.update { it.copy(sheet = sheet.copy(selected = null)) }
    }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        val sheet = _state.value.sheet
        if (sheet is TripSheet.NewProduct && sheet.selected != null && sheet.selected.name != text) clearSuggestion()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val results = if (text.isBlank()) emptyList() else guarded { graph().searchProducts(text) }.orEmpty()
            _state.update { if (it.query == text) it.copy(results = results) else it }
        }
    }

    /** Tocar un favorito o un resultado: UNIT se agrega (la hoja sigue abierta); KG pide la etiqueta. */
    fun pickProduct(productId: Long) {
        restartAutoPause()
        viewModelScope.launch {
            val outcome = guarded { graph().addProductToTrip(productId) } ?: return@launch
            if (outcome is ScanOutcome.NeedsLabelPrice) {
                handleOutcome(outcome, false)
            } else {
                handleOutcome(outcome, false)
                val sheet = _state.value.sheet
                if (sheet is TripSheet.Picker && sheet.tab == PickerTab.SEARCH) {
                    _state.update { it.copy(query = "", results = emptyList()) }
                }
            }
        }
    }

    /** "Crear «texto»" cuando la búsqueda no encuentra nada. */
    fun createFromSearch() {
        val name = _state.value.query.trim()
        if (name.isEmpty()) return
        _state.update { it.copy(query = "", results = emptyList()) }
        openSheet(TripSheet.Keypad(KeypadPurpose.CreateProduct(name)))
    }

    // ---- Edición de líneas ----

    fun increment(lineId: Long) = changeQuantityBy(lineId, +1)

    fun decrement(lineId: Long) = changeQuantityBy(lineId, -1)

    private fun changeQuantityBy(lineId: Long, delta: Int) {
        val line = _state.value.trip?.items?.firstOrNull { it.id == lineId } ?: return
        restartAutoPause()
        viewModelScope.launch {
            when (guarded { graph().changeQuantity(lineId, line.quantity + delta) }) {
                EditResult.REMOVED -> notice(NoticeKind.REMOVED, line.nameSnapshot)
                EditResult.UPDATED -> notice(NoticeKind.QUANTITY, line.nameSnapshot, quantity = line.quantity + delta)
                else -> Unit
            }
        }
    }

    /** Deslizar a la izquierda o "Eliminar" en la hoja de edición. */
    fun removeLine(lineId: Long) {
        val line = _state.value.trip?.items?.firstOrNull { it.id == lineId } ?: return
        if ((_state.value.sheet as? TripSheet.EditLine)?.lineId == lineId) dismissSheet()
        viewModelScope.launch {
            if (guarded { graph().removeLine(lineId) } == EditResult.REMOVED) {
                notice(NoticeKind.REMOVED, line.nameSnapshot)
            }
        }
    }

    fun setQuantity(lineId: Long, quantity: Int) {
        val line = _state.value.trip?.items?.firstOrNull { it.id == lineId } ?: return
        if (quantity == line.quantity) return
        changeQuantityBy(lineId, quantity - line.quantity)
    }

    fun renameLooseLine(lineId: Long, name: String) {
        viewModelScope.launch {
            if (guarded { graph().renameLooseLine(lineId, name) } == EditResult.UPDATED) {
                notice(NoticeKind.RENAMED, name.trim())
            }
        }
    }

    fun undo() {
        _state.update { it.copy(notice = null) }
        viewModelScope.launch {
            if (guarded { graph().undoLastAction() } == true) {
                notice(NoticeKind.UNDONE, undoable = false)
            }
        }
    }


    // ---- Cámara ----

    fun toggleTorch() = _state.update { it.copy(torchOn = !it.torchOn) }

    fun toggleCameraPause() {
        val paused = _state.value.cameraPausedByUser || _state.value.cameraAutoPaused
        _state.update { it.copy(cameraPausedByUser = !paused, cameraAutoPaused = false, torchOn = false) }
        if (paused) restartAutoPause()
    }

    fun resumeCamera() {
        _state.update { it.copy(cameraPausedByUser = false, cameraAutoPaused = false) }
        restartAutoPause()
    }

    private fun restartAutoPause() {
        autoPauseJob?.cancel()
        autoPauseJob = viewModelScope.launch {
            delay(timing.autoPauseMs)
            _state.update { it.copy(cameraAutoPaused = true, torchOn = false) }
        }
    }

    // ---- Avisos ----

    private fun highlight(lineId: Long) {
        highlightJob?.cancel()
        _state.update { it.copy(highlightLineId = lineId) }
        highlightJob = viewModelScope.launch {
            delay(timing.highlightMs)
            _state.update { if (it.highlightLineId == lineId) it.copy(highlightLineId = null) else it }
        }
    }

    private fun notice(
        kind: NoticeKind,
        name: String = "",
        amount: Pesos? = null,
        quantity: Int = 0,
        undoable: Boolean = kind != NoticeKind.UNDONE && kind != NoticeKind.INVALID_PRICE && kind != NoticeKind.ERROR,
    ) {
        val notice = TripNotice(noticeIds.incrementAndGet(), kind, name, amount, quantity, undoable)
        noticeJob?.cancel()
        _state.update { it.copy(notice = notice) }
        noticeJob = viewModelScope.launch {
            delay(timing.noticeMs)
            _state.update { if (it.notice?.id == notice.id) it.copy(notice = null) else it }
        }
    }

    private fun validPrice(price: Pesos): Boolean {
        if (price.isPositive && price <= Pesos.KEYPAD_MAX) return true
        notice(NoticeKind.INVALID_PRICE)
        return false
    }

    /** Un fallo de un caso de uso no tumba la compra: se avisa y se sigue. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        AppLog.w("TripViewModel: acción rechazada", e)
        notice(NoticeKind.ERROR)
        null
    } catch (e: IllegalStateException) {
        AppLog.w("TripViewModel: acción fallida", e)
        notice(NoticeKind.ERROR)
        null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MercadoApp
                val container = app.container
                TripViewModel(
                    graph = container::data,
                    source = container.tripBarcodeSource,
                    feedback = container.scanFeedback,
                    clock = container.clock,
                )
            }
        }
    }
}
