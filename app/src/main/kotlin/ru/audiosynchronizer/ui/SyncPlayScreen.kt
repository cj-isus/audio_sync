package ru.audiosynchronizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.protocol.ControlMessage
import ru.audiosynchronizer.protocol.Message
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SessionState
import ru.audiosynchronizer.sync.SyncSession
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

    val stateLabel = when (sessionState.state) {
        SessionState.DISCONNECTED -> "Отключено"
        SessionState.CONNECTING -> "Подключение..."
        SessionState.CLOCK_SYNCING -> "Синхронизация часов"
        SessionState.FILE_TRANSFER -> "Передача файла"
        SessionState.READY -> "Готово"
        SessionState.PLAYING -> "Воспроизведение"
        SessionState.PAUSED -> "Пауза"
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
            "Синхронная игра",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Роль", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = isLeader,
                        onClick = { isLeader = true },
                        label = { Text("Лидер") },
                        leadingIcon = if (isLeader) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = !isLeader,
                        onClick = { isLeader = false },
                        label = { Text("Ведомый") },
                        leadingIcon = if (!isLeader) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }

                if (!isLeader) {
                    OutlinedTextField(
                        value = leaderIp,
                        onValueChange = { leaderIp = it },
                        label = { Text("IP лидера") },
                        placeholder = { Text("192.168.1.100") },
                        leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                enabled = sessionState.state == SessionState.DISCONNECTED,
                modifier = Modifier.weight(1f)
            ) {
                        Icon(
                            if (isLeader) Icons.Filled.Dns else Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isLeader) "Запустить сервер" else "Подключиться")
            }

            OutlinedButton(
                onClick = { connection.stop(); session.reset() },
                enabled = sessionState.state != SessionState.DISCONNECTED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Отключить")
            }
        }

        HorizontalDivider()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Статус", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                MetricRow("Состояние", stateLabel)
                MetricRow("Роль", if (sessionState.isLeader) "Лидер" else "Ведомый")
                MetricRow("Устройства", "${sessionState.connectedDevices}")

                if (connectionState.connectedClients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Подключённые клиенты:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    connectionState.connectedClients.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (sessionState.isLeader && sessionState.state != SessionState.DISCONNECTED) {
            HorizontalDivider()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Управление лидера", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(onClick = {
                            engine.start()
                            connection.broadcast(Message.Control(ControlMessage(ControlMessage.ACTION_PLAY)))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Играть все")
                        }

                        OutlinedButton(onClick = {
                            engine.pause()
                            connection.broadcast(Message.Control(ControlMessage(ControlMessage.ACTION_PAUSE)))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Пауза все")
                        }

                        OutlinedButton(onClick = {
                            engine.stopPlayback()
                            connection.broadcast(Message.Control(ControlMessage(ControlMessage.ACTION_STOP)))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Стоп все")
                        }
                    }
                }
            }
        }

        if (sessionState.error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(sessionState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        if (connectionState.error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(connectionState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
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
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
