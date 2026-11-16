package co.daniel.mercado.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.daniel.mercado.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sort_order, name")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM category ORDER BY sort_order, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long
}
