package com.loisnel.gestorclaves.core.crypto

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException
import java.security.SecureRandom

/**
 * Suite de tests criptográficos — UT-001 a UT-006
 *
 * Tests de JUnit4 puro — sin emulador, sin dispositivo.
 * El módulo :core no tiene dependencias de UI, por lo que
 * estos tests ejecutan en milisegundos en cualquier JVM.
 *
 * NOTA: DeviceIdentityManager usa Android Keystore que no está
 * disponible en JVM pura. Los tests de Ed25519 usan javax.crypto
 * directamente para simular el comportamiento.
 */
class CryptoTest {

    // ── Datos de prueba comunes ───────────────────────────────────
    private val masterKey = "master-key-test-fcp-32-bytes!!".toByteArray(Charsets.UTF_8).copyOf(32)
    private val userSalt  = "user-salt-fcp-16bytes!!".toByteArray(Charsets.UTF_8).copyOf(16)
    private val tenantKey = ByteArray(32).also { SecureRandom().nextBytes(it) }

    // ═══════════════════════════════════════════════════════════
    // UT-001: HKDF — Determinismo (mismo input → misma clave)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut001_hkdf_determinism_same_input_same_key() {
        val key1 = HKDFManager.deriveTenantKey(masterKey, userSalt, "tenant-empresa-A")
        val key2 = HKDFManager.deriveTenantKey(masterKey, userSalt, "tenant-empresa-A")
        assertArrayEquals(
            "UT-001 FAILED: HKDF debe ser determinístico — misma entrada debe producir misma clave",
            key1, key2
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-002: HKDF — Aislamiento (tenants diferentes → claves diferentes)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut002_hkdf_isolation_different_tenants_different_keys() {
        val keyA = HKDFManager.deriveTenantKey(masterKey, userSalt, "tenant-empresa-A")
        val keyB = HKDFManager.deriveTenantKey(masterKey, userSalt, "tenant-empresa-B")
        assertFalse(
            "UT-002 FAILED: HKDF debe producir claves diferentes para tenants diferentes",
            keyA.contentEquals(keyB)
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-003: HKDF — Longitud correcta para AES-256 (32 bytes)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut003_hkdf_output_length_is_32_bytes_for_aes256() {
        val key = HKDFManager.deriveTenantKey(masterKey, userSalt, "tenant-test")
        assertEquals(
            "UT-003 FAILED: HKDF debe producir exactamente 32 bytes (AES-256)",
            32, key.size
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-004: HKDF — UserSalt diferente → TenantKey diferente
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut004_hkdf_different_salt_different_key() {
        val salt2 = "different-salt-16".toByteArray(Charsets.UTF_8).copyOf(16)
        val key1 = HKDFManager.deriveTenantKey(masterKey, userSalt, "tenant-test")
        val key2 = HKDFManager.deriveTenantKey(masterKey, salt2,    "tenant-test")
        assertFalse(
            "UT-004 FAILED: UserSalt diferente debe producir TenantKey diferente",
            key1.contentEquals(key2)
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-005: AES-GCM — Round-trip (encrypt → decrypt = original)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut005_aes_gcm_encrypt_decrypt_roundtrip() {
        val original = "credential-secret-data-fcp-2026"
        val blob     = AESGCMManager.encrypt(original, tenantKey)
        val decrypted = AESGCMManager.decrypt(blob, tenantKey)
        assertEquals(
            "UT-005 FAILED: AES-GCM decrypt debe retornar el texto original",
            original, decrypted
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-006: AES-GCM — Tamper detection (GCM auth tag)
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut006_aes_gcm_tamper_detection_throws_aead_exception() {
        val blob = AESGCMManager.encrypt("secret-credential", tenantKey)

        // Manipular un byte del ciphertext (flip de bit)
        val tamperedCiphertext = blob.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
        val tamperedBlob = AssetBlob(ciphertext = tamperedCiphertext, nonce = blob.nonce)

        try {
            AESGCMManager.decrypt(tamperedBlob, tenantKey)
            fail("UT-006 FAILED: AES-GCM debe lanzar AEADBadTagException si el ciphertext fue manipulado")
        } catch (e: AEADBadTagException) {
            // ✓ Comportamiento correcto — excepción esperada
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UT-007: AES-GCM — Nonce único por escritura
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut007_aes_gcm_unique_nonce_per_encryption() {
        val blob1 = AESGCMManager.encrypt("same-payload", tenantKey)
        val blob2 = AESGCMManager.encrypt("same-payload", tenantKey)
        assertFalse(
            "UT-007 FAILED: Nonces deben ser diferentes en cada cifrado (SecureRandom)",
            blob1.nonce.contentEquals(blob2.nonce)
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-008: AES-GCM — Clave incorrecta lanza excepción
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut008_aes_gcm_wrong_key_throws_exception() {
        val blob     = AESGCMManager.encrypt("secret", tenantKey)
        val wrongKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            AESGCMManager.decrypt(blob, wrongKey)
            fail("UT-008 FAILED: Clave incorrecta debe lanzar excepción")
        } catch (e: Exception) {
            // ✓ AEADBadTagException o similar — comportamiento correcto
        }
    }
}
