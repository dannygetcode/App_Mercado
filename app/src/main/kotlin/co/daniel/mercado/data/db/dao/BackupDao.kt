package co.daniel.mercado.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.daniel.mercado.data.db.entity.BarcodeEntity
import co.daniel.mercado.data.db.entity.BudgetEntity
import co.daniel.mercado.data.db.entity.CategoryEntity
import co.daniel.mercado.data.db.entity.PriceHistoryEntity
import co.daniel.mercado.data.db.entity.ProductEntity
import co.daniel.mercado.data.db.entity.ShoppingListItemEntity
import co.daniel.mercado.data.db.entity.TripEntity
import co.daniel.mercado.data.db.entity.TripItemEntity

/** Volcado y reemplazo completo de la base para el respaldo (RF-12). Usar dentro de una transacción. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM category ORDER BY id") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM product ORDER BY id") suspend fun products(): List<ProductEntity>
    @Query("SELECT * FROM barcode ORDER BY code") suspend fun barcodes(): List<BarcodeEntity>
    @Query("SELECT * FROM trip ORDER BY id") suspend fun trips(): List<TripEntity>
    @Query("SELECT * FROM trip_item ORDER BY id") suspend fun tripItems(): List<TripItemEntity>
    @Query("SELECT * FROM price_history ORDER BY id") suspend fun priceHistory(): List<PriceHistoryEntity>
    @Query("SELECT * FROM budget ORDER BY year_month") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM shopping_list_item ORDER BY product_id") suspend fun shoppingList(): List<ShoppingListItemEntity>

    @Query("DELETE FROM trip_item") suspend fun deleteTripItems()
    @Query("DELETE FROM shopping_list_item") suspend fun deleteShoppingList()
    @Query("DELETE FROM price_history") suspend fun deletePriceHistory()
    @Query("DELETE FROM barcode") suspend fun deleteBarcodes()
    @Query("DELETE FROM trip") suspend fun deleteTrips()
    @Query("DELETE FROM product") suspend fun deleteProducts()
    @Query("DELETE FROM budget") suspend fun deleteBudgets()
    @Query("DELETE FROM category") suspend fun deleteCategories()

    @Insert suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert suspend fun insertProducts(rows: List<ProductEntity>)
    @Insert suspend fun insertBarcodes(rows: List<BarcodeEntity>)
    @Insert suspend fun insertTrips(rows: List<TripEntity>)
    @Insert suspend fun insertTripItems(rows: List<TripItemEntity>)
    @Insert suspend fun insertPriceHistory(rows: List<PriceHistoryEntity>)
    @Insert suspend fun insertBudgets(rows: List<BudgetEntity>)
    @Insert suspend fun insertShoppingList(rows: List<ShoppingListItemEntity>)
}
