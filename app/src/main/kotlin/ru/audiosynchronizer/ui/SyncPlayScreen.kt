package ru.audiosynchronizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.network.ConnectionState
import ru.audiosynchronizer.protocol.ControlMessage
import ru.audiosynchronizer.protocol.Message
import ru.audiosynchronizer.protocol.TimelineAnchorMessage
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SessionState
import ru.audiosynchronizer.sync.SyncSession
import ru.audiosynchronizer.sync.SyncSessionState
import ru.audiosynchronizer.audio.AudioEngine

@Composable
fun SyncPlayScreen(
    session: SyncSession,
    connection: ConnectionManager,
    clockSync: ClockSynchronizer,
    engine: AudioEngine
) {
    val sessionState by session.sessionState.collectAsState()
    val connectionState by connection.state.collectAsState()

    var isLeader by remember { mutableStateOf(true) }
    var leaderIp by remember { mutableStateOf("192.168.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sync Play", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(
                selected = isLeader,
                onClick = { isLeader = true },
                label = { Text("Leader") }
            )
            FilterChip(
                selected = !isLeader,
                onClick = { isLeader = false },
                label = { Text("Follower") }
            )
        }

        if (!isLeader) {
            OutlinedTextField(
                value = leaderIp,
                onValueChange = { leaderIp = it },
                label = { Text("Leader IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Button(
            onClick = {
                if (isLeader) {
                    session.setLeader(true)
                    connection.startServer()
                } else {
                    session.setLeader(false)
                    connection.connectToLeader(leaderIp)
                }
            },
            enabled = sessionState.state == SessionState.DISCONNECTED
        ) {
            Text(if (isLeader) "Start Server" else "Connect")
        }

        OutlinedButton(
            onClick = { connection.stop(); session.reset() },
            enabled = sessionState.state != SessionState.DISCONNECTED
        ) {
            Text("Disconnect")
        }

        HorizontalDivider()

        MetricRow("State", sessionState.state.name)
        MetricRow("Role", if (sessionState.isLeader) "Leader" else "Follower")
        MetricRow("Devices", "${sessionState.connectedDevices}")

        if (connectionState.connectedClients.isNotEmpty()) {
            Text("Clients: ${connectionState.connectedClients.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }

        if (sessionState.isLeader && sessionState.state != SessionState.DISCONNECTED) {
            HorizontalDivider()
            Text("Leader Controls", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    engine.start()
                    connection.broadcast(Message.Control(ControlMessage(ControlMessage.ACTION_PLAY)))
                }) { Text("Play All") }

                OutlinedButton(onClick = {
                    engine.pause()
                    connection.broadcast(Message.Control(ControlMessage(ControlMessage.ACTION_PAUSE)))
                }) { Text("Pause All") }

                OutlinedButton(onClick = {
                    engine.stopPlayback()
                    connection.broadcast(Message.Control(ControlMessage(ControlMessage.ACTION_STOP)))
                }) { Text("Stop All") }
            }
        }

        if (sessionState.error != null) {
            Text(sessionState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (connectionState.error != null) {
            Text(connectionState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
