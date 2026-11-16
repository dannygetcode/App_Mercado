package co.daniel.mercado.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.UnitType
import java.time.YearMonth

@Entity(tableName = "category", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "monthly_budget") val monthlyBudget: Pesos? = null,
)

@Entity(
    tableName = "product",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"])],
    indices = [Index("name_norm"), Index("favorite", "favorite_order"), Index("category_id")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_norm") val nameNorm: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "unit_type") val unitType: UnitType,
    @ColumnInfo(name = "ref_price") val refPrice: Pesos,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(name = "favorite_order", defaultValue = "0") val favoriteOrder: Int = 0,
    @ColumnInfo(defaultValue = "1") val active: Boolean = true,
    @ColumnInfo(name = "needs_review", defaultValue = "0") val needsReview: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "barcode",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"])],
    indices = [Index("product_id")],
)
data class BarcodeEntity(
    @PrimaryKey val code: String,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "trip", indices = [Index("closed_at")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "closed_at") val closedAt: Long? = null,
    val store: String? = null,
    @ColumnInfo(name = "budget_limit") val budgetLimit: Pesos? = null,
    @ColumnInfo(name = "total_estimated", defaultValue = "0") val totalEstimated: Pesos = Pesos.ZERO,
    @ColumnInfo(name = "total_paid") val totalPaid: Pesos? = null,
)

@Entity(
    tableName = "trip_item",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"]),
    ],
    indices = [Index("trip_id"), Index("product_id")],
)
data class TripItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    @ColumnInfo(name = "product_id") val productId: Long?,
    @ColumnInfo(name = "name_snapshot") val nameSnapshot: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Pesos,
    @ColumnInfo(name = "weight_grams") val weightGrams: Int? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    val position: Int,
)

@Entity(
    tableName = "price_history",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"])],
    indices = [Index("product_id", "at")],
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    val price: Pesos,
    val at: Long,
    val source: PriceSource,
)

@Entity(tableName = "budget")
data class BudgetEntity(
    @PrimaryKey @ColumnInfo(name = "year_month") val yearMonth: YearMonth,
    val amount: Pesos,
)

@Entity(
    tableName = "shopping_list_item",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"])],
)
data class ShoppingListItemEntity(
    @PrimaryKey @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "suggested_qty", defaultValue = "1") val suggestedQty: Int = 1,
    @ColumnInfo(defaultValue = "0") val checked: Boolean = false,
    val source: String,
    @ColumnInfo(name = "dismissed_until") val dismissedUntil: Long? = null,
)

/** Línea con el tipo de unidad de su producto (precio suelto = sin producto). */
data class TripItemWithProduct(
    @Embedded val item: TripItemEntity,
    @Relation(parentColumn = "product_id", entityColumn = "id")
    val product: ProductEntity?,
)

/** Compra con sus líneas, en una sola consulta `@Transaction`. */
data class TripWithItemsRelation(
    @Embedded val trip: TripEntity,
    @Relation(entity = TripItemEntity::class, parentColumn = "id", entityColumn = "trip_id")
    val items: List<TripItemWithProduct>,
)
