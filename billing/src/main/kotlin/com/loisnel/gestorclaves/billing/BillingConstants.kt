package com.loisnel.gestorclaves.billing

/**
 * BillingConstants — Identificadores de productos en Google Play Console
 *
 * Estos IDs deben coincidir EXACTAMENTE con los configurados en:
 * Play Console → Monetización → Productos In-App
 *
 * TAD-B v2.1.1 — Sección 15
 */
object BillingConstants {

    // ── Productos In-App (pago único — lifetime) ──────────────────
    /** Licencia Pro lifetime para contratistas individuales ($49 USD) */
    const val PRODUCT_PRO_LIFETIME = "fcp_pro_lifetime"

    /** Licencia Enterprise para empresas 11-100 técnicos ($299 USD/año) */
    const val PRODUCT_ENTERPRISE_YEARLY = "fcp_enterprise_yearly"

    // ── Preferencias locales (EncryptedSharedPreferences) ─────────
    /** Nombre del archivo de preferencias de billing */
    const val BILLING_PREFS_NAME = "fcp_billing_prefs"

    /** Key para el estado Pro persistido localmente */
    const val PREF_IS_PRO = "is_pro"

    /** Key para el tipo de licencia activa */
    const val PREF_LICENSE_TYPE = "license_type"

    /** Key para el timestamp de expiración (0 = lifetime) */
    const val PREF_EXPIRY_TIMESTAMP = "expiry_timestamp"

    // ── Tipos de licencia ─────────────────────────────────────────
    const val LICENSE_NONE       = "NONE"
    const val LICENSE_PRO        = "PRO_LIFETIME"
    const val LICENSE_ENTERPRISE = "ENTERPRISE_YEARLY"
}
