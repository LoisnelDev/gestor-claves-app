package com.loisnel.gestorclaves.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.loisnel.gestorclaves.core.data.entities.MissionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(mission: MissionLog)

    @Query("SELECT * FROM mission_logs WHERE id = :missionId")
    suspend fun findById(missionId: String): MissionLog?

    @Query("SELECT EXISTS(SELECT 1 FROM mission_logs WHERE id = :missionId)")
    suspend fun existsById(missionId: String): Boolean

    @Query("SELECT * FROM mission_logs WHERE status = :status")
    suspend fun findByStatus(status: String): List<MissionLog>

    @Query("SELECT * FROM mission_logs ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<MissionLog>>

    @Query("SELECT * FROM mission_logs WHERE tenant_id = :tenantId ORDER BY created_at DESC")
    fun getByTenantFlow(tenantId: String): Flow<List<MissionLog>>

    /**
     * Transición atómica de estado FSM.
     * @Transaction garantiza que si hay power failure a mitad,
     * Room hace rollback automático — nunca queda en estado inconsistente.
     */
    @Transaction
    @Query("""
        UPDATE mission_logs
        SET status       = :newStatus,
            signature    = :signature,
            device_model = :deviceModel,
            os_version   = :osVersion,
            clock_drift_ms = :clockDriftMs,
            closed_at    = :closedAt
        WHERE id = :missionId
    """)
    suspend fun transitionStatus(
        missionId: String,
        newStatus: String,
        signature: String,
        deviceModel: String?,
        osVersion: String?,
        clockDriftMs: Long?,
        closedAt: Long?
    )

    /**
     * Marca la misión como INTERRUPTED antes de cualquier escritura.
     * Si hay power failure después de esto y antes de la escritura final,
     * recoverInterruptedMissions() la detecta en el próximo startup.
     *
     * TAD-B v2.1.1 — Mejora 1: Atomicidad FSM ante power failure
     */
    @Query("UPDATE mission_logs SET status = :status WHERE id = :missionId")
    suspend fun updateStatusOnly(missionId: String, status: String)
}
