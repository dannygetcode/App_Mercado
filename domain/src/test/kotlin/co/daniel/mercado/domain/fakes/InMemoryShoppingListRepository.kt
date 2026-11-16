package co.daniel.mercado.domain.fakes

import co.daniel.mercado.domain.model.ShoppingListItem
import co.daniel.mercado.domain.port.ShoppingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryShoppingListRepository : ShoppingListRepository {
    private val items = MutableStateFlow<Map<Long, ShoppingListItem>>(emptyMap())

    override fun observeAll(): Flow<List<ShoppingListItem>> = items.map { it.values.sortedBy { i -> i.productId } }

    override suspend fun getAll(): List<ShoppingListItem> = items.value.values.sortedBy { it.productId }

    override suspend fun upsert(item: ShoppingListItem) {
        items.value = items.value + (item.productId to item)
    }

    override suspend fun delete(productId: Long) {
        items.value = items.value - productId
    }
}
