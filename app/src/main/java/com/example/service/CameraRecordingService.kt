package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.R
import com.example.data.RecordingDatabase
import com.example.data.RecordingLog
import com.example.data.RecordingRepository
import com.example.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraRecordingService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "CameraRecordingService"
        private const val NOTIFICATION_ID = 8812
        private const val CHANNEL_ID = "offscreen_recording_channel"

        // Actions
        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"
        const val ACTION_ADD_MARKER = "com.example.action.ADD_MARKER"

        // Live state exposures for in-app monitoring
        private val _recordingDurationMs = MutableStateFlow(0L)
        val recordingDurationMs: StateFlow<Long> = _recordingDurationMs

        private val _isRecordingActive = MutableStateFlow(false)
        val isRecordingActive: StateFlow<Boolean> = _isRecordingActive

        private val _currentStatus = MutableStateFlow("Stopped")
        val currentStatus: StateFlow<String> = _currentStatus

        var currentMarkers = mutableListOf<String>()
    }

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var settingsManager: SettingsManager
    private lateinit var repository: RecordingRepository
    private var cameraExecutor: ExecutorService? = null
    private var activeRecording: Recording? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentFileName = ""
    private var startTimestamp = 0L
    private var lastNotificationSeconds = -1L

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    // Watchers for low battery to prevent file corruption
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = (level.toFloat() / scale.toFloat() * 100).toInt()
            if (pct <= 5 && settingsManager.batteryShutdown && _isRecordingActive.value) {
                Log.w(TAG, "Battery low ($pct%). Stopping recording gracefully to protect files.")
                stopRecording()
                Toast.makeText(context, "Low battery! Recording saved gracefully.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        settingsManager = SettingsManager(this)
        val db = RecordingDatabase.getDatabase(this)
        repository = RecordingRepository(db.recordingDao())
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Acquire WakeLock to keep CPU alive while screen goes black
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OffScreenRecorder::RecordingLock").apply {
            setReferenceCounted(false)
        }

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // Ensure we immediately display a persistent notification to fulfill foreground service requirements
        val initialNotification = buildNotification("Preparing recorder...", "Please wait", 0)
        startForeground(NOTIFICATION_ID, initialNotification)

        val action = intent?.action ?: ACTION_START_RECORDING
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START_RECORDING -> {
                if (!_isRecordingActive.value) {
                    startRecording()
                } else {
                    Log.i(TAG, "Recording already in progress, ignore.")
                }
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            ACTION_ADD_MARKER -> {
                addMarkerOfCurrentTime()
            }
        }

        return START_NOT_STICKY
    }

    private fun addMarkerOfCurrentTime() {
        if (!_isRecordingActive.value) return
        val currentSecs = _recordingDurationMs.value / 1000L
        val text = "Timestamp marker at ${currentSecs}s"
        currentMarkers.add("$currentSecs:$text")
        Toast.makeText(this, "Marker set in background at ${currentSecs}s", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Marker added: $currentSecs")
    }

    private fun startRecording() {
        try {
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes, refreshed iteratively
            _isRecordingActive.value = true
            _currentStatus.value = "Recording"
            startTimestamp = System.currentTimeMillis()
            currentMarkers.clear()
            _recordingDurationMs.value = 0L
            lastNotificationSeconds = -1L

            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    bindRecordingUseCases(cameraProvider)
                } catch (e: Exception) {
                    Log.e(TAG, "CameraProvider initialization failed", e)
                    handleErrorAndStop("Camera error: ${e.localizedMessage}")
                }
            }, ContextCompat.getMainExecutor(this))

        } catch (e: Exception) {
            Log.e(TAG, "Failed startRecording flow", e)
            handleErrorAndStop("Failed to initialize recording.")
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun bindRecordingUseCases(cameraProvider: ProcessCameraProvider) {
        val lensFacing = settingsManager.cameraLens
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val quality = when (settingsManager.videoQuality) {
            "1080p" -> Quality.FHD
            "720p" -> Quality.HD
            "480p" -> Quality.SD
            "360p" -> Quality.LOWEST
            "214p" -> Quality.LOWEST
            else -> Quality.FHD
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()

        val videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(this, selector, videoCapture)

            // Dynamic Frame Rate / FPS configuration to conserve battery
            val frameRateSetting = settingsManager.frameRate
            val targetFps = when {
                frameRateSetting.contains("15") -> 15
                frameRateSetting.contains("24") -> 24
                else -> 30
            }

            try {
                val camera2Control = Camera2CameraControl.from(camera.cameraControl)
                val options = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(targetFps, targetFps)
                    )
                    .build()
                camera2Control.captureRequestOptions = options
                Log.i(TAG, "Applied battery-saving frame rate restriction: $targetFps FPS (Preset: $frameRateSetting)")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to apply custom camera2 frame rate filter: ${ex.message}", ex)
            }

            triggerCameraXRecording(videoCapture)

        } catch (e: Exception) {
            Log.e(TAG, "Error binding CameraX use cases", e)
            handleErrorAndStop("Camera binding error: ${e.localizedMessage}")
        }
    }

    private fun triggerCameraXRecording(videoCapture: VideoCapture<Recorder>) {
        val timestampText = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentFileName = "VID_$timestampText"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$currentFileName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OffScreenRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        try {
            activeRecording = videoCapture.output
                .prepareRecording(this, outputOptions)
                .apply {
                    if (settingsManager.audioEnabled) {
                        try {
                            withAudioEnabled()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Microphone permission is lacking for audio capture", e)
                        }
                    }
                }
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            settingsManager.isRecording = true
                            Log.d(TAG, "VideoRecordEvent.Start recording initialized")
                        }
                        is VideoRecordEvent.Status -> {
                            val durationNanos = event.recordingStats.recordedDurationNanos
                            val durationMs = durationNanos / 1_000_000
                            _recordingDurationMs.value = durationMs
                            
                            val sec = durationMs / 1000
                            if (sec != lastNotificationSeconds) {
                                lastNotificationSeconds = sec
                                val min = sec / 60
                                val secondsStr = String.format("%02d:%02d", min, sec % 60)
                                updateNotification("Active Background Record", "Elapsed Duration: $secondsStr", durationMs)
                            }
                        }
                        is VideoRecordEvent.Finalize -> {
                            settingsManager.isRecording = false
                            _isRecordingActive.value = false
                            _currentStatus.value = "Stopped"

                            val resultUri = event.outputResults.outputUri
                            if (event.hasError()) {
                                Log.e(TAG, "VideoRecordEvent.Finalize with error code: ${event.error}")
                                if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                                    Toast.makeText(this, "Recording finalized with code context: ${event.error}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Log.i(TAG, "Recording Success. Saving file URI: $resultUri")
                                finalizeAndSaveRecording(resultUri)
                            }
                            stopSelf()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Recorder preparation launch failed", e)
            handleErrorAndStop("Failed to launch video recording pipeline.")
        }
    }

    private fun finalizeAndSaveRecording(uri: android.net.Uri) {
        val elapsed = System.currentTimeMillis() - startTimestamp
        val filenameWithExt = "$currentFileName.mp4"

        // Mark the MediaStore entry as no longer pending if Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, finalizeValues, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Could not remove MediaStore pending flag", e)
            }
        }

        // Build markers JSON string format: second:text;second:text
        val markersString = currentMarkers.joinToString(";")

        // Save entry directly to our local Room log list
        lifecycleScope.launch(Dispatchers.IO) {
            val log = RecordingLog(
                filePath = uri.toString(),
                fileName = filenameWithExt,
                durationMs = elapsed,
                resolution = settingsManager.videoQuality,
                timestamp = System.currentTimeMillis(),
                markersJson = markersString,
                notes = "Recorded via Background Engine."
            )
            val insertId = repository.insertLog(log)
            Log.d(TAG, "Inserted recording log into DB with ID: $insertId")
        }
        
        Toast.makeText(this, "Filming saved to library!", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        Log.i(TAG, "Stopping active recording.")
        _isRecordingActive.value = false
        settingsManager.isRecording = false
        activeRecording?.stop()
        activeRecording = null
        releaseWakelock()
    }

    private fun handleErrorAndStop(error: String) {
        Log.e(TAG, "HandleErrorAndStop triggered: $error")
        _currentStatus.value = "Stopped (Error)"
        _isRecordingActive.value = false
        settingsManager.isRecording = false
        
        val notification = buildNotification("Recording Error", error, 0)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        
        activeRecording?.stop()
        activeRecording = null
        stopSelf()
    }

    private fun buildNotification(title: String, text: String, durationMs: Long): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action
        val stopIntent = Intent(this, CameraRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Marker Action
        val markerIntent = Intent(this, CameraRecordingService::class.java).apply {
            action = ACTION_ADD_MARKER
        }
        val markerPendingIntent = PendingIntent.getService(
            this, 2, markerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sec = durationMs / 1000
        val min = sec / 60
        val subtext = "● REC (${String.format("%02d:%02d", min, sec % 60)})"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (durationMs > 0) subtext else null)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

        if (_isRecordingActive.value) {
            builder.addAction(android.R.drawable.ic_media_pause, "Mark Event", markerPendingIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Recording", stopPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(title: String, text: String, durationMs: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, text, durationMs))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "OffScreen Video Recorder Background Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows recording timer and control buttons during background recording."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun releaseWakelock() {
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock", e)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        unregisterReceiver(batteryReceiver)
        stopRecording()
        
        cameraExecutor?.shutdown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
