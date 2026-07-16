package com.loisnel.gestorclaves.core.domain

/**
 * FCPConstants — Constantes del sistema FCP
 * Fuente única de verdad para todos los valores de configuración.
 */
object FCPConstants {

    // ── Criptografía ──────────────────────────────────────────────
    const val AES_KEY_BYTES       = 32     // 256 bits
    const val GCM_NONCE_BYTES     = 12     // GCM standard
    const val GCM_TAG_BITS        = 128    // Auth tag máximo
    const val HKDF_SALT_MIN_BYTES = 16
    const val PBKDF2_ITERATIONS   = 200_000
    const val PBKDF2_KEY_BITS     = 256

    // ── Paquete de misión ─────────────────────────────────────────
    const val MISSION_PACKET_VERSION = "1.0"
    const val DEFAULT_CLOCK_TOLERANCE_MS = 300_000L  // 5 minutos

    // ── Estado de misión FSM ──────────────────────────────────────
    const val STATUS_ISSUED      = "ISSUED"
    const val STATUS_ACTIVE      = "ACTIVE"
    const val STATUS_COMPLETED   = "COMPLETED"
    const val STATUS_FAILED      = "FAILED"
    const val STATUS_EXPIRED     = "EXPIRED"
    const val STATUS_INTERRUPTED = "INTERRUPTED"  // v2.1.1: power failure recovery

    // ── RBAC ──────────────────────────────────────────────────────
    enum class UserRole { DIRECTOR, SUPERVISOR, TECHNICIAN }

    // ── SaltRecovery — formato del backup ─────────────────────────
    const val BACKUP_WRAP_SALT_BYTES = 16
    const val BACKUP_NONCE_BYTES     = 12
    const val BACKUP_MIN_SIZE        = BACKUP_WRAP_SALT_BYTES + BACKUP_NONCE_BYTES + 1
}
