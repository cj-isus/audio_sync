package ru.audiosynchronizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import ru.audiosynchronizer.audio.AudioEngine

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val engine = remember { AudioEngine() }
    var isPlaying by remember { mutableStateOf(false) }
    var isSineOn by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableStateOf(-1.0) }
    var statusText by remember { mutableStateOf("Stopped") }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            latencyMs = engine.getLatencyMs()
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.stop()
            engine.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "AudioSynchronizer v2",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isPlaying && latencyMs >= 0) {
            Text(
                "Output latency: ${"%.1f".format(latencyMs)} ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (!isPlaying) {
                        val ok = engine.start()
                        isPlaying = ok
                        statusText = if (ok) "Engine started" else "Start failed"
                    }
                },
                enabled = !isPlaying
            ) {
                Text("Start Engine")
            }

            Button(
                onClick = {
                    engine.stop()
                    isPlaying = false
                    isSineOn = false
                    latencyMs = -1.0
                    statusText = "Stopped"
                },
                enabled = isPlaying
            ) {
                Text("Stop Engine")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                isSineOn = !isSineOn
                engine.enableSine(isSineOn)
                statusText = if (isSineOn) "Sine 440Hz ON" else "Sine OFF"
            },
            enabled = isPlaying
        ) {
            Text(if (isSineOn) "Stop Sine" else "Play Sine 440Hz")
        }
    }
}
