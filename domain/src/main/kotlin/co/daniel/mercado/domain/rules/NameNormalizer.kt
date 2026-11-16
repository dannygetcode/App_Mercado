package co.daniel.mercado.domain.rules

import java.text.Normalizer
import java.util.Locale

/** Minúsculas, sin tildes, espacios colapsados. Base de la búsqueda y de la importación idempotente. */
object NameNormalizer {

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val SPACES = Regex("\\s+")

    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(SPACES, " ")
            .trim()
}
