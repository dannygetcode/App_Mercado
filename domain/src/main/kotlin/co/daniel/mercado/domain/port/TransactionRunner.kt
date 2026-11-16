package co.daniel.mercado.domain.port

/** Ejecuta un bloque como una sola transacción de la base: todo o nada. */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
