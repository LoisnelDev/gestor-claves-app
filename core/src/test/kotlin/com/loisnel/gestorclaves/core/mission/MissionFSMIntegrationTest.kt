package com.loisnel.gestorclaves.core.mission

import com.loisnel.gestorclaves.core.domain.FCPConstants
import com.loisnel.gestorclaves.core.security.TTLValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * MissionFSMIntegrationTest — UT-037 a UT-038
 * Tests de integración de la FSM — lógica pura sin base de datos.
 * JVM puro — sin emulador.
 */
class MissionFSMIntegrationTest {

    private val now = System.currentTimeMillis()

    // Simula la lógica de transición de estado sin Room
    private fun simulateActivation(
        currentStatus: String,
        expiryMs: Long,
        toleranceMs: Long = FCPConstants.DEFAULT_CLOCK_TOLERANCE_MS
    ): String {
        if (currentStatus != FCPConstants.STATUS_ISSUED)
            throw IllegalStateException("Only ISSUED can transition to ACTIVE. Current: $currentStatus")

        val ttl = TTLValidator.validate(expiryMs, toleranceMs)
        if (ttl is TTLValidator.TTLResult.Expired)
            throw SecurityException("TTL expired: ${ttl.driftMs}ms drift")

        return FCPConstants.STATUS_ACTIVE
    }

    private fun simulateClose(currentStatus: String, result: String): String {
        if (currentStatus != FCPConstants.STATUS_ACTIVE)
            throw IllegalStateException("Only ACTIVE can be closed. Current: $currentStatus")
        require(result == FCPConstants.STATUS_COMPLETED || result == FCPConstants.STATUS_FAILED) {
            "Invalid close result: $result"
        }
        return result
    }

    private fun simulateRecovery(status: String, expiryMs: Long, toleranceMs: Long): String {
        if (status != FCPConstants.STATUS_INTERRUPTED) return status
        val ttl = TTLValidator.validate(expiryMs, toleranceMs)
        return if (ttl !is TTLValidator.TTLResult.Expired)
            FCPConstants.STATUS_ISSUED
        else
            FCPConstants.STATUS_EXPIRED
    }

    // UT-037: Ciclo completo ISSUED → ACTIVE → COMPLETED
    @Test
    fun ut037_full_mission_cycle_issued_active_completed() {
        val expiry = now + 3_600_000L // 1 hora en el futuro

        // ISSUED → ACTIVE
        val afterActivation = simulateActivation(FCPConstants.STATUS_ISSUED, expiry)
        assertEquals("UT-037: debe ser ACTIVE tras activación",
            FCPConstants.STATUS_ACTIVE, afterActivation)

        // ACTIVE → COMPLETED
        val afterClose = simulateClose(afterActivation, FCPConstants.STATUS_COMPLETED)
        assertEquals("UT-037: debe ser COMPLETED tras cierre",
            FCPConstants.STATUS_COMPLETED, afterClose)
    }

    // UT-038: INTERRUPTED → ISSUED (recovery power failure) + INTERRUPTED → EXPIRED
    @Test
    fun ut038_interrupted_recovery_based_on_ttl() {
        val tolerance = FCPConstants.DEFAULT_CLOCK_TOLERANCE_MS

        // TTL válido → ISSUED
        val futureExpiry = now + 3_600_000L
        val recoveredToIssued = simulateRecovery(
            FCPConstants.STATUS_INTERRUPTED, futureExpiry, tolerance)
        assertEquals("UT-038: INTERRUPTED con TTL válido debe → ISSUED",
            FCPConstants.STATUS_ISSUED, recoveredToIssued)

        // TTL expirado → EXPIRED
        val pastExpiry = now - 3_600_000L
        val recoveredToExpired = simulateRecovery(
            FCPConstants.STATUS_INTERRUPTED, pastExpiry, tolerance)
        assertEquals("UT-038: INTERRUPTED con TTL expirado debe → EXPIRED",
            FCPConstants.STATUS_EXPIRED, recoveredToExpired)

        // Estado no INTERRUPTED → sin cambio
        val unchanged = simulateRecovery(
            FCPConstants.STATUS_ACTIVE, futureExpiry, tolerance)
        assertEquals("UT-038: estado no INTERRUPTED no debe cambiar",
            FCPConstants.STATUS_ACTIVE, unchanged)
    }
}
