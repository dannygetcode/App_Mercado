package co.daniel.mercado.domain.fakes

import co.daniel.mercado.domain.port.TransactionRunner

/** En memoria no hay transacciones reales: ejecuta el bloque y cuenta las llamadas. */
class ImmediateTransactionRunner : TransactionRunner {
    var transactions = 0
        private set

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactions++
        return block()
    }
}
