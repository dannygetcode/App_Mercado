package co.daniel.mercado.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import co.daniel.mercado.data.db.entity.ClosedLineRow
import co.daniel.mercado.data.db.entity.ClosedTripRow
import co.daniel.mercado.data.db.entity.TripEntity
import co.daniel.mercado.data.db.entity.TripItemEntity
import co.daniel.mercado.data.db.entity.TripItemWithProduct
import co.daniel.mercado.data.db.entity.TripWithItemsRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Transaction
    @Query("SELECT * FROM trip WHERE closed_at IS NULL ORDER BY id LIMIT 1")
    fun observeOpen(): Flow<TripWithItemsRelation?>

    @Transaction
    @Query("SELECT * FROM trip WHERE closed_at IS NULL ORDER BY id LIMIT 1")
    suspend fun getOpen(): TripWithItemsRelation?

    @Transaction
    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun getWithItems(id: Long): TripWithItemsRelation?

    /** Compras cerradas con su número de artículos (suma de cantidades), la más reciente primero. */
    @Query(
        "SELECT t.*, COALESCE((SELECT SUM(i.quantity) FROM trip_item i WHERE i.trip_id = t.id), 0) AS article_count " +
            "FROM trip t WHERE t.closed_at IS NOT NULL ORDER BY t.closed_at DESC, t.id DESC",
    )
    fun observeClosed(): Flow<List<ClosedTripRow>>

    /** Líneas de compras cerradas en `[from, to)` con el tipo de unidad de su producto (sueltas: UNIT). */
    @Query(
        "SELECT i.trip_id AS tripId, t.closed_at AS closedAt, i.product_id AS productId, i.quantity AS quantity, " +
            "i.unit_price AS unitPrice, COALESCE(p.unit_type, 'UNIT') AS unitType " +
            "FROM trip_item i JOIN trip t ON t.id = i.trip_id LEFT JOIN product p ON p.id = i.product_id " +
            "WHERE t.closed_at IS NOT NULL AND t.closed_at >= :fromMillis AND t.closed_at < :toMillis " +
            "ORDER BY t.closed_at, i.position",
    )
    suspend fun closedLines(fromMillis: Long, toMillis: Long): List<ClosedLineRow>

    @Query("SELECT MAX(closed_at) FROM trip WHERE closed_at IS NOT NULL")
    suspend fun lastClosedAt(): Long?

    @Query("SELECT COUNT(*) FROM trip WHERE closed_at IS NULL")
    suspend fun countOpen(): Int

    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Transaction
    @Query("SELECT * FROM trip_item WHERE id = :id")
    suspend fun getItem(id: Long): TripItemWithProduct?

    @Insert
    suspend fun insertItem(item: TripItemEntity): Long

    @Update
    suspend fun updateItem(item: TripItemEntity)

    @Query("DELETE FROM trip_item WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM trip_item WHERE trip_id = :tripId")
    suspend fun nextPosition(tripId: Long): Int

    /** Total de una compra: suma de precio × cantidad de sus líneas. */
    @Query("SELECT COALESCE(SUM(unit_price * quantity), 0) FROM trip_item WHERE trip_id = :tripId")
    suspend fun total(tripId: Long): Long

    /** Gastado del mes: compras cerradas en `[from, to)`, el pagado si existe, si no el estimado. */
    @Query(
        "SELECT COALESCE(SUM(COALESCE(total_paid, total_estimated)), 0) FROM trip " +
            "WHERE closed_at IS NOT NULL AND closed_at >= :fromMillis AND closed_at < :toMillis",
    )
    suspend fun spentBetween(fromMillis: Long, toMillis: Long): Long

    @Query(
        "SELECT store FROM trip WHERE closed_at IS NOT NULL AND store IS NOT NULL AND store != '' " +
            "GROUP BY store ORDER BY MAX(closed_at) DESC LIMIT :limit",
    )
    suspend fun recentStores(limit: Int): List<String>
}
