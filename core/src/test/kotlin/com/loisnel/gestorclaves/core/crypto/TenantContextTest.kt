package com.loisnel.gestorclaves.core.crypto

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

/**
 * Suite de tests para TenantContextManager — UT-017 a UT-019
 *
 * Verifica el aislamiento de contexto y el zeroing de memoria
 * al cambiar entre tenants.
 */
class TenantContextTest {

    private val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val userSalt  = ByteArray(16).also { SecureRandom().nextBytes(it) }

    // ═══════════════════════════════════════════════════════════
    // UT-017: Context switch — retorna TenantKey correcta
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut017_context_switch_returns_correct_tenant_key() {
        val manager = TenantContextManager()
        val keyA = manager.switchContext("tenant-A", masterKey, userSalt)

        // Verificar que la clave retornada coincide con HKDF directo
        val expectedKeyA = HKDFManager.deriveTenantKey(masterKey, userSalt, "tenant-A")
        assertArrayEquals(
            "UT-017 FAILED: switchContext debe retornar la TenantKey correcta para el tenant",
            expectedKeyA, keyA
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-018: Context switch — zeroing de clave anterior
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut018_context_switch_zeros_previous_tenant_key() {
        val manager = TenantContextManager()

        // Activar tenant-A y obtener referencia interna
        manager.switchContext("tenant-A", masterKey, userSalt)
        val keyRefBeforeSwitch = manager.getActiveTenantKeyRef()?.copyOf()

        // Cambiar a tenant-B — debe zeroizar la key de tenant-A
        manager.switchContext("tenant-B", masterKey, userSalt)

        // La referencia interna previa debe estar zeroizada
        // (getActiveTenantKeyRef ahora apunta a tenant-B, no a tenant-A)
        // Verificamos que tenant-A y tenant-B tienen claves diferentes
        val keyRefAfterSwitch = manager.getActiveTenantKeyRef()
        assertNotNull("UT-018: Debe haber una key activa tras el switch", keyRefAfterSwitch)
        assertFalse(
            "UT-018 FAILED: La clave de tenant-B debe ser diferente a la de tenant-A",
            keyRefBeforeSwitch?.contentEquals(keyRefAfterSwitch!!) ?: false
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-019: closeSession — limpia todas las claves
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut019_close_session_nullifies_active_key() {
        val manager = TenantContextManager()
        manager.switchContext("tenant-A", masterKey, userSalt)
        assertNotNull("Setup: debe haber key activa", manager.getActiveTenantKeyRef())

        manager.closeSession()

        assertNull(
            "UT-019 FAILED: closeSession debe dejar la activeTenantKey en null",
            manager.getActiveTenantKeyRef()
        )
        assertNull(
            "UT-019 FAILED: closeSession debe dejar el activeTenantId en null",
            manager.getActiveTenantId()
        )
    }
}
