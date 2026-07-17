package com.loisnel.gestorclaves.core.security

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey

/**
 * DetectorSecurityLevel — Detecta el nivel de seguridad del hardware criptográfico
 *
 * Distingue entre tres niveles de protección disponibles en los dispositivos Android:
 *
 * STRONGBOX: Chip de seguridad dedicado (SE — Secure Element). Equivalente al
 *   Secure Enclave de iOS. Resistente a ataques físicos sofisticados (voltage
 *   glitching, side-channel). Disponible en gama alta: Pixel 6+, Samsung S22+.
 *
 * TEE_HARDWARE: Trusted Execution Environment basado en hardware integrado
 *   en el SoC. Protege contra software, resistencia física limitada. La mayoría
 *   de dispositivos gama media: Moto G84, Samsung A series.
 *
 * TEE_SOFTWARE: TEE basado en software — nivel mínimo. Suficiente para la
 *   mayoría de amenazas en campo industrial estándar.
 *
 * El nivel se incluye en la telemetría BI para que el Director pueda auditar
 * el parque de hardware de sus técnicos e identificar dispositivos en riesgo.
 *
 * TAD-B v3.0 — ORION Platform — Observabilidad de Hardware
 */
object DetectorSecurityLevel {

    /**
     * Nivel de seguridad del hardware criptográfico del nodo.
     * Orden de seguridad: STRONGBOX > TEE_HARDWARE > TEE_SOFTWARE > UNKNOWN
     */
    enum class NodeSecurityLevel(val displayName: String, val isHardwareBacked: Boolean) {
        /** Chip de seguridad dedicado — máxima protección */
        STRONGBOX("StrongBox (Chip dedicado)", isHardwareBacked = true),

        /** TEE integrado en SoC — protección hardware estándar */
        TEE_HARDWARE("TEE (Hardware)", isHardwareBacked = true),

        /** TEE basado en software — protección mínima */
        TEE_SOFTWARE("TEE (Software)", isHardwareBacked = false),

        /** No se pudo determinar — tratar como TEE_SOFTWARE por precaución */
        UNKNOWN("Desconocido", isHardwareBacked = false)
    }

    private const val KEYSTORE_ALIAS = "fcp_device_identity_v1"

    /**
     * Determina el nivel de seguridad del nodo actual.
     * Lee el nivel desde la clave Ed25519 almacenada en Android Keystore.
     *
     * Compatibilidad:
     *  - API 28-30: KeyInfo.isInsideSecureHardware() — TEE vs Software
     *  - API 31+:   KeyInfo.getSecurityLevel() — StrongBox vs TEE vs Software
     *
     * @return [NodeSecurityLevel] del hardware de este dispositivo
     */
    fun detect(): NodeSecurityLevel {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = ks.getKey(KEYSTORE_ALIAS, null) as? PrivateKey
                ?: return NodeSecurityLevel.UNKNOWN

            val keyFactory = KeyFactory.getInstance("EC", "AndroidKeyStore")
            val keyInfo    = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: distingue StrongBox de TEE hardware
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX ->
                        NodeSecurityLevel.STRONGBOX

                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ->
                        NodeSecurityLevel.TEE_HARDWARE

                    KeyProperties.SECURITY_LEVEL_SOFTWARE ->
                        NodeSecurityLevel.TEE_SOFTWARE

                    else -> NodeSecurityLevel.UNKNOWN
                }
            } else {
                // API 28-30: solo distingue hardware vs software
                if (keyInfo.isInsideSecureHardware) NodeSecurityLevel.TEE_HARDWARE
                else NodeSecurityLevel.TEE_SOFTWARE
            }
        } catch (e: Exception) {
            NodeSecurityLevel.UNKNOWN
        }
    }

    /**
     * Retorna true si el nodo tiene respaldo de hardware (TEE o StrongBox).
     * Clave privada no puede ser extraída por software, ni siquiera con root.
     */
    fun isHardwareBacked(): Boolean = detect().isHardwareBacked

    /**
     * Retorna true si el nodo tiene StrongBox (chip dedicado).
     * Usado para el campo strongbox_available en telemetría BI.
     */
    fun hasStrongBox(): Boolean = detect() == NodeSecurityLevel.STRONGBOX
}
