package com.loisnel.gestorclaves.core.data.repository

import androidx.room.withTransaction
import com.loisnel.gestorclaves.core.data.database.FCPDatabase
import com.loisnel.gestorclaves.core.data.entities.MissionLog
import com.loisnel.gestorclaves.core.domain.FCPConstants
import com.loisnel.gestorclaves.core.domain.UnauthorizedRoleException
import com.loisnel.gestorclaves.core.domain.UserRole
import com.loisnel.gestorclaves.core.security.TTLValidator

/**
 * MissionRepository — RBAC enforcement + FSM atómica para misiones
 *
 * INVARIANTES (TAD-B v2.1.1):
 * 1. Solo SUPERVISOR puede emitir misiones
 * 2. Solo TECHNICIAN puede activar misiones
 * 3. Toda transición de estado es atómica (@Transaction)
 * 4. El estado INTERRUPTED permite recovery tras power failure (Mejora 1)
 * 5. BillingManager NO es dependencia — Never-block policy (Mejora 2)
 *
 * TAD-B v2.1.1 — Secciones 13, 14
 */
class MissionRepository(
    private val db: FCPDatabase
    // SIN BillingManager — Mejora 2: billing nunca toca datos de misiones
) {
    private val dao = db.missionLogDao()

    /**
     * Emite una nueva misión. Solo SUPERVISOR tiene este permiso.
     *
     * @throws UnauthorizedRoleException si currentRole != SUPERVISOR
     * @throws IllegalStateException si mission_id ya existe (anti-replay)
     */
    suspend fun emitMission(
        mission: MissionLog,
        currentRole: UserRole
    ) {
        // RBAC enforcement en :core
        if (currentRole != UserRole.SUPERVISOR) {
            throw UnauthorizedRoleException(
                required = UserRole.SUPERVISOR,
                actual   = currentRole,
                action   = "emitMission"
            )
        }
        // Anti-replay: mission_id debe ser único
        if (dao.existsById(mission.id)) {
            throw IllegalStateException("Mission '${mission.id}' already exists — replay rejected")
        }
        dao.insert(mission)
    }

    /**
     * Activa una misión (ISSUED → ACTIVE).
     * Implementa el patrón Read-Validate-Write atómico con
     * compensación INTERRUPTED para power failures.
     *
     * TAD-B v2.1.1 — Mejora 1: Atomicidad FSM ante power failure
     *
     * @throws UnauthorizedRoleException si currentRole != TECHNICIAN
     * @throws IllegalStateException si la misión no está en ISSUED
     * @throws SecurityException si TTL expirado o fuera de tolerancia
     */
    suspend fun activateMission(
        missionId: String,
        receiverSignature: String,
        currentRole: UserRole,
        deviceModel: String? = null,
        osVersion: String? = null
    ) {
        if (currentRole != UserRole.TECHNICIAN) {
            throw UnauthorizedRoleException(
                required = UserRole.TECHNICIAN,
                actual   = currentRole,
                action   = "activateMission"
            )
        }

        db.withTransaction {
            // Paso 1: Marcar como INTERRUPTED — si hay power failure aquí,
            // la misión queda en INTERRUPTED y se recupera en el startup.
            val mission = dao.findById(missionId)
                ?: throw IllegalStateException("Mission '$missionId' not found")

            check(mission.status == FCPConstants.STATUS_ISSUED) {
                "Only ISSUED missions can be activated. Current: ${mission.status}"
            }

            // Marcar como INTERRUPTED antes de cualquier validación costosa
            dao.updateStatusOnly(missionId, FCPConstants.STATUS_INTERRUPTED)

            // Paso 2: Validar TTL con tolerancia
            val ttlResult = TTLValidator.validate(
                expiryMs    = mission.expiry_ms,
                toleranceMs = mission.clock_tolerance_ms
            )
            if (ttlResult is TTLValidator.TTLResult.Expired) {
                // Revertir a ISSUED antes de lanzar
                dao.updateStatusOnly(missionId, FCPConstants.STATUS_ISSUED)
                throw SecurityException("Mission TTL expired. Drift: ${ttlResult.driftMs}ms")
            }

            // Paso 3: Escribir estado final ACTIVE — atómica con withTransaction
            dao.transitionStatus(
                missionId    = missionId,
                newStatus    = FCPConstants.STATUS_ACTIVE,
                signature    = receiverSignature,
                deviceModel  = deviceModel,
                osVersion    = osVersion,
                clockDriftMs = ttlResult.driftMs,
                closedAt     = null
            )
        }
    }

    /**
     * Cierra una misión (ACTIVE → COMPLETED o FAILED).
     * Solo TECHNICIAN puede cerrar misiones.
     *
     * @throws UnauthorizedRoleException si currentRole != TECHNICIAN
     */
    suspend fun closeMission(
        missionId: String,
        result: String, // "COMPLETED" o "FAILED"
        closingSignature: String,
        currentRole: UserRole,
        deviceModel: String? = null,
        osVersion: String? = null,
        clockDriftMs: Long? = null
    ) {
        if (currentRole != UserRole.TECHNICIAN) {
            throw UnauthorizedRoleException(
                required = UserRole.TECHNICIAN,
                actual   = currentRole,
                action   = "closeMission"
            )
        }
        val validResults = setOf(
            FCPConstants.STATUS_COMPLETED,
            FCPConstants.STATUS_FAILED
        )
        require(result in validResults) {
            "Invalid close result: '$result'. Must be COMPLETED or FAILED"
        }

        db.withTransaction {
            val mission = dao.findById(missionId)
                ?: throw IllegalStateException("Mission '$missionId' not found")

            check(mission.status == FCPConstants.STATUS_ACTIVE) {
                "Only ACTIVE missions can be closed. Current: ${mission.status}"
            }

            dao.transitionStatus(
                missionId    = missionId,
                newStatus    = result,
                signature    = closingSignature,
                deviceModel  = deviceModel,
                osVersion    = osVersion,
                clockDriftMs = clockDriftMs,
                closedAt     = System.currentTimeMillis()
            )
        }
    }

    /**
     * Recovery de misiones INTERRUPTED tras power failure en campo.
     * Se llama en el startup de la app.
     *
     * - Si TTL aún válido → revierte a ISSUED (re-activable)
     * - Si TTL expirado   → marca como EXPIRED (estado final)
     *
     * TAD-B v2.1.1 — Mejora 1
     */
    suspend fun recoverInterruptedMissions() {
        val interrupted = dao.findByStatus(FCPConstants.STATUS_INTERRUPTED)
        interrupted.forEach { mission ->
            val ttl = TTLValidator.validate(
                expiryMs    = mission.expiry_ms,
                toleranceMs = mission.clock_tolerance_ms
            )
            val recoveryStatus = if (ttl !is TTLValidator.TTLResult.Expired)
                FCPConstants.STATUS_ISSUED
            else
                FCPConstants.STATUS_EXPIRED

            dao.updateStatusOnly(mission.id, recoveryStatus)
        }
    }

    suspend fun findById(missionId: String): MissionLog? = dao.findById(missionId)
    suspend fun existsById(missionId: String): Boolean   = dao.existsById(missionId)
}
