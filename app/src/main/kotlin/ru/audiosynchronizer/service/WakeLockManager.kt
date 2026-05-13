package ru.audiosynchronizer.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.annotation.RequiresPermission

class WakeLockManager(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        private const val TAG = "SyncWakeLock"
        private const val WAKE_LOCK_TAG = "AudioSync:playback"
        private const val WIFI_LOCK_TAG = "AudioSync:wifi"
    }

    @RequiresPermission(android.Manifest.permission.WAKE_LOCK)
    fun acquire() {
        release()

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire(30 * 60 * 1000L)
        }

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG).apply {
            acquire()
        }
    }

    fun release() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        try { wifiLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }
}
