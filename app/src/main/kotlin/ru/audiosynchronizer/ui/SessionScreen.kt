package ru.audiosynchronizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
            "Сессия",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Статус", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                MetricRow("Состояние", stateLabel)
                MetricRow("Роль", if (sessionState.isLeader) "Лидер" else "Ведомый")
                MetricRow("Устройства", "${sessionState.connectedDevices}")

                if (sessionState.state == SessionState.FILE_TRANSFER) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { sessionState.fileTransferProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${(sessionState.fileTransferProgress * 100).toInt()}% передано",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider()

        if (sessionState.isLeader) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Управление лидера", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                    val info by engine.currentInfo.collectAsState()
                    val playbackState by engine.playbackState.collectAsState()

                    if (info != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(info!!.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { engine.resume() },
                                enabled = playbackState != PlaybackState.PLAYING
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Играть")
                            }
                            OutlinedButton(
                                onClick = { engine.pause() },
                                enabled = playbackState == PlaybackState.PLAYING
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Пауза")
                            }
                            OutlinedButton(onClick = { engine.stopPlayback() }) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Стоп")
                            }
                        }
                    } else {
                        Text("Файл не выбран", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (connectionState.connectedClients.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Подключённые ведомые:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Статус ведомого", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                    val playbackState by engine.playbackState.collectAsState()
                    val playbackLabel = when (playbackState) {
                        PlaybackState.PLAYING -> "Воспроизведение"
                        PlaybackState.PAUSED -> "Пауза"
                        PlaybackState.STOPPED -> "Остановлен"
                    }
                    MetricRow("Воспроизведение", playbackLabel)

                    if (sessionState.state == SessionState.READY) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Готов к воспроизведению", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                            }
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
