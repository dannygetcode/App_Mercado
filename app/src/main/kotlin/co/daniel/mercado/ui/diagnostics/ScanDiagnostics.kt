package co.daniel.mercado.ui.diagnostics

/** Una lectura aceptada en la pantalla de diagnóstico. */
data class DiagnosticRead(
    val code: String,
    val atMs: Long,
    /** Desde la lectura aceptada anterior o, si es la primera, desde que arrancó la cámara. */
    val elapsedMs: Long?,
)

/**
 * Estado de la pantalla de diagnóstico del escáner (solo debug). Inmutable: cada evento devuelve
 * una copia. Mide el tiempo de la cámara arrancada a la primera lectura y las lecturas por minuto.
 */
data class ScanDiagnostics(
    val cameraStartedAt: Long? = null,
    val firstReadLatencyMs: Long? = null,
    val reads: List<DiagnosticRead> = emptyList(),
    val accepted: Int = 0,
    val bounced: Int = 0,
) {
    fun cameraStarted(now: Long): ScanDiagnostics =
        copy(cameraStartedAt = now, firstReadLatencyMs = null)

    fun cameraStopped(): ScanDiagnostics = copy(cameraStartedAt = null)

    /** [accepted] = pasó el antirrebote del dominio; si no, solo cuenta como rebote. */
    fun read(code: String, now: Long, accepted: Boolean): ScanDiagnostics {
        if (!accepted) return copy(bounced = bounced + 1)
        val previous = reads.firstOrNull()?.atMs?.takeIf { firstReadLatencyMs != null }
        val reference = previous ?: cameraStartedAt
        val latency = firstReadLatencyMs ?: cameraStartedAt?.let { now - it }
        return copy(
            firstReadLatencyMs = latency,
            reads = (listOf(DiagnosticRead(code, now, reference?.let { now - it })) + reads)
                .take(MAX_READS),
            accepted = this.accepted + 1,
        )
    }

    fun readsPerMinute(now: Long): Int = reads.count { now - it.atMs < MINUTE_MS }

    companion object {
        const val MAX_READS = 100
        const val MINUTE_MS = 60_000L
    }
}
