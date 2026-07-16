package com.loisnel.gestorclaves.core.mission

import com.loisnel.gestorclaves.core.domain.FCPConstants
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID

/**
 * MissionPacketTest — UT-027 a UT-030
 * Tests de serialización y construcción del MissionPacket.
 * JVM puro — sin emulador.
 */
class MissionPacketTest {

    // Par de claves EC para tests (proxy de Ed25519 en JVM)
    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private val issuerPubKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
    private val receiverPubKey = Base64.getEncoder().encodeToString(
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair().public.encoded
    )

    private fun buildTestPacket(): MissionPacket {
        val asset = MissionAsset(
            id      = UUID.randomUUID().toString(),
            label   = "Router-Principal",
            payload = Base64.getEncoder().encodeToString("encrypted-data".toByteArray()),
            nonce   = Base64.getEncoder().encodeToString(ByteArray(12))
        )
        val missionId = UUID.randomUUID().toString()
        val canonical = MissionPacketBuilder.buildCanonicalString(
            missionId = missionId,
            tenantId  = "tenant-test",
            issuedTo  = receiverPubKey,
            expiryMs  = System.currentTimeMillis() + 3_600_000L,
            assets    = listOf(asset)
        )
        val sig = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(canonical.toByteArray(Charsets.UTF_8))
        }.sign()

        return MissionPacket(
            version            = FCPConstants.MISSION_PACKET_VERSION,
            mission_id         = missionId,
            tenant_id          = "tenant-test",
            issued_by          = issuerPubKey,
            issued_to          = receiverPubKey,
            expiry             = System.currentTimeMillis() + 3_600_000L,
            clock_tolerance_ms = FCPConstants.DEFAULT_CLOCK_TOLERANCE_MS,
            assets             = listOf(asset),
            signature          = Base64.getEncoder().encodeToString(sig)
        )
    }

    // UT-027: Serialización a JSON contiene todos los campos
    @Test
    fun ut027_packet_serializes_to_json_with_all_fields() {
        val packet = buildTestPacket()
        val json   = MissionSerializer.toJson(packet)

        assertTrue("UT-027: JSON debe contener version",    json.contains("\"version\""))
        assertTrue("UT-027: JSON debe contener mission_id", json.contains("\"mission_id\""))
        assertTrue("UT-027: JSON debe contener tenant_id",  json.contains("\"tenant_id\""))
        assertTrue("UT-027: JSON debe contener issued_by",  json.contains("\"issued_by\""))
        assertTrue("UT-027: JSON debe contener issued_to",  json.contains("\"issued_to\""))
        assertTrue("UT-027: JSON debe contener expiry",     json.contains("\"expiry\""))
        assertTrue("UT-027: JSON debe contener assets",     json.contains("\"assets\""))
        assertTrue("UT-027: JSON debe contener signature",  json.contains("\"signature\""))
        assertTrue("UT-027: JSON debe contener clock_tolerance_ms",
            json.contains("\"clock_tolerance_ms\""))
    }

    // UT-028: Deserialización round-trip produce el mismo paquete
    @Test
    fun ut028_packet_deserializes_roundtrip() {
        val original     = buildTestPacket()
        val json         = MissionSerializer.toJson(original)
        val deserialized = MissionSerializer.fromJson(json)

        assertEquals("UT-028: version", original.version, deserialized.version)
        assertEquals("UT-028: mission_id", original.mission_id, deserialized.mission_id)
        assertEquals("UT-028: tenant_id", original.tenant_id, deserialized.tenant_id)
        assertEquals("UT-028: issued_by", original.issued_by, deserialized.issued_by)
        assertEquals("UT-028: issued_to", original.issued_to, deserialized.issued_to)
        assertEquals("UT-028: expiry", original.expiry, deserialized.expiry)
        assertEquals("UT-028: clock_tolerance_ms",
            original.clock_tolerance_ms, deserialized.clock_tolerance_ms)
        assertEquals("UT-028: assets count",
            original.assets.size, deserialized.assets.size)
        assertEquals("UT-028: signature", original.signature, deserialized.signature)
    }

    // UT-029: Cadena canónica es determinística (mismo input → mismo output)
    @Test
    fun ut029_canonical_string_is_deterministic() {
        val missionId = UUID.randomUUID().toString()
        val assets    = listOf(MissionAsset(
            id = "asset-1", label = "Router",
            payload = "ciphertext", nonce = "nonce123"
        ))
        val expiry = System.currentTimeMillis() + 3_600_000L

        val canonical1 = MissionPacketBuilder.buildCanonicalString(
            missionId, "tenant-A", receiverPubKey, expiry, assets)
        val canonical2 = MissionPacketBuilder.buildCanonicalString(
            missionId, "tenant-A", receiverPubKey, expiry, assets)

        assertEquals("UT-029: cadena canónica debe ser determinística", canonical1, canonical2)
    }

    // UT-030: Versión incorrecta rechazada en Check 1
    @Test
    fun ut030_wrong_version_rejected_in_check1() {
        val packet      = buildTestPacket().copy(version = "0.9") // Downgrade
        val tenantKey   = ByteArray(32)
        val result      = MissionValidator.validate(packet, receiverPubKey, tenantKey, emptySet())

        assertTrue("UT-030: version 0.9 debe ser inválido",
            result is MissionValidator.ValidationResult.Invalid)
        assertEquals("UT-030: debe fallar en Check 1",
            1, (result as MissionValidator.ValidationResult.Invalid).check)
    }
}
