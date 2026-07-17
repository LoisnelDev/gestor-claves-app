package com.loisnel.gestorclaves.sync

import com.loisnel.gestorclaves.sync.manager.ConflictResolver
import com.loisnel.gestorclaves.sync.protocol.SyncPacket
import com.loisnel.gestorclaves.sync.registry.NodeInfo
import com.loisnel.gestorclaves.sync.registry.NodeRegistry
import com.loisnel.gestorclaves.core.domain.FCPConstants
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

/**
 * SyncProtocolTest — UT-058 a UT-061
 * Tests del protocolo Store-and-Forward P2P.
 * JVM puro — sin emulador.
 */
class SyncProtocolTest {

    private val now = System.currentTimeMillis()

    private fun buildSyncPacket(
        senderKey: String = "sender-node-key-base64",
        tenantId: String  = "tenant-test"
    ): SyncPacket {
        val fakePayload = "fake-encrypted-payload".toByteArray()
        val fakeNonce   = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return SyncPacket(
            senderNodeId     = senderKey,
            tenantId         = tenantId,
            createdAtMs      = now,
            recordCount      = 3,
            encryptedPayload = fakePayload,
            nonce            = fakeNonce,
            signature        = "fake-signature-base64"
        )
    }

    // UT-058: SyncPacket serializa y deserializa correctamente (round-trip)
    @Test
    fun ut058_sync_packet_json_roundtrip() {
        val original  = buildSyncPacket()
        val json      = original.toJson()
        val restored  = SyncPacket.fromJson(json)

        assertEquals("UT-058: senderNodeId", original.senderNodeId, restored.senderNodeId)
        assertEquals("UT-058: tenantId",     original.tenantId,     restored.tenantId)
        assertEquals("UT-058: createdAtMs",  original.createdAtMs,  restored.createdAtMs)
        assertEquals("UT-058: recordCount",  original.recordCount,  restored.recordCount)
        assertArrayEquals("UT-058: nonce",   original.nonce,        restored.nonce)
        assertEquals("UT-058: protocolVersion",
            original.protocolVersion, restored.protocolVersion)
    }

    // UT-059: SyncManager deduplica mission_ids ya procesados
    @Test
    fun ut059_sync_manager_deduplicates_mission_ids() {
        val registry  = NodeRegistry()
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val userSalt  = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val manager   = com.loisnel.gestorclaves.sync.manager.SyncManager(
            nodeRegistry = registry,
            localNodeId  = "local-node-key",
            masterKey    = masterKey,
            userSalt     = userSalt
        )

        val missionId = "mission-dedup-test"
        assertFalse("UT-059: antes de marcar → no procesado",
            manager.wasProcessed(missionId))

        manager.markProcessed(missionId)
        assertTrue("UT-059: después de marcar → procesado",
            manager.wasProcessed(missionId))

        // Segundo marcado → sigue siendo procesado (idempotente)
        manager.markProcessed(missionId)
        assertTrue("UT-059: marcado dos veces → sigue procesado",
            manager.wasProcessed(missionId))
    }

    // UT-060: ConflictResolver ignora paquete entrante si estado local es COMPLETED
    @Test
    fun ut060_conflict_resolver_protects_completed_state() {
        // Estado local COMPLETED → nunca sobreescribir
        val acceptFromCompleted = ConflictResolver.shouldAcceptIncoming(
            existingStatus    = FCPConstants.STATUS_COMPLETED,
            existingTimestamp = now - 1000,
            incomingStatus    = FCPConstants.STATUS_ACTIVE,
            incomingTimestamp = now
        )
        assertFalse(
            "UT-060: COMPLETED local no debe ser sobreescrito por ACTIVE entrante",
            acceptFromCompleted
        )

        // Estado local ACTIVE → aceptar COMPLETED entrante (actualización válida)
        val acceptCompletedIncoming = ConflictResolver.shouldAcceptIncoming(
            existingStatus    = FCPConstants.STATUS_ACTIVE,
            existingTimestamp = now - 1000,
            incomingStatus    = FCPConstants.STATUS_COMPLETED,
            incomingTimestamp = now
        )
        assertTrue(
            "UT-060: ACTIVE local debe aceptar COMPLETED entrante",
            acceptCompletedIncoming
        )

        // No existe localmente → siempre aceptar
        val acceptNew = ConflictResolver.shouldAcceptIncoming(
            existingStatus    = null,
            existingTimestamp = 0L,
            incomingStatus    = FCPConstants.STATUS_ISSUED,
            incomingTimestamp = now
        )
        assertTrue("UT-060: registro nuevo siempre se acepta", acceptNew)
    }

    // UT-061: NodeRegistry almacena, recupera y verifica nodos correctamente
    @Test
    fun ut061_node_registry_stores_and_retrieves_nodes() {
        val registry = NodeRegistry()
        val nodeKey  = "node-ed25519-public-key-base64"
        val node     = NodeInfo(
            publicKeyBase64 = nodeKey,
            role            = "TECHNICIAN",
            trustedSinceMs  = now - 86_400_000L,
            lastSeenMs      = now,
            tenantId        = "tenant-empresa-A",
            displayName     = "Juan Técnico"
        )

        // Antes de registrar → no es de confianza
        assertFalse("UT-061: nodo no registrado no es de confianza",
            registry.isTrusted(nodeKey))

        registry.register(node)

        // Después de registrar → es de confianza
        assertTrue("UT-061: nodo registrado es de confianza",
            registry.isTrusted(nodeKey))

        // Recuperar por clave
        val recovered = registry.getNode(nodeKey)
        assertNotNull("UT-061: debe recuperar el nodo por clave", recovered)
        assertEquals("UT-061: role correcto", "TECHNICIAN", recovered!!.role)
        assertEquals("UT-061: displayName correcto", "Juan Técnico", recovered.displayName)

        // Nodo desconocido → no de confianza
        assertFalse("UT-061: clave desconocida no es de confianza",
            registry.isTrusted("unknown-key"))

        // Filtrar por tenant
        val tenantNodes = registry.getTenantNodes("tenant-empresa-A")
        assertEquals("UT-061: 1 nodo en el tenant", 1, tenantNodes.size)
        assertEquals("UT-061: tamaño del registry", 1, registry.size())
    }
}
