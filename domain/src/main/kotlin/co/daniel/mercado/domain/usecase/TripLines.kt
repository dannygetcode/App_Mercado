package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.ScanOutcome
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.TripRepository

/** Operaciones de líneas compartidas por los casos de uso. Se llaman dentro de `TripSession.mutate`. */
internal class TripLines(
    private val trips: TripRepository,
    private val clock: Clock,
) {
    /** Producto `UNIT`: incrementa su línea si ya está, si no agrega una con [price] (o su referencia). */
    suspend fun addUnit(
        active: TripWithItems,
        product: Product,
        price: Pesos = product.refPrice,
        reverts: MutableList<Revert>,
    ): ScanOutcome {
        val existing = active.items.firstOrNull { it.productId == product.id && it.unitType == UnitType.UNIT }
        if (existing != null) {
            val updated = existing.copy(quantity = existing.quantity + 1, unitPrice = price)
            trips.updateItem(updated)
            reverts += Revert.LineUpdated(existing)
            return ScanOutcome.Incremented(existing.id, existing.nameSnapshot, updated.quantity)
        }
        val lineId = insert(active.trip.id, product.id, product.name, UnitType.UNIT, price, null, reverts)
        return ScanOutcome.Added(lineId, product.name, price)
    }

    suspend fun insert(
        tripId: Long,
        productId: Long?,
        name: String,
        unitType: UnitType,
        price: Pesos,
        weightGrams: Int?,
        reverts: MutableList<Revert>,
    ): Long {
        val item = TripItem(
            tripId = tripId,
            productId = productId,
            nameSnapshot = name,
            unitType = unitType,
            quantity = 1,
            unitPrice = price,
            weightGrams = weightGrams,
            addedAt = clock.nowMillis(),
            position = trips.nextPosition(tripId),
        )
        val id = trips.insertItem(item)
        reverts += Revert.LineInserted(id)
        return id
    }
}

internal fun requireValidPrice(price: Pesos) {
    require(price.isPositive && price <= Pesos.KEYPAD_MAX) { "precio fuera de rango" }
}
