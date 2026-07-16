package com.loisnel.gestorclaves.core.security

/**
 * TTLValidator — Validación de TTL con tolerancia para clock drift
 *
 * Los dispositivos de campo industriales operan sin conexión NTP,
 * lo que puede causar desajustes de reloj. Este validator permite
 * un margen configurable (firmado en el paquete de misión — no
 * manipulable por el receptor) y registra el drift para auditoría.
 *
 * TAD-B v2.1.1 — Sección 11
 */
object TTLValidator {

    /**
     * Resultado de la validación TTL con drift registrado.
     */
    sealed class TTLResult(open val driftMs: Long) {
        /** TTL vigente — sin drift significativo */
        data class Valid(override val driftMs: Long) : TTLResult(driftMs)

        /** TTL expirado pero dentro del margen de tolerancia */
        data class ValidWithTolerance(override val driftMs: Long) : TTLResult(driftMs)

        /** TTL expirado fuera del margen — rechazar */
        data class Expired(override val driftMs: Long) : TTLResult(driftMs)
    }

    /**
     * Valida el TTL con tolerancia configurable.
     *
     * @param expiryMs     Timestamp de expiración (milisegundos Unix, firmado en el paquete)
     * @param toleranceMs  Margen de tolerancia en ms (firmado en el paquete — no manipulable)
     * @param nowMs        Tiempo actual en ms (por defecto System.currentTimeMillis())
     * @return [TTLResult] con el drift registrado para auditoría
     */
    fun validate(
        expiryMs: Long,
        toleranceMs: Long = 0L,
        nowMs: Long = System.currentTimeMillis()
    ): TTLResult {
        val drift = nowMs - expiryMs

        return when {
            nowMs <= expiryMs ->
                TTLResult.Valid(drift)

            nowMs <= expiryMs + toleranceMs ->
                TTLResult.ValidWithTolerance(drift)

            else ->
                TTLResult.Expired(drift)
        }
    }

    /** Conveniencia: retorna true si el TTL está vigente (con o sin tolerancia) */
    fun isValid(expiryMs: Long, toleranceMs: Long = 0L): Boolean =
        validate(expiryMs, toleranceMs) !is TTLResult.Expired
}
