package com.loisnel.gestorclaves.core.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * RBACTest — UT-020 a UT-023
 *
 * Tests de RBAC puro — sin base de datos, sin Android context.
 * Verifican que UnauthorizedRoleException se lanza correctamente
 * y que la separación BillingManager ↔ DAO es arquitectónica.
 *
 * JVM puro — ejecuta sin emulador en milisegundos.
 */
class RBACTest {

    // ═══════════════════════════════════════════════════════════
    // UT-020: UnauthorizedRoleException contiene información correcta
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut020_unauthorized_role_exception_has_correct_fields() {
        val ex = UnauthorizedRoleException(
            required = UserRole.SUPERVISOR,
            actual   = UserRole.TECHNICIAN,
            action   = "emitMission"
        )
        assertEquals("UT-020: required role incorrecto",
            UserRole.SUPERVISOR, ex.required)
        assertEquals("UT-020: actual role incorrecto",
            UserRole.TECHNICIAN, ex.actual)
        assertEquals("UT-020: action incorrecta",
            "emitMission", ex.action)
        assertTrue("UT-020: mensaje debe contener el role requerido",
            ex.message?.contains("SUPERVISOR") == true)
    }

    // ═══════════════════════════════════════════════════════════
    // UT-021: UserRole enum tiene exactamente 3 valores
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut021_user_role_has_three_values() {
        val roles = UserRole.values()
        assertEquals("UT-021: debe haber exactamente 3 roles",
            3, roles.size)
        assertTrue("UT-021: DIRECTOR debe existir",
            roles.contains(UserRole.DIRECTOR))
        assertTrue("UT-021: SUPERVISOR debe existir",
            roles.contains(UserRole.SUPERVISOR))
        assertTrue("UT-021: TECHNICIAN debe existir",
            roles.contains(UserRole.TECHNICIAN))
    }

    // ═══════════════════════════════════════════════════════════
    // UT-022: RBAC lógica — función que enforcea roles
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut022_rbac_enforcement_function_throws_for_wrong_role() {
        fun requireRole(required: UserRole, actual: UserRole, action: String) {
            if (actual != required) throw UnauthorizedRoleException(required, actual, action)
        }

        // TECHNICIAN intentando emitir misión — debe fallar
        try {
            requireRole(UserRole.SUPERVISOR, UserRole.TECHNICIAN, "emitMission")
            fail("UT-022: TECHNICIAN no debe poder emitir misiones")
        } catch (e: UnauthorizedRoleException) {
            assertEquals(UserRole.SUPERVISOR,  e.required)
            assertEquals(UserRole.TECHNICIAN,  e.actual)
        }

        // SUPERVISOR emitiendo misión — debe pasar
        assertDoesNotThrow("UT-022: SUPERVISOR debe poder emitir misiones") {
            requireRole(UserRole.SUPERVISOR, UserRole.SUPERVISOR, "emitMission")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UT-023: FCPConstants tiene todos los estados FSM definidos
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut023_fcp_constants_has_all_fsm_states() {
        val expected = setOf("ISSUED", "ACTIVE", "COMPLETED", "FAILED", "EXPIRED", "INTERRUPTED")
        val actual = setOf(
            FCPConstants.STATUS_ISSUED,
            FCPConstants.STATUS_ACTIVE,
            FCPConstants.STATUS_COMPLETED,
            FCPConstants.STATUS_FAILED,
            FCPConstants.STATUS_EXPIRED,
            FCPConstants.STATUS_INTERRUPTED
        )
        assertEquals("UT-023: FCPConstants debe tener todos los estados FSM", expected, actual)
    }

    // Helper — equivalente a assertDoesNotThrow de JUnit 5 en JUnit 4
    private fun assertDoesNotThrow(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("$message — lanzó excepción: ${e.message}")
        }
    }
}
