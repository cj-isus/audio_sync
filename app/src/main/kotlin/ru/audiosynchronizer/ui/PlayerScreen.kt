package ru.audiosynchronizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.audio.PlaybackState
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(engine: AudioEngine) {
    val context = LocalContext.current
    val playbackState by engine.playbackState.collectAsState()
    val currentInfo by engine.currentInfo.collectAsState()
    val positionFrames by engine.positionFrames.collectAsState()
    val latencyMs by engine.latencyMs.collectAsState()

    var isSineOn by remember { mutableStateOf(false) }
    var isEngineStarted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) engine.playFile(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickAudio.launch(arrayOf("audio/*"))
        else permissionDenied = true
    }

    LaunchedEffect(isEngineStarted) {
        while (isEngineStarted || playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.PAUSED) {
            engine.getLatencyMs()
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Player", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            when (playbackState) {
                PlaybackState.STOPPED -> if (isEngineStarted) "Stopped" else "Ready"
                PlaybackState.PLAYING -> "Playing"
                PlaybackState.PAUSED -> "Paused"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        currentInfo?.let { info ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(info.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

            val posSec = if (info.sampleRate > 0) positionFrames / info.sampleRate else 0L
            val durSec = info.durationMs / 1000
            Text(
                "%d:%02d / %d:%02d".format(posSec / 60, posSec % 60, durSec / 60, durSec % 60),
                style = MaterialTheme.typography.bodySmall
            )

            if (info.sampleRate > 0 && info.totalFrames > 0) {
                val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
        }

        if (latencyMs >= 0) {
            Text("Latency: ${"%.1f".format(latencyMs)} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                           else Manifest.permission.READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                    pickAudio.launch(arrayOf("audio/*"))
                } else {
                    permissionDenied = false
                    permissionLauncher.launch(perm)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pick Audio File") }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (playbackState) {
                PlaybackState.PLAYING -> {
                    OutlinedButton(onClick = { engine.pause() }, modifier = Modifier.weight(1f)) { Text("Pause") }
                    OutlinedButton(onClick = { engine.stopPlayback(); isEngineStarted = false }, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
                PlaybackState.PAUSED -> {
                    Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f)) { Text("Resume") }
                    OutlinedButton(onClick = { engine.stopPlayback(); isEngineStarted = false }, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
                PlaybackState.STOPPED -> {
                    if (!isEngineStarted) {
                        Button(onClick = { isEngineStarted = engine.start() }, modifier = Modifier.weight(1f)) { Text("Start Engine") }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                if (!isEngineStarted) isEngineStarted = engine.start()
                isSineOn = !isSineOn
                engine.enableSine(isSineOn)
            },
            enabled = isEngineStarted || playbackState != PlaybackState.STOPPED,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isSineOn) "Stop Sine 440Hz" else "Play Sine 440Hz") }

        if (permissionDenied) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Audio permission required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
