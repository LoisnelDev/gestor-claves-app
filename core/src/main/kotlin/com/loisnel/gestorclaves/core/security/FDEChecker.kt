package com.loisnel.gestorclaves.core.security

import android.app.admin.DevicePolicyManager
import android.content.Context

/**
 * FDEChecker — Verificación de cifrado de disco en runtime
 *
 * En entornos industriales de gama baja es posible encontrar
 * dispositivos sin FDE (Full Disk Encryption). Aunque Android API 28+
 * lo habilita por defecto, es responsabilidad de la app verificarlo
 * y advertir al usuario si el dispositivo no cumple el requisito.
 *
 * Los BLOBs de assets ya están cifrados con AES-256-GCM, por lo que
 * la ausencia de FDE no expone credenciales directamente. Sin embargo,
 * el audit trail (MissionLog) incluye metadatos que podrían ser
 * sensibles en un dispositivo sin FDE.
 *
 * TAD-B v2.1.1 — Sección 8
 */
object FDEChecker {

    enum class FDEStatus {
        /** Disco cifrado — configuración óptima */
        ENCRYPTED,
        /** Disco no cifrado — mostrar advertencia prominente en UI */
        NOT_ENCRYPTED,
        /** Estado desconocido — tratar como NOT_ENCRYPTED por precaución */
        UNKNOWN
    }

    /**
     * Verifica el estado de cifrado del almacenamiento del dispositivo.
     * Debe llamarse en el startup de la app Enterprise.
     *
     * @param context ApplicationContext
     * @return [FDEStatus] del dispositivo
     */
    fun check(context: Context): FDEStatus {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? DevicePolicyManager
            ?: return FDEStatus.UNKNOWN

        return when (dpm.storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ->
                FDEStatus.ENCRYPTED

            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE ->
                FDEStatus.NOT_ENCRYPTED

            else -> FDEStatus.UNKNOWN
        }
    }

    /** Retorna true si el dispositivo tiene FDE activo */
    fun isEncrypted(context: Context): Boolean =
        check(context) == FDEStatus.ENCRYPTED
}
