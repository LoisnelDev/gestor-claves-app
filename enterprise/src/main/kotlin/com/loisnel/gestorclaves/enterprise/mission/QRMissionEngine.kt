package com.loisnel.gestorclaves.enterprise.mission

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.loisnel.gestorclaves.core.mission.MissionPacket
import com.loisnel.gestorclaves.core.mission.MissionSerializer

/**
 * QRMissionEngine — Generación y decodificación de QR para paquetes de misión
 *
 * ARQUITECTURA: este componente está en :enterprise (no en :core)
 * porque depende de Android Bitmap y ZXing (hardware).
 * La lógica de validación del paquete reside en :core (MissionValidator).
 *
 * Nivel de corrección de error: H (máximo — 30% de recuperación)
 * Necesario para uso en entornos industriales con pantallas sucias o dañadas.
 *
 * TAD-B v2.1.1 — Sección 10
 */
object QRMissionEngine {

    /**
     * Genera un Bitmap QR desde un [MissionPacket].
     * El QR contiene el JSON completo del paquete firmado.
     *
     * @param packet  El paquete de misión a codificar
     * @param sizePx  Tamaño del QR en píxeles (default 512px)
     * @return [Bitmap] del QR listo para mostrar en pantalla
     * @throws WriterException si el QR no puede generarse
     */
    fun generateQRBitmap(packet: MissionPacket, sizePx: Int = 512): Bitmap {
        val json = MissionSerializer.toJson(packet)
        return generateQRFromString(json, sizePx)
    }

    /**
     * Genera un Bitmap QR desde cualquier string (para QR de cierre también).
     */
    fun generateQRFromString(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET    to "UTF-8",
            EncodeHintType.MARGIN           to 1
        )
        val writer = QRCodeWriter()
        val matrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        return bitMatrixToBitmap(matrix)
    }

    /**
     * Convierte un [MissionPacket] a JSON string para transferencia NFC.
     * El contenido es idéntico al QR — el canal de transferencia varía.
     */
    fun serializeForNFC(packet: MissionPacket): ByteArray =
        MissionSerializer.toJson(packet).toByteArray(Charsets.UTF_8)

    /**
     * Deserializa un [MissionPacket] recibido por NFC.
     */
    fun deserializeFromNFC(bytes: ByteArray): MissionPacket =
        MissionSerializer.fromJson(bytes.toString(Charsets.UTF_8))

    private fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val width  = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Alto contraste: negro puro / blanco puro para uso industrial
                pixels[y * width + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
