package co.daniel.mercado.domain.fakes

import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.PricePoint
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.port.ProductRepository
import co.daniel.mercado.domain.rules.NameNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryProductRepository : ProductRepository {

    private val products = MutableStateFlow<Map<Long, Product>>(emptyMap())
    val barcodes = mutableMapOf<String, Long>()
    val categories = mutableListOf(Category(1, "Granos y despensa"), Category(10, Category.OTHER, 9))
    val priceHistory = mutableListOf<PricePoint>()
    private var nextId = 1L

    val all: List<Product> get() = products.value.values.sortedBy { it.id }

    override suspend fun findByBarcode(code: String): Product? = barcodes[code]?.let { products.value[it] }

    override suspend fun getById(id: Long): Product? = products.value[id]

    override suspend fun search(normalizedQuery: String, limit: Int): List<Product> =
        products.value.values
            .filter { it.active && NameNormalizer.normalize(it.name).contains(normalizedQuery) }
            .sortedWith(compareByDescending<Product> { it.favorite }.thenBy { it.name })
            .take(limit)

    override suspend fun findByNormalizedName(normalizedName: String): Product? =
        products.value.values.firstOrNull { NameNormalizer.normalize(it.name) == normalizedName }

    override fun observeActive(): Flow<List<Product>> = products.map { map ->
        map.values.filter { it.active }.sortedBy { it.name }
    }

    override suspend fun priceHistory(productId: Long): List<PricePoint> =
        priceHistory.filter { it.productId == productId }.sortedByDescending { it.at }

    override fun observeFavorites(): Flow<List<Product>> = products.map { map ->
        map.values.filter { it.favorite && it.active }.sortedWith(compareBy({ it.favoriteOrder }, { it.name }))
    }

    override suspend fun insert(product: Product): Long {
        val id = nextId++
        products.value = products.value + (id to product.copy(id = id))
        return id
    }

    override suspend fun update(product: Product) {
        check(products.value.containsKey(product.id)) { "producto ${product.id} no existe" }
        products.value = products.value + (product.id to product)
    }

    override suspend fun delete(id: Long) {
        products.value = products.value - id
        barcodes.entries.removeAll { it.value == id }
    }

    override suspend fun barcodeOwner(code: String): Long? = barcodes[code]

    override suspend fun linkBarcode(code: String, productId: Long, at: Long) {
        barcodes[code] = productId
    }

    override suspend fun unlinkBarcode(code: String) {
        barcodes.remove(code)
    }

    override suspend fun barcodesOf(productId: Long): List<String> =
        barcodes.filterValues { it == productId }.keys.sorted()

    override suspend fun categories(): List<Category> = categories.sortedBy { it.sortOrder }

    override suspend fun categoryByName(name: String): Category? = categories.firstOrNull { it.name == name }

    override suspend fun insertCategory(category: Category): Long {
        val id = (categories.maxOfOrNull { it.id } ?: 0) + 1
        categories += category.copy(id = id)
        return id
    }

    override suspend fun lastPricePoint(productId: Long): PricePoint? =
        priceHistory.filter { it.productId == productId }.maxByOrNull { it.at }

    override suspend fun addPricePoint(point: PricePoint) {
        priceHistory += point
    }
}
