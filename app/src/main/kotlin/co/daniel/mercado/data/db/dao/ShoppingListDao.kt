package co.daniel.mercado.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.daniel.mercado.data.db.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_list_item ORDER BY product_id")
    fun observeAll(): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_item ORDER BY product_id")
    suspend fun all(): List<ShoppingListItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingListItemEntity)

    @Query("DELETE FROM shopping_list_item WHERE product_id = :productId")
    suspend fun delete(productId: Long)
}
