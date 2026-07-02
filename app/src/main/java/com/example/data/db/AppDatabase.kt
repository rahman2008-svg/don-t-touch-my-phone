package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val pin: String = "", // Default empty (means user needs to set it up)
    val alarmSensitivity: Float = 5.0f, // 1 to 10
    val selectedSound: String = "Siren", // Siren, Police, Laser, Digital
    val isFlashEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val countdownSeconds: Int = 3, // 3, 5, 10
    val isDarkMode: Boolean = true,
    val language: String = "en", // "en" or "bn"
    val isDonTouchActive: Boolean = true,
    val isMotionActive: Boolean = false,
    val isPocketActive: Boolean = false,
    val isChargingActive: Boolean = false,
    val isWhoTouchedActive: Boolean = false,
    val isClapWhistleActive: Boolean = false
)

@Entity(tableName = "alarm_history")
data class AlarmHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String, // e.g. "Don't Touch", "Motion Alarm", "Pocket Mode", etc.
    val wasIntruderCaptured: Boolean = false,
    val intruderPhotoPath: String? = null
)

@Entity(tableName = "intruder_photos")
data class IntruderPhotoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String, // local file path
    val modeTriggered: String
)

@Dao
interface AppDao {
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)

    @Query("SELECT * FROM alarm_history ORDER BY timestamp DESC")
    fun getAlarmHistory(): Flow<List<AlarmHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmHistory(item: AlarmHistoryItem): Long

    @Query("DELETE FROM alarm_history")
    suspend fun clearAlarmHistory()

    @Query("SELECT * FROM intruder_photos ORDER BY timestamp DESC")
    fun getIntruderPhotos(): Flow<List<IntruderPhotoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntruderPhoto(photo: IntruderPhotoItem)

    @Query("DELETE FROM intruder_photos WHERE id = :id")
    suspend fun deleteIntruderPhoto(id: Long)

    @Query("DELETE FROM intruder_photos")
    suspend fun clearIntruderPhotos()
}

@Database(entities = [AppSettings::class, AlarmHistoryItem::class, IntruderPhotoItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dont_touch_my_phone_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
