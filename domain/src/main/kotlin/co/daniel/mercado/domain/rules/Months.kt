package co.daniel.mercado.domain.rules

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Meses calendario en la hora de Colombia (las fechas se guardan en UTC). */
object Months {

    val ZONE: ZoneId = ZoneId.of("America/Bogota")

    fun monthOf(epochMillis: Long): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(ZONE))

    /** Inicio del mes (inclusive), en milisegundos epoch. */
    fun startMillis(month: YearMonth): Long =
        month.atDay(1).atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** Inicio del día en Colombia, en milisegundos epoch. */
    fun dayStartMillis(day: LocalDate): Long = day.atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** Inicio del mes siguiente (exclusivo), en milisegundos epoch. */
    fun endMillisExclusive(month: YearMonth): Long = startMillis(month.plusMonths(1))
}
