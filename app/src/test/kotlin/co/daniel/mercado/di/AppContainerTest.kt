package co.daniel.mercado.di

import androidx.test.core.app.ApplicationProvider
import co.daniel.mercado.data.db.InMemoryDatabaseFactory
import co.daniel.mercado.data.settings.InMemoryPreferencesDataStore
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.usecase.StartTripResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    @Test
    fun `primer arranque abre la base, importa la semilla y marca la bandera`() = runTest {
        val container = AppContainer(
            appContext = ApplicationProvider.getApplicationContext(),
            databaseFactory = InMemoryDatabaseFactory(),
            dataStore = InMemoryPreferencesDataStore(),
            clock = Clock { 1_800_000_000_000L },
            scope = backgroundScope,
            ioDispatcher = Dispatchers.IO,
        )

        val data = container.data()

        assertEquals(48, data.db.productDao().count())
        assertTrue(container.settings.current().seedImported)
        assertTrue(data.startTrip() is StartTripResult.Started)
        val outcome = data.handleScan("7702310048088")
        assertEquals("Detergente líquido AK-1 1800 ml", (outcome as ScanOutcome.Added).productName)
        data.db.close()
    }
}
