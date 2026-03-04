package io.github.eucsoh.android.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for wheel database operations.
 */
@Dao
interface WheelDao {
    
    @Query("SELECT * FROM detected_wheels ORDER BY displayName ASC")
    suspend fun getAllWheels(): List<WheelEntity>
    
    @Query("SELECT * FROM detected_wheels WHERE macAddress = :mac")
    suspend fun getWheelByMac(mac: String): WheelEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWheels(wheels: List<WheelEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWheel(wheel: WheelEntity)
    
    @Query("DELETE FROM detected_wheels")
    suspend fun clearAll()
    
    @Query("DELETE FROM detected_wheels WHERE macAddress = :mac")
    suspend fun deleteWheel(mac: String)
    
    @Query("SELECT COUNT(*) FROM detected_wheels")
    suspend fun getWheelCount(): Int
}
