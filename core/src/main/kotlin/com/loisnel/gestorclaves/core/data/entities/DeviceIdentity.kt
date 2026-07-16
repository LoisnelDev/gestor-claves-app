package com.loisnel.gestorclaves.core.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * DeviceIdentity — Identidad criptográfica persistida del dispositivo
 *
 * La clave privada Ed25519 reside en Android Keystore (TEE/StrongBox)
 * y NUNCA se persiste en Room. Solo se guarda la clave pública y el
 * alias del Keystore para recuperar el KeyPair en runtime.
 *
 * TAD-B v2.1.1 — Sección 7
 */
@Entity(tableName = "device_identity")
data class DeviceIdentity(
    @PrimaryKey
    val id: Int = 1,                     // Siempre 1 — un único dispositivo
    val public_key_base64: String,        // Clave pública Ed25519 en Base64
    val keystore_alias: String,           // Alias en Android Keystore
    val created_at: Long                  // Timestamp de generación
)
