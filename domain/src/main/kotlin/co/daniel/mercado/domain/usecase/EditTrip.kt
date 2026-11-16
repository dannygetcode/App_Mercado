package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.TripRepository

/** Resultado de editar una línea. */
enum class EditResult { UPDATED, REMOVED, NOT_FOUND, NOT_ALLOWED }

/** Cambia la cantidad; en 0 elimina la línea. Las líneas `KG` son siempre de 1. */
class ChangeQuantity(
    private val trips: TripRepository,
    private val session: TripSession,
) {
    suspend operator fun invoke(lineId: Long, newQuantity: Int): EditResult = session.mutate {
        val line = openLine(trips, lineId) ?: return@mutate EditResult.NOT_FOUND
        when {
            newQuantity <= 0 -> {
                trips.deleteItem(line.id)
                session.undo.record(listOf(Revert.LineDeleted(line)))
                EditResult.REMOVED
            }
            line.unitType == UnitType.KG && newQuantity != 1 -> EditResult.NOT_ALLOWED
            newQuantity == line.quantity -> EditResult.UPDATED
            else -> update(trips, session, line, line.copy(quantity = newQuantity))
        }
    }
}

/** Corrige el precio de una línea; al cerrar pasa a ser el precio de referencia. */
class ChangeLinePrice(
    private val trips: TripRepository,
    private val session: TripSession,
) {
    suspend operator fun invoke(lineId: Long, price: Pesos): EditResult {
        requireValidPrice(price)
        return session.mutate {
            val line = openLine(trips, lineId) ?: return@mutate EditResult.NOT_FOUND
            if (line.unitPrice == price) EditResult.UPDATED
            else update(trips, session, line, line.copy(unitPrice = price))
        }
    }
}

class RemoveLine(
    private val trips: TripRepository,
    private val session: TripSession,
) {
    suspend operator fun invoke(lineId: Long): EditResult = session.mutate {
        val line = openLine(trips, lineId) ?: return@mutate EditResult.NOT_FOUND
        trips.deleteItem(line.id)
        session.undo.record(listOf(Revert.LineDeleted(line)))
        EditResult.REMOVED
    }
}

/** Nombra una línea de precio suelto. Vacío la deja como "Artículo sin nombre". */
class RenameLooseLine(
    private val trips: TripRepository,
    private val session: TripSession,
) {
    suspend operator fun invoke(lineId: Long, name: String): EditResult {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.length <= MAX_NAME_LENGTH) { "nombre demasiado largo" }
        return session.mutate {
            val line = openLine(trips, lineId) ?: return@mutate EditResult.NOT_FOUND
            if (!line.isLoose) return@mutate EditResult.NOT_ALLOWED
            update(trips, session, line, line.copy(nameSnapshot = clean))
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}

/** Revierte exactamente la última acción de la compra. Una segunda llamada seguida no hace nada. */
class UndoLastAction(
    private val trips: TripRepository,
    private val products: ProductRepository,
    private val session: TripSession,
) {
    suspend operator fun invoke(): Boolean = session.mutate {
        val steps = session.undo.take() ?: return@mutate false
        if (trips.getActive() == null) return@mutate false
        steps.asReversed().forEach { step ->
            when (step) {
                is Revert.LineInserted -> trips.deleteItem(step.lineId)
                is Revert.LineUpdated -> trips.updateItem(step.before)
                is Revert.LineDeleted -> trips.insertItem(step.item)
                is Revert.BarcodeLinked ->
                    if (step.previousOwner == null) products.unlinkBarcode(step.code)
                    else products.linkBarcode(step.code, step.previousOwner, 0)
                is Revert.ProductCreated -> products.delete(step.productId)
            }
        }
        true
    }
}

private suspend fun openLine(trips: TripRepository, lineId: Long): TripItem? {
    val line = trips.getItem(lineId) ?: return null
    val trip = trips.getTrip(line.tripId) ?: return null
    return line.takeIf { trip.isOpen }
}

private suspend fun update(
    trips: TripRepository,
    session: TripSession,
    before: TripItem,
    after: TripItem,
): EditResult {
    trips.updateItem(after)
    session.undo.record(listOf(Revert.LineUpdated(before)))
    return EditResult.UPDATED
}
