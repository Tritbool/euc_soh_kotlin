package io.github.eucsoh.android.data.database;

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
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class WheelDao_Impl implements WheelDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<WheelEntity> __insertionAdapterOfWheelEntity;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  private final SharedSQLiteStatement __preparedStmtOfDeleteWheel;

  public WheelDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfWheelEntity = new EntityInsertionAdapter<WheelEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `detected_wheels` (`macAddress`,`displayName`,`userAlias`,`manufacturer`,`model`,`serialNumber`,`csvFileUris`,`source`,`lastScanTimestamp`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WheelEntity entity) {
        statement.bindString(1, entity.getMacAddress());
        statement.bindString(2, entity.getDisplayName());
        if (entity.getUserAlias() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getUserAlias());
        }
        if (entity.getManufacturer() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getManufacturer());
        }
        if (entity.getModel() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getModel());
        }
        if (entity.getSerialNumber() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getSerialNumber());
        }
        statement.bindString(7, entity.getCsvFileUris());
        statement.bindString(8, entity.getSource());
        statement.bindLong(9, entity.getLastScanTimestamp());
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM detected_wheels";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteWheel = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM detected_wheels WHERE macAddress = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertWheels(final List<WheelEntity> wheels,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfWheelEntity.insert(wheels);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertWheel(final WheelEntity wheel, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfWheelEntity.insert(wheel);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
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
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteWheel(final String mac, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteWheel.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, mac);
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
          __preparedStmtOfDeleteWheel.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllWheels(final Continuation<? super List<WheelEntity>> $completion) {
    final String _sql = "SELECT * FROM detected_wheels ORDER BY displayName ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<WheelEntity>>() {
      @Override
      @NonNull
      public List<WheelEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMacAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "macAddress");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfUserAlias = CursorUtil.getColumnIndexOrThrow(_cursor, "userAlias");
          final int _cursorIndexOfManufacturer = CursorUtil.getColumnIndexOrThrow(_cursor, "manufacturer");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfSerialNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "serialNumber");
          final int _cursorIndexOfCsvFileUris = CursorUtil.getColumnIndexOrThrow(_cursor, "csvFileUris");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfLastScanTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "lastScanTimestamp");
          final List<WheelEntity> _result = new ArrayList<WheelEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WheelEntity _item;
            final String _tmpMacAddress;
            _tmpMacAddress = _cursor.getString(_cursorIndexOfMacAddress);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpUserAlias;
            if (_cursor.isNull(_cursorIndexOfUserAlias)) {
              _tmpUserAlias = null;
            } else {
              _tmpUserAlias = _cursor.getString(_cursorIndexOfUserAlias);
            }
            final String _tmpManufacturer;
            if (_cursor.isNull(_cursorIndexOfManufacturer)) {
              _tmpManufacturer = null;
            } else {
              _tmpManufacturer = _cursor.getString(_cursorIndexOfManufacturer);
            }
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final String _tmpSerialNumber;
            if (_cursor.isNull(_cursorIndexOfSerialNumber)) {
              _tmpSerialNumber = null;
            } else {
              _tmpSerialNumber = _cursor.getString(_cursorIndexOfSerialNumber);
            }
            final String _tmpCsvFileUris;
            _tmpCsvFileUris = _cursor.getString(_cursorIndexOfCsvFileUris);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            final long _tmpLastScanTimestamp;
            _tmpLastScanTimestamp = _cursor.getLong(_cursorIndexOfLastScanTimestamp);
            _item = new WheelEntity(_tmpMacAddress,_tmpDisplayName,_tmpUserAlias,_tmpManufacturer,_tmpModel,_tmpSerialNumber,_tmpCsvFileUris,_tmpSource,_tmpLastScanTimestamp);
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
  public Object getWheelByMac(final String mac,
      final Continuation<? super WheelEntity> $completion) {
    final String _sql = "SELECT * FROM detected_wheels WHERE macAddress = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, mac);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<WheelEntity>() {
      @Override
      @Nullable
      public WheelEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMacAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "macAddress");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfUserAlias = CursorUtil.getColumnIndexOrThrow(_cursor, "userAlias");
          final int _cursorIndexOfManufacturer = CursorUtil.getColumnIndexOrThrow(_cursor, "manufacturer");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfSerialNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "serialNumber");
          final int _cursorIndexOfCsvFileUris = CursorUtil.getColumnIndexOrThrow(_cursor, "csvFileUris");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfLastScanTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "lastScanTimestamp");
          final WheelEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpMacAddress;
            _tmpMacAddress = _cursor.getString(_cursorIndexOfMacAddress);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpUserAlias;
            if (_cursor.isNull(_cursorIndexOfUserAlias)) {
              _tmpUserAlias = null;
            } else {
              _tmpUserAlias = _cursor.getString(_cursorIndexOfUserAlias);
            }
            final String _tmpManufacturer;
            if (_cursor.isNull(_cursorIndexOfManufacturer)) {
              _tmpManufacturer = null;
            } else {
              _tmpManufacturer = _cursor.getString(_cursorIndexOfManufacturer);
            }
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final String _tmpSerialNumber;
            if (_cursor.isNull(_cursorIndexOfSerialNumber)) {
              _tmpSerialNumber = null;
            } else {
              _tmpSerialNumber = _cursor.getString(_cursorIndexOfSerialNumber);
            }
            final String _tmpCsvFileUris;
            _tmpCsvFileUris = _cursor.getString(_cursorIndexOfCsvFileUris);
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            final long _tmpLastScanTimestamp;
            _tmpLastScanTimestamp = _cursor.getLong(_cursorIndexOfLastScanTimestamp);
            _result = new WheelEntity(_tmpMacAddress,_tmpDisplayName,_tmpUserAlias,_tmpManufacturer,_tmpModel,_tmpSerialNumber,_tmpCsvFileUris,_tmpSource,_tmpLastScanTimestamp);
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
  public Object getWheelCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM detected_wheels";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
