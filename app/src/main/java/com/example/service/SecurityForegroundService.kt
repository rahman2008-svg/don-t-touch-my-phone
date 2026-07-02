package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.db.SecurityRepository
import kotlinx.coroutines.*
import java.io.IOException
import kotlin.math.sqrt

class SecurityForegroundService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "SecurityService"
        private const val CHANNEL_ID = "Security_Service_Channel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_START = "ACTION_START_MONITORING"
        const val ACTION_STOP = "ACTION_STOP_MONITORING"
        const val EXTRA_MODE = "EXTRA_MODE"
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var proximity: Sensor? = null

    // Player & alerts
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var cameraManager: CameraManager? = null
    private var mainHandler: Handler? = null

    // State
    private var isMonitoring = false
    private var activeMode: String? = null
    private var sensitivity: Float = 5.0f // 1 to 10
    private var selectedSound: String = "Siren"
    private var isFlashEnabled = true
    private var isVibrationEnabled = true

    // Sensor thresholds and calculations
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var isFirstAccelReading = true

    private var initialProximity: Float? = null

    // For Flash blinking
    private var isFlashOn = false
    private val flashRunnable = object : Runnable {
        override fun run() {
            toggleFlashlight()
            mainHandler?.postDelayed(this, 300)
        }
    }

    // Coroutine Scope for Repository calls
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Charger disconnected receiver
    private val chargerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                if (isMonitoring && activeMode == "Charging Alarm") {
                    triggerSecurityAlarm("Charging Alarm")
                }
            }
        }
    }

    // Clap/Whistle Audio Thread
    private var audioRecord: AudioRecord? = null
    private var isAudioMonitoring = false
    private var audioJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        mainHandler = Handler(Looper.getMainLooper())

        createNotificationChannel()

        // Register power receiver
        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        registerReceiver(chargerReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        activeMode = intent?.getStringExtra(EXTRA_MODE) ?: "Don't Touch"

        if (action == ACTION_START) {
            startMonitoring()
        } else if (action == ACTION_STOP) {
            stopMonitoring()
        }

        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val repo = SecurityRepository(db.appDao())
            val settings = repo.getSettings()
            sensitivity = settings.alarmSensitivity
            selectedSound = settings.selectedSound
            isFlashEnabled = settings.isFlashEnabled
            isVibrationEnabled = settings.isVibrationEnabled

            // Update state manager
            SecurityStateManager.setArmed(true)
            SecurityStateManager.setActiveMode(activeMode)

            // Start foreground notification
            startForeground(NOTIFICATION_ID, createNotification("Security system armed. Monitoring: $activeMode"))

            // Initialize sensors based on mode
            registerSensorsForMode()
        }
    }

    private fun registerSensorsForMode() {
        isFirstAccelReading = true
        initialProximity = null

        when (activeMode) {
            "Don't Touch Mode", "Don't Touch", "Motion Alarm" -> {
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
                gyroscope?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
            "Pocket Mode" -> {
                proximity?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
                // Pocket Mode also uses motion to detect being pulled out
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
            "Who Touched My Phone" -> {
                // Combines motion detection
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
            "Charging Alarm" -> {
                // Works primarily off the BroadcastReceiver (ACTION_POWER_DISCONNECTED)
            }
            "Clap/Whistle" -> {
                startClapDetection()
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        sensorManager.unregisterListener(this)
        stopClapDetection()
        stopAlarmAlerts()

        SecurityStateManager.setArmed(false)
        stopSelf()
    }

    // SensorEventListener overrides
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMonitoring || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                if (isFirstAccelReading) {
                    lastAccelX = x
                    lastAccelY = y
                    lastAccelZ = z
                    isFirstAccelReading = false
                    return
                }

                val deltaX = Math.abs(x - lastAccelX)
                val deltaY = Math.abs(y - lastAccelY)
                val deltaZ = Math.abs(z - lastAccelZ)

                val motionForce = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble())

                // Adjust threshold based on sensitivity (1 to 10 scale).
                // 10 sensitivity -> 0.3 threshold (extremely sensitive)
                // 1 sensitivity -> 3.0 threshold (needs a hard shake)
                val threshold = (11f - sensitivity) * 0.3f

                if (motionForce > threshold) {
                    val modeToTrigger = activeMode ?: "Don't Touch"
                    if (modeToTrigger == "Don't Touch Mode" || modeToTrigger == "Don't Touch" || modeToTrigger == "Motion Alarm" || modeToTrigger == "Who Touched My Phone" || modeToTrigger == "Pocket Mode") {
                        triggerSecurityAlarm(modeToTrigger)
                    }
                }

                lastAccelX = x
                lastAccelY = y
                lastAccelZ = z
            }

            Sensor.TYPE_GYROSCOPE -> {
                val rotX = Math.abs(event.values[0])
                val rotY = Math.abs(event.values[1])
                val rotZ = Math.abs(event.values[2])
                val totalRotation = rotX + rotY + rotZ

                // Threshold scales with sensitivity
                val threshold = (11f - sensitivity) * 0.15f
                if (totalRotation > threshold) {
                    val modeToTrigger = activeMode ?: "Don't Touch"
                    if (modeToTrigger == "Don't Touch Mode" || modeToTrigger == "Don't Touch" || modeToTrigger == "Motion Alarm") {
                        triggerSecurityAlarm(modeToTrigger)
                    }
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val value = event.values[0]
                val maxRange = event.sensor.maximumRange

                if (initialProximity == null) {
                    initialProximity = value
                    return
                }

                // If proximity sensor goes from close (< 2.0 or 0.0) to far (value >= 4.0 or maxRange)
                if (activeMode == "Pocket Mode" && value > 3.0f && initialProximity!! < 3.0f) {
                    triggerSecurityAlarm("Pocket Mode")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Alarm Trigger Mechanism
    private fun triggerSecurityAlarm(mode: String) {
        if (SecurityStateManager.isTriggered.value) return

        Log.d(TAG, "ALARM TRIGGERED BY MODE: $mode")
        SecurityStateManager.triggerAlarm(mode)

        // Log history item
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val repo = SecurityRepository(db.appDao())
            repo.addAlarmHistory(mode)
        }

        // Play loud sound, vibrate, blink flash
        startAlarmAlerts()

        // Launch MainActivity in foreground to snap intruder camera and prompt PIN code
        try {
            val activityIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("LAUNCH_ALARM_SCREEN", true)
                putExtra("TRIGGER_MODE_EXTRA", mode)
            }
            startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity", e)
        }
    }

    private fun startAlarmAlerts() {
        // Stop any current alerts first
        stopAlarmAlerts()

        // Sound Playback
        val rawSoundRes = when (selectedSound) {
            "Police" -> android.provider.Settings.System.DEFAULT_RINGTONE_URI
            "Laser" -> android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            "Digital" -> android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            else -> android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, rawSoundRes)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Media Player failed", e)
        }

        // Vibration
        if (isVibrationEnabled) {
            vibrator?.let { vib ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 400, 200, 400, 200)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0)
                    vib.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 1))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 500, 500), 0)
                }
            }
        }

        // Flash Blinking
        if (isFlashEnabled) {
            mainHandler?.post(flashRunnable)
        }
    }

    private fun stopAlarmAlerts() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()

        mainHandler?.removeCallbacks(flashRunnable)
        turnOffFlashlight()
    }

    private fun toggleFlashlight() {
        try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                isFlashOn = !isFlashOn
                cameraManager?.setTorchMode(cameraId, isFlashOn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
        }
    }

    private fun turnOffFlashlight() {
        try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                cameraManager?.setTorchMode(cameraId, false)
                isFlashOn = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off flashlight", e)
        }
    }

    // Clap/Whistle Audio Detection using simple AudioRecord decibel scanner
    private fun startClapDetection() {
        if (isAudioMonitoring) return
        isAudioMonitoring = true

        val bufferSize = AudioRecord.getMinBufferSize(
            8000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                8000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

            audioRecord?.startRecording()

            audioJob = serviceScope.launch(Dispatchers.Default) {
                val buffer = ShortArray(bufferSize)
                while (isAudioMonitoring) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var maxAmplitude = 0
                        for (i in 0 until readSize) {
                            val value = Math.abs(buffer[i].toInt())
                            if (value > maxAmplitude) {
                                maxAmplitude = value
                            }
                        }

                        // Determine if there is a sudden clap.
                        // Sensitivity 10: lower threshold (~12000)
                        // Sensitivity 1: higher threshold (~28000)
                        val triggerThreshold = 30000 - (sensitivity * 1800)

                        if (maxAmplitude > triggerThreshold) {
                            withContext(Dispatchers.Main) {
                                triggerSecurityAlarm("Clap/Whistle")
                            }
                        }
                    }
                    delay(50)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Record audio permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing clap detection", e)
        }
    }

    private fun stopClapDetection() {
        isAudioMonitoring = false
        audioJob?.cancel()
        audioJob = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recorder", e)
        }
        audioRecord = null
    }

    // Notifications configuration
    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security System Active")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Security Monitoring Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Don't Touch My Phone active in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        unregisterReceiver(chargerReceiver)
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
}
