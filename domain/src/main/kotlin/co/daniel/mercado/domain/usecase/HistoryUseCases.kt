package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.port.TripRepository
import kotlinx.coroutines.flow.Flow

/** Compras cerradas, la más reciente primero (historial e "Última compra" del Inicio). */
class ObserveClosedTrips(private val trips: TripRepository) {
    operator fun invoke(): Flow<List<TripSummary>> = trips.observeClosed()
}

/** Detalle de una compra cerrada. Una abierta no se muestra aquí (se edita en la compra). */
class GetTripDetail(private val trips: TripRepository) {
    suspend operator fun invoke(tripId: Long): TripWithItems? =
        trips.getWithItems(tripId)?.takeIf { !it.trip.isOpen }
}
