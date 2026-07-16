package com.loisnel.gestorclaves.core.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.loisnel.gestorclaves.core.data.entities.Asset;
import java.lang.Class;
import java.lang.Exception;
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
public final class AssetDao_Impl implements AssetDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Asset> __insertionAdapterOfAsset;

  private final EntityDeletionOrUpdateAdapter<Asset> __updateAdapterOfAsset;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByTenant;

  public AssetDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAsset = new EntityInsertionAdapter<Asset>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `assets` (`id`,`tenant_id`,`label`,`data_encrypted_blob`,`nonce`,`updated_at`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Asset entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTenant_id());
        statement.bindString(3, entity.getLabel());
        statement.bindBlob(4, entity.getData_encrypted_blob());
        statement.bindBlob(5, entity.getNonce());
        statement.bindLong(6, entity.getUpdated_at());
      }
    };
    this.__updateAdapterOfAsset = new EntityDeletionOrUpdateAdapter<Asset>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `assets` SET `id` = ?,`tenant_id` = ?,`label` = ?,`data_encrypted_blob` = ?,`nonce` = ?,`updated_at` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Asset entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTenant_id());
        statement.bindString(3, entity.getLabel());
        statement.bindBlob(4, entity.getData_encrypted_blob());
        statement.bindBlob(5, entity.getNonce());
        statement.bindLong(6, entity.getUpdated_at());
        statement.bindString(7, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM assets WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByTenant = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM assets WHERE tenant_id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final Asset asset, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAsset.insert(asset);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final Asset asset, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfAsset.handle(asset);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByTenant(final String tenantId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByTenant.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, tenantId);
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
          __preparedStmtOfDeleteByTenant.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Asset>> getByTenantFlow(final String tenantId) {
    final String _sql = "SELECT * FROM assets WHERE tenant_id = ? ORDER BY label ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tenantId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"assets"}, new Callable<List<Asset>>() {
      @Override
      @NonNull
      public List<Asset> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTenantId = CursorUtil.getColumnIndexOrThrow(_cursor, "tenant_id");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfDataEncryptedBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "data_encrypted_blob");
          final int _cursorIndexOfNonce = CursorUtil.getColumnIndexOrThrow(_cursor, "nonce");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<Asset> _result = new ArrayList<Asset>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Asset _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTenant_id;
            _tmpTenant_id = _cursor.getString(_cursorIndexOfTenantId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final byte[] _tmpData_encrypted_blob;
            _tmpData_encrypted_blob = _cursor.getBlob(_cursorIndexOfDataEncryptedBlob);
            final byte[] _tmpNonce;
            _tmpNonce = _cursor.getBlob(_cursorIndexOfNonce);
            final long _tmpUpdated_at;
            _tmpUpdated_at = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new Asset(_tmpId,_tmpTenant_id,_tmpLabel,_tmpData_encrypted_blob,_tmpNonce,_tmpUpdated_at);
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
  public Object getByTenant(final String tenantId,
      final Continuation<? super List<Asset>> $completion) {
    final String _sql = "SELECT * FROM assets WHERE tenant_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tenantId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Asset>>() {
      @Override
      @NonNull
      public List<Asset> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTenantId = CursorUtil.getColumnIndexOrThrow(_cursor, "tenant_id");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfDataEncryptedBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "data_encrypted_blob");
          final int _cursorIndexOfNonce = CursorUtil.getColumnIndexOrThrow(_cursor, "nonce");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<Asset> _result = new ArrayList<Asset>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Asset _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTenant_id;
            _tmpTenant_id = _cursor.getString(_cursorIndexOfTenantId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final byte[] _tmpData_encrypted_blob;
            _tmpData_encrypted_blob = _cursor.getBlob(_cursorIndexOfDataEncryptedBlob);
            final byte[] _tmpNonce;
            _tmpNonce = _cursor.getBlob(_cursorIndexOfNonce);
            final long _tmpUpdated_at;
            _tmpUpdated_at = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new Asset(_tmpId,_tmpTenant_id,_tmpLabel,_tmpData_encrypted_blob,_tmpNonce,_tmpUpdated_at);
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
  public Object findById(final String id, final Continuation<? super Asset> $completion) {
    final String _sql = "SELECT * FROM assets WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Asset>() {
      @Override
      @Nullable
      public Asset call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTenantId = CursorUtil.getColumnIndexOrThrow(_cursor, "tenant_id");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfDataEncryptedBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "data_encrypted_blob");
          final int _cursorIndexOfNonce = CursorUtil.getColumnIndexOrThrow(_cursor, "nonce");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final Asset _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTenant_id;
            _tmpTenant_id = _cursor.getString(_cursorIndexOfTenantId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final byte[] _tmpData_encrypted_blob;
            _tmpData_encrypted_blob = _cursor.getBlob(_cursorIndexOfDataEncryptedBlob);
            final byte[] _tmpNonce;
            _tmpNonce = _cursor.getBlob(_cursorIndexOfNonce);
            final long _tmpUpdated_at;
            _tmpUpdated_at = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new Asset(_tmpId,_tmpTenant_id,_tmpLabel,_tmpData_encrypted_blob,_tmpNonce,_tmpUpdated_at);
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
