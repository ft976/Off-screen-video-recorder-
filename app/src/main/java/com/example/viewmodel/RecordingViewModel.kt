package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.CameraRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val database = RecordingDatabase.getDatabase(application)
    private val repository = RecordingRepository(database.recordingDao())

    // Database Logs Flow
    val recordingLogs: StateFlow<List<RecordingLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Real-time Battery Level Monitoring
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isBatteryCharging = MutableStateFlow(false)
    val isBatteryCharging: StateFlow<Boolean> = _isBatteryCharging.asStateFlow()

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                100
            }
            _batteryLevel.value = pct

            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            _isBatteryCharging.value = charging
        }
    }

    init {
        try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = application.registerReceiver(batteryReceiver, filter)
            stickyIntent?.let { intent ->
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) {
                    (level * 100 / scale.toFloat()).toInt()
                } else {
                    100
                }
                _batteryLevel.value = pct

                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL
                _isBatteryCharging.value = charging
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Expose flows for Settings and Status
    private val _cameraLens = MutableStateFlow(settingsManager.cameraLens)
    val cameraLens: StateFlow<Int> = _cameraLens.asStateFlow()

    private val _videoQuality = MutableStateFlow(settingsManager.videoQuality)
    val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()

    private val _frameRate = MutableStateFlow(settingsManager.frameRate)
    val frameRate: StateFlow<String> = _frameRate.asStateFlow()

    private val _audioEnabled = MutableStateFlow(settingsManager.audioEnabled)
    val audioEnabled: StateFlow<Boolean> = _audioEnabled.asStateFlow()

    private val _silentMode = MutableStateFlow(settingsManager.silentMode)
    val silentMode: StateFlow<Boolean> = _silentMode.asStateFlow()

    private val _hideAppOnStart = MutableStateFlow(settingsManager.hideAppOnStart)
    val hideAppOnStart: StateFlow<Boolean> = _hideAppOnStart.asStateFlow()

    private val _autoRecovery = MutableStateFlow(settingsManager.autoRecovery)
    val autoRecovery: StateFlow<Boolean> = _autoRecovery.asStateFlow()

    private val _batteryShutdown = MutableStateFlow(settingsManager.batteryShutdown)
    val batteryShutdown: StateFlow<Boolean> = _batteryShutdown.asStateFlow()

    // Service Status Flow Integration
    val isRecordingRunning: StateFlow<Boolean> = CameraRecordingService.isRecordingActive
    val activeDurationMs: StateFlow<Long> = CameraRecordingService.recordingDurationMs
    val serviceStatus: StateFlow<String> = CameraRecordingService.currentStatus

    fun setCameraLens(lens: Int) {
        settingsManager.cameraLens = lens
        _cameraLens.value = lens
        
        // Auto-correct quality & frame rate if they aren't supported on this lens!
        val resolutions = getSupportedResolutions()
        if (_videoQuality.value !in resolutions && resolutions.isNotEmpty()) {
            setVideoQuality(resolutions.first())
        }
        val fpsList = getSupportedFrameRates()
        if (_frameRate.value !in fpsList && fpsList.isNotEmpty()) {
            setFrameRate(fpsList.first())
        }
    }

    // Dynamic resolution query from Selected Physical Sensor
    fun getSupportedResolutions(): List<String> {
        val context = getApplication<Application>()
        val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            ?: return listOf("480p", "720p", "1080p")
        try {
            val lensFacing = settingsManager.cameraLens
            var targetCameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                val requestedFacing = if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                } else {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                }
                if (facing == requestedFacing) {
                    targetCameraId = id
                    break
                }
            }
            if (targetCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                targetCameraId = cameraManager.cameraIdList[0]
            }
            if (targetCameraId == null) return listOf("480p", "720p")

            val characteristics = cameraManager.getCameraCharacteristics(targetCameraId)
            val map = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return listOf("480p", "720p")

            val sizes = map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                ?: map.getOutputSizes(android.media.MediaRecorder::class.java)
                ?: return listOf("480p", "720p")

            val resolutions = mutableListOf<String>()
            val widthHeights = sizes.map { Pair(it.width, it.height) }

            if (widthHeights.any { (w, h) -> (w >= 1920 && h >= 1080) || (w >= 1080 && h >= 1920) }) {
                resolutions.add("1080p")
            }
            if (widthHeights.any { (w, h) -> (w >= 1280 && h >= 720) || (w >= 720 && h >= 1280) }) {
                resolutions.add("720p")
            }
            if (widthHeights.any { (w, h) -> (w >= 720 && h >= 480) || (w >= 480 && h >= 720) || (w >= 640 && h >= 480) || (w >= 480 && h >= 640) }) {
                resolutions.add("480p")
            }
            resolutions.add("360p")

            val uniqueList = resolutions.distinct()
            return if (uniqueList.isNotEmpty()) uniqueList else listOf("480p")
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf("480p", "720p", "1080p")
        }
    }

    // Dynamic FPS query from Selected Physical Sensor
    fun getSupportedFrameRates(): List<String> {
        val context = getApplication<Application>()
        val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            ?: return listOf("15 FPS", "24 FPS", "30 FPS")
        try {
            val lensFacing = settingsManager.cameraLens
            var targetCameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                val requestedFacing = if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                } else {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                }
                if (facing == requestedFacing) {
                    targetCameraId = id
                    break
                }
            }
            if (targetCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                targetCameraId = cameraManager.cameraIdList[0]
            }
            if (targetCameraId == null) return listOf("30 FPS")

            val characteristics = cameraManager.getCameraCharacteristics(targetCameraId)
            val fpsRanges = characteristics.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: return listOf("30 FPS")

            val supported = mutableListOf<String>()
            val uppers = fpsRanges.map { it.upper }.distinct()
            
            if (uppers.any { it >= 30 }) {
                supported.add("30 FPS")
            }
            if (uppers.any { it >= 24 }) {
                supported.add("24 FPS")
            }
            if (uppers.any { it >= 15 }) {
                supported.add("15 FPS")
            }
            
            val result = supported.distinct().sortedDescending()
            return if (result.isNotEmpty()) result else listOf("30 FPS")
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf("15 FPS", "24 FPS", "30 FPS")
        }
    }

    fun setVideoQuality(quality: String) {
        settingsManager.videoQuality = quality
        _videoQuality.value = quality
    }

    fun setFrameRate(fps: String) {
        settingsManager.frameRate = fps
        _frameRate.value = fps
    }

    fun setAudioEnabled(enabled: Boolean) {
        settingsManager.audioEnabled = enabled
        _audioEnabled.value = enabled
    }

    fun setSilentMode(enabled: Boolean) {
        settingsManager.silentMode = enabled
        _silentMode.value = enabled
    }

    fun setHideAppOnStart(enabled: Boolean) {
        settingsManager.hideAppOnStart = enabled
        _hideAppOnStart.value = enabled
    }

    fun setAutoRecovery(enabled: Boolean) {
        settingsManager.autoRecovery = enabled
        _autoRecovery.value = enabled
    }

    fun setBatteryShutdown(enabled: Boolean) {
        settingsManager.batteryShutdown = enabled
        _batteryShutdown.value = enabled
    }

    fun startBackgroundRecording() {
        if (!isRecordingRunning.value) {
            val intent = Intent(getApplication(), CameraRecordingService::class.java).apply {
                action = CameraRecordingService.ACTION_START_RECORDING
            }
            try {
                getApplication<Application>().startService(intent)
            } catch (e: Exception) {
                // If on Android 8.0+ starting background fails, use standard startForegroundService
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            }
        }
    }

    fun stopBackgroundRecording() {
        if (isRecordingRunning.value) {
            val intent = Intent(getApplication(), CameraRecordingService::class.java).apply {
                action = CameraRecordingService.ACTION_STOP_RECORDING
            }
            getApplication<Application>().startService(intent)
        }
    }

    fun triggerMarker() {
        if (isRecordingRunning.value) {
            val intent = Intent(getApplication(), CameraRecordingService::class.java).apply {
                action = CameraRecordingService.ACTION_ADD_MARKER
            }
            getApplication<Application>().startService(intent)
        }
    }

    fun deleteLog(logId: Long) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val log = repository.getLogById(logId)
            log?.let {
                try {
                    if (it.filePath.startsWith("content://")) {
                        context.contentResolver.delete(android.net.Uri.parse(it.filePath), null, null)
                    } else {
                        val f = java.io.File(it.filePath)
                        if (f.exists()) f.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                repository.deleteLogById(logId)
            }
        }
    }

    fun updateNotes(logId: Long, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getLogById(logId)?.let { log ->
                val updatedLog = log.copy(notes = notes)
                repository.updateLog(updatedLog)
            }
        }
    }

    fun addCustomMarker(logId: Long, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getLogById(logId)?.let { log ->
                val seconds = System.currentTimeMillis() / 1000 % 60 // fallback mock offset sec
                val item = "$seconds:$text"
                val newMarkers = if (log.markersJson.isEmpty()) item else "${log.markersJson};$item"
                val updated = log.copy(markersJson = newMarkers)
                repository.updateLog(updated)
            }
        }
    }

    fun clearAllLogsFromSystem() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val logs = recordingLogs.value
            for (log in logs) {
                try {
                    if (log.filePath.startsWith("content://")) {
                        context.contentResolver.delete(android.net.Uri.parse(log.filePath), null, null)
                    } else {
                        val f = java.io.File(log.filePath)
                        if (f.exists()) f.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                repository.deleteLogById(log.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
