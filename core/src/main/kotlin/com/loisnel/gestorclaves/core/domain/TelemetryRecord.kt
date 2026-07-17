package com.loisnel.gestorclaves.core.domain

/**
 * TelemetryRecord — Registro de telemetría industrial ORION v1.0.0
 *
 * Objeto JSON estructurado exportable a Power BI, Tableau, Grafana o
 * cualquier sistema de BI sin desarrollo adicional del cliente.
 *
 * El campo auditTrailHash es el SHA-256 del JSON serializado del MissionLog
 * original. Permite que cualquier auditor externo verifique que los datos
 * no fueron modificados entre el dispositivo y el sistema de análisis.
 *
 * Campos de seguridad certificable:
 * - integrityCheckPassed: las 6 validaciones del protocolo de misión pasaron
 * - tamperFlag: se detectó AEADBadTagException en algún asset
 * - fdeActive: el dispositivo tiene cifrado de disco activo
 * - strongboxAvailable: el dispositivo tiene chip StrongBox
 * - securityLevel: nivel exacto de hardware criptográfico
 *
 * TAD-B v3.0 — ORION Platform — Inmutabilidad del Dato
 */
data class TelemetryRecord(
    // ── Trazabilidad ─────────────────────────────────────────────
    val missionId: String,          // UUID del paquete de misión
    val nodeId: String,             // Ed25519 public key Base64 del técnico
    val tenantId: String,           // Organización (hash — anonimizable)
    val auditTrailHash: String,     // SHA-256 Base64 del MissionLog serializado

    // ── Eficiencia operativa ──────────────────────────────────────
    val timestampStart: Long,       // Emisión de la misión (ISSUED)
    val timestampEnd: Long,         // Cierre (COMPLETED/FAILED) — 0 si abierta
    val durationMs: Long,           // timestampEnd - timestampStart
    val clockDriftMs: Long,         // Drift de reloj registrado en TTL
    val taskStatus: String,         // COMPLETED | FAILED | EXPIRED | ACTIVE

    // ── Calidad de hardware ───────────────────────────────────────
    val deviceModel: String,        // "Motorola Moto G84 5G"
    val osVersion: String,          // "Android 14 (API 34)"
    val integrityCheckPassed: Boolean, // Las 6 validaciones pasaron
    val tamperFlag: Boolean,        // AEADBadTagException detectada
    val fdeActive: Boolean,         // FDEChecker.check() == ENCRYPTED
    val strongboxAvailable: Boolean,// DetectorSecurityLevel == STRONGBOX
    val securityLevel: String       // "STRONGBOX" | "TEE_HARDWARE" | "TEE_SOFTWARE"
) {
    /**
     * Serializa este registro como JSON string determinístico.
     * El orden de los campos es fijo — crítico para reproducibilidad en auditorías.
     */
    fun toJson(): String = buildString {
        append("{")
        append("\"mission_id\":\"$missionId\",")
        append("\"node_id\":\"$nodeId\",")
        append("\"tenant_id\":\"$tenantId\",")
        append("\"audit_trail_hash\":\"$auditTrailHash\",")
        append("\"timestamp_start\":$timestampStart,")
        append("\"timestamp_end\":$timestampEnd,")
        append("\"duration_ms\":$durationMs,")
        append("\"clock_drift_ms\":$clockDriftMs,")
        append("\"task_status\":\"$taskStatus\",")
        append("\"device_model\":\"${deviceModel.replace("\"", "\\\"")}\",")
        append("\"os_version\":\"${osVersion.replace("\"", "\\\"")}\",")
        append("\"integrity_check_passed\":$integrityCheckPassed,")
        append("\"tamper_flag\":$tamperFlag,")
        append("\"fde_active\":$fdeActive,")
        append("\"strongbox_available\":$strongboxAvailable,")
        append("\"security_level\":\"$securityLevel\"")
        append("}")
    }
}
