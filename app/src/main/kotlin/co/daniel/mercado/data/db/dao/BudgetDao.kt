package co.daniel.mercado.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.daniel.mercado.data.db.entity.BudgetEntity
import java.time.YearMonth

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget WHERE year_month = :month")
    suspend fun get(month: YearMonth): BudgetEntity?

    /** `AAAA-MM` ordena igual como texto que como fecha. */
    @Query("SELECT * FROM budget WHERE year_month < :month ORDER BY year_month DESC LIMIT 1")
    suspend fun latestBefore(month: YearMonth): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)
}
