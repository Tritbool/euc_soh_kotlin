package io.github.eucsoh.android.data.database;

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
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class WheelDatabase_Impl extends WheelDatabase {
  private volatile WheelDao _wheelDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `detected_wheels` (`macAddress` TEXT NOT NULL, `displayName` TEXT NOT NULL, `manufacturer` TEXT, `model` TEXT, `serialNumber` TEXT, `csvFileUris` TEXT NOT NULL, `source` TEXT NOT NULL, `lastScanTimestamp` INTEGER NOT NULL, PRIMARY KEY(`macAddress`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '95621fb043facdb3d9ca4dff84270331')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `detected_wheels`");
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
        final HashMap<String, TableInfo.Column> _columnsDetectedWheels = new HashMap<String, TableInfo.Column>(8);
        _columnsDetectedWheels.put("macAddress", new TableInfo.Column("macAddress", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDetectedWheels.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDetectedWheels.put("manufacturer", new TableInfo.Column("manufacturer", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDetectedWheels.put("model", new TableInfo.Column("model", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDetectedWheels.put("serialNumber", new TableInfo.Column("serialNumber", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDetectedWheels.put("csvFileUris", new TableInfo.Column("csvFileUris", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDetectedWheels.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDetectedWheels.put("lastScanTimestamp", new TableInfo.Column("lastScanTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDetectedWheels = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDetectedWheels = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDetectedWheels = new TableInfo("detected_wheels", _columnsDetectedWheels, _foreignKeysDetectedWheels, _indicesDetectedWheels);
        final TableInfo _existingDetectedWheels = TableInfo.read(db, "detected_wheels");
        if (!_infoDetectedWheels.equals(_existingDetectedWheels)) {
          return new RoomOpenHelper.ValidationResult(false, "detected_wheels(io.github.eucsoh.android.data.database.WheelEntity).\n"
                  + " Expected:\n" + _infoDetectedWheels + "\n"
                  + " Found:\n" + _existingDetectedWheels);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "95621fb043facdb3d9ca4dff84270331", "76277cda74886c9ff846a93c023f84c9");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "detected_wheels");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `detected_wheels`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
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
    _typeConvertersMap.put(WheelDao.class, WheelDao_Impl.getRequiredConverters());
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
  public WheelDao wheelDao() {
    if (_wheelDao != null) {
      return _wheelDao;
    } else {
      synchronized(this) {
        if(_wheelDao == null) {
          _wheelDao = new WheelDao_Impl(this);
        }
        return _wheelDao;
      }
    }
  }
}
