package co.daniel.mercado.domain.port

/** Reloj inyectable para que las pruebas controlen el tiempo. */
fun interface Clock {
    /** Milisegundos epoch UTC. */
    fun nowMillis(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
