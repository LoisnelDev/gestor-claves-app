package com.loisnel.gestorclaves.core.mission

/**
 * MissionPacket — Contrato criptográfico inmutable v1.0
 *
 * Artefacto central de FCP. Define el contrato entre el emisor
 * (supervisor) y el receptor (técnico de campo).
 *
 * INMUTABILIDAD: este formato es el estándar v1.0.
 * Cualquier cambio requiere version="2.0" y mecanismo de migración.
 *
 * Campos TAD-B v2.1.1 — Sección 10:
 * - version: anti-downgrade attack (Check 1)
 * - signature: firma Ed25519 sobre cadena canónica (Check 2)
 * - expiry + clock_tolerance_ms: TTL con drift tolerance (Check 3)
 * - issued_to: device binding (Check 4)
 * - assets: payloads con GCM tag (Check 5)
 * - mission_id: anti-replay (Check 6)
 */
data class MissionPacket(
    val version: String,             // "1.0" — anti-downgrade
    val mission_id: String,          // UUID v4 — único, anti-replay
    val tenant_id: String,           // Hash del contratista
    val issued_by: String,           // Base64(Ed25519 public key del supervisor)
    val issued_to: String,           // Base64(Ed25519 public key del técnico)
    val expiry: Long,                // Timestamp Unix ms de expiración
    val clock_tolerance_ms: Long,    // Margen drift firmado — no manipulable
    val assets: List<MissionAsset>,  // Credenciales cifradas
    val signature: String            // Base64(Ed25519 firma sobre canonical string)
)
