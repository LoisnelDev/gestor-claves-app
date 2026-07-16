package com.loisnel.gestorclaves.billing

/**
 * ProStatus — Estado de la licencia Pro del dispositivo
 *
 * NEVER-BLOCK POLICY (TAD-B v2.1.1 — Mejora 2):
 * Cuando la licencia expira, el estado es EXPIRED_ACCESS_PRESERVED.
 * Esto significa que los datos ya cargados SIGUEN siendo accesibles.
 * Solo se desactivan las funciones Pro para nuevas operaciones.
 *
 * En campo industrial, un técnico NUNCA debe perder acceso a sus
 * credenciales por un problema de licencia. La seguridad operativa
 * tiene prioridad sobre la gestión comercial.
 */
enum class ProStatus {
    /** Verificando estado de licencia en Play Store */
    CHECKING,

    /** Licencia Pro activa — todas las funciones disponibles */
    ACTIVE,

    /**
     * Licencia expirada — datos existentes ACCESIBLES.
     * Solo se bloquean nuevas emisiones de misiones y funciones Pro.
     * Los datos previamente cargados permanecen disponibles.
     *
     * NEVER-BLOCK: este estado garantiza acceso en campo incluso
     * si la suscripción expiró durante una operación crítica.
     */
    EXPIRED_ACCESS_PRESERVED,

    /** Sin licencia Pro — solo funciones Free disponibles */
    FREE,

    /** Error al verificar licencia — tratar como FREE por seguridad */
    ERROR
}
