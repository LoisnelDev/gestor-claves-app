package com.loisnel.gestorclaves.core.crypto

import com.loisnel.gestorclaves.core.security.TTLValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * Suite de tests para TTLValidator — UT-013 a UT-016
 *
 * Verifica la validación de TTL con tolerancia para clock drift industrial.
 */
class TTLValidatorTest {

    private val now = System.currentTimeMillis()
    private val tolerance = 300_000L // 5 minutos

    // ═══════════════════════════════════════════════════════════
    // UT-013: TTL vigente → Valid
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut013_ttl_valid_future_expiry() {
        val futureExpiry = now + 3_600_000L // 1 hora en el futuro
        val result = TTLValidator.validate(futureExpiry, tolerance, now)
        assertTrue(
            "UT-013 FAILED: TTL futuro debe ser Valid",
            result is TTLValidator.TTLResult.Valid
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-014: TTL expirado sin tolerancia → Expired
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut014_ttl_expired_no_tolerance() {
        val pastExpiry = now - 60_000L // 1 minuto expirado
        val result = TTLValidator.validate(pastExpiry, toleranceMs = 0L, nowMs = now)
        assertTrue(
            "UT-014 FAILED: TTL expirado sin tolerancia debe ser Expired",
            result is TTLValidator.TTLResult.Expired
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-015: TTL expirado dentro de tolerancia → ValidWithTolerance
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut015_ttl_expired_within_tolerance() {
        val justExpired = now - 60_000L // 1 min expirado (dentro de 5 min tolerancia)
        val result = TTLValidator.validate(justExpired, tolerance, now)
        assertTrue(
            "UT-015 FAILED: TTL expirado dentro de tolerancia debe ser ValidWithTolerance",
            result is TTLValidator.TTLResult.ValidWithTolerance
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-016: TTL muy expirado fuera de tolerancia → Expired
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut016_ttl_extreme_drift_outside_tolerance() {
        val veryOld = now - 3_600_000L // 1 hora expirado (fuera de 5 min tolerancia)
        val result = TTLValidator.validate(veryOld, tolerance, now)
        assertTrue(
            "UT-016 FAILED: TTL muy expirado debe ser Expired incluso con tolerancia",
            result is TTLValidator.TTLResult.Expired
        )
    }
}
