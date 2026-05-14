package ru.audiosynchronizer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.audiosynchronizer.MainActivity
import ru.audiosynchronizer.R

object SyncNotification {

    private const val CHANNEL_ID = "audio_sync"
    private const val CHANNEL_NAME = "Синхронизация аудио"
    const val NOTIFICATION_ID = 1
    const val ACTION_STOP = "ru.audiosynchronizer.ACTION_STOP"

    @Volatile
    private var channelCreated = false

    fun createChannel(context: Context) {
        if (channelCreated) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис синхронизации аудио"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        channelCreated = true
    }

    fun buildNotification(context: Context, title: String, text: String): Notification {
        createChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, SyncService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPi)
            .addAction(0, "Остановить", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification(context: Context, title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(context, title, text)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
