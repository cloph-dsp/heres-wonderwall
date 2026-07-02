package com.wonderwall.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY cachedAt DESC")
    suspend fun getAll(): List<CachedAnalysis>

    @Query("SELECT * FROM analyses WHERE videoId = :videoId")
    suspend fun getById(videoId: String): CachedAnalysis?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(analysis: CachedAnalysis)

    @Query("DELETE FROM analyses WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM analyses")
    suspend fun deleteAll()
}
