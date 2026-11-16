package co.daniel.mercado.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import co.daniel.mercado.data.db.entity.BarcodeEntity
import co.daniel.mercado.data.db.entity.PriceHistoryEntity
import co.daniel.mercado.data.db.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    /** Consulta caliente del escaneo: búsqueda por clave primaria del código. */
    @Query("SELECT p.* FROM barcode b JOIN product p ON p.id = b.product_id WHERE b.code = :code")
    suspend fun findByBarcode(code: String): ProductEntity?

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    /** [pattern] ya viene con `%` y los comodines del usuario escapados con `\`. */
    @Query(
        "SELECT * FROM product WHERE name_norm LIKE :pattern ESCAPE '\\' AND active = 1 " +
            "ORDER BY favorite DESC, name LIMIT :limit",
    )
    suspend fun search(pattern: String, limit: Int): List<ProductEntity>

    @Query("SELECT * FROM product WHERE name_norm = :nameNorm ORDER BY id LIMIT 1")
    suspend fun findByNameNorm(nameNorm: String): ProductEntity?

    @Query("SELECT * FROM product WHERE favorite = 1 AND active = 1 ORDER BY favorite_order, name")
    fun observeFavorites(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM product")
    suspend fun count(): Int

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("DELETE FROM product WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT product_id FROM barcode WHERE code = :code")
    suspend fun barcodeOwner(code: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBarcode(barcode: BarcodeEntity)

    @Query("DELETE FROM barcode WHERE code = :code")
    suspend fun deleteBarcode(code: String)

    @Query("DELETE FROM barcode WHERE product_id = :productId")
    suspend fun deleteBarcodesOf(productId: Long)

    @Query("SELECT code FROM barcode WHERE product_id = :productId ORDER BY code")
    suspend fun barcodesOf(productId: Long): List<String>

    @Query("SELECT * FROM price_history WHERE product_id = :productId ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun lastPricePoint(productId: Long): PriceHistoryEntity?

    @Query("SELECT * FROM price_history WHERE product_id = :productId ORDER BY at DESC, id DESC")
    suspend fun priceHistory(productId: Long): List<PriceHistoryEntity>

    @Insert
    suspend fun insertPricePoint(point: PriceHistoryEntity): Long

    @Query("DELETE FROM price_history WHERE product_id = :productId")
    suspend fun deletePriceHistoryOf(productId: Long)

    @Query("DELETE FROM shopping_list_item WHERE product_id = :productId")
    suspend fun deleteListItemOf(productId: Long)
}
