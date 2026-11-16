package co.daniel.mercado.domain.model

/** Dinero en pesos colombianos enteros (D-05). Las operaciones fallan ante desbordamiento. */
@JvmInline
value class Pesos(val value: Long) : Comparable<Pesos> {

    operator fun plus(other: Pesos): Pesos = Pesos(Math.addExact(value, other.value))

    operator fun minus(other: Pesos): Pesos = Pesos(Math.subtractExact(value, other.value))

    operator fun times(factor: Int): Pesos = Pesos(Math.multiplyExact(value, factor.toLong()))

    override fun compareTo(other: Pesos): Int = value.compareTo(other.value)

    val isPositive: Boolean get() = value > 0

    override fun toString(): String = "Pesos($value)"

    companion object {
        val ZERO = Pesos(0)

        /** Tope del teclado de precios: 9 dígitos. */
        val KEYPAD_MAX = Pesos(999_999_999)
    }
}

fun Iterable<Pesos>.sum(): Pesos = fold(Pesos.ZERO) { acc, p -> acc + p }

fun maxOf(a: Pesos, b: Pesos): Pesos = if (a >= b) a else b
