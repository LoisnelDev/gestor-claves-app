package com.loisnel.gestorclaves.core.crypto

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AESGCMManager — Cifrado y descifrado AES-256-GCM
 *
 * Propiedades de seguridad:
 * - AEAD: confidencialidad + autenticidad en una sola operación
 * - GCM auth tag 128-bit: detecta cualquier manipulación del ciphertext
 * - Nonce 12 bytes aleatorio por escritura: unicidad estadística garantizada
 * - AEADBadTagException en descifrado si el dato fue manipulado
 *
 * TAD-B v2.1.1 — Sección 8
 */
object AESGCMManager {

    private const val ALGORITHM    = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_BYTES  = 12

    /**
     * Cifra un payload String con la TenantKey provista.
     * Genera un nonce aleatorio único por llamada.
     *
     * @param payload Texto plano a cifrar
     * @param tenantKey Clave AES-256 de 32 bytes derivada por HKDF
     * @return [AssetBlob] con ciphertext (incluye GCM tag) y nonce
     */
    fun encrypt(payload: String, tenantKey: ByteArray): AssetBlob {
        require(tenantKey.size == 32) {
            "TenantKey debe ser 32 bytes (AES-256). Recibido: ${tenantKey.size}"
        }
        val nonce  = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(tenantKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        // ciphertext incluye el GCM auth tag (últimos 16 bytes)
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return AssetBlob(ciphertext = ciphertext, nonce = nonce)
    }

    /**
     * Descifra un [AssetBlob] con la TenantKey provista.
     *
     * @throws AEADBadTagException si el ciphertext fue manipulado o la clave es incorrecta
     * @throws IllegalArgumentException si los parámetros son inválidos
     */
    fun decrypt(blob: AssetBlob, tenantKey: ByteArray): String {
        require(tenantKey.size == 32) {
            "TenantKey debe ser 32 bytes (AES-256). Recibido: ${tenantKey.size}"
        }
        require(blob.nonce.size == NONCE_BYTES) {
            "Nonce debe ser $NONCE_BYTES bytes. Recibido: ${blob.nonce.size}"
        }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(tenantKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, blob.nonce)
        )
        // Lanza AEADBadTagException si el tag no coincide — falla explícita, nunca silenciosa
        val plaintext = cipher.doFinal(blob.ciphertext)
        return plaintext.toString(Charsets.UTF_8)
    }
}

/**
 * Contenedor para el resultado de cifrado AES-GCM.
 * El GCM auth tag está incluido al final de [ciphertext].
 */
data class AssetBlob(
    val ciphertext: ByteArray,  // Datos cifrados + GCM auth tag (16 bytes al final)
    val nonce: ByteArray        // IV/Nonce GCM — 12 bytes, único por escritura
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssetBlob) return false
        return ciphertext.contentEquals(other.ciphertext) &&
               nonce.contentEquals(other.nonce)
    }
    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + nonce.contentHashCode()
}
