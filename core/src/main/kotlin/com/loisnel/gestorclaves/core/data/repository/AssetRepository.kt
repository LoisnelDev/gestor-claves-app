package com.loisnel.gestorclaves.core.data.repository

import com.loisnel.gestorclaves.core.crypto.AESGCMManager
import com.loisnel.gestorclaves.core.data.dao.AssetDao
import com.loisnel.gestorclaves.core.data.entities.Asset
import com.loisnel.gestorclaves.core.domain.UnauthorizedRoleException
import com.loisnel.gestorclaves.core.domain.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * AssetRepository — RBAC enforcement para assets de campo
 *
 * INVARIANTE: solo TECHNICIAN puede cargar y descifrar assets.
 * DIRECTOR y SUPERVISOR no tienen acceso directo a credenciales.
 *
 * El enforcement ocurre en esta capa (:core), no en la UI.
 * Si la UI no muestra el botón (ViewModel), Y el Repository
 * lanza excepción (core) → Defense in Depth completo.
 *
 * TAD-B v2.1.1 — Sección 14
 */
class AssetRepository(
    private val assetDao: AssetDao
    // SIN BillingManager — Never-block policy: billing no toca datos
) {
    /**
     * Carga y descifra los assets del tenant activo.
     * Solo TECHNICIAN puede ejecutar esta operación.
     *
     * @throws UnauthorizedRoleException si el rol no es TECHNICIAN
     */
    suspend fun loadDecryptedAssets(
        tenantId: String,
        tenantKey: ByteArray,
        currentRole: UserRole
    ): List<Pair<Asset, String>> {
        // RBAC enforcement en :core — no bypasseable desde UI
        if (currentRole != UserRole.TECHNICIAN) {
            throw UnauthorizedRoleException(
                required = UserRole.TECHNICIAN,
                actual   = currentRole,
                action   = "loadDecryptedAssets"
            )
        }
        val assets = assetDao.getByTenant(tenantId)
        return assets.map { asset ->
            val blob      = com.loisnel.gestorclaves.core.crypto.AssetBlob(
                ciphertext = asset.data_encrypted_blob,
                nonce      = asset.nonce
            )
            val plaintext = AESGCMManager.decrypt(blob, tenantKey)
            Pair(asset, plaintext)
        }
    }

    /**
     * Cifra y guarda un nuevo asset para el tenant activo.
     * Solo SUPERVISOR puede crear assets.
     *
     * @throws UnauthorizedRoleException si el rol no es SUPERVISOR
     */
    suspend fun saveEncryptedAsset(
        asset: Asset,
        plaintext: String,
        tenantKey: ByteArray,
        currentRole: UserRole
    ) {
        if (currentRole != UserRole.SUPERVISOR) {
            throw UnauthorizedRoleException(
                required = UserRole.SUPERVISOR,
                actual   = currentRole,
                action   = "saveEncryptedAsset"
            )
        }
        val blob = AESGCMManager.encrypt(plaintext, tenantKey)
        val encryptedAsset = asset.copy(
            data_encrypted_blob = blob.ciphertext,
            nonce               = blob.nonce
        )
        assetDao.insert(encryptedAsset)
    }

    fun getByTenantFlow(tenantId: String): Flow<List<Asset>> =
        assetDao.getByTenantFlow(tenantId)
}
