package com.loisnel.gestorclaves.core.domain

/**
 * UserRole — Roles del sistema RBAC Industrial
 *
 * Flujo de autoridad delegada descendente:
 *   DIRECTOR → SUPERVISOR → TECHNICIAN
 *
 * Cada rol solo puede delegar capacidades que él mismo posee.
 * El enforcement reside en la capa Repository (:core), NO en la UI.
 *
 * TAD-B v2.1.1 — Sección 4
 */
enum class UserRole {
    /** Visibilidad ejecutiva — KPIs y reportes agregados. Sin operación directa. */
    DIRECTOR,

    /** Emite misiones, audita resultados, gestiona técnicos de su cuadrilla. */
    SUPERVISOR,

    /** Ejecuta misiones offline, genera acuse de recibo criptográfico de cierre. */
    TECHNICIAN
}
