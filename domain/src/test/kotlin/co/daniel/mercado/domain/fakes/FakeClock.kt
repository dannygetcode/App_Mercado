package co.daniel.mercado.domain.fakes

import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.Months
import java.time.LocalDateTime

class FakeClock(var now: Long = bogota(2026, 11, 16, 10, 0)) : Clock {
    override fun nowMillis(): Long = now

    fun advance(ms: Long) {
        now += ms
    }

    companion object {
        fun bogota(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
            LocalDateTime.of(year, month, day, hour, minute).atZone(Months.ZONE).toInstant().toEpochMilli()
    }
}
