package co.daniel.mercado.data.seed

import kotlinx.serialization.Serializable

/** Formato de `catalogo_inicial.json` (03-datos.md). */
@Serializable
data class SeedFileDto(
    val version: Int,
    val source: String? = null,
    val products: List<SeedProductDto>,
)

@Serializable
data class SeedProductDto(
    val name: String,
    val category: String,
    val unit: String,
    val price: Long,
    val barcodes: List<String> = emptyList(),
    val favorite: Boolean = false,
)
