package co.daniel.mercado.domain.port

import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.Product
import kotlinx.coroutines.flow.Flow

/** Catálogo: productos, códigos de barras, categorías e historial de precios. */
interface ProductRepository {

    /** Consulta caliente del escaneo. [code] ya viene normalizado por `Ean`. */
    suspend fun findByBarcode(code: String): Product?

    suspend fun getById(id: Long): Product?

    /** Productos cuyo nombre normalizado contiene [normalizedQuery]; activos, favoritos primero. */
    suspend fun search(normalizedQuery: String, limit: Int): List<Product>

    suspend fun findByNormalizedName(normalizedName: String): Product?

    /** Productos activos ordenados por nombre; se emite de nuevo con cada cambio. */
    fun observeActive(): Flow<List<Product>>

    /** Favoritos activos en su orden. */
    fun observeFavorites(): Flow<List<Product>>

    /** Inserta y devuelve el id. El repositorio calcula el nombre normalizado. */
    suspend fun insert(product: Product): Long

    suspend fun update(product: Product)

    /** Solo para deshacer la creación de un producto provisional sin historial. */
    suspend fun delete(id: Long)

    /** Dueño actual de un código, o nulo. */
    suspend fun barcodeOwner(code: String): Long?

    /** Asocia (o reasigna) [code] a [productId]. */
    suspend fun linkBarcode(code: String, productId: Long, at: Long)

    suspend fun unlinkBarcode(code: String)

    suspend fun barcodesOf(productId: Long): List<String>

    suspend fun categories(): List<Category>

    suspend fun categoryByName(name: String): Category?

    suspend fun insertCategory(category: Category): Long

    suspend fun lastPricePoint(productId: Long): PricePoint?

    suspend fun addPricePoint(point: PricePoint)

    /** Historial de precios del producto, el más reciente primero. */
    suspend fun priceHistory(productId: Long): List<PricePoint>
}
