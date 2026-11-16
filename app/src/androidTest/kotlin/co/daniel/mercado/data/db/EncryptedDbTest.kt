package co.daniel.mercado.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.daniel.mercado.data.db.entity.TripEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/** S-02: la base real con SQLCipher no se puede leer como SQLite plano. */
@RunWith(AndroidJUnit4::class)
class EncryptedDbTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    private fun factory() = SqlCipherDatabaseFactory(keyProvider = { key.copyOf() }, fileName = FILE)

    @Before
    fun setUp() {
        System.loadLibrary(SqlCipherDatabaseFactory.LIBRARY)
        context.deleteDatabase(FILE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(FILE)
    }

    @Test
    fun laBaseCifradaNoAbreComoSqlitePlanoYConLaClaveSi() = runTest {
        val db = factory().create(context)
        db.tripDao().insertTrip(TripEntity(startedAt = 42))
        db.close()

        val file = context.getDatabasePath(FILE)
        val header = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))

        val plainFails = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { plain ->
                plain.rawQuery("SELECT COUNT(*) FROM trip", null).use { it.moveToFirst() }
            }
        }.isFailure
        assertTrue(plainFails)

        val reopened = factory().create(context)
        assertEquals(42L, reopened.tripDao().getOpen()?.trip?.startedAt)
        reopened.close()
    }

    private companion object {
        const val FILE = "encrypted-test.db"
    }
}
