package com.loisnel.gestorclaves.billing

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BillingManager — Wrapper de Google Play Billing Library
 *
 * INVARIANTES ARQUITECTÓNICAS (TAD-B v2.1.1 — Mejora 2):
 *
 * 1. BillingManager NO tiene acceso a ningún DAO de Room.
 *    Los datos de misiones y assets son inaccesibles desde aquí.
 *    Esto garantiza que una expiración de licencia NUNCA puede
 *    ejecutar operaciones destructivas sobre los datos del técnico.
 *
 * 2. NEVER-BLOCK POLICY: onLicenseExpired() solo cambia el
 *    StateFlow<ProStatus>. Nunca llama a delete(), clear() o lock().
 *    La UI se adapta (oculta botones Pro). Los datos NO se tocan.
 *
 * 3. El estado Pro se persiste localmente en SharedPreferences
 *    (NO en EncryptedSharedPreferences — no es dato sensible).
 *    Esto permite verificar el estado sin conexión.
 *
 * Verificación arquitectónica: UT-039 verifica por reflection
 * que esta clase NO tiene campos de tipo DAO.
 */
class BillingManager(
    private val context: Context
    // NOTA: NO hay parámetro de DAO aquí — invariante arquitectónica
    // Si alguien intenta agregar un DAO aquí → UT-039 fallará
) : PurchasesUpdatedListener {

    private val _proStatus = MutableStateFlow(ProStatus.CHECKING)
    val proStatus: StateFlow<ProStatus> = _proStatus.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val prefs = context.getSharedPreferences(
        BillingConstants.BILLING_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Inicializa el BillingManager.
     * Primero carga el estado local (funciona offline),
     * luego verifica con Play Store si hay conexión.
     */
    fun initialize() {
        // Paso 1: Cargar estado local — funciona offline
        loadLocalStatus()

        // Paso 2: Conectar con Play Store para verificar
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases()
                }
                // Si falla la conexión → mantenemos el estado local (never-block)
            }
            override fun onBillingServiceDisconnected() {
                // No hacer nada — el estado local ya está cargado
            }
        })
    }

    /**
     * Carga el estado Pro desde SharedPreferences locales.
     * Funciona completamente offline — crítico para campo industrial.
     */
    private fun loadLocalStatus() {
        val isPro = prefs.getBoolean(BillingConstants.PREF_IS_PRO, false)
        val expiry = prefs.getLong(BillingConstants.PREF_EXPIRY_TIMESTAMP, 0L)
        val now    = System.currentTimeMillis()

        _proStatus.value = when {
            !isPro -> ProStatus.FREE
            // Lifetime (expiry == 0) o aún vigente
            expiry == 0L || now < expiry -> ProStatus.ACTIVE
            // Expirado — NEVER-BLOCK: datos accesibles, features Pro ocultos
            else -> ProStatus.EXPIRED_ACCESS_PRESERVED
        }
    }

    /**
     * Consulta compras existentes en Play Store.
     * Actualiza el estado local si hay cambios.
     */
    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    /**
     * Procesa la lista de compras y actualiza el estado.
     * NEVER-BLOCK: si no hay compras activas, el estado es
     * EXPIRED_ACCESS_PRESERVED (no FREE) si antes era PRO,
     * para garantizar acceso a datos ya cargados.
     */
    private fun processPurchases(purchases: List<Purchase>) {
        val hasActivePro = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            (BillingConstants.PRODUCT_PRO_LIFETIME in purchase.products ||
             BillingConstants.PRODUCT_ENTERPRISE_YEARLY in purchase.products)
        }

        if (hasActivePro) {
            // Confirmar compras no reconocidas
            purchases.filter { !it.isAcknowledged }.forEach { purchase ->
                acknowledgePurchase(purchase)
            }
            persistProStatus(isActive = true)
            _proStatus.value = ProStatus.ACTIVE
        } else {
            val wasPro = prefs.getBoolean(BillingConstants.PREF_IS_PRO, false)
            if (wasPro) {
                // Era Pro pero ya no → EXPIRED_ACCESS_PRESERVED (never-block)
                // Los datos existentes SIGUEN siendo accesibles
                _proStatus.value = ProStatus.EXPIRED_ACCESS_PRESERVED
                // NO llamar a ningún DAO aquí — invariante arquitectónica
            } else {
                _proStatus.value = ProStatus.FREE
            }
        }
    }

    /**
     * Persiste el estado Pro en SharedPreferences locales.
     * SIN acceso a Room — solo preferencias de billing.
     */
    private fun persistProStatus(isActive: Boolean) {
        prefs.edit()
            .putBoolean(BillingConstants.PREF_IS_PRO, isActive)
            .putLong(BillingConstants.PREF_EXPIRY_TIMESTAMP, 0L) // 0 = lifetime
            .apply()
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { _ -> /* silent */ }
    }

    /** Retorna true si el dispositivo tiene acceso Pro activo */
    fun isProActive(): Boolean = _proStatus.value == ProStatus.ACTIVE

    /**
     * Retorna true si los datos deben ser accesibles.
     * ALWAYS TRUE para ACTIVE y EXPIRED_ACCESS_PRESERVED.
     * NEVER-BLOCK: datos accesibles aunque la licencia haya expirado.
     */
    fun isDataAccessible(): Boolean = _proStatus.value != ProStatus.FREE &&
                                      _proStatus.value != ProStatus.ERROR &&
                                      _proStatus.value != ProStatus.CHECKING

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        }
    }

    fun disconnect() { billingClient.endConnection() }
}
