package com.scambait.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scambait.app.data.model.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getCallLogById(id: Long): CallLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity): Long

    @Delete
    suspend fun deleteCallLog(callLog: CallLogEntity)

    @Query("SELECT COUNT(*) FROM call_logs")
    fun getCallCount(): Flow<Int>

    @Query("SELECT SUM(durationSeconds) FROM call_logs")
    fun getTotalDurationSeconds(): Flow<Long?>
}
