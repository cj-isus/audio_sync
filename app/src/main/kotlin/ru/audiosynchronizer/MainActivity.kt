package ru.audiosynchronizer

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                MainApp()
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun MainApp() {
    val context = LocalContext.current
    val engine = remember { AudioEngine(context) }
    val clockSync = remember { ClockSynchronizer() }
    val session = remember { SyncSession() }
    val connection = remember { ConnectionManager() }
    val discovery = remember { DiscoveryManager(context) }
    val hotspot = remember { HotspotManager(context) }

    var permissionsGranted by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        permissionsGranted = allGranted
        if (!allGranted) showPermissionDialog = true
    }

    LaunchedEffect(Unit) {
        val required = getRequiredPermissions()
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    if (showPermissionDialog && !permissionsGranted) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Разрешения необходимы") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Для работы синхронизации нужны:")
                    Text("• Аудиофайлы — для воспроизведения")
                    Text("• Геолокация — для поиска устройств по Wi-Fi")
                    Text("• Камера — для сканирования QR-кода")
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        Text("• Уведомления — для фоновой работы")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val missing = getRequiredPermissions().filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }) { Text("Предоставить") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false; permissionsGranted = true }) { Text("Пропустить") }
            }
        )
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
