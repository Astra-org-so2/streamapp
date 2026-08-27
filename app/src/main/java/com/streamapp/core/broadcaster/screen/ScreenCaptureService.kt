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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_PREVIEW -> {
                startForeground(NOTIFICATION_ID, createPreviewNotification())
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
                }
                if (resultData != null) {
                    screenPreviewManager.startPreview(resultCode, resultData)
                }
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
                }
                
                if (resultData != null) {
                    broadcastManager.initScreenStreaming(resultCode, resultData)
                    broadcastManager.startStreaming(url, isScreenStream = true)
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
                screenPreviewManager.stopPreview()
                broadcastManager.stopStreaming(isScreenStream = true)
                audioManager.stopMusic()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop Stream
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Toggle Mic
        val micIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_TOGGLE_MIC
        }
        val micPendingIntent = PendingIntent.getService(
            this, 2, micIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Toggle Music
        val musicIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_TOGGLE_MUSIC
        }
        val musicPendingIntent = PendingIntent.getService(
            this, 3, musicIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val micTitle = if (audioManager.isMicMuted.value) "🎙️ Вкл. Мик" else "🔇 Замутить"
        val musicTitle = if (audioManager.isPlaying.value) "⏸️ Музыка" else "▶️ Музыка"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 Стрим активен (В эфире)")
            .setContentText("Идет захват экрана и звука")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹️ Стоп", stopPendingIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode, micTitle, micPendingIntent)
            .addAction(android.R.drawable.ic_media_play, musicTitle, musicPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createPreviewNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 Захват игры активен")
            .setContentText("Идет предпросмотр экрана в реальном времени")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⏹️ Остановить", stopPendingIntent)
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
