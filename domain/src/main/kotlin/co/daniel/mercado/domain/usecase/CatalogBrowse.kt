package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.ProductDetail
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.port.TransactionRunner
import co.daniel.mercado.domain.rules.NameNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Lista del catálogo con buscador y filtros. Búsqueda sin tildes ni mayúsculas; con el filtro de
 * favoritos el orden es el de favoritos (el que se usa en la compra), si no, por nombre.
 */
class ObserveCatalog(private val products: ProductRepository) {

    operator fun invoke(filter: CatalogFilter): Flow<List<Product>> {
        val query = NameNormalizer.normalize(filter.query)
        return products.observeActive().map { all ->
            val matching = all.filter { p ->
                (query.isEmpty() || NameNormalizer.normalize(p.name).contains(query)) &&
                    (filter.categoryId == null || p.categoryId == filter.categoryId) &&
                    (!filter.favoritesOnly || p.favorite) &&
                    (!filter.needsReviewOnly || p.needsReview)
            }
            if (filter.favoritesOnly) matching.sortedWith(compareBy({ it.favoriteOrder }, { it.name })) else matching
        }.distinctUntilChanged()
    }
}

/** Productos provisionales que esperan nombre (tarjeta "Productos por revisar"). */
class ObserveNeedsReviewCount(private val products: ProductRepository) {
    operator fun invoke(): Flow<Int> =
        products.observeActive().map { all -> all.count { it.needsReview } }.distinctUntilChanged()
}

class GetProductDetail(private val products: ProductRepository) {
    suspend operator fun invoke(productId: Long): ProductDetail? {
        val product = products.getById(productId) ?: return null
        return ProductDetail(product, products.barcodesOf(productId), products.priceHistory(productId))
    }
}

/** Quita un código del producto; el código vuelve a leerse como desconocido. */
class UnlinkBarcode(private val products: ProductRepository) {
    suspend operator fun invoke(productId: Long, code: String): Boolean {
        if (products.barcodeOwner(code) != productId) return false
        products.unlinkBarcode(code)
        return true
    }
}

/**
 * Sube o baja un favorito una posición. Reescribe el orden de todos como 0..n-1 para que no queden
 * empates ni huecos.
 */
class MoveFavorite(
    private val products: ProductRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(productId: Long, up: Boolean): Boolean = transactions.inTransaction {
        val favorites = products.observeFavorites().first().toMutableList()
        val from = favorites.indexOfFirst { it.id == productId }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in favorites.indices) return@inTransaction false
        favorites.add(to, favorites.removeAt(from))
        val now = clock.nowMillis()
        favorites.forEachIndexed { index, product ->
            if (product.favoriteOrder != index) products.update(product.copy(favoriteOrder = index, updatedAt = now))
        }
        true
    }
}
