package com.loisnel.gestorclaves.sync.manager

import com.loisnel.gestorclaves.core.domain.FCPConstants

/**
 * ConflictResolver — Resuelve conflictos cuando el mismo mission_id
 * llega por dos rutas de sincronización diferentes
 *
 * En una red P2P con múltiples canales (WiFi + NFC + Bluetooth), es
 * posible que el mismo TelemetryRecord llegue por dos caminos distintos.
 * El ConflictResolver garantiza que los datos persistan de forma coherente.
 *
 * Reglas de resolución (en orden de prioridad):
 *
 * 1. COMPLETED/FAILED/EXPIRED son estados finales inmutables.
 *    Si el registro local ya está en estado final → ignorar el entrante.
 *    Un estado final no puede retroceder.
 *
 * 2. Si ambos están en estado transitorio (ISSUED, ACTIVE, INTERRUPTED):
 *    El más reciente (mayor timestamp) gana.
 *
 * 3. Si el registro local no existe → siempre aceptar el entrante.
 *
 * TAD-B v3.0 — ORION Platform — Resiliencia P2P
 */
object ConflictResolver {

    private val FINAL_STATES = setOf(
        FCPConstants.STATUS_COMPLETED,
        FCPConstants.STATUS_FAILED,
        FCPConstants.STATUS_EXPIRED
    )

    /**
     * Determina si un registro entrante debe reemplazar al existente.
     *
     * @param existingStatus   Estado del registro actualmente en la DB
     * @param existingTimestamp Timestamp del registro local
     * @param incomingStatus   Estado del registro entrante
     * @param incomingTimestamp Timestamp del registro entrante
     * @return true si el registro entrante debe ser aceptado/actualizado
     */
    fun shouldAcceptIncoming(
        existingStatus: String?,
        existingTimestamp: Long,
        incomingStatus: String,
        incomingTimestamp: Long
    ): Boolean {
        // Regla 0: si no existe localmente → siempre aceptar
        if (existingStatus == null) return true

        // Regla 1: estado final local → nunca sobreescribir
        if (existingStatus in FINAL_STATES) return false

        // Regla 2: si el entrante es un estado final → siempre aceptar
        // (actualización de ACTIVE → COMPLETED desde otro canal)
        if (incomingStatus in FINAL_STATES) return true

        // Regla 3: ambos transitorios → el más reciente gana
        return incomingTimestamp > existingTimestamp
    }

    /**
     * Verifica si un registro es duplicado exacto (misma misión, mismo estado,
     * mismo timestamp). Los duplicados exactos se descartan silenciosamente.
     */
    fun isDuplicate(
        existingStatus: String?,
        existingTimestamp: Long,
        incomingStatus: String,
        incomingTimestamp: Long
    ): Boolean =
        existingStatus == incomingStatus &&
        existingTimestamp == incomingTimestamp
}
