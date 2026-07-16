package com.loisnel.gestorclaves.core.security

import com.loisnel.gestorclaves.core.data.entities.MissionLog
import com.loisnel.gestorclaves.core.domain.FCPConstants
import org.junit.Assert.*
import org.junit.Test

/**
 * AuditTrailTest — UT-043 a UT-045
 * Tests del cálculo de KPIs y generación de reportes de auditoría.
 * JVM puro — sin emulador.
 */
class AuditTrailTest {

    private val tenantId = "tenant-empresa-test"
    private val now      = System.currentTimeMillis()

    private fun buildMission(
        id: String,
        status: String,
        receiverKey: String = "tech-key-001",
        closedAt: Long? = null,
        deviceModel: String? = null,
        clockDriftMs: Long? = null
    ) = MissionLog(
        id                 = id,
        tenant_id          = tenantId,
        mission_data_json  = "{}",
        status             = status,
        issuer_key         = "supervisor-key-001",
        receiver_key       = receiverKey,
        signature          = "sig",
        expiry_ms          = now + 3_600_000L,
        clock_tolerance_ms = 300_000L,
        device_model       = deviceModel,
        os_version         = "Android 14",
        clock_drift_ms     = clockDriftMs,
        created_at         = now - 1_800_000L, // 30 min atrás
        closed_at          = closedAt
    )

    // UT-043: KPIs calculados correctamente
    @Test
    fun ut043_kpis_calculated_correctly() {
        val missions = listOf(
            buildMission("m1", FCPConstants.STATUS_COMPLETED, closedAt = now - 900_000L),
            buildMission("m2", FCPConstants.STATUS_COMPLETED, closedAt = now - 600_000L),
            buildMission("m3", FCPConstants.STATUS_FAILED),
            buildMission("m4", FCPConstants.STATUS_EXPIRED),
            buildMission("m5", FCPConstants.STATUS_ACTIVE),
        )
        val report = AuditTrailManager.generateReport(missions, tenantId)

        assertEquals("UT-043: total misiones", 5, report.totalMissions)
        assertEquals("UT-043: completadas",    2, report.completedMissions)
        assertEquals("UT-043: fallidas",       1, report.failedMissions)
        assertEquals("UT-043: expiradas",      1, report.expiredMissions)
        assertEquals("UT-043: activas",        1, report.activeMissions)
    }

    // UT-044: Misiones INTERRUPTED en reporte de incidentes
    @Test
    fun ut044_interrupted_missions_in_incident_report() {
        val missions = listOf(
            buildMission("m1", FCPConstants.STATUS_COMPLETED),
            buildMission("m2", FCPConstants.STATUS_INTERRUPTED), // Power failure
            buildMission("m3", FCPConstants.STATUS_INTERRUPTED), // Power failure
            buildMission("m4", FCPConstants.STATUS_FAILED),
        )
        val report    = AuditTrailManager.generateReport(missions, tenantId)
        val incidents = AuditTrailManager.getIncidentMissions(missions)

        assertEquals("UT-044: misiones interrumpidas", 2, report.interruptedMissions)
        assertEquals("UT-044: incidentes totales", 3, incidents.size) // 2 INTERRUPTED + 1 FAILED
        assertTrue("UT-044: incidentes incluyen INTERRUPTED",
            incidents.any { it.status == FCPConstants.STATUS_INTERRUPTED })
    }

    // UT-045: Tasa de cumplimiento correcta
    @Test
    fun ut045_compliance_rate_calculated_correctly() {
        val missions = listOf(
            buildMission("m1", FCPConstants.STATUS_COMPLETED),
            buildMission("m2", FCPConstants.STATUS_COMPLETED),
            buildMission("m3", FCPConstants.STATUS_COMPLETED),
            buildMission("m4", FCPConstants.STATUS_FAILED),
        )
        val report = AuditTrailManager.generateReport(missions, tenantId)

        // 3 completadas / (3 completadas + 1 fallida) = 0.75
        assertEquals("UT-045: compliance rate debe ser 0.75",
            0.75f, report.complianceRate, 0.01f)
    }
}
