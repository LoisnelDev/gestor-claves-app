package com.loisnel.gestorclaves.core.security

import com.loisnel.gestorclaves.core.data.entities.MissionLog
import com.loisnel.gestorclaves.core.domain.AuditReport
import com.loisnel.gestorclaves.core.domain.FCPConstants

/**
 * AuditTrailManager — Cálculo de KPIs y reportes de auditoría
 *
 * Transforma la lista de MissionLogs en métricas ejecutivas
 * para el panel del Director.
 *
 * No tiene acceso de escritura — solo lee Mission_Logs.
 * El audit trail es inmutable por diseño de la FSM.
 *
 * TAD-B v2.1.1 — Sección 4 (Director KPIs)
 */
object AuditTrailManager {

    /**
     * Genera un [AuditReport] desde una lista de misiones.
     *
     * @param missions     Lista de MissionLogs del período
     * @param tenantId     ID del tenant para el reporte
     * @param periodStartMs Inicio del período en ms Unix
     * @param periodEndMs   Fin del período en ms Unix
     */
    fun generateReport(
        missions: List<MissionLog>,
        tenantId: String,
        periodStartMs: Long = 0L,
        periodEndMs: Long = System.currentTimeMillis()
    ): AuditReport {

        // Filtrar por tenant y período
        val filtered = missions.filter { m ->
            m.tenant_id == tenantId &&
            m.created_at >= periodStartMs &&
            m.created_at <= periodEndMs
        }

        val completed    = filtered.count { it.status == FCPConstants.STATUS_COMPLETED }
        val failed       = filtered.count { it.status == FCPConstants.STATUS_FAILED }
        val expired      = filtered.count { it.status == FCPConstants.STATUS_EXPIRED }
        val active       = filtered.count { it.status == FCPConstants.STATUS_ACTIVE }
        val interrupted  = filtered.count { it.status == FCPConstants.STATUS_INTERRUPTED }

        // Tasa de cumplimiento: completadas / (completadas + fallidas)
        // Excluye expiradas e interrumpidas del denominador
        val denominator    = completed + failed
        val complianceRate = if (denominator > 0)
            completed.toFloat() / denominator.toFloat()
        else 0f

        // Tiempo promedio de completación
        val completedWithClose = filtered.filter {
            it.status == FCPConstants.STATUS_COMPLETED && it.closed_at != null
        }
        val avgCompletionTimeMs = if (completedWithClose.isNotEmpty())
            completedWithClose.map { (it.closed_at!! - it.created_at) }.average().toLong()
        else 0L

        // Técnicos únicos (por clave pública del receptor)
        val uniqueTechnicians = filtered.map { it.receiver_key }.toSet().size

        // Modelos con fallos (para diagnóstico de HW)
        val deviceModelFailures = filtered
            .filter { it.status == FCPConstants.STATUS_FAILED && it.device_model != null }
            .groupBy { it.device_model!! }
            .mapValues { (_, missions) -> missions.size }

        // Drift máximo de reloj registrado
        val maxClockDriftMs = filtered
            .mapNotNull { it.clock_drift_ms }
            .maxOrNull() ?: 0L

        return AuditReport(
            tenantId              = tenantId,
            periodStartMs         = periodStartMs,
            periodEndMs           = periodEndMs,
            totalMissions         = filtered.size,
            completedMissions     = completed,
            failedMissions        = failed,
            expiredMissions       = expired,
            activeMissions        = active,
            complianceRate        = complianceRate,
            avgCompletionTimeMs   = avgCompletionTimeMs,
            interruptedMissions   = interrupted,
            uniqueTechnicians     = uniqueTechnicians,
            deviceModelFailures   = deviceModelFailures,
            maxClockDriftMs       = maxClockDriftMs
        )
    }

    /**
     * Detecta misiones problemáticas para alerta al Director.
     * Incluye: INTERRUPTED (power failure), FAILED, EXPIRED sin cierre.
     */
    fun getIncidentMissions(missions: List<MissionLog>): List<MissionLog> =
        missions.filter { m ->
            m.status in setOf(
                FCPConstants.STATUS_INTERRUPTED,
                FCPConstants.STATUS_FAILED,
                FCPConstants.STATUS_EXPIRED
            )
        }.sortedByDescending { it.created_at }
}
