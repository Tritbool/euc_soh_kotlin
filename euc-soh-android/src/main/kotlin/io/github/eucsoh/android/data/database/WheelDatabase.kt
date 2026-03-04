package io.github.eucsoh.android.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for persisting detected wheels.
 */
@Database(
    entities = [WheelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WheelDatabase : RoomDatabase() {
    
    abstract fun wheelDao(): WheelDao
    
    companion object {
        @Volatile
        private var INSTANCE: WheelDatabase? = null
        
        fun getInstance(context: Context): WheelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WheelDatabase::class.java,
                    "euc_wheel_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
