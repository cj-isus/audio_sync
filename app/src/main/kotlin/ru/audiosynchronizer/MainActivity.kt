package ru.audiosynchronizer

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.network.DiscoveryManager
import ru.audiosynchronizer.network.HotspotManager
import ru.audiosynchronizer.service.getRequiredPermissions
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SyncSession
import ru.audiosynchronizer.ui.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MainApp()
                }
            }
        }
    }
}

enum class AppMode { SELECT, LEADER, FOLLOWER }

@Composable
private fun MainApp() {
    val context = LocalContext.current
    val engine = remember { AudioEngine(context) }
    val clockSync = remember { ClockSynchronizer() }
    val session = remember { SyncSession() }
    val connection = remember { ConnectionManager() }
    val discovery = remember { DiscoveryManager(context) }
    val hotspot = remember { HotspotManager(context) }

    var mode by remember { mutableStateOf(AppMode.SELECT) }
    var permissionsRequested by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> permissionsRequested = true }

    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            val missing = getRequiredPermissions().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permLauncher.launch(missing.toTypedArray())
            }
            permissionsRequested = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.close()
            clockSync.stop()
            connection.stop()
            discovery.stopAll()
            hotspot.stopHotspot()
        }
    }

    when (mode) {
        AppMode.SELECT -> ModeSelectScreen(
            onSelectLeader = { mode = AppMode.LEADER },
            onSelectFollower = { mode = AppMode.FOLLOWER }
        )
        AppMode.LEADER -> LeaderScreen(
            engine = engine,
            discovery = discovery,
            hotspot = hotspot,
            connection = connection,
            session = session,
            clockSync = clockSync,
            onBack = { mode = AppMode.SELECT }
        )
        AppMode.FOLLOWER -> FollowerScreen(
            engine = engine,
            connection = connection,
            clockSync = clockSync,
            session = session,
            onBack = { mode = AppMode.SELECT }
        )
    }
}
