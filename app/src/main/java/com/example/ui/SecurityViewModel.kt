package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.AppSettings
import com.example.data.db.SecurityRepository
import com.example.service.SecurityForegroundService
import com.example.service.SecurityStateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SecurityRepository
    val settingsState: StateFlow<AppSettings>
    val alarmHistoryState: StateFlow<List<com.example.data.db.AlarmHistoryItem>>
    val intruderPhotosState: StateFlow<List<com.example.data.db.IntruderPhotoItem>>

    // Splash, Onboarding, Permission, and Main navigation flows
    private val _currentAppScreen = MutableStateFlow("splash") // splash, onboarding, permissions, main, alarm_active
    val currentAppScreen: StateFlow<String> = _currentAppScreen

    // Is the user setting up their PIN for the first time?
    private val _isPinSetupMode = MutableStateFlow(false)
    val isPinSetupMode: StateFlow<Boolean> = _isPinSetupMode

    // PIN inputs
    private val _pinBuffer = MutableStateFlow("")
    val pinBuffer: StateFlow<String> = _pinBuffer

    init {
        val db = AppDatabase.getDatabase(application)
        repository = SecurityRepository(db.appDao())

        settingsState = repository.settingsFlow
            .filterNotNull()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AppSettings()
            )

        alarmHistoryState = repository.alarmHistoryFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        intruderPhotosState = repository.intruderPhotosFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Determine if onboarding is already completed (we store this locally or verify if PIN is set)
        viewModelScope.launch {
            // Wait for DB loading
            val settings = repository.getSettings()
            if (settings.pin.isEmpty()) {
                _currentAppScreen.value = "splash"
            } else {
                _currentAppScreen.value = "main"
            }
        }
    }

    fun navigateTo(screen: String) {
        _currentAppScreen.value = screen
    }

    // Toggle arming of a specific mode
    fun toggleArm(mode: String, context: Context) {
        val currentSettings = settingsState.value
        val isArmedNow = SecurityStateManager.isArmed.value

        if (isArmedNow) {
            // Requesting PIN to disarm, we should show the PIN deactivation input overlay!
            // Handled in the UI container
        } else {
            // Activate the system!
            viewModelScope.launch {
                // Update active mode state
                SecurityStateManager.setActiveMode(mode)

                // Start Foreground Service
                val serviceIntent = Intent(context, SecurityForegroundService::class.java).apply {
                    action = SecurityForegroundService.ACTION_START
                    putExtra(SecurityForegroundService.EXTRA_MODE, mode)
                }
                context.startService(serviceIntent)
            }
        }
    }

    fun disarmSystem(context: Context) {
        val serviceIntent = Intent(context, SecurityForegroundService::class.java).apply {
            action = SecurityForegroundService.ACTION_STOP
        }
        context.startService(serviceIntent)
        SecurityStateManager.setArmed(false)
        SecurityStateManager.resetAlarm()
    }

    // PIN PAD Interactions
    fun onPinKeyPress(char: String) {
        if (_pinBuffer.value.length < 4) {
            _pinBuffer.value += char
        }
    }

    fun onPinDelete() {
        if (_pinBuffer.value.isNotEmpty()) {
            _pinBuffer.value = _pinBuffer.value.dropLast(1)
        }
    }

    fun clearPinBuffer() {
        _pinBuffer.value = ""
    }

    fun savePIN(newPin: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(pin = newPin) }
            _isPinSetupMode.value = false
            clearPinBuffer()
        }
    }

    fun updateSensitivity(value: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(alarmSensitivity = value) }
        }
    }

    fun updateSound(sound: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(selectedSound = sound) }
        }
    }

    fun toggleFlash(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isFlashEnabled = enabled) }
        }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isVibrationEnabled = enabled) }
        }
    }

    fun updateCountdown(seconds: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(countdownSeconds = seconds) }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isDarkMode = enabled) }
        }
    }

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(language = lang) }
        }
    }

    // Logs & photos operations
    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun addIntruderSelfie(photoPath: String, triggeredMode: String) {
        viewModelScope.launch {
            repository.addIntruderPhoto(photoPath, triggeredMode)
            SecurityStateManager.setIntruderPhoto(photoPath)

            // Update latest history entry to note that intruder was captured
            val latestHistory = alarmHistoryState.value.firstOrNull()
            if (latestHistory != null) {
                // Since Room is simple, we just record photo in history too
                repository.addAlarmHistory(
                    mode = triggeredMode,
                    wasIntruderCaptured = true,
                    photoPath = photoPath
                )
            }
        }
    }

    fun deleteSelfie(id: Long) {
        viewModelScope.launch {
            repository.deleteIntruderPhoto(id)
        }
    }

    fun clearAllSelfies() {
        viewModelScope.launch {
            repository.clearIntruderPhotos()
        }
    }
}
