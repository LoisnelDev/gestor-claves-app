package com.loisnel.gestorclaves

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * ClipboardUtils — Borrado automático del portapapeles via WorkManager.
 *
 * Por qué WorkManager y no Handler.postDelayed():
 *   Handler vive en el MainLooper → Doze Mode lo pausa indefinidamente.
 *
 * Por qué WorkManager y no AlarmManager.setExactAndAllowWhileIdle():
 *   Requiere SCHEDULE_EXACT_ALARM (API 31+) — solo concedido a apps de
 *   alarma/calendario. Un gestor de contraseñas no califica → Play Store
 *   puede rechazar o pedir justificación. Además, lanza SecurityException
 *   si el usuario no activa el permiso en Ajustes → crash en campo.
 *
 * WorkManager usa JobScheduler internamente → exento de Doze Mode
 * para trabajos con delay corto. Sin permisos especiales. Play Store safe.
 *
 * Precisión: ~30s ± 15s en condiciones normales. Suficiente para
 * borrado de portapapeles — el objetivo es garantizar que ocurra,
 * no precisión de milisegundos.
 *
 * Cumplimiento: CWE-312 · OWASP MSTG-STORAGE-10
 */
object ClipboardUtils {

    private const val WORK_TAG        = "clipboard_clear"
    private const val CLEAR_DELAY_SEC = 30L

    /**
     * Programa el borrado del portapapeles en ~30 segundos.
     *
     * Llamar en el contexto de la Activity (no Composable) después de
     * cada clipboard.setText(). ExistingWorkPolicy.REPLACE garantiza
     * que cada copia reinicia el temporizador de 30s.
     *
     * @param context Activity context (para WorkManager.getInstance)
     */
    fun scheduleClear(context: Context) {
        val request = OneTimeWorkRequestBuilder<ClipboardClearWorker>()
            .setInitialDelay(CLEAR_DELAY_SEC, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_TAG,
            ExistingWorkPolicy.REPLACE, // Reinicia el timer en cada copia
            request
        )
    }

    /**
     * Limpia el portapapeles INMEDIATAMENTE y cancela el trabajo pendiente.
     * Llamar en onStop() — borrado instantáneo al salir de la app.
     *
     * @param context Activity context
     */
    fun clearNow(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? ClipboardManager ?: return
        clipboard.clearPrimaryClip()
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
