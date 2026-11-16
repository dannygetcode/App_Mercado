package co.daniel.mercado.ui.trip

import co.daniel.mercado.data.db.inMemoryDb
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.scanner.ScanFeedback

/** Retroalimentación que solo cuenta, para verificar sin vibrador. */
class RecordingFeedback : ScanFeedback {
    var accepted = 0
    var unknown = 0
    val warnings = mutableListOf<Boolean>()
    override fun accepted() { accepted++ }
    override fun unknown() { unknown++ }
    override fun budgetWarning(red: Boolean) { warnings += red }
    override fun release() = Unit
}

/** Reloj manual en milisegundos. */
class ManualClock(var now: Long = 1_800_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
    fun advance(ms: Long) { now += ms }
}

/** `DataGraph` real sobre Room en memoria (Robolectric). */
class TripGraph(val clock: ManualClock = ManualClock()) {
    val graph = DataGraph(inMemoryDb(), clock) { "Producto ····$it" }

    suspend fun product(
        name: String,
        price: Long,
        code: String? = null,
        unit: UnitType = UnitType.UNIT,
        favorite: Boolean = false,
    ): Long {
        val categoryId = graph.products.categoryByName("Otros")!!.id
        val id = graph.products.insert(
            Product(name = name, categoryId = categoryId, unitType = unit, refPrice = Pesos(price), favorite = favorite),
        )
        code?.let { graph.products.linkBarcode(it, id, 0) }
        return id
    }

    fun close() = graph.db.close()

    companion object {
        const val ARROZ = "7702004003508"
        const val LECHE = "96385074"
        const val DESCONOCIDO = "7701234567897"
    }
}
