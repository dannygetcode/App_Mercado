package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.sum

/** Aritmética de líneas de compra con detección de desbordamiento. */
object LineMath {

    /** `unitPrice × quantity`; lanza [ArithmeticException] si desborda. */
    fun lineTotal(unitPrice: Pesos, quantity: Int): Pesos = unitPrice * quantity

    fun total(items: Iterable<TripItem>): Pesos = items.map { it.lineTotal }.sum()

    /** Artículos de la compra: suma de cantidades (cada bolsa `KG` cuenta 1). */
    fun articleCount(items: Iterable<TripItem>): Int = items.sumOf { it.quantity }
}
