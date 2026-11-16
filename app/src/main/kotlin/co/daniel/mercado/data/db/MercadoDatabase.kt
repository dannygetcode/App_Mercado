package co.daniel.mercado.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import co.daniel.mercado.data.db.dao.BackupDao
import co.daniel.mercado.data.db.dao.BudgetDao
import co.daniel.mercado.data.db.dao.CategoryDao
import co.daniel.mercado.data.db.dao.ProductDao
import co.daniel.mercado.data.db.dao.ShoppingListDao
import co.daniel.mercado.data.db.dao.TripDao
import co.daniel.mercado.data.db.entity.BarcodeEntity
import co.daniel.mercado.data.db.entity.BudgetEntity
import co.daniel.mercado.data.db.entity.CategoryEntity
import co.daniel.mercado.data.db.entity.PriceHistoryEntity
import co.daniel.mercado.data.db.entity.ProductEntity
import co.daniel.mercado.data.db.entity.ShoppingListItemEntity
import co.daniel.mercado.data.db.entity.TripEntity
import co.daniel.mercado.data.db.entity.TripItemEntity

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        BarcodeEntity::class,
        TripEntity::class,
        TripItemEntity::class,
        PriceHistoryEntity::class,
        BudgetEntity::class,
        ShoppingListItemEntity::class,
    ],
    version = MercadoDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MercadoDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun tripDao(): TripDao
    abstract fun budgetDao(): BudgetDao
    abstract fun backupDao(): BackupDao
    abstract fun shoppingListDao(): ShoppingListDao

    companion object {
        const val FILE_NAME = "mercado.db"
        const val VERSION = 1

        /** Categorías sembradas al crear la base (03-datos.md), en su orden. */
        val SEED_CATEGORIES = listOf(
            "Granos y despensa",
            "Lácteos y huevos",
            "Carnes",
            "Frutas y verduras",
            "Panadería",
            "Bebidas",
            "Aseo del hogar",
            "Cuidado personal",
            "Mascotas",
            "Otros",
        )
    }
}

/** Siembra categorías al crear la base y activa las claves foráneas en cada apertura. */
object MercadoDatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        MercadoDatabase.SEED_CATEGORIES.forEachIndexed { index, name ->
            db.execSQL("INSERT INTO category (name, sort_order) VALUES (?, ?)", arrayOf<Any>(name, index))
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
