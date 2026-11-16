package co.daniel.mercado.data.repo

import co.daniel.mercado.data.db.entity.ShoppingListItemEntity
import co.daniel.mercado.domain.model.ListSource
import co.daniel.mercado.domain.model.ShoppingListItem
import co.daniel.mercado.data.db.entity.BudgetEntity
import co.daniel.mercado.data.db.entity.CategoryEntity
import co.daniel.mercado.data.db.entity.PriceHistoryEntity
import co.daniel.mercado.data.db.entity.ProductEntity
import co.daniel.mercado.data.db.entity.TripEntity
import co.daniel.mercado.data.db.entity.TripItemEntity
import co.daniel.mercado.data.db.entity.TripItemWithProduct
import co.daniel.mercado.data.db.entity.TripWithItemsRelation
import co.daniel.mercado.domain.model.Budget
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.NameNormalizer

internal fun CategoryEntity.toModel() = Category(id, name, sortOrder, monthlyBudget)
internal fun Category.toEntity() = CategoryEntity(id, name, sortOrder, monthlyBudget)

internal fun ProductEntity.toModel() = Product(
    id = id,
    name = name,
    categoryId = categoryId,
    unitType = unitType,
    refPrice = refPrice,
    favorite = favorite,
    favoriteOrder = favoriteOrder,
    active = active,
    needsReview = needsReview,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Product.toEntity() = ProductEntity(
    id = id,
    name = name,
    nameNorm = NameNormalizer.normalize(name),
    categoryId = categoryId,
    unitType = unitType,
    refPrice = refPrice,
    favorite = favorite,
    favoriteOrder = favoriteOrder,
    active = active,
    needsReview = needsReview,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun PriceHistoryEntity.toModel() = PricePoint(productId, price, at, source)
internal fun PricePoint.toEntity() = PriceHistoryEntity(productId = productId, price = price, at = at, source = source)

internal fun TripEntity.toModel() = Trip(id, startedAt, closedAt, store, budgetLimit, totalEstimated, totalPaid)
internal fun Trip.toEntity() = TripEntity(id, startedAt, closedAt, store, budgetLimit, totalEstimated, totalPaid)

internal fun TripItemWithProduct.toModel() = TripItem(
    id = item.id,
    tripId = item.tripId,
    productId = item.productId,
    nameSnapshot = item.nameSnapshot,
    unitType = product?.unitType ?: UnitType.UNIT,
    quantity = item.quantity,
    unitPrice = item.unitPrice,
    weightGrams = item.weightGrams,
    addedAt = item.addedAt,
    position = item.position,
)

internal fun TripItem.toEntity() = TripItemEntity(
    id = id,
    tripId = tripId,
    productId = productId,
    nameSnapshot = nameSnapshot,
    quantity = quantity,
    unitPrice = unitPrice,
    weightGrams = weightGrams,
    addedAt = addedAt,
    position = position,
)

internal fun TripWithItemsRelation.toModel() = TripWithItems(
    trip = trip.toModel(),
    items = items.map { it.toModel() }.sortedByDescending { it.position },
)

internal fun BudgetEntity.toModel() = Budget(yearMonth, amount)
internal fun Budget.toEntity() = BudgetEntity(yearMonth, amount)

internal fun ShoppingListItemEntity.toModel() =
    ShoppingListItem(productId, suggestedQty, checked, ListSource.valueOf(source), dismissedUntil)

internal fun ShoppingListItem.toEntity() =
    ShoppingListItemEntity(productId, suggestedQty, checked, source.name, dismissedUntil)
