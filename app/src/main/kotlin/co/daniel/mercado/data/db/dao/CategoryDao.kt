package co.daniel.mercado.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.daniel.mercado.data.db.entity.CategoryEntity
import co.daniel.mercado.domain.model.Pesos
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

    @Query("UPDATE category SET monthly_budget = :amount WHERE id = :id")
    suspend fun setMonthlyBudget(id: Long, amount: Pesos?)
}
