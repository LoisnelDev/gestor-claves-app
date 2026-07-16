package com.loisnel.gestorclaves.core.domain

/**
 * AuditReport — Reporte de auditoría para el Director
 *
 * Contiene KPIs calculados desde Mission_Logs.
 * Sin acceso a credenciales individuales — solo métricas agregadas.
 *
 * TAD-B v2.1.1 — Sección 4 (rol Director)
 */
data class AuditReport(
    val tenantId: String,
    val periodStartMs: Long,
    val periodEndMs: Long,

    // ── KPIs principales ──────────────────────────────────────────
    val totalMissions: Int,
    val completedMissions: Int,
    val failedMissions: Int,
    val expiredMissions: Int,
    val activeMissions: Int,

    // ── Métricas de calidad ───────────────────────────────────────
    /** Porcentaje de misiones cerradas correctamente (0.0 - 1.0) */
    val complianceRate: Float,

    /** Tiempo promedio entre emisión y cierre en milisegundos */
    val avgCompletionTimeMs: Long,

    // ── Incidentes (TAD-B v2.1.1 — Mejora 1: power failures) ─────
    /** Misiones que quedaron INTERRUPTED — indica power failures en campo */
    val interruptedMissions: Int,

    /** Técnicos únicos que participaron en el período */
    val uniqueTechnicians: Int,

    // ── Diagnóstico de hardware (v2.1.1) ──────────────────────────
    /** Modelos de dispositivo con fallos — para identificar limitaciones de HW */
    val deviceModelFailures: Map<String, Int>,

    /** Drift máximo de reloj registrado en el período (ms) */
    val maxClockDriftMs: Long
)
