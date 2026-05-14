package ru.audiosynchronizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.audiosynchronizer.network.*
import ru.audiosynchronizer.service.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var showQrScanner by remember { mutableStateOf(false) }
    var showPermissionCard by remember { mutableStateOf(false) }

    var permState by remember { mutableStateOf(checkPermissions(context)) }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permState = checkPermissions(context)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showQrScanner = true
    }

    LaunchedEffect(Unit) {
        val missing = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            showPermissionCard = true
        }
    }

    if (showQrScanner) {
        QrCodeScannerScreen(
            onScanned = { qrData ->
                showQrScanner = false
                val host = qrData.removePrefix("audiosync://").substringBefore(":")
                connection.connectToLeader(host)
            },
            onDismiss = { showQrScanner = false }
        )
        return
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
            "Устройства",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (showPermissionCard && !permState.allGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Необходимые разрешения", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }

                    val missingLabels = buildList {
                        if (!permState.audio) add("Доступ к аудиофайлам")
                        if (!permState.location) add("Геолокация (для Wi-Fi поиска)")
                        if (!permState.camera) add("Камера (для QR-кода)")
                        if (!permState.notification) add("Уведомления")
                        if (!permState.storage) add("Доступ к хранилищу")
                    }
                    missingLabels.forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }

                    Button(
                        onClick = {
                            val missing = getRequiredPermissions().filter {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isNotEmpty()) {
                                multiPermissionLauncher.launch(missing.toTypedArray())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Предоставить все")
                    }
                }
            }
        }

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
            }
        }

        if (isLeader) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Подключение ведомых", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                    Button(
                        onClick = {
                            discovery.registerService(ConnectionManager.PORT)
                            hotspot.startHotspot()
                        },
                        enabled = !isRegistered,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запустить точку доступа")
                    }

                    OutlinedButton(
                        onClick = { discovery.stopAll(); hotspot.stopHotspot() },
                        enabled = isRegistered || hotspotInfo.isRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Остановить")
                    }

                    if (hotspotInfo.isRunning) {
                        HorizontalDivider()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Точка доступа активна", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                MetricRow("SSID", hotspotInfo.ssid)
                                if (hotspotInfo.passphrase.isNotEmpty()) {
                                    MetricRow("Пароль", hotspotInfo.passphrase)
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    val wifiIp = getWifiIpAddress(context)
                    if (wifiIp != "0.0.0.0" && wifiIp.isNotEmpty()) {
                        val qrData = "audiosync://$wifiIp:${ConnectionManager.PORT}"
                        val qrBitmap = remember(qrData) { QrCodeGenerator.generate(qrData) }

                        Text(
                            "QR-код для подключения",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Ведомый сканирует этот код для автоматического подключения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        qrBitmap?.let { bitmap ->
                            Card(
                                modifier = Modifier.padding(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "QR-код",
                                    modifier = Modifier
                                        .size(220.dp)
                                        .padding(8.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Router, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$wifiIp:${ConnectionManager.PORT}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "QR-код появится после подключения к Wi-Fi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Подключение к лидеру", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                    FilledTonalButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                showQrScanner = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сканировать QR-код")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!permState.location) {
                                    multiPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                                }
                                discovery.startDiscovery()
                            },
                            enabled = !isDiscovering,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Поиск")
                        }

                        OutlinedButton(
                            onClick = { discovery.stopDiscovery() },
                            enabled = isDiscovering,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Стоп")
                        }
                    }

                    if (devices.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Найденные устройства", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        devices.forEach { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.serviceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalButton(onClick = {
                                    connection.connectToLeader(device.host)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Подключить")
                                }
                            }
                            if (device != devices.last()) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }

                    HorizontalDivider()

                    Text("Подключение вручную", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("IP лидера") },
                        placeholder = { Text("192.168.1.100") },
                        leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = { connection.connectToLeader(manualIp) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Подключиться")
                    }
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

        if (connectionState.connectedClients.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Подключены", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
    }
}

private fun getWifiIpAddress(context: android.content.Context): String {
    return try {
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val dhcpInfo = wifiManager?.dhcpInfo ?: return "0.0.0.0"
        val ip = dhcpInfo.ipAddress
        if (ip == 0) return "0.0.0.0"
        "%d.%d.%d.%d".format(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    } catch (_: Exception) {
        "0.0.0.0"
    }
}
