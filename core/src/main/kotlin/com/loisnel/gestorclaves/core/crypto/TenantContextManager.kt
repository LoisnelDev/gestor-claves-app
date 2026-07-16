package com.loisnel.gestorclaves.core.crypto

import java.util.Arrays

/**
 * TenantContextManager — Gestor de contexto activo multi-tenant
 *
 * Garantía de aislamiento en memoria: al cambiar de organización,
 * la TenantKey anterior se sobreescribe con ceros ANTES de derivar
 * la nueva. Esto usa Arrays.fill() que garantiza la sobreescritura
 * en el heap antes de que el GC procese el array.
 *
 * Por qué Arrays.fill() y NO System.gc():
 * - System.gc() es una sugerencia al OS — no es una garantía
 * - Arrays.fill() sobreescribe los bytes en el array original
 *   en el heap inmediatamente, antes de liberar la referencia
 *
 * TAD-B v2.1.1 — Sección 5
 */
class TenantContextManager(
    private val hkdfManager: HKDFManager = HKDFManager
) {

    // Referencia a la TenantKey activa — interna al manager
    // Solo una TenantKey en memoria a la vez
    private var activeTenantKey: ByteArray? = null
    private var activeTenantId:  String?    = null

    /**
     * Cambia el contexto activo al tenant especificado.
     * Zeroiza la TenantKey anterior ANTES de derivar la nueva.
     *
     * @param newTenantId  Identificador del nuevo tenant activo
     * @param masterKey    Clave maestra del usuario (en memoria de sesión)
     * @param userSalt     Salt del usuario (desde EncryptedSharedPreferences)
     * @return La nueva TenantKey activa (copia — para uso inmediato)
     */
    fun switchContext(
        newTenantId: String,
        masterKey: ByteArray,
        userSalt: ByteArray
    ): ByteArray {
        // PASO 1: Zeroing explícito de la TenantKey anterior
        // Arrays.fill() garantiza sobreescritura en heap ANTES del GC
        activeTenantKey?.let { key ->
            Arrays.fill(key, 0.toByte())
        }
        activeTenantKey = null
        activeTenantId  = null

        // PASO 2: Derivar TenantKey para el nuevo contexto
        val newKey = hkdfManager.deriveTenantKey(masterKey, userSalt, newTenantId)
        activeTenantKey = newKey
        activeTenantId  = newTenantId

        // PASO 3: Retornar copia para uso inmediato — el caller es responsable
        // de hacer zeroing de su copia cuando ya no la necesite
        return newKey.copyOf()
    }

    /**
     * Cierra la sesión activa — zeroiza todas las claves en memoria.
     * Debe llamarse en onSessionClose() / onDestroy().
     */
    fun closeSession() {
        activeTenantKey?.let { Arrays.fill(it, 0.toByte()) }
        activeTenantKey = null
        activeTenantId  = null
    }

    /** Solo para tests — permite verificar que el zeroing funcionó */
    internal fun getActiveTenantKeyRef(): ByteArray? = activeTenantKey

    /** Retorna el ID del tenant activo actualmente */
    fun getActiveTenantId(): String? = activeTenantId
}
