package com.loisnel.gestorclaves.core.crypto

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SaltRecoveryManager — Exportación e importación cifrada del UserSalt
 *
 * El UserSalt es el componente más crítico de la arquitectura HKDF:
 * si se pierde, todos los BLOBs cifrados son indescifrables permanentemente.
 *
 * Este manager implementa key-wrapping: el UserSalt se cifra con una clave
 * derivada de la MasterPassword del usuario via PBKDF2, produciendo un
 * archivo exportable que el usuario guarda de forma segura.
 *
 * Formato del backup: [16B wrapSalt][12B nonce][N bytes wrappedSalt+tag]
 *
 * Seguridad:
 * - El backup sin la MasterPassword es inútil
 * - MasterPassword incorrecta lanza AEADBadTagException
 * - PBKDF2 con 200.000 iteraciones — resistente a fuerza bruta
 *
 * TAD-B v2.1.1 — Sección 9
 */
object SaltRecoveryManager {

    private const val ALGORITHM    = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val WRAP_SALT_BYTES = 16
    private const val NONCE_BYTES     = 12

    /**
     * Exporta el UserSalt cifrado con la MasterPassword.
     *
     * @param userSalt       Salt a proteger (tipicamente 32 bytes)
     * @param masterPassword Contraseña maestra del usuario
     * @return ByteArray del backup — formato: [wrapSalt | nonce | ciphertext+tag]
     */
    fun exportSaltBackup(userSalt: ByteArray, masterPassword: String): ByteArray {
        require(userSalt.isNotEmpty()) { "userSalt no puede estar vacío" }
        require(masterPassword.isNotBlank()) { "masterPassword no puede estar vacía" }

        val wrapSalt = ByteArray(WRAP_SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapKey  = PBKDF2Manager.derive(masterPassword, wrapSalt)

        return try {
            val nonce  = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(wrapKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce)
            )
            val wrapped = cipher.doFinal(userSalt)
            // Concatenar: wrapSalt + nonce + ciphertext (con tag incluido)
            wrapSalt + nonce + wrapped
        } finally {
            Arrays.fill(wrapKey, 0.toByte()) // Zeroing de la wrapKey
        }
    }

    /**
     * Restaura el UserSalt desde un backup cifrado.
     *
     * @param backupBytes    Bytes del archivo de backup exportado
     * @param masterPassword Contraseña maestra del usuario
     * @return El UserSalt original
     * @throws AEADBadTagException si la MasterPassword es incorrecta o el backup está corrupto
     * @throws IllegalArgumentException si el formato del backup es inválido
     */
    fun importSaltBackup(backupBytes: ByteArray, masterPassword: String): ByteArray {
        val minSize = WRAP_SALT_BYTES + NONCE_BYTES + 1
        require(backupBytes.size >= minSize) {
            "Backup inválido: tamaño ${backupBytes.size} < mínimo $minSize"
        }

        val wrapSalt = backupBytes.copyOfRange(0, WRAP_SALT_BYTES)
        val nonce    = backupBytes.copyOfRange(WRAP_SALT_BYTES, WRAP_SALT_BYTES + NONCE_BYTES)
        val wrapped  = backupBytes.copyOfRange(WRAP_SALT_BYTES + NONCE_BYTES, backupBytes.size)
        val wrapKey  = PBKDF2Manager.derive(masterPassword, wrapSalt)

        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(wrapKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce)
            )
            // AEADBadTagException si password incorrecto o backup corrupto
            cipher.doFinal(wrapped)
        } finally {
            Arrays.fill(wrapKey, 0.toByte()) // Zeroing de la wrapKey
        }
    }
}
