package co.daniel.mercado.domain.port

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.TripWithItems
import kotlinx.coroutines.flow.Flow

/** Compras y sus líneas. Como máximo una compra abierta (`closedAt == null`). */
interface TripRepository {

    /** La compra abierta con sus líneas; se emite de nuevo con cada cambio. */
    fun observeActive(): Flow<TripWithItems?>

    suspend fun getActive(): TripWithItems?

    suspend fun getTrip(id: Long): Trip?

    suspend fun insertTrip(trip: Trip): Long

    suspend fun updateTrip(trip: Trip)

    /** Borra la compra y sus líneas (cascada). */
    suspend fun deleteTrip(id: Long)

    suspend fun getItem(id: Long): TripItem?

    /** Inserta la línea; si `item.id != 0` conserva ese id (para deshacer un borrado). */
    suspend fun insertItem(item: TripItem): Long

    suspend fun updateItem(item: TripItem)

    suspend fun deleteItem(id: Long)

    /** Siguiente posición libre (orden de inserción) en la compra. */
    suspend fun nextPosition(tripId: Long): Int

    /** Suma de `COALESCE(totalPaid, totalEstimated)` de las compras cerradas en `[fromMillis, toMillis)`. */
    suspend fun spentBetween(fromMillis: Long, toMillis: Long): Pesos
}
