package co.daniel.mercado.data.db

import androidx.room.TypeConverter
import co.daniel.mercado.domain.model.Pesos
import java.time.YearMonth

/** Dinero como INTEGER (pesos enteros) y meses como TEXT `AAAA-MM`. */
class Converters {
    @TypeConverter
    fun pesosToLong(value: Pesos?): Long? = value?.value

    @TypeConverter
    fun longToPesos(value: Long?): Pesos? = value?.let(::Pesos)

    @TypeConverter
    fun yearMonthToString(value: YearMonth?): String? = value?.toString()

    @TypeConverter
    fun stringToYearMonth(value: String?): YearMonth? = value?.let(YearMonth::parse)
}
