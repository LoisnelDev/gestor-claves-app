package com.loisnel.gestorclaves.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.loisnel.gestorclaves.core.data.dao.AssetDao
import com.loisnel.gestorclaves.core.data.dao.DeviceIdentityDao
import com.loisnel.gestorclaves.core.data.dao.MissionLogDao
import com.loisnel.gestorclaves.core.data.dao.TenantDao
import com.loisnel.gestorclaves.core.data.entities.Asset
import com.loisnel.gestorclaves.core.data.entities.DeviceIdentity
import com.loisnel.gestorclaves.core.data.entities.MissionLog
import com.loisnel.gestorclaves.core.data.entities.Tenant

/**
 * FCPDatabase — RoomDatabase singleton
 *
 * Base de datos local cifrada conceptualmente por la arquitectura de BLOBs:
 * los payloads sensibles (assets) están cifrados con AES-256-GCM antes
 * de ser insertados. Room gestiona la estructura relacional.
 *
 * Version 1 — TAD-B v2.1.1
 * Cambios futuros de schema requieren Migration objects.
 */
@Database(
    entities = [
        Tenant::class,
        Asset::class,
        MissionLog::class,
        DeviceIdentity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FCPDatabase : RoomDatabase() {

    abstract fun tenantDao(): TenantDao
    abstract fun assetDao(): AssetDao
    abstract fun missionLogDao(): MissionLogDao
    abstract fun deviceIdentityDao(): DeviceIdentityDao

    companion object {
        private const val DB_NAME = "fcp_database"

        @Volatile
        private var INSTANCE: FCPDatabase? = null

        fun getInstance(context: Context): FCPDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): FCPDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FCPDatabase::class.java,
                DB_NAME
            )
            .fallbackToDestructiveMigration() // Solo en desarrollo — reemplazar con Migrations en producción
            .build()
        }

        /** Solo para tests — crea DB en memoria, sin persistencia */
        fun buildInMemory(context: Context): FCPDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                FCPDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
