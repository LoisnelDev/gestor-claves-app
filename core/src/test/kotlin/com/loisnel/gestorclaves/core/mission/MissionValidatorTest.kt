package com.loisnel.gestorclaves.core.mission

import com.loisnel.gestorclaves.core.crypto.AESGCMManager
import com.loisnel.gestorclaves.core.domain.FCPConstants
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature as JavaSig
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID

/**
 * MissionValidatorTest — UT-031 a UT-036
 * Tests de las 6 validaciones offline en orden.
 * JVM puro — sin emulador.
 */
class MissionValidatorTest {

    private val issuerKeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private val receiverKeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private val issuerPubB64   = Base64.getEncoder().encodeToString(issuerKeyPair.public.encoded)
    private val receiverPubB64 = Base64.getEncoder().encodeToString(receiverKeyPair.public.encoded)
    private val tenantKey      = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

    private fun buildValidPacket(
        missionId: String = UUID.randomUUID().toString(),
        expiryMs: Long = System.currentTimeMillis() + 3_600_000L,
        issuedTo: String = receiverPubB64,
        withRealEncryption: Boolean = true
    ): MissionPacket {
        val assetPayload = if (withRealEncryption) {
            val blob = AESGCMManager.encrypt("secret-credential", tenantKey)
            Pair(
                Base64.getEncoder().encodeToString(blob.ciphertext),
                Base64.getEncoder().encodeToString(blob.nonce)
            )
        } else {
            Pair(
                Base64.getEncoder().encodeToString("fake-payload".toByteArray()),
                Base64.getEncoder().encodeToString(ByteArray(12))
            )
        }

        val assets = listOf(MissionAsset(
            id      = UUID.randomUUID().toString(),
            label   = "Test Asset",
            payload = assetPayload.first,
            nonce   = assetPayload.second
        ))

        val canonical = MissionPacketBuilder.buildCanonicalString(
            missionId, "tenant-test", issuedTo, expiryMs, assets)
        val sig = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initSign(issuerKeyPair.private)
            update(canonical.toByteArray(Charsets.UTF_8))
        }.sign()

        return MissionPacket(
            version            = FCPConstants.MISSION_PACKET_VERSION,
            mission_id         = missionId,
            tenant_id          = "tenant-test",
            issued_by          = issuerPubB64,
            issued_to          = issuedTo,
            expiry             = expiryMs,
            clock_tolerance_ms = FCPConstants.DEFAULT_CLOCK_TOLERANCE_MS,
            assets             = assets,
            signature          = Base64.getEncoder().encodeToString(sig)
        )
    }

    // UT-031: Firma inválida rechazada (Check 2)
    @Test
    fun ut031_invalid_signature_rejected_check2() {
        val packet = buildValidPacket().copy(
            signature = Base64.getEncoder().encodeToString(ByteArray(64)) // Firma falsa
        )
        val result = MissionValidator.validate(packet, receiverPubB64, tenantKey, emptySet())
        assertTrue("UT-031: firma inválida debe ser rechazada",
            result is MissionValidator.ValidationResult.Invalid)
        assertEquals("UT-031: debe fallar en Check 2",
            2, (result as MissionValidator.ValidationResult.Invalid).check)
    }

    // UT-032: TTL expirado rechazado (Check 3)
    @Test
    fun ut032_expired_ttl_rejected_check3() {
        // Paquete con TTL expirado — sin tolerancia
        val expiredPacket = buildValidPacket(
            expiryMs = System.currentTimeMillis() - 3_600_000L // 1 hora expirado
        ).let { p ->
            // Re-firmar con el TTL expirado para que la firma sea válida
            val assets    = p.assets
            val canonical = MissionPacketBuilder.buildCanonicalString(
                p.mission_id, p.tenant_id, p.issued_to, p.expiry, assets)
            val sig = java.security.Signature.getInstance("SHA256withECDSA").apply {
                initSign(issuerKeyPair.private)
                update(canonical.toByteArray(Charsets.UTF_8))
            }.sign()
            p.copy(
                clock_tolerance_ms = 0L, // Sin tolerancia
                signature = Base64.getEncoder().encodeToString(sig)
            )
        }
        val result = MissionValidator.validate(expiredPacket, receiverPubB64, tenantKey, emptySet())
        assertTrue("UT-032: TTL expirado debe ser rechazado",
            result is MissionValidator.ValidationResult.Invalid)
        assertEquals("UT-032: debe fallar en Check 3",
            3, (result as MissionValidator.ValidationResult.Invalid).check)
    }

    // UT-033: TTL dentro de tolerancia aceptado (Check 3 con drift)
    @Test
    fun ut033_ttl_within_tolerance_accepted() {
        val justExpiredMs = System.currentTimeMillis() - 60_000L // 1 min expirado
        val packet = buildValidPacket(expiryMs = justExpiredMs).let { p ->
            val canonical = MissionPacketBuilder.buildCanonicalString(
                p.mission_id, p.tenant_id, p.issued_to, p.expiry, p.assets)
            val sig = java.security.Signature.getInstance("SHA256withECDSA").apply {
                initSign(issuerKeyPair.private)
                update(canonical.toByteArray(Charsets.UTF_8))
            }.sign()
            p.copy(
                clock_tolerance_ms = 300_000L, // 5 min tolerancia
                signature = Base64.getEncoder().encodeToString(sig)
            )
        }
        val result = MissionValidator.validate(packet, receiverPubB64, tenantKey, emptySet())
        assertTrue("UT-033: TTL dentro de tolerancia debe aceptarse",
            result is MissionValidator.ValidationResult.Valid)
    }

    // UT-034: Device binding incorrecto rechazado (Check 4)
    @Test
    fun ut034_wrong_device_binding_rejected_check4() {
        val packet    = buildValidPacket()
        val wrongDevice = Base64.getEncoder().encodeToString(
            KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair().public.encoded
        )
        val result = MissionValidator.validate(packet, wrongDevice, tenantKey, emptySet())
        assertTrue("UT-034: device incorrecto debe ser rechazado",
            result is MissionValidator.ValidationResult.Invalid)
        assertEquals("UT-034: debe fallar en Check 4",
            4, (result as MissionValidator.ValidationResult.Invalid).check)
    }

    // UT-035: Anti-replay — mission_id duplicado rechazado (Check 6)
    @Test
    fun ut035_duplicate_mission_id_rejected_check6() {
        val missionId   = UUID.randomUUID().toString()
        val packet      = buildValidPacket(missionId = missionId)
        val existingIds = setOf(missionId) // Ya procesado
        val result      = MissionValidator.validate(packet, receiverPubB64, tenantKey, existingIds)
        assertTrue("UT-035: mission_id duplicado debe ser rechazado",
            result is MissionValidator.ValidationResult.Invalid)
        assertEquals("UT-035: debe fallar en Check 6",
            6, (result as MissionValidator.ValidationResult.Invalid).check)
    }

    // UT-036: Paquete válido pasa las 6 validaciones
    @Test
    fun ut036_valid_packet_passes_all_6_checks() {
        val packet = buildValidPacket(withRealEncryption = true)
        val result = MissionValidator.validate(packet, receiverPubB64, tenantKey, emptySet())
        assertTrue("UT-036: paquete válido debe pasar todas las validaciones",
            result is MissionValidator.ValidationResult.Valid)
    }
}
