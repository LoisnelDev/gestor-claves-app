package com.loisnel.gestorclaves.core.mission

/**
 * MissionAsset — Credencial individual dentro de un paquete de misión
 *
 * El payload está cifrado con AES-256-GCM usando la TenantKey.
 * La autenticidad del asset individual está garantizada por el GCM tag
 * incluido en el payload (verificación en Check 5 de MissionValidator).
 *
 * TAD-B v2.1.1 — Sección 10
 */
data class MissionAsset(
    val id: String,       // UUID único del asset
    val label: String,    // Nombre legible (router, SCADA, switch)
    val payload: String,  // Base64(AES-256-GCM ciphertext + tag)
    val nonce: String     // Base64(12 bytes GCM nonce)
)
