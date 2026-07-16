package com.loisnel.gestorclaves.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loisnel.gestorclaves.core.data.entities.Tenant
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tenant: Tenant)

    @Query("SELECT * FROM tenants ORDER BY name ASC")
    fun getAllFlow(): Flow<List<Tenant>>

    @Query("SELECT * FROM tenants WHERE id = :id")
    suspend fun findById(id: String): Tenant?

    @Query("DELETE FROM tenants WHERE id = :id")
    suspend fun deleteById(id: String)
}
