package com.loisnel.gestorclaves.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.loisnel.gestorclaves.core.data.entities.Asset
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: Asset)

    @Update
    suspend fun update(asset: Asset)

    @Query("SELECT * FROM assets WHERE tenant_id = :tenantId ORDER BY label ASC")
    fun getByTenantFlow(tenantId: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE tenant_id = :tenantId")
    suspend fun getByTenant(tenantId: String): List<Asset>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun findById(id: String): Asset?

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM assets WHERE tenant_id = :tenantId")
    suspend fun deleteByTenant(tenantId: String)
}
