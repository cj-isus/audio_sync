package ru.audiosynchronizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Плеер",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (playbackState) {
                    PlaybackState.PLAYING -> MaterialTheme.colorScheme.primaryContainer
                    PlaybackState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                    PlaybackState.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = when (playbackState) {
                        PlaybackState.PLAYING -> Icons.Filled.PlayArrow
                        PlaybackState.PAUSED -> Icons.Filled.Pause
                        PlaybackState.STOPPED -> if (isEngineStarted) Icons.Filled.Stop else Icons.Filled.FiberManualRecord
                    },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = when (playbackState) {
                        PlaybackState.PLAYING -> MaterialTheme.colorScheme.primary
                        PlaybackState.PAUSED -> MaterialTheme.colorScheme.tertiary
                        PlaybackState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    when (playbackState) {
                        PlaybackState.STOPPED -> if (isEngineStarted) "Остановлен" else "Готов"
                        PlaybackState.PLAYING -> "Воспроизведение"
                        PlaybackState.PAUSED -> "Пауза"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                currentInfo?.let { info ->
                    Text(
                        info.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    val posSec = if (info.sampleRate > 0) positionFrames / info.sampleRate else 0L
                    val durSec = info.durationMs / 1000
                    Text(
                        "%d:%02d / %d:%02d".format(posSec / 60, posSec % 60, durSec / 60, durSec % 60),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        currentInfo?.let { info ->
            if (info.sampleRate > 0 && info.totalFrames > 0) {
                val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (latencyMs >= 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Задержка: ${"%.1f".format(latencyMs)} мс",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
        ) {
            Icon(Icons.Filled.AudioFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Выбрать аудиофайл")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (playbackState) {
                PlaybackState.PLAYING -> {
                    OutlinedButton(onClick = { engine.pause() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Пауза")
                    }
                    OutlinedButton(onClick = { engine.stopPlayback(); isEngineStarted = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Стоп")
                    }
                }
                PlaybackState.PAUSED -> {
                    Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Продолжить")
                    }
                    OutlinedButton(onClick = { engine.stopPlayback(); isEngineStarted = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Стоп")
                    }
                }
                PlaybackState.STOPPED -> {
                    if (!isEngineStarted) {
                        Button(onClick = { isEngineStarted = engine.start() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Запустить")
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                if (!isEngineStarted) isEngineStarted = engine.start()
                isSineOn = !isSineOn
                engine.enableSine(isSineOn)
            },
            enabled = isEngineStarted || playbackState != PlaybackState.STOPPED,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (isSineOn) Icons.Filled.Stop else Icons.Filled.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isSineOn) "Стоп синус 440 Гц" else "Тест синус 440 Гц")
        }

        if (permissionDenied) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Требуется разрешение на доступ к аудио",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
