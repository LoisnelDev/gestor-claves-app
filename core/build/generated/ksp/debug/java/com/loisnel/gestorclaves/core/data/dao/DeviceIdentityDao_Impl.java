package com.loisnel.gestorclaves.core.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.loisnel.gestorclaves.core.data.entities.DeviceIdentity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DeviceIdentityDao_Impl implements DeviceIdentityDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DeviceIdentity> __insertionAdapterOfDeviceIdentity;

  public DeviceIdentityDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDeviceIdentity = new EntityInsertionAdapter<DeviceIdentity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `device_identity` (`id`,`public_key_base64`,`keystore_alias`,`created_at`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DeviceIdentity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPublic_key_base64());
        statement.bindString(3, entity.getKeystore_alias());
        statement.bindLong(4, entity.getCreated_at());
      }
    };
  }

  @Override
  public Object insert(final DeviceIdentity identity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDeviceIdentity.insert(identity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final Continuation<? super DeviceIdentity> $completion) {
    final String _sql = "SELECT * FROM device_identity WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DeviceIdentity>() {
      @Override
      @Nullable
      public DeviceIdentity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPublicKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "public_key_base64");
          final int _cursorIndexOfKeystoreAlias = CursorUtil.getColumnIndexOrThrow(_cursor, "keystore_alias");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final DeviceIdentity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpPublic_key_base64;
            _tmpPublic_key_base64 = _cursor.getString(_cursorIndexOfPublicKeyBase64);
            final String _tmpKeystore_alias;
            _tmpKeystore_alias = _cursor.getString(_cursorIndexOfKeystoreAlias);
            final long _tmpCreated_at;
            _tmpCreated_at = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new DeviceIdentity(_tmpId,_tmpPublic_key_base64,_tmpKeystore_alias,_tmpCreated_at);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
