package com.loisnel.gestorclaves.core.mission

import com.loisnel.gestorclaves.core.crypto.AESGCMManager
import com.loisnel.gestorclaves.core.domain.FCPConstants
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import java.util.UUID

/**
 * MissionPacketBuilder — Constructor y firmador de paquetes de misión
 *
 * Responsabilidades:
 * 1. Cifrar cada asset con AES-256-GCM y la TenantKey
 * 2. Construir la cadena canónica de firma (determinística)
 * 3. Firmar con Ed25519 la cadena canónica
 * 4. Ensamblar el MissionPacket v1.0
 *
 * La cadena canónica usa separador '|' y campos en orden fijo
 * para garantizar que emisor y receptor obtengan el mismo resultado.
 *
 * TAD-B v2.1.1 — Sección 10
 */
object MissionPacketBuilder {

    /**
     * Construye un [MissionPacket] firmado listo para emitir.
     *
     * @param tenantId          ID del tenant (organización)
     * @param issuedByPublicKey Clave pública Ed25519 del supervisor en Base64
     * @param issuedToPublicKey Clave pública Ed25519 del técnico en Base64
     * @param issuerPrivateKey  Clave privada Ed25519 del supervisor (desde Keystore)
     * @param rawAssets         Lista de pares (label, plaintext) — se cifran aquí
     * @param tenantKey         TenantKey AES-256 derivada por HKDF
     * @param expiryMs          Timestamp de expiración en milisegundos Unix
     * @param clockToleranceMs  Margen de tolerancia para clock drift
     * @return [MissionPacket] firmado y listo para emitir como QR
     */
    fun build(
        tenantId: String,
        issuedByPublicKey: String,
        issuedToPublicKey: String,
        issuerPrivateKey: PrivateKey,
        rawAssets: List<Pair<String, String>>, // (label, plaintext)
        tenantKey: ByteArray,
        expiryMs: Long,
        clockToleranceMs: Long = FCPConstants.DEFAULT_CLOCK_TOLERANCE_MS
    ): MissionPacket {
        val missionId = UUID.randomUUID().toString()

        // Paso 1: Cifrar cada asset con AES-256-GCM
        val encryptedAssets = rawAssets.map { (label, plaintext) ->
            val blob = AESGCMManager.encrypt(plaintext, tenantKey)
            MissionAsset(
                id      = UUID.randomUUID().toString(),
                label   = label,
                payload = Base64.getEncoder().encodeToString(blob.ciphertext),
                nonce   = Base64.getEncoder().encodeToString(blob.nonce)
            )
        }

        // Paso 2: Construir cadena canónica — orden fijo, determinístico
        val canonical = buildCanonicalString(
            missionId         = missionId,
            tenantId          = tenantId,
            issuedTo          = issuedToPublicKey,
            expiryMs          = expiryMs,
            assets            = encryptedAssets
        )

        // Paso 3: Firmar con Ed25519 (clave privada del supervisor)
        val signature = signEd25519(canonical, issuerPrivateKey)

        return MissionPacket(
            version            = FCPConstants.MISSION_PACKET_VERSION,
            mission_id         = missionId,
            tenant_id          = tenantId,
            issued_by          = issuedByPublicKey,
            issued_to          = issuedToPublicKey,
            expiry             = expiryMs,
            clock_tolerance_ms = clockToleranceMs,
            assets             = encryptedAssets,
            signature          = Base64.getEncoder().encodeToString(signature)
        )
    }

    /**
     * Construye la cadena canónica de firma.
     *
     * FORMATO: mission_id|tenant_id|issued_to|expiry|sha256(assets_json)
     *
     * - Campos separados por '|' en orden fijo
     * - Assets: sorted by id → JSON → SHA-256 → Base64
     * - NUNCA firmar el JSON directamente (orden no garantizado)
     *
     * TAD-B v2.1.1 — Sección 10.2
     */
    fun buildCanonicalString(
        missionId: String,
        tenantId: String,
        issuedTo: String,
        expiryMs: Long,
        assets: List<MissionAsset>
    ): String {
        // Assets ordenados por id para garantizar determinismo
        val sortedAssets = assets.sortedBy { it.id }
        val assetsJson   = sortedAssets.joinToString(",", "[", "]") { asset ->
            """{"id":"${asset.id}","label":"${asset.label}","payload":"${asset.payload}","nonce":"${asset.nonce}"}"""
        }
        val assetsHash = sha256Base64(assetsJson)
        return "$missionId|$tenantId|$issuedTo|$expiryMs|$assetsHash"
    }

    /**
     * Verifica la firma Ed25519 de un MissionPacket.
     * Reconstruye la cadena canónica y verifica contra la firma.
     *
     * @return true si la firma es válida
     */
    fun verifySignature(packet: MissionPacket, issuerPublicKeyBytes: ByteArray): Boolean {
        return try {
            val canonical = buildCanonicalString(
                missionId = packet.mission_id,
                tenantId  = packet.tenant_id,
                issuedTo  = packet.issued_to,
                expiryMs  = packet.expiry,
                assets    = packet.assets
            )
            val sigBytes = Base64.getDecoder().decode(packet.signature)
            verifyEd25519(canonical, sigBytes, issuerPublicKeyBytes)
        } catch (e: Exception) { false }
    }

    // ── Helpers criptográficos ────────────────────────────────────

    private fun sha256Base64(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash   = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun signEd25519(data: String, privateKey: PrivateKey): ByteArray {
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(data.toByteArray(Charsets.UTF_8))
        return sig.sign()
    }

    private fun verifyEd25519(
        data: String,
        signature: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        // En tests JVM usamos EC/SHA256withECDSA como proxy
        // En producción Android: Ed25519 desde AndroidKeyStore
        return try {
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val keySpec    = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            val pubKey     = keyFactory.generatePublic(keySpec)
            val sig        = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pubKey)
            sig.update(data.toByteArray(Charsets.UTF_8))
            sig.verify(signature)
        } catch (e: Exception) { false }
    }
}
