package co.daniel.mercado.security

import co.daniel.mercado.ui.trip.ManualClock
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockTest {

    private val clock = ManualClock()
    private val lock = AppLock(clock)
    private val on = LockPolicy(enabled = true, graceMinutes = 5, deviceSecure = true)

    private fun away(minutes: Long, policy: LockPolicy = on) {
        lock.onBackground()
        clock.advance(minutes * 60_000)
        lock.onForeground(policy)
    }

    @Test
    fun `bloquea al inicio`() {
        assertEquals(LockState.CHECKING, lock.state.value)
        lock.onAppStart(on)
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `no bloquea dentro de la gracia`() {
        lock.onAppStart(on)
        lock.unlock()
        away(4)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `bloquea pasada la gracia`() {
        lock.onAppStart(on)
        lock.unlock()
        away(5)
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `la gracia elegida cuenta`() {
        lock.onAppStart(on)
        lock.unlock()
        away(10, on.copy(graceMinutes = 15))
        assertEquals(LockState.UNLOCKED, lock.state.value)
        away(1, on.copy(graceMinutes = 1))
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `desactivado nunca bloquea`() {
        val off = on.copy(enabled = false)
        lock.onAppStart(off)
        assertEquals(LockState.UNLOCKED, lock.state.value)
        away(60, off)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `sin bloqueo de pantalla en el telefono entra directo`() {
        val insecure = on.copy(deviceSecure = false)
        lock.onAppStart(insecure)
        assertEquals(LockState.UNLOCKED, lock.state.value)
        away(60, insecure)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `salir mientras esta bloqueada no cambia nada y volver sin haber salido tampoco`() {
        lock.onAppStart(on)
        away(60)
        assertEquals(LockState.LOCKED, lock.state.value)
        lock.unlock()
        clock.advance(60 * 60_000)
        lock.onForeground(on)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }
}
