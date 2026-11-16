package co.daniel.mercado.domain.model

sealed interface ScanOutcome {
    data class Added(val lineId: Long, val productName: String, val unitPrice: Pesos) : ScanOutcome
    data class Incremented(val lineId: Long, val productName: String, val newQuantity: Int) : ScanOutcome
    /** Producto `KG`: hay que pedir el precio de la etiqueta. No se agrega línea. */
    data class NeedsLabelPrice(val productId: Long, val productName: String) : ScanOutcome
    /** Código válido sin producto: se abre la hoja "Producto nuevo". */
    data class Unknown(val code: String) : ScanOutcome
    /** Rebote o dígito de control inválido. */
    data object Ignored : ScanOutcome
    data object NoActiveTrip : ScanOutcome
}
