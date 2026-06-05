package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<RecordingLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RecordingLog): Long

    @Update
    suspend fun updateLog(log: RecordingLog)

    @Query("DELETE FROM recording_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("SELECT * FROM recording_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): RecordingLog?
}
