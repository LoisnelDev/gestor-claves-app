package com.loisnel.gestorclaves.core.security

import com.loisnel.gestorclaves.core.data.entities.MissionLog
import com.loisnel.gestorclaves.core.domain.FCPConstants
import com.loisnel.gestorclaves.core.domain.TelemetryRecord
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * TelemetryTest — UT-053 a UT-057
 * Tests del sistema de telemetría BI y audit_trail_hash.
 * JVM puro — sin emulador.
 */
class TelemetryTest {

    private val now = System.currentTimeMillis()

    private fun buildMission(
        id: String = "mission-telemetry-test",
        status: String = FCPConstants.STATUS_COMPLETED
    ) = MissionLog(
        id                 = id,
        tenant_id          = "tenant-test",
        mission_data_json  = "{}",
        status             = status,
        issuer_key         = "supervisor-key",
        receiver_key       = "node-key-base64",
        signature          = "sig",
        expiry_ms          = now + 3_600_000L,
        clock_tolerance_ms = 300_000L,
        device_model       = "Motorola Moto G84 5G",
        os_version         = "Android 14 (API 34)",
        clock_drift_ms     = 1500L,
        created_at         = now - 1_800_000L,
        closed_at          = now - 900_000L
    )

    // UT-053: DetectorSecurityLevel retorna un nivel válido (sin excepción en JVM)
    @Test
    fun ut053_security_level_enum_values_are_valid() {
        val levels = DetectorSecurityLevel.NodeSecurityLevel.values()
        assertEquals("UT-053: debe haber exactamente 4 niveles",
            4, levels.size)
        assertTrue("UT-053: STRONGBOX debe existir",
            levels.any { it.name == "STRONGBOX" })
        assertTrue("UT-053: TEE_HARDWARE debe existir",
            levels.any { it.name == "TEE_HARDWARE" })
        assertTrue("UT-053: TEE_SOFTWARE debe existir",
            levels.any { it.name == "TEE_SOFTWARE" })
        assertTrue("UT-053: UNKNOWN debe existir",
            levels.any { it.name == "UNKNOWN" })
    }

    // UT-054: STRONGBOX y TEE_HARDWARE son hardware-backed; TEE_SOFTWARE y UNKNOWN no
    @Test
    fun ut054_hardware_backed_classification_correct() {
        assertTrue("UT-054: STRONGBOX es hardware-backed",
            DetectorSecurityLevel.NodeSecurityLevel.STRONGBOX.isHardwareBacked)
        assertTrue("UT-054: TEE_HARDWARE es hardware-backed",
            DetectorSecurityLevel.NodeSecurityLevel.TEE_HARDWARE.isHardwareBacked)
        assertFalse("UT-054: TEE_SOFTWARE NO es hardware-backed",
            DetectorSecurityLevel.NodeSecurityLevel.TEE_SOFTWARE.isHardwareBacked)
        assertFalse("UT-054: UNKNOWN NO es hardware-backed",
            DetectorSecurityLevel.NodeSecurityLevel.UNKNOWN.isHardwareBacked)
    }

    // UT-055: audit_trail_hash es determinístico — mismo input → mismo hash
    @Test
    fun ut055_audit_trail_hash_is_deterministic() {
        val mission  = buildMission()
        val record1  = generateTelemetryRecord(mission)
        val record2  = generateTelemetryRecord(mission)
        assertEquals(
            "UT-055: audit_trail_hash debe ser determinístico",
            record1.auditTrailHash, record2.auditTrailHash
        )
    }

    // UT-056: audit_trail_hash cambia si se modifica algún campo del MissionLog
    @Test
    fun ut056_audit_trail_hash_changes_when_mission_modified() {
        val mission1 = buildMission(id = "mission-original")
        val mission2 = buildMission(id = "mission-modified") // id diferente
        val hash1    = generateTelemetryRecord(mission1).auditTrailHash
        val hash2    = generateTelemetryRecord(mission2).auditTrailHash
        assertNotEquals(
            "UT-056: audit_trail_hash debe cambiar si cambia el MissionLog",
            hash1, hash2
        )
    }

    // UT-057: TelemetryRecord contiene todos los campos ORION v1.0.0
    @Test
    fun ut057_telemetry_record_has_all_orion_fields() {
        val mission = buildMission()
        val record  = generateTelemetryRecord(
            mission       = mission,
            integrityOk   = true,
            tamperDetected = false,
            fdeActive     = true,
            strongboxOk   = false,
            securityLevel = "TEE_HARDWARE"
        )
        // Verificar todos los campos requeridos por TAD-B v3.0
        assertNotNull("UT-057: missionId", record.missionId)
        assertNotNull("UT-057: nodeId",    record.nodeId)
        assertNotNull("UT-057: tenantId",  record.tenantId)
        assertTrue("UT-057: auditTrailHash no vacío",
            record.auditTrailHash.isNotEmpty())
        assertTrue("UT-057: timestampStart > 0",
            record.timestampStart > 0)
        assertEquals("UT-057: deviceModel",
            "Motorola Moto G84 5G", record.deviceModel)
        assertEquals("UT-057: securityLevel",
            "TEE_HARDWARE", record.securityLevel)
        assertTrue("UT-057: integrityCheckPassed", record.integrityCheckPassed)
        assertFalse("UT-057: tamperFlag", record.tamperFlag)
        assertTrue("UT-057: fdeActive",   record.fdeActive)

        // Verificar que el JSON serializado contiene todos los campos clave
        val json = record.toJson()
        assertTrue("UT-057: JSON contiene mission_id",    json.contains("mission_id"))
        assertTrue("UT-057: JSON contiene audit_trail_hash", json.contains("audit_trail_hash"))
        assertTrue("UT-057: JSON contiene strongbox_available", json.contains("strongbox_available"))
        assertTrue("UT-057: JSON contiene integrity_check_passed", json.contains("integrity_check_passed"))
    }
}
