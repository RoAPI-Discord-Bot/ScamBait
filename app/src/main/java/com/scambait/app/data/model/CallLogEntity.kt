package com.scambait.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerNumber: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val transcriptJson: String, // Store exchanges as JSON string
    val audioFilePath: String,
    val personaName: String,
    val isSpamConfirmed: Boolean = true
)
