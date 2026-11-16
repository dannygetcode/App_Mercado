package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.UnitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogBrowseTest {

    private val f = TripFixture()
    private val observeCatalog = ObserveCatalog(f.products)
    private val saveProduct = SaveProduct(f.products, f.tx, f.clock)
    private val moveFavorite = MoveFavorite(f.products, f.tx, f.clock)

    private suspend fun names(filter: CatalogFilter) = observeCatalog(filter).first().map { it.name }

    @Test
    fun `el catalogo busca sin tildes y filtra por categoria, favoritos y por revisar`() = runTest {
        f.product("Limón Tahití", 3_000)
        f.product("Arroz Diana", 5_900)
        val leche = f.product("Leche entera", 4_300)
        f.products.update(leche.copy(categoryId = 10, favorite = true))
        val provisional = f.product("Producto ····7897", 2_000)
        f.products.update(provisional.copy(needsReview = true))

        assertEquals(listOf("Arroz Diana", "Leche entera", "Limón Tahití", "Producto ····7897"), names(CatalogFilter()))
        assertEquals(listOf("Limón Tahití"), names(CatalogFilter(query = "LIMON")))
        assertEquals(listOf("Leche entera"), names(CatalogFilter(categoryId = 10)))
        assertEquals(listOf("Leche entera"), names(CatalogFilter(favoritesOnly = true)))
        assertEquals(listOf("Producto ····7897"), names(CatalogFilter(needsReviewOnly = true)))
        assertEquals(1, ObserveNeedsReviewCount(f.products)().first())
    }

    @Test
    fun `un producto desactivado sale del catalogo y reactivarlo lo devuelve`() = runTest {
        val arroz = f.product("Arroz Diana", 5_900)
        DeactivateProduct(f.products, f.clock)(arroz.id)
        assertTrue(names(CatalogFilter()).isEmpty())

        assertTrue(ReactivateProduct(f.products, f.clock)(arroz.id))
        assertEquals(listOf("Arroz Diana"), names(CatalogFilter()))
    }

    @Test
    fun `la ficha trae codigos e historial del mas reciente al mas viejo`() = runTest {
        val arroz = f.product("Arroz Diana", 5_900, TripFixture.ARROZ)
        f.clock.advance(1_000)
        saveProduct(ProductDraft(arroz.id, "Arroz Diana", 1, UnitType.UNIT, Pesos(6_200)))

        val detail = GetProductDetail(f.products)(arroz.id)!!

        assertEquals(listOf(TripFixture.ARROZ), detail.barcodes)
        assertEquals(listOf(Pesos(6_200), Pesos(5_900)), detail.priceHistory.map { it.price })
        assertNull(GetProductDetail(f.products)(999))
    }

    @Test
    fun `quitar un codigo solo funciona sobre su dueno`() = runTest {
        val arroz = f.product("Arroz Diana", 5_900, TripFixture.ARROZ)
        val leche = f.product("Leche", 4_300)
        val unlink = UnlinkBarcode(f.products)

        assertFalse(unlink(leche.id, TripFixture.ARROZ))
        assertTrue(unlink(arroz.id, TripFixture.ARROZ))
        assertNull(f.products.findByBarcode(TripFixture.ARROZ))
    }

    @Test
    fun `mover un favorito cambia el orden que usa la compra`() = runTest {
        val ids = listOf("Arroz", "Leche", "Pan").map { name ->
            val p = f.product(name, 1_000)
            (saveProduct(ProductDraft(p.id, name, 1, UnitType.UNIT, Pesos(1_000), favorite = true)) as SaveProductResult.Saved).productId
        }

        assertTrue(moveFavorite(ids[2], up = true))
        assertEquals(listOf("Arroz", "Pan", "Leche"), f.products.observeFavorites().first().map { it.name })
        assertFalse(moveFavorite(ids[0], up = true))
        assertTrue(moveFavorite(ids[0], up = false))
        assertEquals(listOf("Pan", "Arroz", "Leche"), names(CatalogFilter(favoritesOnly = true)))
    }
}
