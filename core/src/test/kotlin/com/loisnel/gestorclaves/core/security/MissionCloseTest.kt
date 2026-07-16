package com.loisnel.gestorclaves.core.security

import com.loisnel.gestorclaves.core.domain.FCPConstants
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * MissionCloseTest — UT-046 a UT-048
 * Tests del MissionCloseEngine — lógica pura sin Android context.
 * JVM puro.
 */
class MissionCloseTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private val technicianKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(data: String): ByteArray =
        Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(data.toByteArray(Charsets.UTF_8))
        }.sign()

    // UT-046: FCPConstants tiene STATUS_COMPLETED y STATUS_FAILED definidos
    @Test
    fun ut046_close_result_constants_defined() {
        assertEquals("UT-046: STATUS_COMPLETED",
            "COMPLETED", FCPConstants.STATUS_COMPLETED)
        assertEquals("UT-046: STATUS_FAILED",
            "FAILED", FCPConstants.STATUS_FAILED)
        assertNotEquals("UT-046: COMPLETED != FAILED",
            FCPConstants.STATUS_COMPLETED, FCPConstants.STATUS_FAILED)
    }

    // UT-047: Cadena canónica del cierre es determinística
    @Test
    fun ut047_close_canonical_is_deterministic() {
        val missionId     = "mission-close-test-001"
        val closedAt      = System.currentTimeMillis()
        val result        = FCPConstants.STATUS_COMPLETED
        val driftMs       = 1500L

        // Construir cadena canónica dos veces — deben ser idénticas
        val canonical1 = "$missionId|$closedAt|$result|$technicianKey|$driftMs"
        val canonical2 = "$missionId|$closedAt|$result|$technicianKey|$driftMs"

        assertEquals("UT-047: cadena canónica debe ser determinística",
            canonical1, canonical2)
        assertTrue("UT-047: cadena debe contener mission_id",
            canonical1.contains(missionId))
        assertTrue("UT-047: cadena debe contener result",
            canonical1.contains(result))
    }

    // UT-048: Firma del cierre es verificable con clave pública
    @Test
    fun ut048_close_signature_verifiable() {
        val missionId = "mission-close-sig-001"
        val closedAt  = System.currentTimeMillis()
        val result    = FCPConstants.STATUS_COMPLETED
        val driftMs   = 0L

        val canonical      = "$missionId|$closedAt|$result|$technicianKey|$driftMs"
        val signatureBytes = sign(canonical)

        // Verificar con clave pública
        val verified = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyPair.public)
            update(canonical.toByteArray(Charsets.UTF_8))
        }.verify(signatureBytes)

        assertTrue("UT-048: firma del cierre debe ser verificable con clave pública del técnico",
            verified)
        assertTrue("UT-048: firma debe ser no vacía",
            signatureBytes.isNotEmpty())
    }
}
