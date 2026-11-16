package co.daniel.mercado.data.seed

import co.daniel.mercado.data.db.MercadoDatabase
import java.io.FileNotFoundException
import java.io.InputStream

/** RF-02: en el primer arranque, si la base no tiene productos, importa la semilla de `assets`. */
class InitialCatalogLoader(
    private val db: MercadoDatabase,
    private val importer: SeedImporter,
    private val openAsset: (String) -> InputStream,
) {
    /** Nulo si no hacía falta (ya hay productos) o no hay semilla empaquetada. */
    suspend fun loadIfEmpty(): SeedResult? {
        if (db.productDao().count() > 0) return null
        val stream = try {
            openAsset(SeedImporter.ASSET_PATH)
        } catch (_: FileNotFoundException) {
            return null
        }
        return stream.use { importer.import(it) }
    }
}
