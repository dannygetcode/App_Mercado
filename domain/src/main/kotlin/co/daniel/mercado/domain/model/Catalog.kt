package co.daniel.mercado.domain.model

enum class UnitType { UNIT, KG }

data class Category(
    val id: Long,
    val name: String,
    val sortOrder: Int = 0,
    val monthlyBudget: Pesos? = null,
) {
    companion object {
        /** Categoría de respaldo para productos provisionales y líneas sin producto. */
        const val OTHER = "Otros"
    }
}

data class Product(
    val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val unitType: UnitType = UnitType.UNIT,
    val refPrice: Pesos,
    val favorite: Boolean = false,
    val favoriteOrder: Int = 0,
    val active: Boolean = true,
    val needsReview: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Ficha completa para el editor del catálogo. Historial de precios del más reciente al más viejo. */
data class ProductDetail(
    val product: Product,
    val barcodes: List<String>,
    val priceHistory: List<PricePoint>,
)

/** Filtro del catálogo. [query] es lo que el usuario teclea (se normaliza al filtrar). */
data class CatalogFilter(
    val query: String = "",
    val categoryId: Long? = null,
    val favoritesOnly: Boolean = false,
    val needsReviewOnly: Boolean = false,
)

enum class PriceSource { SEED, TRIP, MANUAL }

data class PricePoint(
    val productId: Long,
    val price: Pesos,
    val at: Long,
    val source: PriceSource,
)
