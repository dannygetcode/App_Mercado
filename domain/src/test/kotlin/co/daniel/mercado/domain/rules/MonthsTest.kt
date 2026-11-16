package co.daniel.mercado.domain.rules

import co.daniel.mercado.domain.fakes.FakeClock
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthsTest {

    @Test
    fun `una compra del 30 de noviembre a las 2330 en Bogota cuenta en noviembre`() {
        val millis = FakeClock.bogota(2026, 11, 30, 23, 30)
        assertEquals(YearMonth.of(2026, 11), Months.monthOf(millis))
    }

    @Test
    fun `el rango del mes empieza y termina a medianoche de Bogota`() {
        val nov = YearMonth.of(2026, 11)
        val late = FakeClock.bogota(2026, 11, 30, 23, 30)
        assertTrue(late >= Months.startMillis(nov) && late < Months.endMillisExclusive(nov))
        assertEquals(FakeClock.bogota(2026, 12, 1, 0, 0), Months.endMillisExclusive(nov))
    }
}
