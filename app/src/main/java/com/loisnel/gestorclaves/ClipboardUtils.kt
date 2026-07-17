package com.loisnel.gestorclaves

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * ClipboardUtils — Gestión segura del portapapeles
 *
 * Copia datos al portapapeles y los borra automáticamente después de 30 segundos.
 * Mitiga la exposición de credenciales a apps de terceros que leen el portapapeles.
 *
 * Cumplimiento: CWE-312 (Cleartext Storage of Sensitive Information)
 * Estándar: OWASP MSTG-STORAGE-10
 */
object ClipboardUtils {

    private const val CLEAR_DELAY_MS = 30_000L // 30 segundos
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Copia texto al portapapeles y programa su borrado en 30 segundos.
     *
     * @param context ApplicationContext
     * @param label   Etiqueta legible del contenido (ej. "Contraseña")
     * @param text    Texto a copiar — se borrará automáticamente
     */
    fun copyWithAutoClear(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager

        // Copiar al portapapeles
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

        // Cancelar cualquier borrado pendiente anterior
        handler.removeCallbacksAndMessages(null)

        // Programar borrado automático en 30 segundos
        handler.postDelayed({
            clearClipboard(context)
        }, CLEAR_DELAY_MS)
    }

    /**
     * Borra el portapapeles inmediatamente.
     * Llamar en onPause() o cuando el usuario sale de la app.
     */
    fun clearClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
        // Sobreescribir con string vacío — el contenido sensible ya no es recuperable
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        handler.removeCallbacksAndMessages(null)
    }
}
