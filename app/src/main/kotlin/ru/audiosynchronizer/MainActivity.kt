package ru.audiosynchronizer

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.audio.PlaybackState
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.network.DiscoveryManager
import ru.audiosynchronizer.network.HotspotManager
import ru.audiosynchronizer.service.getRequiredPermissions
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SyncSession
import ru.audiosynchronizer.ui.*
import ru.audiosynchronizer.ui.theme.AudioSynchronizerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            installSplashScreen()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AudioSynchronizerTheme {
                MainApp()
            }
        }
    }
}

enum class AppMode { SELECT, LEADER, FOLLOWER }

@Composable
private fun MainApp() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val engine = remember { AudioEngine(context) }
    val clockSync = remember { ClockSynchronizer() }
    val session = remember { SyncSession() }
    val connection = remember { ConnectionManager() }
    val discovery = remember { DiscoveryManager(context) }
    val hotspot = remember { HotspotManager(context) }

    var mode by remember { mutableStateOf(AppMode.SELECT) }
    var permissionsRequested by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    val playbackState by engine.playbackState.collectAsState()
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.PLAYING) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.close()
            clockSync.stop()
            clockSync.cancelScope()
            connection.stop()
            connection.cancelScope()
            discovery.stopAll()
            hotspot.stopHotspot()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                if (targetState == AppMode.SELECT) {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) togetherWith
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
                } else {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) togetherWith
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
                }
            },
            label = "modeTransition"
        ) { currentMode ->
            when (currentMode) {
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
                    snackbarHostState = snackbarHostState,
                    onBack = {
                        discovery.stopAll()
                        hotspot.stopHotspot()
                        connection.stop()
                        mode = AppMode.SELECT
                    }
                )
                AppMode.FOLLOWER -> FollowerScreen(
                    engine = engine,
                    connection = connection,
                    clockSync = clockSync,
                    session = session,
                    discovery = discovery,
                    snackbarHostState = snackbarHostState,
                    onBack = {
                        discovery.stopDiscovery()
                        connection.stop()
                        session.reset()
                        clockSync.stop()
                        mode = AppMode.SELECT
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}
