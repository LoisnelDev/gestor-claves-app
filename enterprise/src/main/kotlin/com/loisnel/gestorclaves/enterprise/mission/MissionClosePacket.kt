package com.loisnel.gestorclaves.enterprise.mission

/**
 * MissionClosePacket — Acuse de Recibo Criptográfico (QR/NFC de cierre)
 *
 * Convierte a FCP en un sistema de auditoría legalmente vinculante.
 * El flujo no se considera finalizado hasta que el supervisor
 * escanea este QR firmado por el técnico.
 *
 * Campos v2.1.1: device_model, os_version, clock_drift_ms
 * permiten distinguir fallo de software vs limitación de hardware.
 *
 * TAD-B v2.1.1 — Sección 12
 */
data class MissionClosePacket(
    val mission_id: String,        // Referencia a la misión abierta
    val closed_at: Long,           // Timestamp Unix ms del cierre
    val result: String,            // "COMPLETED" o "FAILED"
    val notes: String,             // Notas opcionales del técnico
    val device_model: String,      // v2.1.1: ej. "Motorola Moto G84 5G"
    val os_version: String,        // v2.1.1: ej. "Android 14 (API 34)"
    val clock_drift_ms: Long,      // v2.1.1: drift registrado
    val technician_key: String,    // Base64(Ed25519 public key del técnico)
    val signature: String          // Base64(Ed25519 firma del técnico)
)
