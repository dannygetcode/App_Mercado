package co.daniel.mercado.data.backup

import co.daniel.mercado.data.db.entity.BarcodeEntity
import co.daniel.mercado.data.db.entity.BudgetEntity
import co.daniel.mercado.data.db.entity.CategoryEntity
import co.daniel.mercado.data.db.entity.PriceHistoryEntity
import co.daniel.mercado.data.db.entity.ProductEntity
import co.daniel.mercado.data.db.entity.ShoppingListItemEntity
import co.daniel.mercado.data.db.entity.TripEntity
import co.daniel.mercado.data.db.entity.TripItemEntity
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.UnitType
import kotlinx.serialization.Serializable
import java.time.YearMonth

/** Contenido lógico del respaldo (03-datos): todas las tablas + versión de esquema + fecha. */
@Serializable
data class BackupFileDto(
    val schemaVersion: Int,
    val exportedAt: Long,
    val categories: List<CategoryDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val barcodes: List<BarcodeDto> = emptyList(),
    val trips: List<TripDto> = emptyList(),
    val tripItems: List<TripItemDto> = emptyList(),
    val priceHistory: List<PriceHistoryDto> = emptyList(),
    val budgets: List<BudgetDto> = emptyList(),
    val shoppingList: List<ShoppingListItemDto> = emptyList(),
)

@Serializable
data class CategoryDto(val id: Long, val name: String, val sortOrder: Int, val monthlyBudget: Long? = null)

@Serializable
data class ProductDto(
    val id: Long,
    val name: String,
    val nameNorm: String,
    val categoryId: Long,
    val unitType: String,
    val refPrice: Long,
    val favorite: Boolean,
    val favoriteOrder: Int,
    val active: Boolean,
    val needsReview: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BarcodeDto(val code: String, val productId: Long, val createdAt: Long)

@Serializable
data class TripDto(
    val id: Long,
    val startedAt: Long,
    val closedAt: Long? = null,
    val store: String? = null,
    val budgetLimit: Long? = null,
    val totalEstimated: Long,
    val totalPaid: Long? = null,
)

@Serializable
data class TripItemDto(
    val id: Long,
    val tripId: Long,
    val productId: Long? = null,
    val nameSnapshot: String,
    val quantity: Int,
    val unitPrice: Long,
    val weightGrams: Int? = null,
    val addedAt: Long,
    val position: Int,
)

@Serializable
data class PriceHistoryDto(val id: Long, val productId: Long, val price: Long, val at: Long, val source: String)

@Serializable
data class BudgetDto(val yearMonth: String, val amount: Long)

@Serializable
data class ShoppingListItemDto(
    val productId: Long,
    val suggestedQty: Int,
    val checked: Boolean,
    val source: String,
    val dismissedUntil: Long? = null,
)

internal fun CategoryEntity.toDto() = CategoryDto(id, name, sortOrder, monthlyBudget?.value)
internal fun CategoryDto.toEntity() = CategoryEntity(id, name, sortOrder, monthlyBudget?.let(::Pesos))

internal fun ProductEntity.toDto() = ProductDto(
    id, name, nameNorm, categoryId, unitType.name, refPrice.value, favorite, favoriteOrder, active, needsReview, createdAt, updatedAt,
)

internal fun ProductDto.toEntity() = ProductEntity(
    id, name, nameNorm, categoryId, UnitType.valueOf(unitType), Pesos(refPrice), favorite, favoriteOrder, active, needsReview, createdAt, updatedAt,
)

internal fun BarcodeEntity.toDto() = BarcodeDto(code, productId, createdAt)
internal fun BarcodeDto.toEntity() = BarcodeEntity(code, productId, createdAt)

internal fun TripEntity.toDto() =
    TripDto(id, startedAt, closedAt, store, budgetLimit?.value, totalEstimated.value, totalPaid?.value)

internal fun TripDto.toEntity() =
    TripEntity(id, startedAt, closedAt, store, budgetLimit?.let(::Pesos), Pesos(totalEstimated), totalPaid?.let(::Pesos))

internal fun TripItemEntity.toDto() =
    TripItemDto(id, tripId, productId, nameSnapshot, quantity, unitPrice.value, weightGrams, addedAt, position)

internal fun TripItemDto.toEntity() =
    TripItemEntity(id, tripId, productId, nameSnapshot, quantity, Pesos(unitPrice), weightGrams, addedAt, position)

internal fun PriceHistoryEntity.toDto() = PriceHistoryDto(id, productId, price.value, at, source.name)
internal fun PriceHistoryDto.toEntity() = PriceHistoryEntity(id, productId, Pesos(price), at, PriceSource.valueOf(source))

internal fun BudgetEntity.toDto() = BudgetDto(yearMonth.toString(), amount.value)
internal fun BudgetDto.toEntity() = BudgetEntity(YearMonth.parse(yearMonth), Pesos(amount))

internal fun ShoppingListItemEntity.toDto() = ShoppingListItemDto(productId, suggestedQty, checked, source, dismissedUntil)
internal fun ShoppingListItemDto.toEntity() = ShoppingListItemEntity(productId, suggestedQty, checked, source, dismissedUntil)
