package com.streamapp.core.broadcaster.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.streamapp.MainActivity
import com.streamapp.core.broadcaster.audio.AudioManagerController
import com.streamapp.core.broadcaster.stream.BroadcastManager
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject
    lateinit var broadcastManager: BroadcastManager

    @Inject
    lateinit var audioManager: AudioManagerController

    @Inject
    lateinit var screenPreviewManager: ScreenPreviewManager

    enum class ServiceState {
        IDLE,
        STARTING,
        RUNNING,
        STOPPING
    }

    @Volatile
    private var currentState = ServiceState.IDLE

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_PREVIEW -> {
                if (currentState == ServiceState.STARTING || currentState == ServiceState.RUNNING) {
                    AppLogger.w(LogCategory.STREAMING, "Ignoring duplicate ACTION_START_PREVIEW in state $currentState")
                    return START_NOT_STICKY
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = extractResultData(intent)
                if (resultData == null || resultCode == 0) {
                    AppLogger.e(LogCategory.STREAMING, "Cannot start preview: MediaProjection resultData is missing")
                    stopSelf()
                    return START_NOT_STICKY
                }

                currentState = ServiceState.STARTING
                startForeground(NOTIFICATION_ID, createPreviewNotification())
                try {
                    screenPreviewManager.startPreview(resultCode, resultData)
                    currentState = ServiceState.RUNNING
                } catch (e: Exception) {
                    AppLogger.e(LogCategory.STREAMING, "Failed to start screen preview", e)
                    stopServiceGracefully()
                }
            }

            ACTION_START -> {
                if (currentState == ServiceState.STARTING || currentState == ServiceState.RUNNING) {
                    AppLogger.w(LogCategory.STREAMING, "Ignoring duplicate ACTION_START in state $currentState")
                    return START_NOT_STICKY
                }

                val url = intent.getStringExtra(EXTRA_URL)
                if (url.isNullOrBlank()) {
                    AppLogger.e(LogCategory.STREAMING, "Cannot start stream: RTMP URL is missing")
                    stopSelf()
                    return START_NOT_STICKY
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = extractResultData(intent)
                if (resultData == null || resultCode == 0) {
                    AppLogger.e(LogCategory.STREAMING, "Cannot start stream: MediaProjection resultData is missing")
                    stopSelf()
                    return START_NOT_STICKY
                }

                currentState = ServiceState.STARTING
                startForeground(NOTIFICATION_ID, createNotification())
                try {
                    broadcastManager.initScreenStreaming(resultCode, resultData)
                    broadcastManager.startStreaming(url, isScreenStream = true)
                    currentState = ServiceState.RUNNING
                } catch (e: Exception) {
                    AppLogger.e(LogCategory.STREAMING, "Failed to start screen broadcast", e)
                    stopServiceGracefully()
                }
            }

            ACTION_TOGGLE_MIC -> {
                audioManager.toggleMicMute()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            }

            ACTION_TOGGLE_MUSIC -> {
                audioManager.togglePlayPause()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            }

            ACTION_STOP -> {
                stopServiceGracefully()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServiceGracefully()
        super.onDestroy()
    }

    private fun extractResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        }
    }

    private fun stopServiceGracefully() {
        if (currentState == ServiceState.STOPPING || currentState == ServiceState.IDLE) return
        currentState = ServiceState.STOPPING

        try {
            broadcastManager.stopStreaming(isScreenStream = true)
        } catch (e: Exception) {
            AppLogger.e(LogCategory.STREAMING, "Error stopping broadcast", e)
        } finally {
            try {
                screenPreviewManager.stopPreview()
            } catch (e: Exception) {
                AppLogger.e(LogCategory.STREAMING, "Error stopping preview", e)
            } finally {
                try {
                    audioManager.stopMusic()
                } catch (e: Exception) {
                    AppLogger.e(LogCategory.AUDIO, "Error stopping music", e)
                } finally {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    currentState = ServiceState.IDLE
                    stopSelf()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "StreamApp Live Broadcast",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for active live stream"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotification(): Notification {
        val micTitle = if (audioManager.isMicMuted.value) "🎙️ Вкл. Мик" else "🔇 Замутить"
        val musicTitle = if (audioManager.isPlaying.value) "⏸️ Музыка" else "▶️ Музыка"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Стрим активен (В эфире)")
            .setContentText("Идет захват экрана и звука")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(createOpenAppPendingIntent())
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹️ Стоп", createServiceActionPendingIntent(ACTION_STOP, 1))
            .addAction(android.R.drawable.ic_lock_silent_mode, micTitle, createServiceActionPendingIntent(ACTION_TOGGLE_MIC, 2))
            .addAction(android.R.drawable.ic_media_play, musicTitle, createServiceActionPendingIntent(ACTION_TOGGLE_MUSIC, 3))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createPreviewNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 Захват игры активен")
            .setContentText("Идет предпросмотр экрана в реальном времени")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(createOpenAppPendingIntent())
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹️ Остановить", createServiceActionPendingIntent(ACTION_STOP, 1))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_START_PREVIEW = "ACTION_START_PREVIEW"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_MIC = "ACTION_TOGGLE_MIC"
        const val ACTION_TOGGLE_MUSIC = "ACTION_TOGGLE_MUSIC"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val CHANNEL_ID = "StreamAppBroadcastChannel"
        const val NOTIFICATION_ID = 1001
    }
}
