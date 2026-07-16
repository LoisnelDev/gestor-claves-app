package com.loisnel.gestorclaves.core.mission

import com.loisnel.gestorclaves.core.crypto.AESGCMManager
import com.loisnel.gestorclaves.core.crypto.AssetBlob
import com.loisnel.gestorclaves.core.domain.FCPConstants
import com.loisnel.gestorclaves.core.security.TTLValidator
import java.util.Base64
import javax.crypto.AEADBadTagException

/**
 * MissionValidator — Las 6 validaciones offline en orden crítico
 *
 * El orden es innegociable (TAD-B v2.1.1 — Sección 10.3):
 *
 *  1. Version == "1.0"            — anti-downgrade attack
 *  2. Firma Ed25519 válida        — ANTES del TTL para prevenir timing attack y DoS
 *  3. TTL vigente (con tolerancia) — el paquete es auténtico, el TTL es confiable
 *  4. Device binding              — issued_to == devicePublicKey
 *  5. GCM tag por asset           — detecta manipulación individual de credenciales
 *  6. Anti-replay                 — mission_id NOT IN Mission_Logs
 *
 * Por qué la firma va ANTES del TTL (Check 2 antes de Check 3):
 * - Si verificamos TTL primero, un atacante puede enviar paquetes
 *   con TTL válido pero firma falsa, forzando operaciones costosas
 *   de verificación → DoS (Denial of Service)
 * - Verificando la firma primero: paquetes no autenticados se rechazan
 *   inmediatamente sin procesar nada más
 */
object MissionValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val check: Int, val reason: String) : ValidationResult() {
            override fun toString() = "Check $check FAILED: $reason"
        }
    }

    /**
     * Ejecuta las 6 validaciones en orden sobre un [MissionPacket].
     *
     * @param packet          El paquete de misión a validar
     * @param devicePublicKey Clave pública Ed25519 de ESTE dispositivo (Base64)
     * @param tenantKey       TenantKey AES-256 para verificar GCM tags de assets
     * @param existingIds     Set de mission_ids ya procesados (anti-replay)
     * @return [ValidationResult.Valid] o [ValidationResult.Invalid] con el check que falló
     */
    fun validate(
        packet: MissionPacket,
        devicePublicKey: String,
        tenantKey: ByteArray,
        existingIds: Set<String>
    ): ValidationResult {

        // ── CHECK 1: Versión — anti-downgrade ────────────────────
        if (packet.version != FCPConstants.MISSION_PACKET_VERSION) {
            return ValidationResult.Invalid(1,
                "Unknown version '${packet.version}'. Expected '${FCPConstants.MISSION_PACKET_VERSION}'")
        }

        // ── CHECK 2: Firma Ed25519 — ANTES del TTL ───────────────
        // Reconstruir cadena canónica y verificar firma
        val canonical = MissionPacketBuilder.buildCanonicalString(
            missionId = packet.mission_id,
            tenantId  = packet.tenant_id,
            issuedTo  = packet.issued_to,
            expiryMs  = packet.expiry,
            assets    = packet.assets
        )
        val issuerKeyBytes = try {
            Base64.getDecoder().decode(packet.issued_by)
        } catch (e: Exception) {
            return ValidationResult.Invalid(2, "Invalid Base64 in issued_by: ${e.message}")
        }
        val signatureBytes = try {
            Base64.getDecoder().decode(packet.signature)
        } catch (e: Exception) {
            return ValidationResult.Invalid(2, "Invalid Base64 in signature: ${e.message}")
        }
        if (!verifySignature(canonical, signatureBytes, issuerKeyBytes)) {
            return ValidationResult.Invalid(2, "Ed25519 signature verification failed")
        }

        // ── CHECK 3: TTL con tolerancia clock drift ───────────────
        val ttlResult = TTLValidator.validate(
            expiryMs    = packet.expiry,
            toleranceMs = packet.clock_tolerance_ms
        )
        if (ttlResult is TTLValidator.TTLResult.Expired) {
            return ValidationResult.Invalid(3,
                "Mission TTL expired. Drift: ${ttlResult.driftMs}ms, " +
                "Tolerance: ${packet.clock_tolerance_ms}ms")
        }

        // ── CHECK 4: Device binding ───────────────────────────────
        if (packet.issued_to != devicePublicKey) {
            return ValidationResult.Invalid(4,
                "Mission not issued to this device. " +
                "Expected: ${packet.issued_to.take(16)}... " +
                "Got: ${devicePublicKey.take(16)}...")
        }

        // ── CHECK 5: GCM auth tag por asset ──────────────────────
        for (asset in packet.assets) {
            try {
                val ciphertext = Base64.getDecoder().decode(asset.payload)
                val nonce      = Base64.getDecoder().decode(asset.nonce)
                AESGCMManager.decrypt(AssetBlob(ciphertext, nonce), tenantKey)
            } catch (e: AEADBadTagException) {
                return ValidationResult.Invalid(5,
                    "Asset '${asset.id}' (${asset.label}) GCM tag invalid — tampered")
            } catch (e: Exception) {
                return ValidationResult.Invalid(5,
                    "Asset '${asset.id}' decryption failed: ${e.message}")
            }
        }

        // ── CHECK 6: Anti-replay ──────────────────────────────────
        if (packet.mission_id in existingIds) {
            return ValidationResult.Invalid(6,
                "Replay detected: mission_id '${packet.mission_id}' already processed")
        }

        return ValidationResult.Valid
    }

    // ── Helper: verificar firma Ed25519 ──────────────────────────
    private fun verifySignature(
        canonical: String,
        signatureBytes: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        // Manejo robusto: si la clave no es un formato válido → false (no crash)
        return try {
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val keySpec    = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            val pubKey     = keyFactory.generatePublic(keySpec)
            val sig        = java.security.Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pubKey)
            sig.update(canonical.toByteArray(Charsets.UTF_8))
            sig.verify(signatureBytes)
        } catch (e: Exception) { false }
    }
}
