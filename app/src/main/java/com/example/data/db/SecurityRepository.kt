package com.example.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class SecurityRepository(private val appDao: AppDao) {

    val settingsFlow: Flow<AppSettings?> = appDao.getSettingsFlow()
    val alarmHistoryFlow: Flow<List<AlarmHistoryItem>> = appDao.getAlarmHistory()
    val intruderPhotosFlow: Flow<List<IntruderPhotoItem>> = appDao.getIntruderPhotos()

    suspend fun getSettings(): AppSettings {
        return appDao.getSettings() ?: AppSettings().also {
            appDao.saveSettings(it)
        }
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        val current = getSettings()
        appDao.saveSettings(update(current))
    }

    suspend fun addAlarmHistory(mode: String, wasIntruderCaptured: Boolean = false, photoPath: String? = null): Long {
        val item = AlarmHistoryItem(mode = mode, wasIntruderCaptured = wasIntruderCaptured, intruderPhotoPath = photoPath)
        return appDao.insertAlarmHistory(item)
    }

    suspend fun clearHistory() {
        appDao.clearAlarmHistory()
    }

    suspend fun addIntruderPhoto(photoPath: String, modeTriggered: String) {
        val photo = IntruderPhotoItem(imagePath = photoPath, modeTriggered = modeTriggered)
        appDao.insertIntruderPhoto(photo)
    }

    suspend fun deleteIntruderPhoto(id: Long) {
        appDao.deleteIntruderPhoto(id)
    }

    suspend fun clearIntruderPhotos() {
        appDao.clearIntruderPhotos()
    }
}
