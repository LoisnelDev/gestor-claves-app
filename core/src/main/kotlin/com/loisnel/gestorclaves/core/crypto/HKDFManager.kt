package com.loisnel.gestorclaves.core.crypto

import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDFManager — HMAC-based Key Derivation Function (RFC 5869)
 *
 * Convierte el aislamiento multi-tenant de una promesa de diseño
 * en una garantía matemática. El mismo mecanismo de TLS 1.3 y Signal Protocol.
 *
 * Propiedades garantizadas:
 * - Aislamiento matemático: TenantKey_A ≠ TenantKey_B por construcción
 * - Comprometer TenantKey_A no revela información sobre TenantKey_B
 * - Determinismo: misma entrada → misma clave siempre
 * - PRK zeroing: la clave intermedia se limpia tras la derivación
 * - Sin dependencias externas: javax.crypto nativo de Android
 *
 * TAD-B v2.1.1 — Sección 6
 */
object HKDFManager {

    private const val HMAC_ALGO = "HmacSHA256"
    private const val KEY_BYTES = 32 // 256 bits para AES-256

    /**
     * Deriva una TenantKey AES-256 para el tenant especificado.
     *
     * Flujo RFC 5869:
     *   PRK = HMAC-SHA256(salt=userSalt, IKM=masterKey)      // Extract
     *   OKM = HMAC-SHA256(key=PRK, info="tenant:id" || 0x01) // Expand
     *
     * @param masterKey Clave maestra derivada del PIN/biometría (32 bytes)
     * @param userSalt  Salt persistente del usuario (guardado en EncryptedSharedPreferences)
     * @param tenantId  Identificador único del tenant/organización
     * @return TenantKey de 32 bytes (AES-256) — única por tenant
     */
    fun deriveTenantKey(
        masterKey: ByteArray,
        userSalt: ByteArray,
        tenantId: String
    ): ByteArray {
        require(masterKey.isNotEmpty()) { "masterKey no puede estar vacío" }
        require(userSalt.isNotEmpty())  { "userSalt no puede estar vacío" }
        require(tenantId.isNotBlank()) { "tenantId no puede estar vacío" }

        // ── HKDF-Extract: PRK = HMAC-SHA256(salt, IKM) ──────────────
        val hmacExtract = Mac.getInstance(HMAC_ALGO)
        hmacExtract.init(SecretKeySpec(userSalt, HMAC_ALGO))
        val prk = hmacExtract.doFinal(masterKey)

        return try {
            // ── HKDF-Expand: OKM = HMAC-SHA256(PRK, info || counter) ─
            // info = "tenant:<tenantId>" — único por organización
            val info = "tenant:$tenantId".toByteArray(Charsets.UTF_8)
            val hmacExpand = Mac.getInstance(HMAC_ALGO)
            hmacExpand.init(SecretKeySpec(prk, HMAC_ALGO))
            hmacExpand.update(info)
            hmacExpand.update(0x01.toByte()) // Counter RFC 5869 §2.3 — siempre 0x01 para 32 bytes
            hmacExpand.doFinal().copyOf(KEY_BYTES)
        } finally {
            // ── Zeroing del PRK — clave intermedia nunca debe persistir ──
            Arrays.fill(prk, 0.toByte())
        }
    }
}
