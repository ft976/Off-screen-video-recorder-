package com.example.data

import android.content.Context
import androidx.camera.core.CameraSelector

class SettingsManager(context: Context) {
    private val prefs = context.getApplicationContext().getSharedPreferences("offscreen_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_CAMERA_LENS = "camera_lens"
        const val KEY_VIDEO_QUALITY = "video_quality"
        const val KEY_FRAME_RATE = "frame_rate"
        const val KEY_AUDIO_ENABLED = "audio_enabled"
        const val KEY_SILENT_MODE = "silent_mode"
        const val KEY_HIDE_ON_START = "hide_on_start"
        const val KEY_AUTO_RECOVERY = "auto_recovery"
        const val KEY_BATTERY_SHUTDOWN = "battery_shutdown"
        const val KEY_HIDE_NOTIFICATION = "hide_notification"
        const val KEY_IS_RECORDING = "is_recording"
        const val KEY_ACTIVE_RECORDING_ID = "active_recording_id"
    }

    var cameraLens: Int
        get() = prefs.getInt(KEY_CAMERA_LENS, CameraSelector.LENS_FACING_BACK)
        set(value) = prefs.edit().putInt(KEY_CAMERA_LENS, value).apply()

    var videoQuality: String
        get() = prefs.getString(KEY_VIDEO_QUALITY, "1080p") ?: "1080p"
        set(value) = prefs.edit().putString(KEY_VIDEO_QUALITY, value).apply()

    var frameRate: String
        get() = prefs.getString(KEY_FRAME_RATE, "30 FPS") ?: "30 FPS"
        set(value) = prefs.edit().putString(KEY_FRAME_RATE, value).apply()

    var audioEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_ENABLED, value).apply()

    var silentMode: Boolean
        get() = prefs.getBoolean(KEY_SILENT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SILENT_MODE, value).apply()

    var hideAppOnStart: Boolean
        get() = prefs.getBoolean(KEY_HIDE_ON_START, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_ON_START, value).apply()

    var autoRecovery: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECOVERY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECOVERY, value).apply()

    var batteryShutdown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_SHUTDOWN, true)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_SHUTDOWN, value).apply()

    var hideNotification: Boolean
        get() = prefs.getBoolean(KEY_HIDE_NOTIFICATION, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_NOTIFICATION, value).apply()

    var isRecording: Boolean
        get() = prefs.getBoolean(KEY_IS_RECORDING, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_RECORDING, value).apply()

    var activeRecordingId: Long
        get() = prefs.getLong(KEY_ACTIVE_RECORDING_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_ACTIVE_RECORDING_ID, value).apply()
}
