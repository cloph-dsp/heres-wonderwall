package com.wonderwall.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analyses")
data class CachedAnalysis(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val chordsJson: String,          // JSON: [{start, end, chord}]
    val key: String,
    val bpm: Float,
    val cachedAt: Long = System.currentTimeMillis(),
)
