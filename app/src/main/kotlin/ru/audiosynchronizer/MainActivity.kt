package ru.audiosynchronizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.ui.ClockSyncScreen
import ru.audiosynchronizer.ui.PlayerScreen

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

@Composable
private fun MainApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { AudioEngine(context) }
    val clockSync = remember { ClockSynchronizer() }

    DisposableEffect(Unit) {
        onDispose {
            engine.close()
            clockSync.stop()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Player", "Clock Sync")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = { Text((index + 1).toString()) },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> PlayerScreen(engine)
            1 -> ClockSyncScreen(clockSync)
        }
    }
}
