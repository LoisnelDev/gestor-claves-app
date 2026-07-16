package com.loisnel.gestorclaves.core.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Asset — Credencial cifrada perteneciente a un Tenant
 *
 * El payload sensible se almacena como BLOB AES-256-GCM.
 * Nunca se guarda en texto plano en la base de datos.
 * La clave de descifrado (TenantKey) se deriva en runtime por HKDF
 * y nunca se persiste.
 *
 * TAD-B v2.1.1 — Sección 8
 */
@Entity(
    tableName = "assets",
    foreignKeys = [ForeignKey(
        entity = Tenant::class,
        parentColumns = ["id"],
        childColumns = ["tenant_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tenant_id")]
)
data class Asset(
    @PrimaryKey
    val id: String,                       // UUID único del asset
    val tenant_id: String,                // FK → tenants.id
    val label: String,                    // Nombre legible (router, SCADA, PLC)
    val data_encrypted_blob: ByteArray,   // AES-256-GCM ciphertext + GCM auth tag
    val nonce: ByteArray,                 // GCM nonce 12 bytes — único por escritura
    val updated_at: Long                  // Unix timestamp
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Asset) return false
        return id == other.id &&
               tenant_id == other.tenant_id &&
               label == other.label &&
               data_encrypted_blob.contentEquals(other.data_encrypted_blob) &&
               nonce.contentEquals(other.nonce) &&
               updated_at == other.updated_at
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + tenant_id.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + data_encrypted_blob.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + updated_at.hashCode()
        return result
    }
}
