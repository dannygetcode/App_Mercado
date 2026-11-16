package co.daniel.mercado.data.db

import co.daniel.mercado.data.db.entity.BarcodeEntity
import co.daniel.mercado.data.db.entity.ProductEntity
import co.daniel.mercado.data.db.entity.TripEntity
import co.daniel.mercado.data.db.entity.TripItemEntity
import co.daniel.mercado.data.repo.RoomProductRepository
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.NameNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private val db = inMemoryDb()
    private val products = db.productDao()
    private val trips = db.tripDao()

    @After
    fun close() = db.close()

    private suspend fun product(name: String, price: Long, active: Boolean = true, favorite: Boolean = false): Long {
        val categoryId = db.categoryDao().byName("Otros")!!.id
        return products.insert(
            ProductEntity(
                name = name, nameNorm = NameNormalizer.normalize(name), categoryId = categoryId,
                unitType = UnitType.UNIT, refPrice = Pesos(price), favorite = favorite, active = active,
                createdAt = 0, updatedAt = 0,
            ),
        )
    }

    @Test
    fun `al crear la base se siembran las diez categorias en orden`() = runTest {
        assertEquals(MercadoDatabase.SEED_CATEGORIES, db.categoryDao().all().map { it.name })
    }

    @Test
    fun `buscar por codigo devuelve el producto`() = runTest {
        val id = product("Arroz Diana 1 kg", 5_900)
        products.upsertBarcode(BarcodeEntity("7702004003508", id, 0))

        assertEquals("Arroz Diana 1 kg", products.findByBarcode("7702004003508")?.name)
        assertNull(products.findByBarcode("96385074"))
    }

    @Test
    fun `busqueda sin tildes, solo activos, favoritos primero`() = runTest {
        product("Limón Tahití", 3_000)
        product("Limón mandarino", 3_500, favorite = true)
        product("Limonada vieja", 2_000, active = false)

        val found = products.search(RoomProductRepository.likePattern(NameNormalizer.normalize("LIMON")), 30)

        assertEquals(listOf("Limón mandarino", "Limón Tahití"), found.map { it.name })
    }

    @Test
    fun `los comodines del usuario no actuan como comodines`() = runTest {
        product("Leche 100% natural", 4_000)
        product("Leche entera", 4_300)

        val found = products.search(RoomProductRepository.likePattern("100%"), 30)

        assertEquals(listOf("Leche 100% natural"), found.map { it.name })
    }

    @Test
    fun `total de compra y gastado del mes`() = runTest {
        val open = trips.insertTrip(TripEntity(startedAt = 0))
        trips.insertItem(item(open, 5_900, 2, 0))
        trips.insertItem(item(open, 4_300, 1, 1))
        assertEquals(16_100L, trips.total(open))
        trips.insertTrip(TripEntity(startedAt = 0, closedAt = 1_000, totalEstimated = Pesos(50_000)))
        trips.insertTrip(TripEntity(startedAt = 0, closedAt = 2_000, totalEstimated = Pesos(80_000), totalPaid = Pesos(79_000)))
        trips.insertTrip(TripEntity(startedAt = 0, closedAt = 5_000, totalEstimated = Pesos(10_000)))

        assertEquals(129_000L, trips.spentBetween(0, 5_000))
    }

    @Test
    fun `borrar una compra borra sus lineas en cascada`() = runTest {
        val trip = trips.insertTrip(TripEntity(startedAt = 0))
        val line = trips.insertItem(item(trip, 1_000, 1, 0))

        trips.deleteTrip(trip)

        assertNull(trips.getItem(line))
    }

    @Test
    fun `las claves foraneas estan activas`() = runTest {
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { trips.insertItem(item(tripId = 999, price = 1, qty = 1, pos = 0)) }
        }
    }

    @Test
    fun `la siguiente posicion empieza en 0 y crece`() = runTest {
        val trip = trips.insertTrip(TripEntity(startedAt = 0))
        assertEquals(0, trips.nextPosition(trip))
        trips.insertItem(item(trip, 1, 1, 0))
        assertEquals(1, trips.nextPosition(trip))
        assertTrue(trips.getOpen() != null)
    }

    private fun item(tripId: Long, price: Long, qty: Int, pos: Int) = TripItemEntity(
        tripId = tripId, productId = null, nameSnapshot = "", quantity = qty,
        unitPrice = Pesos(price), addedAt = 0, position = pos,
    )
}
