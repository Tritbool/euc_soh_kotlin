package io.github.eucsoh.android.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WheelEntity::class],
    version = 2,                      // ← 1 → 2
    exportSchema = false
)
abstract class WheelDatabase : RoomDatabase() {

    abstract fun wheelDao(): WheelDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE detected_wheels ADD COLUMN userAlias TEXT")
            }
        }

        @Volatile
        private var INSTANCE: WheelDatabase? = null

        fun getInstance(context: Context): WheelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WheelDatabase::class.java,
                    "euc_wheel_database"
                )
                    .addMigrations(MIGRATION_1_2)   // ← remplace fallbackToDestructive
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}