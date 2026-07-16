package com.loisnel.gestorclaves.core.crypto

import java.util.Arrays
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2Manager — Derivación de clave desde contraseña maestra
 *
 * Usado exclusivamente por SaltRecoveryManager para el key-wrapping del UserSalt.
 * NO se usa para derivar TenantKeys (eso es responsabilidad de HKDFManager).
 *
 * Parámetros: PBKDF2WithHmacSHA256, 200.000 iteraciones, 256 bits
 * Cumple: OWASP 2023 + NIST SP 800-132
 *
 * TAD-B v2.1.1 — Sección 9
 */
object PBKDF2Manager {

    private const val ALGORITHM  = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 200_000
    private const val KEY_BITS   = 256

    /**
     * Deriva una clave AES-256 desde una contraseña maestra y un salt.
     * La contraseña se limpia de memoria tras la derivación.
     *
     * @param password Contraseña maestra del usuario
     * @param salt Salt aleatorio de 16 bytes
     * @return ByteArray de 32 bytes (clave AES-256)
     */
    fun derive(password: String, salt: ByteArray): ByteArray {
        require(salt.size >= 16) { "Salt debe ser mínimo 16 bytes" }
        val chars = password.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            spec.clearPassword() // Limpiar la copia interna de la contraseña
            keyBytes
        } finally {
            Arrays.fill(chars, '\u0000') // Zeroing de la copia local
        }
    }
}
