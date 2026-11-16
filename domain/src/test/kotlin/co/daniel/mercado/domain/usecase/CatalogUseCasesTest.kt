package co.daniel.mercado.domain.usecase

import co.daniel.mercado.domain.fakes.TripFixture
import co.daniel.mercado.domain.fakes.TripFixture.Companion.ARROZ
import co.daniel.mercado.domain.fakes.TripFixture.Companion.ARROZ_UPCA
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.PriceSource
import co.daniel.mercado.domain.model.UnitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatalogUseCasesTest {

    private val f = TripFixture()
    private val search = SearchProducts(f.products)
    private val save = SaveProduct(f.products, f.tx, f.clock)
    private val deactivate = DeactivateProduct(f.products, f.clock)
    private val link = LinkBarcode(f.products, f.tx, f.clock)
    private val favorites = ObserveFavorites(f.products)

    private fun draft(name: String, price: Long = 1_000, favorite: Boolean = false, id: Long = 0) =
        ProductDraft(id = id, name = name, categoryId = 1, unitType = UnitType.UNIT, refPrice = Pesos(price), favorite = favorite)

    @Test
    fun `buscar sin tildes ni mayusculas encuentra el producto`() = runTest {
        f.product("Limón Tahití", 3_000)
        f.product("Leche Alquería 1 L", 4_300)

        assertEquals(listOf("Limón Tahití"), search("LIMON").map { it.name })
        assertEquals(listOf("Leche Alquería 1 L"), search("alqueria").map { it.name })
        assertTrue(search("   ").isEmpty())
    }

    @Test
    fun `guardar crea un producto revisado con su primer precio manual`() = runTest {
        val saved = assertIs<SaveProductResult.Saved>(save(draft("  Panela   Doña Pepa ", 4_500)))

        val product = requireNotNull(f.products.getById(saved.productId))
        assertEquals("Panela Doña Pepa", product.name)
        assertFalse(product.needsReview)
        assertEquals(listOf(PriceSource.MANUAL), f.products.priceHistory.map { it.source })
    }

    @Test
    fun `guardar valida nombre, precio, categoria y duplicados`() = runTest {
        val arroz = f.product("Arroz Diana 1 kg", 5_900)

        assertEquals(SaveProductResult.InvalidName, save(draft("   ")))
        assertEquals(SaveProductResult.InvalidName, save(draft("x".repeat(81))))
        assertEquals(SaveProductResult.InvalidPrice, save(draft("Algo", price = 0)))
        assertEquals(SaveProductResult.InvalidPrice, save(draft("Algo", price = 50_000_001)))
        assertEquals(SaveProductResult.UnknownCategory, save(draft("Algo").copy(categoryId = 999)))
        assertEquals(SaveProductResult.DuplicateName(arroz.id), save(draft("arroz diana 1 KG")))
        assertEquals(SaveProductResult.NotFound, save(draft("Algo", id = 999)))
    }

    @Test
    fun `nombrar un provisional lo marca revisado y registra el nuevo precio`() = runTest {
        f.startTrip()
        f.addUnknown("7701234567897", Pesos(7_800))
        val provisional = f.products.all.single()

        save(draft("Galletas Festival", 8_100, id = provisional.id))

        val named = requireNotNull(f.products.getById(provisional.id))
        assertEquals("Galletas Festival", named.name)
        assertFalse(named.needsReview)
        assertEquals(Pesos(8_100), f.products.lastPricePoint(provisional.id)?.price)
    }

    @Test
    fun `los favoritos nuevos van al final y los desactivados desaparecen`() = runTest {
        val a = assertIs<SaveProductResult.Saved>(save(draft("Tomate", favorite = true))).productId
        val b = assertIs<SaveProductResult.Saved>(save(draft("Cebolla", favorite = true))).productId

        assertEquals(listOf(a, b), favorites().first().map { it.id })

        deactivate(a)
        assertEquals(listOf(b), favorites().first().map { it.id })
        assertTrue(search("tomate").isEmpty())
    }

    @Test
    fun `asociar codigo valida, normaliza y reasigna si era de otro`() = runTest {
        val arroz = f.product("Arroz Diana 1 kg", 5_900)
        val otro = f.product("Arroz Roa 1 kg", 5_600, ARROZ)

        assertEquals(LinkBarcodeResult.InvalidCode, link(arroz.id, "123"))
        assertEquals(LinkBarcodeResult.Linked, link(arroz.id, ARROZ_UPCA))
        assertEquals(arroz.id, f.products.barcodeOwner("0042100005264"))
        assertEquals(LinkBarcodeResult.AlreadyLinked, link(arroz.id, "0042100005264"))
        assertEquals(LinkBarcodeResult.Moved(otro.id), link(arroz.id, ARROZ))
        assertEquals(LinkBarcodeResult.ProductNotFound, link(999, ARROZ))
    }
}
