package com.loisnel.gestorclaves.sync.protocol

/**
 * SyncPacket — Contenedor cifrado para sincronización P2P entre nodos
 *
 * El SyncPacket es el artefacto central del protocolo Store-and-Forward.
 * Permite que un nodo ORION acumule TelemetryRecords mientras está
 * desconectado y los transfiera a otro nodo cuando encuentra conectividad.
 *
 * Seguridad:
 * - El payload está cifrado con AES-256-GCM (misma arquitectura que los assets)
 * - La firma Ed25519 garantiza que el paquete no fue modificado en tránsito
 * - Solo nodos registrados en NodeRegistry pueden emitir paquetes válidos
 *
 * Formato del payload cifrado: JSON array de TelemetryRecord
 * Clave de cifrado: derivada con HKDF del tenantId + "sync:channel"
 *
 * TAD-B v3.0 — ORION Platform — Resiliencia P2P
 */
data class SyncPacket(
    /** node_id del emisor — clave pública Ed25519 Base64 */
    val senderNodeId: String,

    /** Tenant al que pertenecen los registros */
    val tenantId: String,

    /** Timestamp Unix ms de creación del paquete */
    val createdAtMs: Long,

    /** Número de TelemetryRecords incluidos en el payload */
    val recordCount: Int,

    /** Payload cifrado AES-256-GCM (JSON array de TelemetryRecord) */
    val encryptedPayload: ByteArray,

    /** Nonce GCM 12 bytes para descifrado */
    val nonce: ByteArray,

    /** Firma Ed25519 del emisor sobre el payload cifrado */
    val signature: String,

    /** Versión del protocolo de sincronización */
    val protocolVersion: String = "sync/1.0"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncPacket) return false
        return senderNodeId == other.senderNodeId &&
               tenantId == other.tenantId &&
               createdAtMs == other.createdAtMs &&
               encryptedPayload.contentEquals(other.encryptedPayload) &&
               nonce.contentEquals(other.nonce) &&
               signature == other.signature
    }

    override fun hashCode(): Int {
        var r = senderNodeId.hashCode()
        r = 31 * r + tenantId.hashCode()
        r = 31 * r + createdAtMs.hashCode()
        r = 31 * r + encryptedPayload.contentHashCode()
        return r
    }

    /** Serializa el paquete como JSON para transmisión por red o NFC */
    fun toJson(): String = buildString {
        append("{")
        append("\"protocol_version\":\"$protocolVersion\",")
        append("\"sender_node_id\":\"$senderNodeId\",")
        append("\"tenant_id\":\"$tenantId\",")
        append("\"created_at_ms\":$createdAtMs,")
        append("\"record_count\":$recordCount,")
        append("\"encrypted_payload\":\"${java.util.Base64.getEncoder().encodeToString(encryptedPayload)}\",")
        append("\"nonce\":\"${java.util.Base64.getEncoder().encodeToString(nonce)}\",")
        append("\"signature\":\"$signature\"")
        append("}")
    }

    companion object {
        /** Deserializa un SyncPacket desde JSON */
        fun fromJson(json: String): SyncPacket {
            fun str(key: String): String {
                val pattern = Regex(""""$key"\s*:\s*"([^"]*)"  """.trim())
                return pattern.find(json)?.groupValues?.get(1)
                    ?: throw IllegalArgumentException("Missing field: $key")
            }
            fun lng(key: String): Long {
                val pattern = Regex(""""$key"\s*:\s*(\d+)""")
                return pattern.find(json)?.groupValues?.get(1)?.toLong()
                    ?: throw IllegalArgumentException("Missing field: $key")
            }
            fun int(key: String): Int {
                val pattern = Regex(""""$key"\s*:\s*(\d+)""")
                return pattern.find(json)?.groupValues?.get(1)?.toInt()
                    ?: throw IllegalArgumentException("Missing field: $key")
            }
            val decoder = java.util.Base64.getDecoder()
            return SyncPacket(
                protocolVersion   = str("protocol_version"),
                senderNodeId      = str("sender_node_id"),
                tenantId          = str("tenant_id"),
                createdAtMs       = lng("created_at_ms"),
                recordCount       = int("record_count"),
                encryptedPayload  = decoder.decode(str("encrypted_payload")),
                nonce             = decoder.decode(str("nonce")),
                signature         = str("signature")
            )
        }
    }
}
