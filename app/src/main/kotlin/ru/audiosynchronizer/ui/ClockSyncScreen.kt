package ru.audiosynchronizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Clock Synchronization",
            style = MaterialTheme.typography.headlineMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(
                selected = selectedRole == ClockRole.LEADER,
                onClick = { selectedRole = ClockRole.LEADER },
                label = { Text("Leader") }
            )
            FilterChip(
                selected = selectedRole == ClockRole.FOLLOWER,
                onClick = { selectedRole = ClockRole.FOLLOWER },
                label = { Text("Follower") }
            )
        }

        if (selectedRole == ClockRole.FOLLOWER) {
            OutlinedTextField(
                value = leaderIp,
                onValueChange = { leaderIp = it },
                label = { Text("Leader IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    when (selectedRole) {
                        ClockRole.LEADER -> synchronizer.startLeader()
                        ClockRole.FOLLOWER -> if (leaderIp.isNotBlank()) synchronizer.startFollower(leaderIp)
                        ClockRole.IDLE -> {}
                    }
                },
                enabled = syncState.role == ClockRole.IDLE && selectedRole != ClockRole.IDLE
            ) {
                Text("Start Sync")
            }

            OutlinedButton(
                onClick = { synchronizer.stop(); selectedRole = ClockRole.IDLE },
                enabled = syncState.isSyncing
            ) {
                Text("Stop")
            }
        }

        HorizontalDivider()

        MetricRow("Role", syncState.role.name)
        MetricRow("Offset", "${"%.3f".format(syncState.offsetMs)} ms")
        MetricRow("Drift", "${"%.3f".format(syncState.driftPpm)} ppm")
        MetricRow("RTT", "${"%.3f".format(syncState.rttMs)} ms")
        MetricRow("Samples", "${syncState.sampleCount}")
        MetricRow("Stable", if (syncState.isStable) "Yes" else "No")

        if (syncState.error != null) {
            Text(
                syncState.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
