package com.loisnel.gestorclaves.enterprise.mission

import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.loisnel.gestorclaves.enterprise.mission.QRMissionEngine

/**
 * MissionCloseEngine — Generación del QR/NFC de Cierre
 *
 * El Acuse de Recibo Criptográfico convierte a FCP en un sistema
 * de auditoría legalmente vinculante. El flujo de trabajo NO se
 * considera finalizado hasta que el supervisor escanea este QR.
 *
 * Campos v2.1.1 incluidos en el cierre:
 * - device_model: identifica HW del técnico para diagnóstico
 * - os_version: identifica Android del técnico
 * - clock_drift_ms: drift registrado durante la validación TTL
 *
 * TAD-B v2.1.1 — Sección 12
 */
object MissionCloseEngine {

    /**
     * Genera el JSON del QR de cierre con todos los campos v2.1.1.
     *
     * @param missionId       ID de la misión a cerrar
     * @param result          "COMPLETED" o "FAILED"
     * @param notes           Notas opcionales del técnico
     * @param technicianKey   Clave pública Ed25519 del técnico en Base64
     * @param technicianSign  Función de firma — recibe canonical string y retorna bytes
     * @param lastClockDriftMs Último drift registrado por TTLValidator
     * @return JSON string listo para codificar en QR o enviar por NFC
     */
    fun generateCloseJson(
        missionId: String,
        result: String,
        notes: String = "",
        technicianKey: String,
        technicianSign: (String) -> ByteArray,
        lastClockDriftMs: Long = 0L
    ): String {
        require(result == "COMPLETED" || result == "FAILED") {
            "result debe ser COMPLETED o FAILED. Recibido: $result"
        }

        val closedAt   = System.currentTimeMillis()
        val deviceModel = Build.MODEL
        val osVersion   = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        // Cadena canónica para firma — campos en orden fijo
        val canonical = buildCloseCanonical(
            missionId     = missionId,
            closedAt      = closedAt,
            result        = result,
            technicianKey = technicianKey,
            driftMs       = lastClockDriftMs
        )

        val signatureBytes = technicianSign(canonical)
        val signature      = java.util.Base64.getEncoder().encodeToString(signatureBytes)

        // Construir JSON del cierre
        return buildCloseJson(
            missionId    = missionId,
            closedAt     = closedAt,
            result       = result,
            notes        = notes.replace("\"", "\\\""),
            deviceModel  = deviceModel,
            osVersion    = osVersion,
            driftMs      = lastClockDriftMs,
            technicianKey = technicianKey,
            signature    = signature
        )
    }

    /**
     * Genera el QR de cierre como JSON string (para codificar con ZXing).
     * Wrapper que llama a generateCloseJson y genera el bitmap.
     */
    fun generateCloseQRContent(
        missionId: String,
        result: String,
        notes: String = "",
        technicianKey: String,
        technicianSign: (String) -> ByteArray,
        lastClockDriftMs: Long = 0L,
        sizePx: Int = 512
    ): android.graphics.Bitmap {
        val json = generateCloseJson(
            missionId        = missionId,
            result           = result,
            notes            = notes,
            technicianKey    = technicianKey,
            technicianSign   = technicianSign,
            lastClockDriftMs = lastClockDriftMs
        )
        return QRMissionEngine.generateQRFromString(json, sizePx)
    }

    /**
     * Cadena canónica del cierre — determinística para verificación
     */
    fun buildCloseCanonical(
        missionId: String,
        closedAt: Long,
        result: String,
        technicianKey: String,
        driftMs: Long
    ): String = "$missionId|$closedAt|$result|$technicianKey|$driftMs"

    private fun buildCloseJson(
        missionId: String,
        closedAt: Long,
        result: String,
        notes: String,
        deviceModel: String,
        osVersion: String,
        driftMs: Long,
        technicianKey: String,
        signature: String
    ): String = """{"mission_id":"$missionId","closed_at":$closedAt,"result":"$result","notes":"$notes","device_model":"${deviceModel.replace("\"","\\\"")}","os_version":"${osVersion.replace("\"","\\\"")}","clock_drift_ms":$driftMs,"technician_key":"$technicianKey","signature":"$signature"}"""
}
