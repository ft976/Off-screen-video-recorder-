package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_logs")
data class RecordingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val filePath: String,
    val fileName: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val resolution: String,
    val notes: String = "",
    val markersJson: String = "" // Semicolon separated, e.g. "recording_seconds:text"
)
