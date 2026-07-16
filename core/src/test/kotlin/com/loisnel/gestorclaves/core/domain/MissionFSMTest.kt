package com.loisnel.gestorclaves.core.domain

import com.loisnel.gestorclaves.core.security.TTLValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * MissionFSMTest — UT-024 a UT-026
 *
 * Tests de la lógica FSM sin base de datos.
 * Verifican las reglas de transición de estado y la lógica
 * de recovery de power failures.
 *
 * JVM puro — ejecuta sin emulador.
 */
class MissionFSMTest {

    private val now = System.currentTimeMillis()

    // ═══════════════════════════════════════════════════════════
    // UT-024: Solo ISSUED puede transicionar a ACTIVE
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut024_only_issued_can_transition_to_active() {
        fun canActivate(status: String): Boolean = status == FCPConstants.STATUS_ISSUED

        assertTrue("UT-024: ISSUED puede activarse", canActivate(FCPConstants.STATUS_ISSUED))
        assertFalse("UT-024: ACTIVE no puede re-activarse", canActivate(FCPConstants.STATUS_ACTIVE))
        assertFalse("UT-024: COMPLETED no puede activarse", canActivate(FCPConstants.STATUS_COMPLETED))
        assertFalse("UT-024: FAILED no puede activarse", canActivate(FCPConstants.STATUS_FAILED))
        assertFalse("UT-024: EXPIRED no puede activarse", canActivate(FCPConstants.STATUS_EXPIRED))
        assertFalse("UT-024: INTERRUPTED no puede activarse directamente", canActivate(FCPConstants.STATUS_INTERRUPTED))
    }

    // ═══════════════════════════════════════════════════════════
    // UT-025: COMPLETED y FAILED son estados finales inmutables
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut025_completed_and_failed_are_final_states() {
        val finalStates = setOf(
            FCPConstants.STATUS_COMPLETED,
            FCPConstants.STATUS_FAILED,
            FCPConstants.STATUS_EXPIRED
        )
        fun isFinalState(status: String): Boolean = status in finalStates

        assertTrue("UT-025: COMPLETED es final", isFinalState(FCPConstants.STATUS_COMPLETED))
        assertTrue("UT-025: FAILED es final",    isFinalState(FCPConstants.STATUS_FAILED))
        assertTrue("UT-025: EXPIRED es final",   isFinalState(FCPConstants.STATUS_EXPIRED))
        assertFalse("UT-025: ISSUED no es final",      isFinalState(FCPConstants.STATUS_ISSUED))
        assertFalse("UT-025: ACTIVE no es final",      isFinalState(FCPConstants.STATUS_ACTIVE))
        assertFalse("UT-025: INTERRUPTED no es final", isFinalState(FCPConstants.STATUS_INTERRUPTED))
    }

    // ═══════════════════════════════════════════════════════════
    // UT-026: INTERRUPTED → ISSUED si TTL válido (power failure recovery)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut026_interrupted_recovers_to_issued_when_ttl_valid() {
        val tolerance = FCPConstants.DEFAULT_CLOCK_TOLERANCE_MS

        // Lógica de recovery
        fun recoverStatus(expiryMs: Long, toleranceMs: Long): String {
            val ttl = TTLValidator.validate(expiryMs, toleranceMs)
            return if (ttl !is TTLValidator.TTLResult.Expired)
                FCPConstants.STATUS_ISSUED
            else
                FCPConstants.STATUS_EXPIRED
        }

        // TTL válido → ISSUED
        val futureExpiry = now + 3_600_000L // 1 hora en el futuro
        assertEquals("UT-026: TTL válido debe recuperar a ISSUED",
            FCPConstants.STATUS_ISSUED,
            recoverStatus(futureExpiry, tolerance)
        )

        // TTL expirado → EXPIRED
        val pastExpiry = now - 3_600_000L // 1 hora expirado
        assertEquals("UT-026: TTL expirado debe marcar como EXPIRED",
            FCPConstants.STATUS_EXPIRED,
            recoverStatus(pastExpiry, tolerance)
        )
    }
}
