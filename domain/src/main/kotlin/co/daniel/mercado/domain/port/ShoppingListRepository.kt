package co.daniel.mercado.domain.port

import co.daniel.mercado.domain.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow

/** Lista de mercado (RF-09). Un ítem por producto. */
interface ShoppingListRepository {

    fun observeAll(): Flow<List<ShoppingListItem>>

    suspend fun getAll(): List<ShoppingListItem>

    suspend fun upsert(item: ShoppingListItem)

    suspend fun delete(productId: Long)
}
