package com.adwatcher.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Represents a single instance of a detected background popup or activity change.
 */
@Entity(tableName = "popup_logs")
data class PopupLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val isSystemApp: Boolean,
    val eventType: String = "POPUP", // "POPUP", "ACTIVITY_CHANGE", or "ATTACK"
    val isSideloaded: Boolean = false,
    val isAttackState: Boolean = false,
    val installSource: String? = null
)

@Dao
interface PopupLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PopupLog)

    @Query("SELECT * FROM popup_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<PopupLog>>

    @Query("DELETE FROM popup_logs")
    suspend fun clearLogs()
}

@Database(entities = [PopupLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun popupLogDao(): PopupLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "adwatcher_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
