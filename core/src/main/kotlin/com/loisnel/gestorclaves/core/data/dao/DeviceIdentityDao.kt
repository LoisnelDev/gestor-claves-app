package com.loisnel.gestorclaves.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loisnel.gestorclaves.core.data.entities.DeviceIdentity

@Dao
interface DeviceIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(identity: DeviceIdentity)

    @Query("SELECT * FROM device_identity WHERE id = 1")
    suspend fun get(): DeviceIdentity?
}
