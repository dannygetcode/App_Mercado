package co.daniel.mercado.data.seed

import androidx.test.core.app.ApplicationProvider
import co.daniel.mercado.data.db.inMemoryDb
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.port.Clock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SeedImporterTest {

    private val db = inMemoryDb()
    private val importer = SeedImporter(db, Clock { 1_000L })

    @After
    fun close() = db.close()

    private val example: String get() = File("../seed/catalogo_inicial.ejemplo.json").readText()

    private suspend fun import(text: String) = importer.import(text.byteInputStream())

    private fun product(name: String, price: Long = 1000, unit: String = "UNIT", codes: String = "", category: String = "Otros") =
        """{"name":"$name","category":"$category","unit":"$unit","price":$price,"barcodes":[$codes]}"""

    private fun file(vararg products: String, version: Int = 1) =
        """{"version":$version,"products":[${products.joinToString(",")}]}"""

    @Test
    fun `importa el ejemplo`() = runTest {
        val result = import(example)

        assertEquals(SeedResult.Imported(created = 7, updated = 0), result)
        val dao = db.productDao()
        assertEquals("Leche entera 1 L", dao.findByBarcode("7702004003508")?.name)
        val tomate = dao.findByNameNorm("tomate chonto")!!
        assertTrue(tomate.favorite)
        assertEquals(Pesos(4_200), tomate.refPrice)
        assertEquals(PriceSource.SEED, dao.lastPricePoint(dao.findByNameNorm("arroz 1 kg")!!.id)?.source)
        assertTrue(dao.priceHistory(tomate.id).isEmpty())
    }

    @Test
    fun `reimportar no duplica`() = runTest {
        import(example)
        val historyBefore = db.productDao().priceHistory(db.productDao().findByNameNorm("arroz 1 kg")!!.id).size

        val again = import(example)

        assertEquals(SeedResult.Imported(created = 0, updated = 0), again)
        assertEquals(7, db.productDao().count())
        assertEquals(historyBefore, db.productDao().priceHistory(db.productDao().findByNameNorm("arroz 1 kg")!!.id).size)
    }

    @Test
    fun `reimportar con otro precio actualiza y agrega codigos`() = runTest {
        import(file(product("Panela", 4_000)))

        val result = import(file(product("PANELA ", 4_500, codes = "\"96385074\"")))

        assertEquals(SeedResult.Imported(created = 0, updated = 1), result)
        val panela = db.productDao().findByNameNorm("panela")!!
        assertEquals(Pesos(4_500), panela.refPrice)
        assertEquals(listOf("96385074"), db.productDao().barcodesOf(panela.id))
        assertEquals(2, db.productDao().priceHistory(panela.id).size)
    }

    @Test
    fun `categoria desconocida se crea`() = runTest {
        import(file(product("Concentrado", category = "Jardín")))

        assertTrue(db.categoryDao().byName("Jardín") != null)
    }

    @Test
    fun `archivo invalido no deja nada e indica el producto`() = runTest {
        val result = import(file(product("Bueno"), product("Malo", price = 0), product("Otro")))

        assertEquals(SeedResult.Invalid(SeedError.INVALID_PRICE, index = 1), result)
        assertEquals(0, db.productDao().count())
    }

    @Test
    fun `cada regla de validacion se aplica`() = runTest {
        assertEquals(SeedResult.Invalid(SeedError.MALFORMED), import("{no es json"))
        assertEquals(SeedResult.Invalid(SeedError.MALFORMED), import("""{"version":1,"products":[{"name":"x"}]}"""))
        assertEquals(SeedResult.Invalid(SeedError.MALFORMED), import(file(product("Decimal").replace("1000", "10.5"))))
        assertEquals(SeedResult.Invalid(SeedError.UNSUPPORTED_VERSION), import(file(product("x"), version = 2)))
        assertEquals(SeedResult.Invalid(SeedError.INVALID_NAME, 0), import(file(product("   "))))
        assertEquals(SeedResult.Invalid(SeedError.INVALID_NAME, 0), import(file(product("x".repeat(81)))))
        assertEquals(SeedResult.Invalid(SeedError.INVALID_UNIT, 0), import(file(product("x", unit = "LB"))))
        assertEquals(SeedResult.Invalid(SeedError.INVALID_PRICE, 0), import(file(product("x", price = 50_000_001))))
        assertEquals(SeedResult.Invalid(SeedError.INVALID_CATEGORY, 0), import(file(product("x", category = " "))))
        assertEquals(SeedResult.Invalid(SeedError.INVALID_BARCODE, 0), import(file(product("x", codes = "\"7702004003509\""))))
        assertEquals(
            SeedResult.Invalid(SeedError.DUPLICATE_BARCODE, 1),
            import(file(product("a", codes = "\"96385074\""), product("b", codes = "\"96385074\""))),
        )
        assertEquals(0, db.productDao().count())
    }

    @Test
    fun `archivos gigantes o con demasiados productos se rechazan sin leerlos enteros`() = runTest {
        val tooMany = file(*Array(SeedImporter.MAX_PRODUCTS + 1) { product("p$it") })
        assertEquals(SeedResult.Invalid(SeedError.TOO_LARGE), import(tooMany + " ".repeat(SeedImporter.MAX_BYTES)))
        val compact = file(*Array(SeedImporter.MAX_PRODUCTS + 1) { """{"name":"p$it","category":"O","unit":"UNIT","price":1}""" })
        assertTrue(compact.length < SeedImporter.MAX_BYTES)
        assertEquals(SeedResult.Invalid(SeedError.TOO_MANY_PRODUCTS), import(compact))
        assertFalse(db.productDao().count() > 0)
    }

    @Test
    fun `la semilla real empaquetada importa limpia`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val result = context.assets.open(SeedImporter.ASSET_PATH).use { importer.import(it) }

        assertEquals(SeedResult.Imported(created = 48, updated = 0), result)
    }
}
