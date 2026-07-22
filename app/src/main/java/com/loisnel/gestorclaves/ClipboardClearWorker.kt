package com.loisnel.gestorclaves

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * ClipboardClearWorker — Limpia el portapapeles via WorkManager.
 *
 * Ejecutado por WorkManager ~30 segundos después de copiar una credencial.
 * Usa JobScheduler internamente — exento de Doze Mode para delays cortos.
 * No requiere SCHEDULE_EXACT_ALARM ni ningún permiso especial.
 *
 * Accede al ClipboardManager nativo (android.content), que comparte
 * el mismo portapapeles del sistema que LocalClipboardManager (Compose).
 */
class ClipboardClearWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val clipboard = applicationContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            // Doble limpieza — máxima compatibilidad cross-device y OEM
            clipboard?.clearPrimaryClip()
            clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
            Result.success()
        } catch (e: Exception) {
            // No crashear si clipboard no disponible (pantalla bloqueada en algunos OEM)
            Result.failure()
        }
    }
}
