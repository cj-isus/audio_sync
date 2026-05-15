package ru.audiosynchronizer.network

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HotspotError {
    PERMISSION_DENIED,
    TETHERING_DISALLOWED,
    NO_CHANNEL,
    UNSUPPORTED_DEVICE,
    WIFI_MANAGER_UNAVAILABLE,
    GENERIC
}

data class HotspotInfo(
    val ssid: String = "",
    val passphrase: String = "",
    val isRunning: Boolean = false,
    val isStarting: Boolean = false,
    val error: HotspotError? = null
) {
    val errorTitle: String
        get() = when (error) {
            HotspotError.PERMISSION_DENIED -> "Нет разрешения"
            HotspotError.TETHERING_DISALLOWED -> "Тетеринг запрещён"
            HotspotError.NO_CHANNEL -> "Нет доступных каналов Wi-Fi"
            HotspotError.UNSUPPORTED_DEVICE -> "Устройство не поддерживается"
            HotspotError.WIFI_MANAGER_UNAVAILABLE -> "Wi-Fi недоступен"
            HotspotError.GENERIC -> "Ошибка запуска"
            null -> ""
        }

    val errorDescription: String
        get() = when (error) {
            HotspotError.PERMISSION_DENIED -> "Приложению нужно разрешение на использование Wi-Fi. Откройте настройки и включите доступ."
            HotspotError.TETHERING_DISALLOWED -> "Тетеринг (раздача Wi-Fi) запрещён на этом устройстве. Проверьте настройки или обратитесь к администратору."
            HotspotError.NO_CHANNEL -> "Нет свободных Wi-Fi каналов. Попробуйте выключить Wi-Fi и повторить."
            HotspotError.UNSUPPORTED_DEVICE -> "Точка доступа не поддерживается на этой версии Android (нужен Android 8+)."
            HotspotError.WIFI_MANAGER_UNAVAILABLE -> "Wi-Fi менеджер недоступен. Проверьте, что Wi-Fi включён в системе."
            HotspotError.GENERIC -> "Не удалось запустить точку доступа. Попробуйте ещё раз."
            null -> ""
        }

    val needsSettings: Boolean
        get() = error == HotspotError.PERMISSION_DENIED || error == HotspotError.TETHERING_DISALLOWED
}

fun HotspotError?.openSettingsIntent(context: Context) {
    val intent = when (this) {
        HotspotError.PERMISSION_DENIED -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        HotspotError.TETHERING_DISALLOWED -> Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        else -> return
    }
    context.startActivity(intent)
}

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
            ?: run { Log.e(TAG, "WifiManager not available"); _hotspotInfo.value = HotspotInfo(error = HotspotError.WIFI_MANAGER_UNAVAILABLE); return }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _hotspotInfo.value = HotspotInfo(error = HotspotError.UNSUPPORTED_DEVICE)
            return
        }

        _hotspotInfo.value = HotspotInfo(isStarting = true)

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
                    val errorType = when (reason) {
                        2 -> HotspotError.TETHERING_DISALLOWED
                        1 -> HotspotError.NO_CHANNEL
                        else -> HotspotError.GENERIC
                    }
                    _hotspotInfo.value = HotspotInfo(error = errorType)
                    Log.e(TAG, "Hotspot failed: reason=$reason")
                }
            }
            wifiManager.startLocalOnlyHotspot(callback, null)
        } catch (e: SecurityException) {
            _hotspotInfo.value = HotspotInfo(error = HotspotError.PERMISSION_DENIED)
            Log.e(TAG, "No permission for hotspot", e)
        } catch (e: Exception) {
            _hotspotInfo.value = HotspotInfo(error = HotspotError.GENERIC)
            Log.e(TAG, "Hotspot start error", e)
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
