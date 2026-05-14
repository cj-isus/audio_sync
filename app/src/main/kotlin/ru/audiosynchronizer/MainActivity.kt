package ru.audiosynchronizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dagger.hilt.android.AndroidEntryPoint
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.network.DiscoveryManager
import ru.audiosynchronizer.network.HotspotManager
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SyncSession
import ru.audiosynchronizer.ui.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainApp()
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun MainApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { AudioEngine(context) }
    val clockSync = remember { ClockSynchronizer() }
    val session = remember { SyncSession() }
    val connection = remember { ConnectionManager() }
    val discovery = remember { DiscoveryManager(context) }
    val hotspot = remember { HotspotManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            engine.close()
            clockSync.stop()
            connection.stop()
            discovery.stopAll()
            hotspot.stopHotspot()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        TabItem("Плеер", Icons.Filled.PlayCircle),
        TabItem("Синхр. часов", Icons.Filled.Schedule),
        TabItem("Синхр. игра", Icons.Filled.Sync),
        TabItem("Устройства", Icons.Filled.Devices),
        TabItem("Сессия", Icons.Filled.Link),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> PlayerScreen(engine)
                1 -> ClockSyncScreen(clockSync)
                2 -> SyncPlayScreen(session, connection, clockSync, engine)
                3 -> DevicesScreen(discovery, hotspot, connection)
                4 -> SessionScreen(session, connection, clockSync, engine)
            }
        }
    }
}
