package com.loisnel.gestorclaves.core.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tenant — Organización o contratista registrado en el dispositivo
 *
 * Metadatos no sensibles — la clave pública Ed25519 identifica
 * criptográficamente al tenant. Los assets de este tenant se cifran
 * con la TenantKey derivada por HKDF usando el id como info string.
 *
 * TAD-B v2.1.1 — Sección 8
 */
@Entity(tableName = "tenants")
data class Tenant(
    @PrimaryKey
    val id: String,                   // Hash del nombre del contratista
    val name: String,                 // Nombre legible para la UI
    val public_key_ed25519: String,   // Clave pública Base64 del tenant
    val created_at: Long              // Unix timestamp de registro
)
