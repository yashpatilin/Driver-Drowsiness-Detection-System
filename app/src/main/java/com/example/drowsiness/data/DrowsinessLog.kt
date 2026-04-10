package com.example.drowsiness.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drowsiness_logs")
data class DrowsinessLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val earValue: Float,
    val duration: Long,
    val status: String // "AWAKE" or "DROWSY"
)
