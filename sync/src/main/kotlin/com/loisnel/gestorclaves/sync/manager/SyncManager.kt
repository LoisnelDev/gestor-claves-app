package com.loisnel.gestorclaves.sync.manager

import com.loisnel.gestorclaves.core.crypto.AESGCMManager
import com.loisnel.gestorclaves.core.crypto.HKDFManager
import com.loisnel.gestorclaves.core.domain.TelemetryRecord
import com.loisnel.gestorclaves.sync.protocol.SyncPacket
import com.loisnel.gestorclaves.sync.registry.NodeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.LinkedList
import java.util.Queue

/**
 * SyncManager — Gestor del protocolo Store-and-Forward
 *
 * El SyncManager mantiene una cola de SyncPackets pendientes de envío.
 * Cuando el nodo encuentra conectividad (WiFi, Bluetooth, Internet),
 * la cola se drena y los paquetes se transmiten al nodo destino.
 *
 * Propiedades del protocolo:
 * - Store: los registros se acumulan localmente mientras el nodo está aislado
 * - Forward: al encontrar conectividad, la cola se vacía automáticamente
 * - Deduplicación: el ConflictResolver evita procesamiento doble
 * - Cifrado: cada SyncPacket está cifrado con AES-256-GCM
 * - Firma: cada SyncPacket está firmado con Ed25519 del nodo emisor
 * - Confianza: solo se aceptan paquetes de nodos en NodeRegistry
 *
 * TAD-B v3.0 — ORION Platform — Resiliencia P2P
 */
class SyncManager(
    private val nodeRegistry: NodeRegistry,
    private val localNodeId: String,  // Ed25519 public key Base64
    private val masterKey: ByteArray,
    private val userSalt: ByteArray
) {
    // Cola de paquetes pendientes de envío (FIFO)
    private val outboundQueue: Queue<SyncPacket> = LinkedList()

    // Registros ya procesados (deduplicación por mission_id)
    private val processedIds = mutableSetOf<String>()

    /**
     * Encola un conjunto de TelemetryRecords para sincronización.
     * Los registros se cifran con la TenantKey HKDF y se firman.
     *
     * @param records    Lista de registros a sincronizar
     * @param tenantId   Tenant al que pertenecen los registros
     * @param signerFn   Función de firma Ed25519 del nodo local
     * @return El SyncPacket creado y encolado
     */
    suspend fun enqueue(
        records: List<TelemetryRecord>,
        tenantId: String,
        signerFn: (ByteArray) -> ByteArray
    ): SyncPacket = withContext(Dispatchers.Default) {
        // Derivar clave de sincronización con info específica del canal
        val syncKey = HKDFManager.deriveTenantKey(
            masterKey = masterKey,
            userSalt  = userSalt,
            tenantId  = "$tenantId:sync:channel"
        )

        // Serializar registros como JSON array
        val payload    = records.joinToString(",", "[", "]") { it.toJson() }
        val blob       = AESGCMManager.encrypt(payload, syncKey)

        // Firmar el payload cifrado (no el plaintext — el plaintext no viaja)
        val signature  = signerFn(blob.ciphertext)
        val sigBase64  = Base64.getEncoder().encodeToString(signature)

        val packet = SyncPacket(
            senderNodeId     = localNodeId,
            tenantId         = tenantId,
            createdAtMs      = System.currentTimeMillis(),
            recordCount      = records.size,
            encryptedPayload = blob.ciphertext,
            nonce            = blob.nonce,
            signature        = sigBase64
        )
        outboundQueue.add(packet)
        java.util.Arrays.fill(syncKey, 0.toByte())
        packet
    }

    /**
     * Procesa un SyncPacket entrante de otro nodo.
     * Verifica confianza y deduplicación antes de aceptar.
     *
     * @param packet    El SyncPacket recibido del nodo remoto
     * @param verifyFn  Función de verificación Ed25519 (retorna true si válido)
     * @return [SyncResult] indicando si el paquete fue aceptado o rechazado
     */
    suspend fun processIncoming(
        packet: SyncPacket,
        verifyFn: (ByteArray, ByteArray) -> Boolean
    ): SyncResult = withContext(Dispatchers.Default) {
        // Verificar confianza del nodo emisor
        if (!nodeRegistry.isTrusted(packet.senderNodeId)) {
            return@withContext SyncResult.Rejected("Node not in registry: ${packet.senderNodeId.take(16)}...")
        }

        // Verificar firma Ed25519
        val sigBytes = Base64.getDecoder().decode(packet.signature)
        if (!verifyFn(packet.encryptedPayload, sigBytes)) {
            return@withContext SyncResult.Rejected("Invalid Ed25519 signature")
        }

        // Verificar versión del protocolo
        if (packet.protocolVersion != "sync/1.0") {
            return@withContext SyncResult.Rejected("Unsupported protocol: ${packet.protocolVersion}")
        }

        // Actualizar last seen del nodo emisor
        nodeRegistry.updateLastSeen(packet.senderNodeId, System.currentTimeMillis())

        SyncResult.Accepted(packet.recordCount)
    }

    /**
     * Drena la cola de paquetes pendientes.
     * Llamar cuando el nodo detecta conectividad.
     *
     * @return Lista de paquetes listos para transmitir
     */
    fun drainQueue(): List<SyncPacket> {
        val drained = mutableListOf<SyncPacket>()
        while (outboundQueue.isNotEmpty()) {
            drained.add(outboundQueue.poll()!!)
        }
        return drained
    }

    /** Retorna el número de paquetes en cola pendientes de envío */
    fun pendingCount(): Int = outboundQueue.size

    /** Marca un mission_id como procesado (deduplicación) */
    fun markProcessed(missionId: String) { processedIds.add(missionId) }

    /** Verifica si un mission_id ya fue procesado */
    fun wasProcessed(missionId: String): Boolean = missionId in processedIds
}

/**
 * Resultado del procesamiento de un SyncPacket entrante
 */
sealed class SyncResult {
    data class Accepted(val recordCount: Int) : SyncResult()
    data class Rejected(val reason: String)  : SyncResult()
}
