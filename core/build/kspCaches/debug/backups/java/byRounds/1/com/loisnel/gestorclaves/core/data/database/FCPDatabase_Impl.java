package com.loisnel.gestorclaves.core.data.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.loisnel.gestorclaves.core.data.dao.AssetDao;
import com.loisnel.gestorclaves.core.data.dao.AssetDao_Impl;
import com.loisnel.gestorclaves.core.data.dao.DeviceIdentityDao;
import com.loisnel.gestorclaves.core.data.dao.DeviceIdentityDao_Impl;
import com.loisnel.gestorclaves.core.data.dao.MissionLogDao;
import com.loisnel.gestorclaves.core.data.dao.MissionLogDao_Impl;
import com.loisnel.gestorclaves.core.data.dao.TenantDao;
import com.loisnel.gestorclaves.core.data.dao.TenantDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class FCPDatabase_Impl extends FCPDatabase {
  private volatile TenantDao _tenantDao;

  private volatile AssetDao _assetDao;

  private volatile MissionLogDao _missionLogDao;

  private volatile DeviceIdentityDao _deviceIdentityDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tenants` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `public_key_ed25519` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`id` TEXT NOT NULL, `tenant_id` TEXT NOT NULL, `label` TEXT NOT NULL, `data_encrypted_blob` BLOB NOT NULL, `nonce` BLOB NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tenant_id`) REFERENCES `tenants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_tenant_id` ON `assets` (`tenant_id`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `mission_logs` (`id` TEXT NOT NULL, `tenant_id` TEXT NOT NULL, `mission_data_json` TEXT NOT NULL, `status` TEXT NOT NULL, `issuer_key` TEXT NOT NULL, `receiver_key` TEXT NOT NULL, `signature` TEXT NOT NULL, `expiry_ms` INTEGER NOT NULL, `clock_tolerance_ms` INTEGER NOT NULL, `device_model` TEXT, `os_version` TEXT, `clock_drift_ms` INTEGER, `created_at` INTEGER NOT NULL, `closed_at` INTEGER, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `device_identity` (`id` INTEGER NOT NULL, `public_key_base64` TEXT NOT NULL, `keystore_alias` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c8c76b08b8756f90440ed361bc99ee0c')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `tenants`");
        db.execSQL("DROP TABLE IF EXISTS `assets`");
        db.execSQL("DROP TABLE IF EXISTS `mission_logs`");
        db.execSQL("DROP TABLE IF EXISTS `device_identity`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsTenants = new HashMap<String, TableInfo.Column>(4);
        _columnsTenants.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTenants.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTenants.put("public_key_ed25519", new TableInfo.Column("public_key_ed25519", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTenants.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTenants = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTenants = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTenants = new TableInfo("tenants", _columnsTenants, _foreignKeysTenants, _indicesTenants);
        final TableInfo _existingTenants = TableInfo.read(db, "tenants");
        if (!_infoTenants.equals(_existingTenants)) {
          return new RoomOpenHelper.ValidationResult(false, "tenants(com.loisnel.gestorclaves.core.data.entities.Tenant).\n"
                  + " Expected:\n" + _infoTenants + "\n"
                  + " Found:\n" + _existingTenants);
        }
        final HashMap<String, TableInfo.Column> _columnsAssets = new HashMap<String, TableInfo.Column>(6);
        _columnsAssets.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("tenant_id", new TableInfo.Column("tenant_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("label", new TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("data_encrypted_blob", new TableInfo.Column("data_encrypted_blob", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("nonce", new TableInfo.Column("nonce", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssets.put("updated_at", new TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAssets = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysAssets.add(new TableInfo.ForeignKey("tenants", "CASCADE", "NO ACTION", Arrays.asList("tenant_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesAssets = new HashSet<TableInfo.Index>(1);
        _indicesAssets.add(new TableInfo.Index("index_assets_tenant_id", false, Arrays.asList("tenant_id"), Arrays.asList("ASC")));
        final TableInfo _infoAssets = new TableInfo("assets", _columnsAssets, _foreignKeysAssets, _indicesAssets);
        final TableInfo _existingAssets = TableInfo.read(db, "assets");
        if (!_infoAssets.equals(_existingAssets)) {
          return new RoomOpenHelper.ValidationResult(false, "assets(com.loisnel.gestorclaves.core.data.entities.Asset).\n"
                  + " Expected:\n" + _infoAssets + "\n"
                  + " Found:\n" + _existingAssets);
        }
        final HashMap<String, TableInfo.Column> _columnsMissionLogs = new HashMap<String, TableInfo.Column>(14);
        _columnsMissionLogs.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("tenant_id", new TableInfo.Column("tenant_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("mission_data_json", new TableInfo.Column("mission_data_json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("issuer_key", new TableInfo.Column("issuer_key", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("receiver_key", new TableInfo.Column("receiver_key", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("signature", new TableInfo.Column("signature", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("expiry_ms", new TableInfo.Column("expiry_ms", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("clock_tolerance_ms", new TableInfo.Column("clock_tolerance_ms", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("device_model", new TableInfo.Column("device_model", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("os_version", new TableInfo.Column("os_version", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("clock_drift_ms", new TableInfo.Column("clock_drift_ms", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMissionLogs.put("closed_at", new TableInfo.Column("closed_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMissionLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMissionLogs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMissionLogs = new TableInfo("mission_logs", _columnsMissionLogs, _foreignKeysMissionLogs, _indicesMissionLogs);
        final TableInfo _existingMissionLogs = TableInfo.read(db, "mission_logs");
        if (!_infoMissionLogs.equals(_existingMissionLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "mission_logs(com.loisnel.gestorclaves.core.data.entities.MissionLog).\n"
                  + " Expected:\n" + _infoMissionLogs + "\n"
                  + " Found:\n" + _existingMissionLogs);
        }
        final HashMap<String, TableInfo.Column> _columnsDeviceIdentity = new HashMap<String, TableInfo.Column>(4);
        _columnsDeviceIdentity.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceIdentity.put("public_key_base64", new TableInfo.Column("public_key_base64", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceIdentity.put("keystore_alias", new TableInfo.Column("keystore_alias", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeviceIdentity.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDeviceIdentity = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDeviceIdentity = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDeviceIdentity = new TableInfo("device_identity", _columnsDeviceIdentity, _foreignKeysDeviceIdentity, _indicesDeviceIdentity);
        final TableInfo _existingDeviceIdentity = TableInfo.read(db, "device_identity");
        if (!_infoDeviceIdentity.equals(_existingDeviceIdentity)) {
          return new RoomOpenHelper.ValidationResult(false, "device_identity(com.loisnel.gestorclaves.core.data.entities.DeviceIdentity).\n"
                  + " Expected:\n" + _infoDeviceIdentity + "\n"
                  + " Found:\n" + _existingDeviceIdentity);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "c8c76b08b8756f90440ed361bc99ee0c", "a78b7d20fbc755a3717ab55fe7fc6f59");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "tenants","assets","mission_logs","device_identity");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `tenants`");
      _db.execSQL("DELETE FROM `assets`");
      _db.execSQL("DELETE FROM `mission_logs`");
      _db.execSQL("DELETE FROM `device_identity`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TenantDao.class, TenantDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AssetDao.class, AssetDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MissionLogDao.class, MissionLogDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DeviceIdentityDao.class, DeviceIdentityDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public TenantDao tenantDao() {
    if (_tenantDao != null) {
      return _tenantDao;
    } else {
      synchronized(this) {
        if(_tenantDao == null) {
          _tenantDao = new TenantDao_Impl(this);
        }
        return _tenantDao;
      }
    }
  }

  @Override
  public AssetDao assetDao() {
    if (_assetDao != null) {
      return _assetDao;
    } else {
      synchronized(this) {
        if(_assetDao == null) {
          _assetDao = new AssetDao_Impl(this);
        }
        return _assetDao;
      }
    }
  }

  @Override
  public MissionLogDao missionLogDao() {
    if (_missionLogDao != null) {
      return _missionLogDao;
    } else {
      synchronized(this) {
        if(_missionLogDao == null) {
          _missionLogDao = new MissionLogDao_Impl(this);
        }
        return _missionLogDao;
      }
    }
  }

  @Override
  public DeviceIdentityDao deviceIdentityDao() {
    if (_deviceIdentityDao != null) {
      return _deviceIdentityDao;
    } else {
      synchronized(this) {
        if(_deviceIdentityDao == null) {
          _deviceIdentityDao = new DeviceIdentityDao_Impl(this);
        }
        return _deviceIdentityDao;
      }
    }
  }
}
