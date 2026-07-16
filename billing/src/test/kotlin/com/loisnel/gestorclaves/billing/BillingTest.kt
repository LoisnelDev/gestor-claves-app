package com.loisnel.gestorclaves.billing

import org.junit.Assert.*
import org.junit.Test

/**
 * BillingTest — UT-039 a UT-042
 *
 * Tests de la Never-block policy y la separación arquitectónica
 * de BillingManager respecto a los DAOs de Room.
 *
 * JVM puro — sin emulador, sin Context de Android.
 * BillingManager no puede instanciarse sin Context en estos tests,
 * pero podemos verificar sus invariantes arquitectónicas por reflection.
 *
 * TAD-B v2.1.1 — Mejora 2: Never-block policy
 */
class BillingTest {

    // ═══════════════════════════════════════════════════════════
    // UT-039: BillingManager NO tiene campos de tipo DAO (reflection)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut039_billing_manager_has_no_dao_fields() {
        val billingFields = BillingManager::class.java.declaredFields
            .map { it.type.simpleName }

        val forbiddenTypes = listOf("TenantDao", "AssetDao", "MissionLogDao",
                                    "DeviceIdentityDao", "FCPDatabase")
        forbiddenTypes.forEach { daoType ->
            assertFalse(
                "UT-039 FAILED: BillingManager tiene campo '$daoType' — viola Never-block policy. " +
                "BillingManager NO debe tener acceso a ningún DAO.",
                billingFields.contains(daoType)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UT-040: ProStatus tiene todos los estados requeridos
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut040_pro_status_has_all_required_states() {
        val statuses = ProStatus.values()
        val required = setOf("CHECKING", "ACTIVE", "EXPIRED_ACCESS_PRESERVED", "FREE", "ERROR")

        required.forEach { state ->
            assertTrue(
                "UT-040 FAILED: ProStatus debe tener el estado '$state'",
                statuses.any { it.name == state }
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UT-041: Never-block — EXPIRED_ACCESS_PRESERVED existe y es distinto de FREE
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut041_expired_access_preserved_is_different_from_free() {
        val expired = ProStatus.EXPIRED_ACCESS_PRESERVED
        val free    = ProStatus.FREE

        assertNotEquals(
            "UT-041 FAILED: EXPIRED_ACCESS_PRESERVED debe ser diferente de FREE",
            expired, free
        )
        // Verificar semántica: EXPIRED_ACCESS_PRESERVED indica acceso a datos
        // FREE indica sin acceso a datos Pro
        assertNotEquals("UT-041: estados deben ser distintos", expired.name, free.name)
    }

    // ═══════════════════════════════════════════════════════════
    // UT-042: BillingConstants tiene todos los product IDs definidos
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut042_billing_constants_has_required_product_ids() {
        assertTrue(
            "UT-042: PRODUCT_PRO_LIFETIME debe estar definido",
            BillingConstants.PRODUCT_PRO_LIFETIME.isNotBlank()
        )
        assertTrue(
            "UT-042: PRODUCT_ENTERPRISE_YEARLY debe estar definido",
            BillingConstants.PRODUCT_ENTERPRISE_YEARLY.isNotBlank()
        )
        assertTrue(
            "UT-042: BILLING_PREFS_NAME debe estar definido",
            BillingConstants.BILLING_PREFS_NAME.isNotBlank()
        )
        // Verificar formato esperado de los product IDs
        assertTrue(
            "UT-042: product ID debe seguir formato snake_case",
            BillingConstants.PRODUCT_PRO_LIFETIME.contains("_")
        )
    }
}
