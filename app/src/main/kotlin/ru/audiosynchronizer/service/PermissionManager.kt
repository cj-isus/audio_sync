package ru.audiosynchronizer.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

data class PermissionState(
    val audio: Boolean = false,
    val location: Boolean = false,
    val camera: Boolean = false,
    val notification: Boolean = false,
    val storage: Boolean = false,
    val allGranted: Boolean = false
)

@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(checkPermissions(context)) }

    LaunchedEffect(Unit) {
        state = checkPermissions(context)
    }

    return state
}

fun checkPermissions(context: Context): PermissionState {
    val audio = hasPermission(context, getAudioPermission())
    val location = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val camera = hasPermission(context, Manifest.permission.CAMERA)
    val notification = if (Build.VERSION.SDK_INT >= 33) {
        hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    } else true
    val storage = if (Build.VERSION.SDK_INT >= 33) true else {
        hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return PermissionState(
        audio = audio,
        location = location,
        camera = camera,
        notification = notification,
        storage = storage,
        allGranted = audio && location && camera && notification && storage
    )
}

fun getRequiredPermissions(): List<String> {
    val perms = mutableListOf(
        getAudioPermission(),
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA
    )
    if (Build.VERSION.SDK_INT >= 33) {
        perms.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT < 33) {
        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return perms
}

fun getAudioPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context, permission
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
