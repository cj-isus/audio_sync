package ru.audiosynchronizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.audio.PlaybackState
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SessionState
import ru.audiosynchronizer.sync.SyncSession

@Composable
fun SessionScreen(
    session: SyncSession,
    connection: ConnectionManager,
    clockSync: ClockSynchronizer,
    engine: AudioEngine
) {
    val sessionState by session.sessionState.collectAsState()
    val connectionState by connection.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Session", style = MaterialTheme.typography.headlineMedium)

        SessionMetricRow("State", sessionState.state.name)
        SessionMetricRow("Role", if (sessionState.isLeader) "Leader" else "Follower")
        SessionMetricRow("Devices", "${sessionState.connectedDevices}")

        if (sessionState.state == SessionState.FILE_TRANSFER) {
            LinearProgressIndicator(
                progress = { sessionState.fileTransferProgress },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${(sessionState.fileTransferProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()

        if (sessionState.isLeader) {
            Text("Leader Controls", style = MaterialTheme.typography.titleMedium)

            val info by engine.currentInfo.collectAsState()
            val playbackState by engine.playbackState.collectAsState()

            if (info != null) {
                Text("File: ${info!!.displayName}", style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { engine.resume() },
                        enabled = playbackState != PlaybackState.PLAYING
                    ) { Text("Play") }
                    OutlinedButton(
                        onClick = { engine.pause() },
                        enabled = playbackState == PlaybackState.PLAYING
                    ) { Text("Pause") }
                    OutlinedButton(
                        onClick = { engine.stopPlayback() }
                    ) { Text("Stop") }
                }
            }

            if (connectionState.connectedClients.isNotEmpty()) {
                Text("Connected followers:", style = MaterialTheme.typography.bodySmall)
                connectionState.connectedClients.forEach { name ->
                    Text("  - $name", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text("Follower Status", style = MaterialTheme.typography.titleMedium)

            val playbackState by engine.playbackState.collectAsState()
            Text("Playback: ${playbackState.name}", style = MaterialTheme.typography.bodyMedium)

            if (sessionState.state == SessionState.READY) {
                Text("Ready to play", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (sessionState.error != null) {
            Text(sessionState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SessionMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
