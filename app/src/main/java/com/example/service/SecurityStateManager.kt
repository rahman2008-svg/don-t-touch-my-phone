package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SecurityStateManager {

    // Is the security system currently active (armed & monitoring)?
    private val _isArmed = MutableStateFlow(false)
    val isArmed: StateFlow<Boolean> = _isArmed

    // Is the alarm currently triggered (siren playing, visual warning active)?
    private val _isTriggered = MutableStateFlow(false)
    val isTriggered: StateFlow<Boolean> = _isTriggered

    // Which mode is active? e.g. "Don't Touch", "Motion Alarm", "Pocket Mode", "Charging Alarm", "Who Touched", "Clap/Whistle"
    private val _activeMode = MutableStateFlow<String?>("Don't Touch")
    val activeMode: StateFlow<String?> = _activeMode

    // The mode that actually triggered the alarm
    private val _triggerMode = MutableStateFlow<String?>("")
    val triggerMode: StateFlow<String?> = _triggerMode

    // A flag to check if intruder photo is captured during current breach
    private val _intruderPhotoCaptured = MutableStateFlow<String?>(null)
    val intruderPhotoCaptured: StateFlow<String?> = _intruderPhotoCaptured

    fun setArmed(armed: Boolean) {
        _isArmed.value = armed
        if (!armed) {
            _isTriggered.value = false
            _triggerMode.value = null
            _intruderPhotoCaptured.value = null
        }
    }

    fun triggerAlarm(mode: String) {
        if (_isArmed.value && !_isTriggered.value) {
            _isTriggered.value = true
            _triggerMode.value = mode
        }
    }

    fun resetAlarm() {
        _isTriggered.value = false
        _triggerMode.value = null
        _intruderPhotoCaptured.value = null
    }

    fun setActiveMode(mode: String?) {
        _activeMode.value = mode
    }

    fun setIntruderPhoto(path: String?) {
        _intruderPhotoCaptured.value = path
    }
}
