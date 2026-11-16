package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.model.Pesos

/** Formato fijo de pesos colombianos (`$5.900`, `-$300`), sin depender del `Locale` del sistema. */
object MoneyFormat {

    fun format(pesos: Pesos): String {
        val raw = pesos.value
        val digits = if (raw < 0) raw.toString().substring(1) else raw.toString()
        val grouped = groupThousands(digits)
        return if (raw < 0) "-$$grouped" else "$$grouped"
    }

    /** Solo los dígitos agrupados, sin signo de pesos (para el visor del teclado). */
    fun groupThousands(digits: String): String {
        val sb = StringBuilder(digits.length + digits.length / 3)
        digits.forEachIndexed { index, c ->
            if (index > 0 && (digits.length - index) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }
}
