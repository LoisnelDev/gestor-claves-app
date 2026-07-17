package com.loisnel.gestorclaves.core.security

import com.loisnel.gestorclaves.core.data.entities.MissionLog
import com.loisnel.gestorclaves.core.domain.TelemetryRecord
import java.security.MessageDigest
import java.util.Base64

// ═══════════════════════════════════════════════════════════════════
// ORION v1.0.0 — Extensiones AuditTrailManager: Telemetría BI
// ═══════════════════════════════════════════════════════════════════

/**
 * Genera el JSON canónico del MissionLog para calcular su hash.
 * El orden de los campos es fijo e inmutable — cualquier cambio
 * en el contenido produce un hash diferente.
 */
private fun missionLogToCanonicalJson(mission: com.loisnel.gestorclaves.core.data.entities.MissionLog): String =
    buildString {
        append("{")
        append("\"id\":\"${mission.id}\",")
        append("\"tenant_id\":\"${mission.tenant_id}\",")
        append("\"status\":\"${mission.status}\",")
        append("\"issuer_key\":\"${mission.issuer_key}\",")
        append("\"receiver_key\":\"${mission.receiver_key}\",")
        append("\"signature\":\"${mission.signature}\",")
        append("\"expiry_ms\":${mission.expiry_ms},")
        append("\"clock_tolerance_ms\":${mission.clock_tolerance_ms},")
        append("\"device_model\":\"${mission.device_model ?: ""}\",")
        append("\"os_version\":\"${mission.os_version ?: ""}\",")
        append("\"clock_drift_ms\":${mission.clock_drift_ms ?: 0},")
        append("\"created_at\":${mission.created_at},")
        append("\"closed_at\":${mission.closed_at ?: 0}")
        append("}")
    }

/**
 * Calcula el SHA-256 de un string y lo retorna como Base64.
 * Usado para audit_trail_hash — verificable por cualquier auditor externo.
 */
private fun sha256Base64(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(input.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(hash)
}

/**
 * Genera un [TelemetryRecord] completo desde un MissionLog.
 *
 * El auditTrailHash garantiza que el registro no fue modificado
 * entre el dispositivo y el sistema de BI del cliente. Para verificar:
 *   1. Reconstruir el JSON canónico del MissionLog
 *   2. Calcular SHA-256
 *   3. Comparar con auditTrailHash del TelemetryRecord
 *
 * Si coinciden: dato íntegro. Si no: tamper detectado.
 *
 * @param mission          El Mission_Log a exportar como telemetría
 * @param integrityOk      Las 6 validaciones del protocolo pasaron
 * @param tamperDetected   Se detectó AEADBadTagException en algún asset
 * @param fdeActive        FDEChecker confirmó cifrado de disco
 * @param strongboxOk      DetectorSecurityLevel == STRONGBOX
 * @param securityLevel    Nivel exacto de seguridad del hardware
 */
fun generateTelemetryRecord(
    mission: com.loisnel.gestorclaves.core.data.entities.MissionLog,
    integrityOk: Boolean      = true,
    tamperDetected: Boolean   = false,
    fdeActive: Boolean        = true,
    strongboxOk: Boolean      = false,
    securityLevel: String     = "TEE_HARDWARE"
): TelemetryRecord {
    val canonicalJson  = missionLogToCanonicalJson(mission)
    val auditHash      = sha256Base64(canonicalJson)
    val durationMs     = (mission.closed_at ?: mission.created_at) - mission.created_at

    return TelemetryRecord(
        missionId             = mission.id,
        nodeId                = mission.receiver_key,
        tenantId              = mission.tenant_id,
        auditTrailHash        = auditHash,
        timestampStart        = mission.created_at,
        timestampEnd          = mission.closed_at ?: 0L,
        durationMs            = durationMs,
        clockDriftMs          = mission.clock_drift_ms ?: 0L,
        taskStatus            = mission.status,
        deviceModel           = mission.device_model ?: "Unknown",
        osVersion             = mission.os_version ?: "Unknown",
        integrityCheckPassed  = integrityOk,
        tamperFlag            = tamperDetected,
        fdeActive             = fdeActive,
        strongboxAvailable    = strongboxOk,
        securityLevel         = securityLevel
    )
}

/**
 * Exporta una lista de misiones como array JSON de TelemetryRecords.
 * Listo para importar en Power BI, Tableau o cualquier herramienta BI.
 *
 * Verificación de integridad por el cliente:
 *   Para cada registro, el cliente calcula SHA-256 del JSON canónico
 *   del MissionLog y lo compara con el campo audit_trail_hash.
 */
fun exportTelemetryJson(
    missions: List<com.loisnel.gestorclaves.core.data.entities.MissionLog>,
    tenantId: String,
    fdeActive: Boolean    = true,
    strongboxOk: Boolean  = false,
    securityLevel: String = "TEE_HARDWARE"
): String {
    val filtered = missions.filter { it.tenant_id == tenantId }
    val records  = filtered.map { mission ->
        generateTelemetryRecord(
            mission       = mission,
            fdeActive     = fdeActive,
            strongboxOk   = strongboxOk,
            securityLevel = securityLevel
        )
    }
    return records.joinToString(",", "[", "]") { it.toJson() }
}
