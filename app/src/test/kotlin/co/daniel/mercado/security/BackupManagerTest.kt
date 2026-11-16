package co.daniel.mercado.security

import co.daniel.mercado.data.db.MercadoDatabase
import co.daniel.mercado.data.db.inMemoryDb
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.ui.trip.TripGraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Conscrypt (el proveedor que Robolectric instala) no pasa la verificación JCE del JDK 17; en el teléfono no aplica. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class BackupManagerTest {

    private val t = TripGraph()
    private val password = "contraseña-larga".toCharArray()
    private val manager = BackupManager(t.graph.db, t.clock, iterations = 100_000)
    private val others = mutableListOf<MercadoDatabase>()

    @After
    fun tearDown() {
        others.forEach { it.close() }
        t.close()
    }

    /** Todas las tablas como listas de entidades (comparables por igualdad). */
    private fun snapshot(db: MercadoDatabase): List<List<Any>> = runBlocking {
        val d = db.backupDao()
        listOf(d.categories(), d.products(), d.barcodes(), d.trips(), d.tripItems(), d.priceHistory(), d.budgets(), d.shoppingList())
    }

    private fun fillData() = runBlocking {
        val g = t.graph
        g.setMonthlyBudget(Pesos(900_000))
        val arroz = t.product("Arroz Diana", 5_900, code = TripGraph.ARROZ, favorite = true)
        g.startTrip(TripLimit.Custom(Pesos(200_000)))
        g.addProductToTrip(arroz)
        g.addProductToTrip(arroz)
        g.addLoosePrice(Pesos(3_200))
        g.addUnknownScanned(TripGraph.DESCONOCIDO, Pesos(7_000))
        g.closeTrip(totalPaid = Pesos(21_500), store = "La Granja")
        t.clock.advance(86_400_000)
        g.startTrip(TripLimit.None)
        g.handleScan(TripGraph.ARROZ)
    }

    private fun open(file: ByteArray, pass: CharArray = password) = manager.open(file, pass)

    @Test
    fun `exportar, cambiar todo e importar deja la base identica`() {
        fillData()
        val before = snapshot(t.graph.db)
        val file = runBlocking { manager.export(password) }

        runBlocking {
            t.product("Producto que no estaba", 1_000)
            t.graph.setMonthlyBudget(Pesos(1_000_000))
            t.graph.discardTrip()
        }
        val opened = (open(file) as BackupOpenResult.Ok).backup
        assertEquals(2, opened.tripCount)
        runBlocking { manager.restore(opened) }

        assertEquals(before, snapshot(t.graph.db))
        val sum = runBlocking { t.graph.trips.spentBetween(0, Long.MAX_VALUE) }
        assertEquals(Pesos(21_500), sum)
    }

    @Test
    fun `restaurar en una base nueva trae todo`() {
        fillData()
        val file = runBlocking { manager.export(password) }
        val fresh = inMemoryDb().also { others += it }
        val target = BackupManager(fresh, t.clock, iterations = 100_000)

        val opened = (target.open(file, password) as BackupOpenResult.Ok).backup
        runBlocking { target.restore(opened) }

        assertEquals(snapshot(t.graph.db), snapshot(fresh))
    }

    @Test
    fun `contrasena incorrecta no toca la base`() {
        fillData()
        val file = runBlocking { manager.export(password) }
        val before = snapshot(t.graph.db)

        assertEquals(BackupOpenResult.Failed(BackupError.WRONG_PASSWORD_OR_TAMPERED), open(file, "otra-clave-xyz".toCharArray()))
        assertEquals(before, snapshot(t.graph.db))
    }

    private fun craft(json: String): ByteArray {
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray()
        return BackupCrypto.encrypt(gz, password, iterations = 100_000)
    }

    @Test
    fun `contenido invalido o de una version mas nueva se rechaza`() {
        assertEquals(BackupOpenResult.Failed(BackupError.NEWER_VERSION), open(craft("""{"schemaVersion":99,"exportedAt":0}""")))
        assertEquals(BackupOpenResult.Failed(BackupError.INVALID_CONTENT), open(craft("no es json")))
        val brokenReference = """{"schemaVersion":1,"exportedAt":0,"categories":[{"id":1,"name":"Otros","sortOrder":0}],
            "products":[{"id":1,"name":"Arroz","nameNorm":"arroz","categoryId":7,"unitType":"UNIT","refPrice":100,
            "favorite":false,"favoriteOrder":0,"active":true,"needsReview":false,"createdAt":0,"updatedAt":0}]}"""
        assertEquals(BackupOpenResult.Failed(BackupError.INVALID_CONTENT), open(craft(brokenReference)))
        val twoOpenTrips = """{"schemaVersion":1,"exportedAt":0,"trips":[
            {"id":1,"startedAt":0,"totalEstimated":0},{"id":2,"startedAt":0,"totalEstimated":0}]}"""
        assertEquals(BackupOpenResult.Failed(BackupError.INVALID_CONTENT), open(craft(twoOpenTrips)))
        assertEquals(BackupOpenResult.Failed(BackupError.NOT_A_BACKUP), open("{}".toByteArray()))
    }

    @Test
    fun `un archivo que se infla demasiado se rechaza sin agotar la memoria`() {
        val bomb = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { gz -> val zeros = ByteArray(1024 * 1024); repeat(70) { gz.write(zeros) } }
        }.toByteArray()
        val file = BackupCrypto.encrypt(bomb, password, iterations = 100_000)
        assertEquals(BackupOpenResult.Failed(BackupError.INVALID_CONTENT), open(file))
    }

    @Test
    fun `nombre sugerido con la fecha de Bogota`() {
        assertTrue(BackupManager.suggestedFileName(t.clock.now).matches(Regex("""mercado-\d{4}-\d{2}-\d{2}\.mercado""")))
    }
}
