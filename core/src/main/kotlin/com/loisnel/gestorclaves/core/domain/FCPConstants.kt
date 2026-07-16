package com.loisnel.gestorclaves.core.domain

/**
 * FCPConstants — Fuente única de verdad para constantes del sistema FCP
 * TAD-B v2.1.1
 */
object FCPConstants {

    // ── Criptografía ──────────────────────────────────────────────
    const val AES_KEY_BYTES           = 32
    const val GCM_NONCE_BYTES         = 12
    const val GCM_TAG_BITS            = 128
    const val PBKDF2_ITERATIONS       = 200_000
    const val PBKDF2_KEY_BITS         = 256

    // ── Paquete de misión ─────────────────────────────────────────
    const val MISSION_PACKET_VERSION       = "1.0"
    const val DEFAULT_CLOCK_TOLERANCE_MS   = 300_000L  // 5 minutos

    // ── Estados FSM ───────────────────────────────────────────────
    const val STATUS_ISSUED      = "ISSUED"
    const val STATUS_ACTIVE      = "ACTIVE"
    const val STATUS_COMPLETED   = "COMPLETED"
    const val STATUS_FAILED      = "FAILED"
    const val STATUS_EXPIRED     = "EXPIRED"
    /** Estado de compensación para power failures — no es estado final */
    const val STATUS_INTERRUPTED = "INTERRUPTED"

    // ── Room ──────────────────────────────────────────────────────
    const val DB_VERSION = 1

    // ── SaltRecovery ──────────────────────────────────────────────
    const val BACKUP_WRAP_SALT_BYTES = 16
    const val BACKUP_NONCE_BYTES     = 12
}
