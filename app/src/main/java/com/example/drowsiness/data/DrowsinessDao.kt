package com.example.drowsiness.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DrowsinessDao {
    @Insert
    suspend fun insertLog(log: DrowsinessLog)

    @Query("SELECT * FROM drowsiness_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<DrowsinessLog>>
    
    @Query("SELECT * FROM drowsiness_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<DrowsinessLog>>
}
