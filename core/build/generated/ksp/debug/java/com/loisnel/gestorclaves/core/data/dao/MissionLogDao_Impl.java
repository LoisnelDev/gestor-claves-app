package com.loisnel.gestorclaves.core.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.loisnel.gestorclaves.core.data.entities.MissionLog;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MissionLogDao_Impl implements MissionLogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MissionLog> __insertionAdapterOfMissionLog;

  private final SharedSQLiteStatement __preparedStmtOfTransitionStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatusOnly;

  public MissionLogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMissionLog = new EntityInsertionAdapter<MissionLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `mission_logs` (`id`,`tenant_id`,`mission_data_json`,`status`,`issuer_key`,`receiver_key`,`signature`,`expiry_ms`,`clock_tolerance_ms`,`device_model`,`os_version`,`clock_drift_ms`,`created_at`,`closed_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MissionLog entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTenant_id());
        statement.bindString(3, entity.getMission_data_json());
        statement.bindString(4, entity.getStatus());
        statement.bindString(5, entity.getIssuer_key());
        statement.bindString(6, entity.getReceiver_key());
        statement.bindString(7, entity.getSignature());
        statement.bindLong(8, entity.getExpiry_ms());
        statement.bindLong(9, entity.getClock_tolerance_ms());
        if (entity.getDevice_model() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getDevice_model());
        }
        if (entity.getOs_version() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getOs_version());
        }
        if (entity.getClock_drift_ms() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getClock_drift_ms());
        }
        statement.bindLong(13, entity.getCreated_at());
        if (entity.getClosed_at() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getClosed_at());
        }
      }
    };
    this.__preparedStmtOfTransitionStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE mission_logs\n"
                + "        SET status       = ?,\n"
                + "            signature    = ?,\n"
                + "            device_model = ?,\n"
                + "            os_version   = ?,\n"
                + "            clock_drift_ms = ?,\n"
                + "            closed_at    = ?\n"
                + "        WHERE id = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateStatusOnly = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE mission_logs SET status = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final MissionLog mission, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMissionLog.insert(mission);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object transitionStatus(final String missionId, final String newStatus,
      final String signature, final String deviceModel, final String osVersion,
      final Long clockDriftMs, final Long closedAt, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfTransitionStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, newStatus);
        _argIndex = 2;
        _stmt.bindString(_argIndex, signature);
        _argIndex = 3;
        if (deviceModel == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, deviceModel);
        }
        _argIndex = 4;
        if (osVersion == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, osVersion);
        }
        _argIndex = 5;
        if (clockDriftMs == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, clockDriftMs);
        }
        _argIndex = 6;
        if (closedAt == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, closedAt);
        }
        _argIndex = 7;
        _stmt.bindString(_argIndex, missionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfTransitionStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatusOnly(final String missionId, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatusOnly.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindString(_argIndex, missionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateStatusOnly.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object findById(final String missionId,
      final Continuation<? super MissionLog> $completion) {
    final String _sql = "SELECT * FROM mission_logs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, missionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MissionLog>() {
      @Override
      @Nullable
      public MissionLog call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTenantId = CursorUtil.getColumnIndexOrThrow(_cursor, "tenant_id");
          final int _cursorIndexOfMissionDataJson = CursorUtil.getColumnIndexOrThrow(_cursor, "mission_data_json");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIssuerKey = CursorUtil.getColumnIndexOrThrow(_cursor, "issuer_key");
          final int _cursorIndexOfReceiverKey = CursorUtil.getColumnIndexOrThrow(_cursor, "receiver_key");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfExpiryMs = CursorUtil.getColumnIndexOrThrow(_cursor, "expiry_ms");
          final int _cursorIndexOfClockToleranceMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_tolerance_ms");
          final int _cursorIndexOfDeviceModel = CursorUtil.getColumnIndexOrThrow(_cursor, "device_model");
          final int _cursorIndexOfOsVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "os_version");
          final int _cursorIndexOfClockDriftMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_drift_ms");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfClosedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "closed_at");
          final MissionLog _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTenant_id;
            _tmpTenant_id = _cursor.getString(_cursorIndexOfTenantId);
            final String _tmpMission_data_json;
            _tmpMission_data_json = _cursor.getString(_cursorIndexOfMissionDataJson);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpIssuer_key;
            _tmpIssuer_key = _cursor.getString(_cursorIndexOfIssuerKey);
            final String _tmpReceiver_key;
            _tmpReceiver_key = _cursor.getString(_cursorIndexOfReceiverKey);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpExpiry_ms;
            _tmpExpiry_ms = _cursor.getLong(_cursorIndexOfExpiryMs);
            final long _tmpClock_tolerance_ms;
            _tmpClock_tolerance_ms = _cursor.getLong(_cursorIndexOfClockToleranceMs);
            final String _tmpDevice_model;
            if (_cursor.isNull(_cursorIndexOfDeviceModel)) {
              _tmpDevice_model = null;
            } else {
              _tmpDevice_model = _cursor.getString(_cursorIndexOfDeviceModel);
            }
            final String _tmpOs_version;
            if (_cursor.isNull(_cursorIndexOfOsVersion)) {
              _tmpOs_version = null;
            } else {
              _tmpOs_version = _cursor.getString(_cursorIndexOfOsVersion);
            }
            final Long _tmpClock_drift_ms;
            if (_cursor.isNull(_cursorIndexOfClockDriftMs)) {
              _tmpClock_drift_ms = null;
            } else {
              _tmpClock_drift_ms = _cursor.getLong(_cursorIndexOfClockDriftMs);
            }
            final long _tmpCreated_at;
            _tmpCreated_at = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpClosed_at;
            if (_cursor.isNull(_cursorIndexOfClosedAt)) {
              _tmpClosed_at = null;
            } else {
              _tmpClosed_at = _cursor.getLong(_cursorIndexOfClosedAt);
            }
            _result = new MissionLog(_tmpId,_tmpTenant_id,_tmpMission_data_json,_tmpStatus,_tmpIssuer_key,_tmpReceiver_key,_tmpSignature,_tmpExpiry_ms,_tmpClock_tolerance_ms,_tmpDevice_model,_tmpOs_version,_tmpClock_drift_ms,_tmpCreated_at,_tmpClosed_at);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object existsById(final String missionId,
      final Continuation<? super Boolean> $completion) {
    final String _sql = "SELECT EXISTS(SELECT 1 FROM mission_logs WHERE id = ?)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, missionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Boolean>() {
      @Override
      @NonNull
      public Boolean call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Boolean _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp != 0;
          } else {
            _result = false;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findByStatus(final String status,
      final Continuation<? super List<MissionLog>> $completion) {
    final String _sql = "SELECT * FROM mission_logs WHERE status = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, status);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MissionLog>>() {
      @Override
      @NonNull
      public List<MissionLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTenantId = CursorUtil.getColumnIndexOrThrow(_cursor, "tenant_id");
          final int _cursorIndexOfMissionDataJson = CursorUtil.getColumnIndexOrThrow(_cursor, "mission_data_json");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIssuerKey = CursorUtil.getColumnIndexOrThrow(_cursor, "issuer_key");
          final int _cursorIndexOfReceiverKey = CursorUtil.getColumnIndexOrThrow(_cursor, "receiver_key");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfExpiryMs = CursorUtil.getColumnIndexOrThrow(_cursor, "expiry_ms");
          final int _cursorIndexOfClockToleranceMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_tolerance_ms");
          final int _cursorIndexOfDeviceModel = CursorUtil.getColumnIndexOrThrow(_cursor, "device_model");
          final int _cursorIndexOfOsVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "os_version");
          final int _cursorIndexOfClockDriftMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_drift_ms");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfClosedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "closed_at");
          final List<MissionLog> _result = new ArrayList<MissionLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MissionLog _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTenant_id;
            _tmpTenant_id = _cursor.getString(_cursorIndexOfTenantId);
            final String _tmpMission_data_json;
            _tmpMission_data_json = _cursor.getString(_cursorIndexOfMissionDataJson);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpIssuer_key;
            _tmpIssuer_key = _cursor.getString(_cursorIndexOfIssuerKey);
            final String _tmpReceiver_key;
            _tmpReceiver_key = _cursor.getString(_cursorIndexOfReceiverKey);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpExpiry_ms;
            _tmpExpiry_ms = _cursor.getLong(_cursorIndexOfExpiryMs);
            final long _tmpClock_tolerance_ms;
            _tmpClock_tolerance_ms = _cursor.getLong(_cursorIndexOfClockToleranceMs);
            final String _tmpDevice_model;
            if (_cursor.isNull(_cursorIndexOfDeviceModel)) {
              _tmpDevice_model = null;
            } else {
              _tmpDevice_model = _cursor.getString(_cursorIndexOfDeviceModel);
            }
            final String _tmpOs_version;
            if (_cursor.isNull(_cursorIndexOfOsVersion)) {
              _tmpOs_version = null;
            } else {
              _tmpOs_version = _cursor.getString(_cursorIndexOfOsVersion);
            }
            final Long _tmpClock_drift_ms;
            if (_cursor.isNull(_cursorIndexOfClockDriftMs)) {
              _tmpClock_drift_ms = null;
            } else {
              _tmpClock_drift_ms = _cursor.getLong(_cursorIndexOfClockDriftMs);
            }
            final long _tmpCreated_at;
            _tmpCreated_at = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpClosed_at;
            if (_cursor.isNull(_cursorIndexOfClosedAt)) {
              _tmpClosed_at = null;
            } else {
              _tmpClosed_at = _cursor.getLong(_cursorIndexOfClosedAt);
            }
            _item = new MissionLog(_tmpId,_tmpTenant_id,_tmpMission_data_json,_tmpStatus,_tmpIssuer_key,_tmpReceiver_key,_tmpSignature,_tmpExpiry_ms,_tmpClock_tolerance_ms,_tmpDevice_model,_tmpOs_version,_tmpClock_drift_ms,_tmpCreated_at,_tmpClosed_at);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MissionLog>> getAllFlow() {
    final String _sql = "SELECT * FROM mission_logs ORDER BY created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"mission_logs"}, new Callable<List<MissionLog>>() {
      @Override
      @NonNull
      public List<MissionLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTenantId = CursorUtil.getColumnIndexOrThrow(_cursor, "tenant_id");
          final int _cursorIndexOfMissionDataJson = CursorUtil.getColumnIndexOrThrow(_cursor, "mission_data_json");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIssuerKey = CursorUtil.getColumnIndexOrThrow(_cursor, "issuer_key");
          final int _cursorIndexOfReceiverKey = CursorUtil.getColumnIndexOrThrow(_cursor, "receiver_key");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfExpiryMs = CursorUtil.getColumnIndexOrThrow(_cursor, "expiry_ms");
          final int _cursorIndexOfClockToleranceMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_tolerance_ms");
          final int _cursorIndexOfDeviceModel = CursorUtil.getColumnIndexOrThrow(_cursor, "device_model");
          final int _cursorIndexOfOsVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "os_version");
          final int _cursorIndexOfClockDriftMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_drift_ms");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfClosedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "closed_at");
          final List<MissionLog> _result = new ArrayList<MissionLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MissionLog _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTenant_id;
            _tmpTenant_id = _cursor.getString(_cursorIndexOfTenantId);
            final String _tmpMission_data_json;
            _tmpMission_data_json = _cursor.getString(_cursorIndexOfMissionDataJson);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpIssuer_key;
            _tmpIssuer_key = _cursor.getString(_cursorIndexOfIssuerKey);
            final String _tmpReceiver_key;
            _tmpReceiver_key = _cursor.getString(_cursorIndexOfReceiverKey);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpExpiry_ms;
            _tmpExpiry_ms = _cursor.getLong(_cursorIndexOfExpiryMs);
            final long _tmpClock_tolerance_ms;
            _tmpClock_tolerance_ms = _cursor.getLong(_cursorIndexOfClockToleranceMs);
            final String _tmpDevice_model;
            if (_cursor.isNull(_cursorIndexOfDeviceModel)) {
              _tmpDevice_model = null;
            } else {
              _tmpDevice_model = _cursor.getString(_cursorIndexOfDeviceModel);
            }
            final String _tmpOs_version;
            if (_cursor.isNull(_cursorIndexOfOsVersion)) {
              _tmpOs_version = null;
            } else {
              _tmpOs_version = _cursor.getString(_cursorIndexOfOsVersion);
            }
            final Long _tmpClock_drift_ms;
            if (_cursor.isNull(_cursorIndexOfClockDriftMs)) {
              _tmpClock_drift_ms = null;
            } else {
              _tmpClock_drift_ms = _cursor.getLong(_cursorIndexOfClockDriftMs);
            }
            final long _tmpCreated_at;
            _tmpCreated_at = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpClosed_at;
            if (_cursor.isNull(_cursorIndexOfClosedAt)) {
              _tmpClosed_at = null;
            } else {
              _tmpClosed_at = _cursor.getLong(_cursorIndexOfClosedAt);
            }
            _item = new MissionLog(_tmpId,_tmpTenant_id,_tmpMission_data_json,_tmpStatus,_tmpIssuer_key,_tmpReceiver_key,_tmpSignature,_tmpExpiry_ms,_tmpClock_tolerance_ms,_tmpDevice_model,_tmpOs_version,_tmpClock_drift_ms,_tmpCreated_at,_tmpClosed_at);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<MissionLog>> getByTenantFlow(final String tenantId) {
    final String _sql = "SELECT * FROM mission_logs WHERE tenant_id = ? ORDER BY created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tenantId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"mission_logs"}, new Callable<List<MissionLog>>() {
      @Override
      @NonNull
      public List<MissionLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTenantId = CursorUtil.getColumnIndexOrThrow(_cursor, "tenant_id");
          final int _cursorIndexOfMissionDataJson = CursorUtil.getColumnIndexOrThrow(_cursor, "mission_data_json");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIssuerKey = CursorUtil.getColumnIndexOrThrow(_cursor, "issuer_key");
          final int _cursorIndexOfReceiverKey = CursorUtil.getColumnIndexOrThrow(_cursor, "receiver_key");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfExpiryMs = CursorUtil.getColumnIndexOrThrow(_cursor, "expiry_ms");
          final int _cursorIndexOfClockToleranceMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_tolerance_ms");
          final int _cursorIndexOfDeviceModel = CursorUtil.getColumnIndexOrThrow(_cursor, "device_model");
          final int _cursorIndexOfOsVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "os_version");
          final int _cursorIndexOfClockDriftMs = CursorUtil.getColumnIndexOrThrow(_cursor, "clock_drift_ms");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfClosedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "closed_at");
          final List<MissionLog> _result = new ArrayList<MissionLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MissionLog _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTenant_id;
            _tmpTenant_id = _cursor.getString(_cursorIndexOfTenantId);
            final String _tmpMission_data_json;
            _tmpMission_data_json = _cursor.getString(_cursorIndexOfMissionDataJson);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpIssuer_key;
            _tmpIssuer_key = _cursor.getString(_cursorIndexOfIssuerKey);
            final String _tmpReceiver_key;
            _tmpReceiver_key = _cursor.getString(_cursorIndexOfReceiverKey);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpExpiry_ms;
            _tmpExpiry_ms = _cursor.getLong(_cursorIndexOfExpiryMs);
            final long _tmpClock_tolerance_ms;
            _tmpClock_tolerance_ms = _cursor.getLong(_cursorIndexOfClockToleranceMs);
            final String _tmpDevice_model;
            if (_cursor.isNull(_cursorIndexOfDeviceModel)) {
              _tmpDevice_model = null;
            } else {
              _tmpDevice_model = _cursor.getString(_cursorIndexOfDeviceModel);
            }
            final String _tmpOs_version;
            if (_cursor.isNull(_cursorIndexOfOsVersion)) {
              _tmpOs_version = null;
            } else {
              _tmpOs_version = _cursor.getString(_cursorIndexOfOsVersion);
            }
            final Long _tmpClock_drift_ms;
            if (_cursor.isNull(_cursorIndexOfClockDriftMs)) {
              _tmpClock_drift_ms = null;
            } else {
              _tmpClock_drift_ms = _cursor.getLong(_cursorIndexOfClockDriftMs);
            }
            final long _tmpCreated_at;
            _tmpCreated_at = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpClosed_at;
            if (_cursor.isNull(_cursorIndexOfClosedAt)) {
              _tmpClosed_at = null;
            } else {
              _tmpClosed_at = _cursor.getLong(_cursorIndexOfClosedAt);
            }
            _item = new MissionLog(_tmpId,_tmpTenant_id,_tmpMission_data_json,_tmpStatus,_tmpIssuer_key,_tmpReceiver_key,_tmpSignature,_tmpExpiry_ms,_tmpClock_tolerance_ms,_tmpDevice_model,_tmpOs_version,_tmpClock_drift_ms,_tmpCreated_at,_tmpClosed_at);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
