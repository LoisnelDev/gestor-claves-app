package com.loisnel.gestorclaves.sync.registry

/**
 * NodeRegistry — Registro de nodos conocidos en el ecosistema ORION
 *
 * Mantiene el mapa de confianza del ecosistema. Un nodo solo puede
 * recibir SyncPackets de nodos previamente registrados mediante
 * el Handshake de Confianza (TAD-B v3.0 Sección 5).
 *
 * La confianza es un prerequisito del P2P: solo se aceptan datos
 * de nodos cuya clave pública Ed25519 está en el registro.
 * Esto previene ataques de inserción de datos falsos por nodos
 * desconocidos que intercepten el canal de sincronización.
 *
 * TAD-B v3.0 — ORION Platform — Protocolo de Nodos
 */
class NodeRegistry {

    // Mapa: publicKeyBase64 → NodeInfo
    private val nodes = mutableMapOf<String, NodeInfo>()

    /**
     * Registra un nodo tras completar el Handshake de Confianza.
     * Solo llamar tras verificar el QR/NFC de registro físico.
     */
    fun register(node: NodeInfo) {
        nodes[node.publicKeyBase64] = node
    }

    /**
     * Verifica si un nodo es de confianza.
     * Un nodo no registrado NO puede sincronizar datos.
     *
     * @param publicKeyBase64 Clave pública Ed25519 del nodo remitente
     * @return true si el nodo fue registrado mediante Handshake
     */
    fun isTrusted(publicKeyBase64: String): Boolean =
        nodes.containsKey(publicKeyBase64)

    /**
     * Recupera la información de un nodo por su clave pública.
     */
    fun getNode(publicKeyBase64: String): NodeInfo? =
        nodes[publicKeyBase64]

    /**
     * Lista todos los nodos de confianza para un tenant específico.
     */
    fun getTenantNodes(tenantId: String): List<NodeInfo> =
        nodes.values.filter { it.tenantId == tenantId }

    /**
     * Actualiza el timestamp de último contacto de un nodo.
     */
    fun updateLastSeen(publicKeyBase64: String, timestampMs: Long) {
        nodes[publicKeyBase64]?.let { node ->
            nodes[publicKeyBase64] = node.copy(lastSeenMs = timestampMs)
        }
    }

    /** Retorna el número total de nodos registrados */
    fun size(): Int = nodes.size

    /** Elimina todos los nodos (solo para tests) */
    internal fun clear() = nodes.clear()
}
