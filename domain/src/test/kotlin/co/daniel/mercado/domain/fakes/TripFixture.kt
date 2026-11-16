package co.daniel.mercado.domain.fakes

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.ScanDebouncer
import co.daniel.mercado.domain.usecase.AddLoosePrice
import co.daniel.mercado.domain.usecase.AddProductToTrip
import co.daniel.mercado.domain.usecase.AddUnknownScanned
import co.daniel.mercado.domain.usecase.AddWeighedItem
import co.daniel.mercado.domain.usecase.ChangeLinePrice
import co.daniel.mercado.domain.usecase.ChangeQuantity
import co.daniel.mercado.domain.usecase.CloseTrip
import co.daniel.mercado.domain.usecase.DiscardTrip
import co.daniel.mercado.domain.usecase.GetMonthStatus
import co.daniel.mercado.domain.usecase.HandleScan
import co.daniel.mercado.domain.usecase.ObserveActiveTrip
import co.daniel.mercado.domain.usecase.RemoveLine
import co.daniel.mercado.domain.usecase.RenameLooseLine
import co.daniel.mercado.domain.usecase.SetMonthlyBudget
import co.daniel.mercado.domain.usecase.SetPaidTotal
import co.daniel.mercado.domain.usecase.StartTrip
import co.daniel.mercado.domain.usecase.TripSession
import co.daniel.mercado.domain.usecase.UndoLastAction

/** Todo el dominio de compra cableado sobre repositorios en memoria. */
class TripFixture {
    val clock = FakeClock()
    val products = InMemoryProductRepository()
    val trips = InMemoryTripRepository()
    val budgets = InMemoryBudgetRepository()
    val tx = ImmediateTransactionRunner()
    val shoppingList = InMemoryShoppingListRepository()
    val session = TripSession(tx)

    val monthStatus = GetMonthStatus(budgets, trips, clock)
    val setMonthlyBudget = SetMonthlyBudget(budgets, clock)
    val startTrip = StartTrip(trips, monthStatus, session, clock)
    val observeActiveTrip = ObserveActiveTrip(trips)
    val handleScan = HandleScan(trips, products, ScanDebouncer(clock), session, clock)
    val addProduct = AddProductToTrip(trips, products, session, clock)
    val addUnknown = AddUnknownScanned(trips, products, session, clock) { "Producto ····$it" }
    val addLoose = AddLoosePrice(trips, session, clock)
    val addWeighed = AddWeighedItem(trips, products, session, clock)
    val changeQuantity = ChangeQuantity(trips, session)
    val changePrice = ChangeLinePrice(trips, session)
    val removeLine = RemoveLine(trips, session)
    val renameLoose = RenameLooseLine(trips, session)
    val undo = UndoLastAction(trips, products, session)
    val closeTrip = CloseTrip(trips, products, session, clock, shoppingList)
    val discardTrip = DiscardTrip(trips, session)
    val setPaidTotal = SetPaidTotal(trips)

    suspend fun product(
        name: String,
        price: Long,
        vararg codes: String,
        unit: UnitType = UnitType.UNIT,
        withSeedHistory: Boolean = true,
    ): Product {
        val draft = Product(name = name, categoryId = 1, unitType = unit, refPrice = Pesos(price))
        val id = products.insert(draft)
        codes.forEach { products.linkBarcode(it, id, 0) }
        if (withSeedHistory && unit == UnitType.UNIT) {
            products.addPricePoint(PricePoint(id, Pesos(price), 0, PriceSource.SEED))
        }
        return draft.copy(id = id)
    }

    suspend fun activeItems() = trips.getActive()?.items.orEmpty()

    /** Pasa la ventana del antirrebote para poder leer el mismo código otra vez. */
    fun afterDebounce() = clock.advance(ScanDebouncer.DEFAULT_WINDOW_MS + 100)

    companion object {
        const val ARROZ = "7702004003508"
        const val ARROZ_UPCA = "042100005264"
        const val LECHE = "96385074"
        const val DESCONOCIDO = "7701234567897"
    }
}
