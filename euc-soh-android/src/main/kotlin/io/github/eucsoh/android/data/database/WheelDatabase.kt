/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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