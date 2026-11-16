package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.port.TransactionRunner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/** Cómo revertir un paso de una acción. Una acción del usuario puede tener varios pasos. */
sealed interface Revert {
    data class LineInserted(val lineId: Long) : Revert
    data class LineUpdated(val before: TripItem) : Revert
    data class LineDeleted(val item: TripItem) : Revert
    data class BarcodeLinked(val code: String, val previousOwner: Long?) : Revert
    data class ProductCreated(val productId: Long) : Revert
}

/** Deshacer de tamaño 1: solo la última acción de la compra (en memoria). */
class UndoBuffer {
    private val last = AtomicReference<List<Revert>?>(null)

    fun record(steps: List<Revert>) {
        last.set(steps.takeIf { it.isNotEmpty() })
    }

    fun take(): List<Revert>? = last.getAndSet(null)

    fun clear() {
        last.set(null)
    }

    val canUndo: Boolean get() = last.get() != null
}

/**
 * Serializa las modificaciones de la compra (dos lecturas seguidas no deben crear dos líneas del
 * mismo producto) y ejecuta cada una en una transacción. Una instancia compartida por la app.
 */
class TripSession(
    private val transactions: TransactionRunner,
    val undo: UndoBuffer = UndoBuffer(),
) {
    private val mutex = Mutex()

    suspend fun <T> mutate(block: suspend () -> T): T =
        mutex.withLock { transactions.inTransaction(block) }
}
