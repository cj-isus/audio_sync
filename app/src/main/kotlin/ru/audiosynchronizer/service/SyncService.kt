package ru.audiosynchronizer.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.LatencyCompensator
import ru.audiosynchronizer.sync.SyncSession
import ru.audiosynchronizer.sync.TimelineManager

class SyncService : Service() {

    private val binder = LocalBinder()
    private lateinit var wakeLockManager: WakeLockManager

    lateinit var audioEngine: AudioEngine
        private set
    lateinit var clockSync: ClockSynchronizer
        private set
    lateinit var session: SyncSession
        private set
    lateinit var connection: ConnectionManager
        private set
    lateinit var timeline: TimelineManager
        private set
    lateinit var latencyCompensator: LatencyCompensator
        private set

    inner class LocalBinder : Binder() {
        fun getService(): SyncService = this@SyncService
    }

    override fun onCreate() {
        super.onCreate()
        wakeLockManager = WakeLockManager(this)
        audioEngine = AudioEngine(this)
        clockSync = ClockSynchronizer()
        session = SyncSession()
        connection = ConnectionManager()
        latencyCompensator = LatencyCompensator()
        timeline = TimelineManager(session, clockSync, latencyCompensator)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SyncNotification.ACTION_STOP -> {
                stopSync()
                return START_NOT_STICKY
            }
        }

        startForeground()
        return START_STICKY
    }

    private fun startForeground() {
        val notification = SyncNotification.buildNotification(
            this,
            "AudioSynchronizer",
            "Service running"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                SyncNotification.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(SyncNotification.NOTIFICATION_ID, notification)
        }
    }

    fun startSync() {
        wakeLockManager.acquire()
        val notification = SyncNotification.buildNotification(
            this,
            "AudioSynchronizer",
            "Sync active"
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(SyncNotification.NOTIFICATION_ID, notification)
    }

    fun stopSync() {
        wakeLockManager.release()
        clockSync.stop()
        connection.stop()
        timeline.stop()
        session.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun updateNotification(title: String, text: String) {
        val notification = SyncNotification.buildNotification(this, title, text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(SyncNotification.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        wakeLockManager.release()
        audioEngine.close()
        clockSync.stop()
        connection.stop()
        super.onDestroy()
    }
}
