package com.loisnel.gestorclaves.core.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MissionLog — Registro inmutable del ciclo de vida de una misión
 *
 * Estados FSM válidos (TAD-B v2.1.1 — Sección 13):
 *   ISSUED → ACTIVE → COMPLETED (final)
 *                   → FAILED    (final)
 *            → EXPIRED          (final)
 *   INTERRUPTED → ISSUED (recovery tras power failure — v2.1.1 Mejora 1)
 *
 * Campos v2.1.1: device_model, os_version, clock_drift_ms
 * Permiten al supervisor distinguir fallo de software vs limitación
 * de hardware del técnico en los reportes ejecutivos.
 */
@Entity(tableName = "mission_logs")
data class MissionLog(
    @PrimaryKey
    val id: String,                    // mission_id del paquete — anti-replay
    val tenant_id: String,             // Tenant al que pertenece la misión
    val mission_data_json: String,     // Paquete JSON completo serializado
    val status: String,                // ISSUED|ACTIVE|COMPLETED|FAILED|EXPIRED|INTERRUPTED
    val issuer_key: String,            // Clave pública Ed25519 del supervisor
    val receiver_key: String,          // Clave pública Ed25519 del técnico
    val signature: String,             // Firma Ed25519 del actor en la última transición
    val expiry_ms: Long,               // TTL en milisegundos Unix
    val clock_tolerance_ms: Long,      // Tolerancia clock drift (firmada en el paquete)
    val device_model: String?,         // v2.1.1: ej. "Motorola Moto G84 5G"
    val os_version: String?,           // v2.1.1: ej. "Android 14 API 34"
    val clock_drift_ms: Long?,         // v2.1.1: drift registrado en validación TTL
    val created_at: Long,              // Timestamp de emisión
    val closed_at: Long?               // Null hasta COMPLETED/FAILED/EXPIRED
)
