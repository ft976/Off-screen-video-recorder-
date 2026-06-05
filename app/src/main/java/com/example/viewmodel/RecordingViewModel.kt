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
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLogById(logId)
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
}
