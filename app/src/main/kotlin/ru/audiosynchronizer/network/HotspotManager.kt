package ru.audiosynchronizer.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HotspotInfo(
    val ssid: String = "",
    val passphrase: String = "",
    val isRunning: Boolean = false
)

class HotspotManager(private val context: Context) {

    private val _hotspotInfo = MutableStateFlow(HotspotInfo())
    val hotspotInfo: StateFlow<HotspotInfo> = _hotspotInfo.asStateFlow()

    @Volatile
    private var reservation: Any? = null

    companion object {
        private const val TAG = "HotspotManager"
    }

    @RequiresPermission(allOf = [
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.CHANGE_WIFI_STATE
    ])
    fun startHotspot() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: run { Log.e(TAG, "WifiManager not available"); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                        reservation = res
                        val wifiConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            null
                        } else {
                            @Suppress("DEPRECATION")
                            res.wifiConfiguration
                        }
                        val ssid = wifiConfig?.SSID ?: "AudioSync"
                        val passphrase = wifiConfig?.preSharedKey ?: ""
                        _hotspotInfo.value = HotspotInfo(
                            ssid = ssid,
                            passphrase = passphrase,
                            isRunning = true
                        )
                        Log.i(TAG, "Hotspot started: $ssid")
                    }

                    override fun onStopped() {
                        reservation = null
                        _hotspotInfo.value = HotspotInfo()
                        Log.i(TAG, "Hotspot stopped")
                    }

                    override fun onFailed(reason: Int) {
                        reservation = null
                        _hotspotInfo.value = HotspotInfo()
                        Log.e(TAG, "Hotspot failed: reason=$reason")
                    }
                }
                wifiManager.startLocalOnlyHotspot(callback, null)
            } catch (e: SecurityException) {
                Log.e(TAG, "No permission for hotspot", e)
            } catch (e: Exception) {
                Log.e(TAG, "Hotspot start error", e)
            }
        }
    }

    fun stopHotspot() {
        try {
            (reservation as? WifiManager.LocalOnlyHotspotReservation)?.close()
        } catch (_: Exception) {}
        reservation = null
        _hotspotInfo.value = HotspotInfo()
    }
}
