package co.daniel.mercado.ui.trip

import co.daniel.mercado.domain.model.ActiveTrip
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.ui.components.TotalCardModel

/** Estado de la pantalla "Compra en curso". */
data class TripUiState(
    /** La base aún se está abriendo o la compra aún no se ha leído. */
    val loading: Boolean = true,
    val trip: ActiveTrip? = null,
    /** Productos de la compra cuyo precio de referencia tiene más de 60 días (punto "precio viejo"). */
    val staleProductIds: Set<Long> = emptySet(),
    /** Línea recién agregada o incrementada, resaltada 1 s. */
    val highlightLineId: Long? = null,
    val notice: TripNotice? = null,
    val sheet: TripSheet? = null,
    val cameraPausedByUser: Boolean = false,
    /** Pausa sola tras 2 min sin lecturas (04-ux). */
    val cameraAutoPaused: Boolean = false,
    val torchOn: Boolean = false,
    /** La cámara lee la etiqueta del estante (texto + código) en vez de solo códigos. */
    val labelMode: Boolean = false,
    val favorites: List<Product> = emptyList(),
    /** Texto del buscador (hoja Buscar) o del nombre en "Producto nuevo". */
    val query: String = "",
    val results: List<Product> = emptyList(),
) {
    /** Sin compra abierta tras cargar: la pantalla vuelve a Inicio. */
    val noActiveTrip: Boolean get() = !loading && trip == null

    /** La cámara se suelta en pausa manual o automática y mientras está abierta "Producto nuevo". */
    val cameraPaused: Boolean
        get() = cameraPausedByUser || cameraAutoPaused || sheet is TripSheet.NewProduct

    val totalCard: TotalCardModel?
        get() = trip?.let {
            TotalCardModel(it.total, it.articleCount, it.limit, it.remaining, it.trafficLight, it.percentOfLimit)
        }
}

/** Aviso de la última acción (4 s). [undoable]: muestra "Deshacer". */
data class TripNotice(
    val id: Long,
    val kind: NoticeKind,
    val name: String = "",
    val amount: Pesos? = null,
    val quantity: Int = 0,
    val undoable: Boolean = true,
)

enum class NoticeKind { ADDED, INCREMENTED, QUANTITY, REMOVED, PRICE_CHANGED, RENAMED, UNDONE, INVALID_PRICE, ERROR }

/** Hojas inferiores de la compra (04-ux). */
sealed interface TripSheet {
    data class Keypad(val purpose: KeypadPurpose, val initial: Pesos? = null) : TripSheet

    /** Código desconocido: precio primero, nombre opcional con autocompletado. */
    data class NewProduct(val code: String, val selected: Product? = null, val price: Pesos? = null) : TripSheet

    data class Picker(val tab: PickerTab) : TripSheet

    data class EditLine(val lineId: Long) : TripSheet
}

sealed interface KeypadPurpose {
    /** RF-17: precio suelto. */
    data object LoosePrice : KeypadPurpose

    /** Producto KG: precio total de la etiqueta de balanza. */
    data class LabelPrice(val productId: Long, val productName: String) : KeypadPurpose

    /** Corregir el precio unitario de una línea. */
    data class FixPrice(val lineId: Long, val name: String) : KeypadPurpose

    /** Producto identificado en la etiqueta del estante, con el precio leído prellenado. */
    data class ShelfPrice(val productId: Long, val productName: String) : KeypadPurpose

    /** "Crear «texto»" desde la búsqueda. */
    data class CreateProduct(val name: String) : KeypadPurpose
}

enum class PickerTab { FAVORITES, SEARCH }
