package co.daniel.mercado.ui.components

import co.daniel.mercado.domain.rules.Months
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Fechas en español de Colombia y en la hora de Bogotá, sin depender del idioma del teléfono. */
object Dates {
    private val LOCALE: Locale = Locale.forLanguageTag("es-CO")
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", LOCALE)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE)
    private val DAY_TIME = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM · h:mm a", LOCALE)

    /** "Noviembre". */
    fun monthName(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, LOCALE).replaceFirstChar { it.titlecase(LOCALE) }

    /** "Noviembre 2026". */
    fun monthYear(month: YearMonth): String = "${monthName(month)} ${month.year}"

    /** "16 nov." */
    fun dayMonth(epochMillis: Long): String = DAY_MONTH.format(Instant.ofEpochMilli(epochMillis).atZone(Months.ZONE))

    /** "16 nov. 2026" */
    fun dayMonthYear(epochMillis: Long): String =
        DAY_MONTH_YEAR.format(Instant.ofEpochMilli(epochMillis).atZone(Months.ZONE))

    /** "Lunes 16 de noviembre · 10:30 a. m." */
    fun dayAndTime(epochMillis: Long): String =
        DAY_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(Months.ZONE)).replaceFirstChar { it.titlecase(LOCALE) }
}
