package co.daniel.mercado.data.db

import androidx.test.core.app.ApplicationProvider
import co.daniel.mercado.data.repo.RoomBudgetRepository
import co.daniel.mercado.data.repo.RoomProductRepository
import co.daniel.mercado.data.repo.RoomTransactionRunner
import co.daniel.mercado.data.repo.RoomTripRepository

/** Base Room en memoria (sin cifrar) para pruebas JVM con Robolectric. */
fun inMemoryDb(): MercadoDatabase = InMemoryDatabaseFactory().create(ApplicationProvider.getApplicationContext())

class Repos(val db: MercadoDatabase = inMemoryDb()) {
    val products = RoomProductRepository(db)
    val trips = RoomTripRepository(db)
    val budgets = RoomBudgetRepository(db)
    val tx = RoomTransactionRunner(db)
}
