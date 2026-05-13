package ru.audiosynchronizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.audiosynchronizer.network.*

@Composable
fun DevicesScreen(
    discovery: DiscoveryManager,
    hotspot: HotspotManager,
    connection: ConnectionManager
) {
    val context = LocalContext.current
    val devices by discovery.discoveredDevices.collectAsState()
    val isDiscovering by discovery.isDiscovering.collectAsState()
    val isRegistered by discovery.isRegistered.collectAsState()
    val hotspotInfo by hotspot.hotspotInfo.collectAsState()
    val connectionState by connection.state.collectAsState()

    var isLeader by remember { mutableStateOf(true) }
    var manualIp by remember { mutableStateOf("192.168.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Devices", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(selected = isLeader, onClick = { isLeader = true }, label = { Text("Leader") })
            FilterChip(selected = !isLeader, onClick = { isLeader = false }, label = { Text("Follower") })
        }

        if (isLeader) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        discovery.registerService(ConnectionManager.PORT)
                        hotspot.startHotspot()
                    },
                    enabled = !isRegistered
                ) { Text("Start Hotspot") }

                OutlinedButton(
                    onClick = { discovery.stopDiscovery(); hotspot.stopHotspot() },
                    enabled = isRegistered || hotspotInfo.isRunning
                ) { Text("Stop") }
            }

            if (hotspotInfo.isRunning) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hotspot Active", style = MaterialTheme.typography.titleMedium)
                        Text("SSID: ${hotspotInfo.ssid}")
                        Text("Password: ${hotspotInfo.passphrase}")
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { discovery.startDiscovery() },
                    enabled = !isDiscovering
                ) { Text("Scan") }

                OutlinedButton(
                    onClick = { discovery.stopDiscovery() },
                    enabled = isDiscovering
                ) { Text("Stop Scan") }
            }

            if (devices.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        ListItem(
                            headlineContent = { Text(device.serviceName) },
                            supportingContent = { Text("${device.host}:${device.port}") },
                            trailingContent = {
                                FilledTonalButton(onClick = {
                                    connection.connectToLeader(device.host)
                                }) { Text("Connect") }
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("Manual IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = { connection.connectToLeader(manualIp) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Connect to IP") }
        }

        if (connectionState.error != null) {
            Text(connectionState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
