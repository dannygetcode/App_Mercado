package co.daniel.mercado.di

import co.daniel.mercado.data.db.entity.CategoryEntity
import androidx.room.withTransaction
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import co.daniel.mercado.BuildConfig
import co.daniel.mercado.R
import co.daniel.mercado.data.db.DatabaseFactory
import co.daniel.mercado.data.db.MercadoDatabase
import co.daniel.mercado.data.db.SqlCipherDatabaseFactory
import co.daniel.mercado.data.repo.RoomBudgetRepository
import co.daniel.mercado.data.repo.RoomProductRepository
import co.daniel.mercado.data.repo.RoomShoppingListRepository
import co.daniel.mercado.data.repo.RoomTransactionRunner
import co.daniel.mercado.data.repo.RoomTripRepository
import co.daniel.mercado.data.seed.InitialCatalogLoader
import co.daniel.mercado.data.seed.SeedImporter
import co.daniel.mercado.data.seed.SeedResult
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.data.settings.settingsDataStore
import co.daniel.mercado.scanner.BarcodeDecoder
import co.daniel.mercado.scanner.BarcodeSource
import co.daniel.mercado.scanner.SharedBarcodeSource
import co.daniel.mercado.scanner.DefaultScanFeedback
import co.daniel.mercado.scanner.LabelReader
import co.daniel.mercado.scanner.MlKitBarcodeDecoder
import co.daniel.mercado.scanner.MlKitLabelReader
import co.daniel.mercado.scanner.ScanFeedback
import co.daniel.mercado.scanner.ScanFeedbackPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.ScanDebouncer
import co.daniel.mercado.domain.usecase.AddListItem
import co.daniel.mercado.domain.usecase.BuildSuggestedList
import co.daniel.mercado.domain.usecase.GetMonthSummary
import co.daniel.mercado.domain.usecase.GetMonthsComparison
import co.daniel.mercado.domain.usecase.GetPriceChanges
import co.daniel.mercado.domain.usecase.ObserveShoppingList
import co.daniel.mercado.domain.usecase.RemoveListItem
import co.daniel.mercado.domain.usecase.SetCategoryBudget
import co.daniel.mercado.domain.usecase.ShouldRemindShopping
import co.daniel.mercado.domain.usecase.ToggleListItem
import co.daniel.mercado.domain.usecase.AddLoosePrice
import co.daniel.mercado.domain.usecase.AddProductToTrip
import co.daniel.mercado.domain.usecase.AddUnknownScanned
import co.daniel.mercado.domain.usecase.AddWeighedItem
import co.daniel.mercado.domain.usecase.ChangeLinePrice
import co.daniel.mercado.domain.usecase.ChangeQuantity
import co.daniel.mercado.domain.usecase.CloseTrip
import co.daniel.mercado.domain.usecase.DeactivateProduct
import co.daniel.mercado.domain.usecase.DeleteProducts
import co.daniel.mercado.domain.usecase.DeleteTripsInRange
import co.daniel.mercado.domain.usecase.DiscardTrip
import co.daniel.mercado.domain.usecase.GetMonthStatus
import co.daniel.mercado.domain.usecase.GetProductDetail
import co.daniel.mercado.domain.usecase.GetTripDetail
import co.daniel.mercado.domain.usecase.MoveFavorite
import co.daniel.mercado.domain.usecase.ObserveCatalog
import co.daniel.mercado.domain.usecase.ObserveClosedTrips
import co.daniel.mercado.domain.usecase.ObserveMonthStatus
import co.daniel.mercado.domain.usecase.ObserveNeedsReviewCount
import co.daniel.mercado.domain.usecase.UnlinkBarcode
import co.daniel.mercado.domain.usecase.HandleScan
import co.daniel.mercado.domain.usecase.LinkBarcode
import co.daniel.mercado.domain.usecase.ObserveActiveTrip
import co.daniel.mercado.domain.usecase.ObserveFavorites
import co.daniel.mercado.domain.usecase.ReactivateProduct
import co.daniel.mercado.domain.usecase.RemoveLine
import co.daniel.mercado.domain.usecase.RenameLooseLine
import co.daniel.mercado.domain.usecase.SaveProduct
import co.daniel.mercado.domain.usecase.SearchProducts
import co.daniel.mercado.domain.usecase.SetMonthlyBudget
import co.daniel.mercado.domain.usecase.SetTripLimit
import co.daniel.mercado.domain.usecase.ApplyReceipt
import co.daniel.mercado.scanner.MlKitReceiptReader
import co.daniel.mercado.scanner.ReceiptReader
import co.daniel.mercado.domain.usecase.SetPaidTotal
import co.daniel.mercado.domain.usecase.StartTrip
import co.daniel.mercado.domain.usecase.TripSession
import co.daniel.mercado.domain.usecase.UndoLastAction
import co.daniel.mercado.security.AppLock
import co.daniel.mercado.security.BackupManager
import co.daniel.mercado.security.DbKeyManager
import co.daniel.mercado.security.KeystoreKeyWrapper
import co.daniel.mercado.util.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File

/**
 * DI manual (D-03). Los ajustes están disponibles de inmediato; la base (clave del Keystore +
 * SQLCipher + semilla del primer arranque) se prepara en segundo plano y se obtiene con [data].
 */
class AppContainer(
    private val appContext: Context,
    databaseFactory: DatabaseFactory = defaultDatabaseFactory(appContext),
    dataStore: DataStore<Preferences> = appContext.settingsDataStore,
    val clock: Clock = Clock.SYSTEM,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val settings = SettingsStore(dataStore, secureScreenDefault = !BuildConfig.DEBUG)

    /** Ajustes del escáner siempre a mano (sin suspender) para la retroalimentación de cada lectura. */
    private val scanFeedbackPrefs: StateFlow<ScanFeedbackPrefs> = settings.settings
        .map { ScanFeedbackPrefs(sound = it.scanSound, vibration = it.scanVibration) }
        .stateIn(scope, SharingStarted.Eagerly, ScanFeedbackPrefs())

    val scanFeedback: ScanFeedback by lazy {
        DefaultScanFeedback.create(appContext) { scanFeedbackPrefs.value }
    }

    fun newBarcodeDecoder(): BarcodeDecoder = MlKitBarcodeDecoder()

    fun newLabelReader(): LabelReader = MlKitLabelReader()

    fun newReceiptReader(): ReceiptReader = MlKitReceiptReader()

    /** Compuerta de bloqueo, una por proceso (S-05). */
    val appLock = AppLock(clock)

    /** La cámara de la compra ofrece aquí y el `TripViewModel` escucha. */
    val tripBarcodeSource: BarcodeSource = SharedBarcodeSource()

    private val dataGraph: Deferred<DataGraph> = scope.async(ioDispatcher) {
        val db = databaseFactory.create(appContext)
        db.openHelper.writableDatabase
        DataGraph(db, clock) { appContext.getString(R.string.provisional_name, it) }
            .also { importInitialCatalog(it) }
    }

    /** Espera a que la base esté abierta. Falla con `DbKeyException` si la clave no se recupera. */
    suspend fun data(): DataGraph = dataGraph.await()

    /**
     * "Borrar todo": deja los datos como recién instalada (categorías y catálogo inicial), en una
     * transacción. Conserva los ajustes de seguridad, escáner y mercado; la llave de la base no cambia.
     */
    suspend fun resetAllData() {
        val graph = data()
        graph.db.withTransaction {
            val dao = graph.db.backupDao()
            dao.deleteTripItems()
            dao.deleteShoppingList()
            dao.deletePriceHistory()
            dao.deleteBarcodes()
            dao.deleteTrips()
            dao.deleteProducts()
            dao.deleteBudgets()
            dao.deleteCategories()
            MercadoDatabase.SEED_CATEGORIES.forEachIndexed { index, name ->
                graph.db.categoryDao().insert(CategoryEntity(name = name, sortOrder = index))
            }
        }
        graph.tripSession.undo.clear()
        settings.setSeedImported(false)
        settings.setBudgetHintDismissed(false)
        settings.setLastReminderCycle(0)
        settings.setLastBackupReminderAt(0)
        importInitialCatalog(graph)
    }

    private suspend fun importInitialCatalog(graph: DataGraph) {
        if (settings.current().seedImported) return
        val loader = InitialCatalogLoader(graph.db, graph.seedImporter) { appContext.assets.open(it) }
        when (val result = loader.loadIfEmpty()) {
            is SeedResult.Invalid -> AppLog.w("Semilla inicial inválida: ${result.error} en ${result.index}")
            is SeedResult.Imported, null -> settings.setSeedImported(true)
        }
    }

    companion object {
        private fun defaultDatabaseFactory(context: Context): DatabaseFactory {
            val keyManager = DbKeyManager(
                keyFile = File(context.noBackupFilesDir, DbKeyManager.FILE_NAME),
                databaseFile = context.getDatabasePath(MercadoDatabase.FILE_NAME),
                wrapper = KeystoreKeyWrapper(),
            )
            return SqlCipherDatabaseFactory(keyProvider = keyManager::getOrCreateKey)
        }
    }
}

/** Todo lo que depende de la base abierta: repositorios y casos de uso. */
class DataGraph(
    val db: MercadoDatabase,
    clock: Clock,
    provisionalName: (String) -> String,
) {
    val products = RoomProductRepository(db)
    val trips = RoomTripRepository(db)
    val budgets = RoomBudgetRepository(db)
    val shoppingList = RoomShoppingListRepository(db)
    val transactions = RoomTransactionRunner(db)
    val tripSession = TripSession(transactions)
    val seedImporter = SeedImporter(db, clock)
    val backupManager = BackupManager(db, clock)

    val getMonthStatus = GetMonthStatus(budgets, trips, clock)
    val observeMonthStatus = ObserveMonthStatus(budgets, trips, getMonthStatus)
    val setMonthlyBudget = SetMonthlyBudget(budgets, clock)
    val startTrip = StartTrip(trips, getMonthStatus, tripSession, clock)
    val observeActiveTrip = ObserveActiveTrip(trips)
    val handleScan = HandleScan(trips, products, ScanDebouncer(clock), tripSession, clock)
    val addProductToTrip = AddProductToTrip(trips, products, tripSession, clock)
    val addUnknownScanned = AddUnknownScanned(trips, products, tripSession, clock, provisionalName)
    val addLoosePrice = AddLoosePrice(trips, tripSession, clock)
    val addWeighedItem = AddWeighedItem(trips, products, tripSession, clock)
    val changeQuantity = ChangeQuantity(trips, tripSession)
    val changeLinePrice = ChangeLinePrice(trips, tripSession)
    val removeLine = RemoveLine(trips, tripSession)
    val renameLooseLine = RenameLooseLine(trips, tripSession)
    val undoLastAction = UndoLastAction(trips, products, tripSession)
    val closeTrip = CloseTrip(trips, products, tripSession, clock, shoppingList)
    val discardTrip = DiscardTrip(trips, tripSession)
    val setPaidTotal = SetPaidTotal(trips)
    val setTripLimit = SetTripLimit(trips, tripSession)
    val applyReceipt = ApplyReceipt(trips, products, tripSession, clock)

    val searchProducts = SearchProducts(products)
    val observeFavorites = ObserveFavorites(products)
    val saveProduct = SaveProduct(products, transactions, clock)
    val deactivateProduct = DeactivateProduct(products, clock)
    val linkBarcode = LinkBarcode(products, transactions, clock)
    val reactivateProduct = ReactivateProduct(products, clock)
    val unlinkBarcode = UnlinkBarcode(products)
    val observeCatalog = ObserveCatalog(products)
    val observeNeedsReviewCount = ObserveNeedsReviewCount(products)
    val getProductDetail = GetProductDetail(products)
    val moveFavorite = MoveFavorite(products, transactions, clock)

    val observeClosedTrips = ObserveClosedTrips(trips)
    val getTripDetail = GetTripDetail(trips)

    val buildSuggestedList = BuildSuggestedList(trips, shoppingList, transactions, clock)
    val observeShoppingList = ObserveShoppingList(trips, products, shoppingList, clock)
    val toggleListItem = ToggleListItem(shoppingList)
    val addListItem = AddListItem(shoppingList)
    val removeListItem = RemoveListItem(trips, shoppingList, clock)
    val setCategoryBudget = SetCategoryBudget(products)
    val getPriceChanges = GetPriceChanges(trips, products)
    val getMonthSummary = GetMonthSummary(trips, products, getMonthStatus, getPriceChanges)
    val getMonthsComparison = GetMonthsComparison(getMonthStatus)
    val shouldRemindShopping = ShouldRemindShopping(trips, buildSuggestedList, clock)

    val deleteTripsInRange = DeleteTripsInRange(trips, transactions)
    val deleteProducts = DeleteProducts(products, transactions)
}
