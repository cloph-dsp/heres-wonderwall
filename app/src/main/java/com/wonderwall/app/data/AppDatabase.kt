package com.wonderwall.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CachedAnalysis::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(ctx: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "wonderwall.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
