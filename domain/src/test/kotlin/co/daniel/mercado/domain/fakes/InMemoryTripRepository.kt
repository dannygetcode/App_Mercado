package co.daniel.mercado.domain.fakes

import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Trip
import co.daniel.mercado.domain.model.TripItem
import co.daniel.mercado.domain.model.TripSummary
import co.daniel.mercado.domain.model.TripWithItems
import co.daniel.mercado.domain.model.sum
import co.daniel.mercado.domain.port.TripRepository
import co.daniel.mercado.domain.rules.LineMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryTripRepository : TripRepository {

    private data class State(val trips: Map<Long, Trip> = emptyMap(), val items: Map<Long, TripItem> = emptyMap())

    private val state = MutableStateFlow(State())
    private var nextTripId = 1L
    private var nextItemId = 1L

    val trips: List<Trip> get() = state.value.trips.values.sortedBy { it.id }

    fun itemsOf(tripId: Long): List<TripItem> =
        state.value.items.values.filter { it.tripId == tripId }.sortedByDescending { it.position }

    private fun State.active(): TripWithItems? =
        trips.values.firstOrNull { it.isOpen }?.let { trip ->
            TripWithItems(trip, items.values.filter { it.tripId == trip.id }.sortedByDescending { it.position })
        }

    override fun observeActive(): Flow<TripWithItems?> = state.map { it.active() }

    override fun observeClosed(): Flow<List<TripSummary>> = state.map { s ->
        s.trips.values.filter { !it.isOpen }.sortedByDescending { it.closedAt }.map { trip ->
            TripSummary(trip, LineMath.articleCount(s.items.values.filter { it.tripId == trip.id }))
        }
    }

    override suspend fun getWithItems(id: Long): TripWithItems? =
        state.value.trips[id]?.let { TripWithItems(it, itemsOf(id)) }

    override suspend fun getActive(): TripWithItems? = state.value.active()

    override suspend fun getTrip(id: Long): Trip? = state.value.trips[id]

    override suspend fun insertTrip(trip: Trip): Long {
        val id = nextTripId++
        state.value = state.value.copy(trips = state.value.trips + (id to trip.copy(id = id)))
        return id
    }

    override suspend fun updateTrip(trip: Trip) {
        check(state.value.trips.containsKey(trip.id))
        state.value = state.value.copy(trips = state.value.trips + (trip.id to trip))
    }

    override suspend fun deleteTrip(id: Long) {
        state.value = State(
            trips = state.value.trips - id,
            items = state.value.items.filterValues { it.tripId != id },
        )
    }

    override suspend fun getItem(id: Long): TripItem? = state.value.items[id]

    override suspend fun insertItem(item: TripItem): Long {
        val id = if (item.id != 0L) item.id else nextItemId++
        if (id >= nextItemId) nextItemId = id + 1
        check(!state.value.items.containsKey(id)) { "línea $id ya existe" }
        state.value = state.value.copy(items = state.value.items + (id to item.copy(id = id)))
        return id
    }

    override suspend fun updateItem(item: TripItem) {
        check(state.value.items.containsKey(item.id))
        state.value = state.value.copy(items = state.value.items + (item.id to item))
    }

    override suspend fun deleteItem(id: Long) {
        state.value = state.value.copy(items = state.value.items - id)
    }

    override suspend fun nextPosition(tripId: Long): Int =
        (state.value.items.values.filter { it.tripId == tripId }.maxOfOrNull { it.position } ?: -1) + 1

    override suspend fun spentBetween(fromMillis: Long, toMillis: Long): Pesos =
        state.value.trips.values
            .filter { t -> t.closedAt?.let { it in fromMillis until toMillis } == true }
            .map { it.effectiveTotal }
            .sum()

    override suspend fun recentStores(limit: Int): List<String> =
        state.value.trips.values
            .filter { it.closedAt != null && !it.store.isNullOrBlank() }
            .sortedByDescending { it.closedAt }
            .mapNotNull { it.store }
            .distinct()
            .take(limit)
}
