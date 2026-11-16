package co.daniel.mercado.data.repo

import androidx.room.withTransaction
import co.daniel.mercado.data.db.MercadoDatabase
import co.daniel.mercado.data.db.entity.BarcodeEntity
import co.daniel.mercado.domain.model.Budget
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.ClosedLine
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.ShoppingListItem
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.port.BudgetRepository
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.ShoppingListRepository
import co.daniel.mercado.domain.port.TransactionRunner
import co.daniel.mercado.domain.port.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.YearMonth

class RoomTransactionRunner(private val db: MercadoDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}

class RoomProductRepository(private val db: MercadoDatabase) : ProductRepository {

    private val dao = db.productDao()
    private val categoryDao = db.categoryDao()

    override suspend fun findByBarcode(code: String): Product? = dao.findByBarcode(code)?.toModel()

    override suspend fun getById(id: Long): Product? = dao.getById(id)?.toModel()

    override suspend fun search(normalizedQuery: String, limit: Int): List<Product> =
        dao.search(likePattern(normalizedQuery), limit).map { it.toModel() }

    override suspend fun findByNormalizedName(normalizedName: String): Product? =
        dao.findByNameNorm(normalizedName)?.toModel()

    override fun observeActive(): Flow<List<Product>> =
        dao.observeActive().map { list -> list.map { it.toModel() } }.distinctUntilChanged()

    override suspend fun priceHistory(productId: Long): List<PricePoint> = dao.priceHistory(productId).map { it.toModel() }

    override fun observeFavorites(): Flow<List<Product>> =
        dao.observeFavorites().map { list -> list.map { it.toModel() } }.distinctUntilChanged()

    override suspend fun insert(product: Product): Long = dao.insert(product.copy(id = 0).toEntity())

    override suspend fun update(product: Product) = dao.update(product.toEntity())

    override suspend fun delete(id: Long) = db.withTransaction {
        dao.deleteBarcodesOf(id)
        dao.deletePriceHistoryOf(id)
        dao.deleteListItemOf(id)
        dao.delete(id)
    }

    override suspend fun barcodeOwner(code: String): Long? = dao.barcodeOwner(code)

    override suspend fun linkBarcode(code: String, productId: Long, at: Long) =
        dao.upsertBarcode(BarcodeEntity(code, productId, at))

    override suspend fun unlinkBarcode(code: String) = dao.deleteBarcode(code)

    override suspend fun barcodesOf(productId: Long): List<String> = dao.barcodesOf(productId)

    override suspend fun categories(): List<Category> = categoryDao.all().map { it.toModel() }

    override suspend fun categoryByName(name: String): Category? = categoryDao.byName(name)?.toModel()

    override suspend fun insertCategory(category: Category): Long = categoryDao.insert(category.copy(id = 0).toEntity())

    override suspend fun setCategoryBudget(categoryId: Long, amount: Pesos?) = categoryDao.setMonthlyBudget(categoryId, amount)

    override suspend fun getByIds(ids: Collection<Long>): List<Product> =
        ids.distinct().chunked(MAX_SQL_ARGS).flatMap { chunk -> dao.getByIds(chunk).map { it.toModel() } }

    override suspend fun lastPricePoint(productId: Long): PricePoint? = dao.lastPricePoint(productId)?.toModel()

    override suspend fun addPricePoint(point: PricePoint) {
        dao.insertPricePoint(point.toEntity())
    }

    companion object {
        private const val MAX_SQL_ARGS = 900

        /** `%texto%` con los comodines del usuario escapados para `LIKE ... ESCAPE '\'`. */
        fun likePattern(query: String): String =
            "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}

class RoomTripRepository(private val db: MercadoDatabase) : TripRepository {

    private val dao = db.tripDao()

    override fun observeActive(): Flow<TripWithItems?> =
        dao.observeOpen().map { it?.toModel() }.distinctUntilChanged()

    override suspend fun getActive(): TripWithItems? = dao.getOpen()?.toModel()

    override fun observeClosed(): Flow<List<TripSummary>> =
        dao.observeClosed().map { rows -> rows.map { TripSummary(it.trip.toModel(), it.articleCount) } }.distinctUntilChanged()

    override suspend fun getWithItems(id: Long): TripWithItems? = dao.getWithItems(id)?.toModel()

    override suspend fun getTrip(id: Long): Trip? = dao.getTrip(id)?.toModel()

    override suspend fun insertTrip(trip: Trip): Long = dao.insertTrip(trip.copy(id = 0).toEntity())

    override suspend fun updateTrip(trip: Trip) = dao.updateTrip(trip.toEntity())

    override suspend fun deleteTrip(id: Long) = dao.deleteTrip(id)

    override suspend fun getItem(id: Long): TripItem? = dao.getItem(id)?.toModel()

    override suspend fun insertItem(item: TripItem): Long = dao.insertItem(item.toEntity())

    override suspend fun updateItem(item: TripItem) = dao.updateItem(item.toEntity())

    override suspend fun deleteItem(id: Long) = dao.deleteItem(id)

    override suspend fun nextPosition(tripId: Long): Int = dao.nextPosition(tripId)

    override suspend fun spentBetween(fromMillis: Long, toMillis: Long): Pesos =
        Pesos(dao.spentBetween(fromMillis, toMillis))

    override suspend fun recentStores(limit: Int): List<String> = dao.recentStores(limit)

    override suspend fun closedLines(fromMillis: Long, toMillis: Long): List<ClosedLine> =
        dao.closedLines(fromMillis, toMillis).map { ClosedLine(it.tripId, it.closedAt, it.productId, it.quantity, it.unitPrice, it.unitType) }

    override suspend fun lastClosedAt(): Long? = dao.lastClosedAt()
}

class RoomShoppingListRepository(db: MercadoDatabase) : ShoppingListRepository {

    private val dao = db.shoppingListDao()

    override fun observeAll(): Flow<List<ShoppingListItem>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun getAll(): List<ShoppingListItem> = dao.all().map { it.toModel() }

    override suspend fun upsert(item: ShoppingListItem) = dao.upsert(item.toEntity())

    override suspend fun delete(productId: Long) = dao.delete(productId)
}

class RoomBudgetRepository(db: MercadoDatabase) : BudgetRepository {

    private val dao = db.budgetDao()

    override suspend fun get(month: YearMonth): Budget? = dao.get(month)?.toModel()

    override suspend fun latestBefore(month: YearMonth): Budget? = dao.latestBefore(month)?.toModel()

    override suspend fun set(budget: Budget) = dao.upsert(budget.toEntity())

    override fun observeAll(): Flow<List<Budget>> = dao.observeAll().map { list -> list.map { it.toModel() } }
}
