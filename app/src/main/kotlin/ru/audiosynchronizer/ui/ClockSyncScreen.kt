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
import ru.audiosynchronizer.sync.ClockRole
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.ClockSyncState

@Composable
fun ClockSyncScreen(synchronizer: ClockSynchronizer) {
    val syncState by synchronizer.state.collectAsState()
    var leaderIp by remember { mutableStateOf("192.168.") }
    var selectedRole by remember { mutableStateOf(ClockRole.IDLE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Синхронизация часов",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Роль", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = selectedRole == ClockRole.LEADER,
                        onClick = { selectedRole = ClockRole.LEADER },
                        label = { Text("Лидер") },
                        leadingIcon = if (selectedRole == ClockRole.LEADER) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = selectedRole == ClockRole.FOLLOWER,
                        onClick = { selectedRole = ClockRole.FOLLOWER },
                        label = { Text("Ведомый") },
                        leadingIcon = if (selectedRole == ClockRole.FOLLOWER) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }

                if (selectedRole == ClockRole.FOLLOWER) {
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
                    when (selectedRole) {
                        ClockRole.LEADER -> synchronizer.startLeader()
                        ClockRole.FOLLOWER -> if (leaderIp.isNotBlank()) synchronizer.startFollower(leaderIp)
                        ClockRole.IDLE -> {}
                    }
                },
                enabled = syncState.role == ClockRole.IDLE && selectedRole != ClockRole.IDLE,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Запустить")
            }

            OutlinedButton(
                onClick = { synchronizer.stop(); selectedRole = ClockRole.IDLE },
                enabled = syncState.isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Остановить")
            }
        }

        HorizontalDivider()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Метрики", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                MetricRow("Роль", when (syncState.role) {
                    ClockRole.LEADER -> "Лидер"
                    ClockRole.FOLLOWER -> "Ведомый"
                    ClockRole.IDLE -> "Не выбрана"
                })
                MetricRow("Смещение", "${"%.3f".format(syncState.offsetMs)} мс")
                MetricRow("Дрейф", "${"%.3f".format(syncState.driftPpm)} ppm")
                MetricRow("RTT", "${"%.3f".format(syncState.rttMs)} мс")
                MetricRow("Замеры", "${syncState.sampleCount}")
                MetricRow(
                    "Стабильность",
                    if (syncState.isStable) "Да" else "Нет",
                    valueColor = if (syncState.isStable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        if (syncState.error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(syncState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}
