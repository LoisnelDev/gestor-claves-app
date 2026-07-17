package com.loisnel.gestorclaves.sync.registry

/**
 * NodeInfo — Información de un nodo conocido en el ecosistema ORION
 *
 * Cada nodo del ecosistema tiene una identidad Ed25519 inmutable.
 * NodeInfo almacena los metadatos de confianza establecidos en el
 * Handshake inicial (presencia física requerida — TAD-B v3.0 Sección 5).
 */
data class NodeInfo(
    /** Clave pública Ed25519 en Base64 — identificador único del nodo */
    val publicKeyBase64: String,

    /** Rol del nodo en el ecosistema */
    val role: String, // "SUPERVISOR" | "TECHNICIAN" | "DIRECTOR"

    /** Timestamp Unix ms del primer handshake (confianza establecida) */
    val trustedSinceMs: Long,

    /** Timestamp Unix ms del último contacto conocido */
    val lastSeenMs: Long,

    /** Tenant al que pertenece este nodo */
    val tenantId: String,

    /** Alias legible para el Director (nombre del técnico/supervisor) */
    val displayName: String = ""
)
