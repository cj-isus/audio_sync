package ru.audiosynchronizer.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.audio.PlaybackState
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.network.DiscoveryManager
import ru.audiosynchronizer.network.DiscoveredDevice
import ru.audiosynchronizer.network.HotspotManager
import ru.audiosynchronizer.network.openSettingsIntent
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SessionState
import ru.audiosynchronizer.sync.SyncSession
import java.net.NetworkInterface

private val CardShape = RoundedCornerShape(20.dp)
private val SmallShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(50.dp)

@Composable
fun ModeSelectScreen(
    onSelectLeader: () -> Unit,
    onSelectFollower: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.SurroundSound,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "АудиоСинхронизатор",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "Синхронное воспроизведение\nна нескольких устройствах",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(44.dp))

        ModeCard(
            icon = Icons.Filled.WifiTethering,
            title = "Лидер",
            description = "Создаю точку доступа, выбираю\nмузыку, управляю воспроизведением",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = onSelectLeader
        )

        Spacer(modifier = Modifier.height(16.dp))

        ModeCard(
            icon = Icons.Filled.QrCodeScanner,
            title = "Ведомый",
            description = "Подключаюсь к лидеру по QR или IP,\nслушаю синхронно",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.tertiary,
            onClick = onSelectFollower
        )
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = iconTint)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String, value: String) {
    Surface(
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    reason: String,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onError)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            FilledTonalButton(onClick = onGrant, shape = SmallShape, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("Включить")
            }
        }
    }
}

@Composable
private fun PulsingDot(color: androidx.compose.ui.graphics.Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "dotScale"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun StepIndicator(step: Int, total: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        for (i in 0 until total) {
            if (i > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.height(2.dp).weight(1f).background(
                    if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(1.dp)
                ))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            i < step -> MaterialTheme.colorScheme.primary
                            i == step -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (i < step) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                } else if (i == step) {
                    PulsingDot(MaterialTheme.colorScheme.onTertiary)
                } else {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}

private fun getHotspotIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val name = iface.name
            if (!name.startsWith("wlan") && !name.startsWith("ap") && !name.startsWith("softap") && !name.startsWith("ccmni")) continue
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    if (ip != "0.0.0.0") return ip
                }
            }
        }
    } catch (_: Exception) {}
    return "0.0.0.0"
}

@Composable
fun LeaderScreen(
    engine: AudioEngine,
    discovery: DiscoveryManager,
    hotspot: HotspotManager,
    connection: ConnectionManager,
    session: SyncSession,
    clockSync: ClockSynchronizer,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackState by engine.playbackState.collectAsState()
    val currentInfo by engine.currentInfo.collectAsState()
    val positionFrames by engine.positionFrames.collectAsState()
    val latencyMs by engine.latencyMs.collectAsState()
    val hotspotInfo by hotspot.hotspotInfo.collectAsState()
    val connectionState by connection.state.collectAsState()

    var isEngineStarted by remember { mutableStateOf(false) }
    var isSineOn by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasNearbyWifi = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    val hasAudio = ContextCompat.checkSelfPermission(
        context, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    val hasNotification = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val canStart = hasLocation && hasNearbyWifi && hasNotification

    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && hasNearbyWifi && hasNotification) doStartBroadcast(discovery, hotspot, connection, session)
    }
    val nearbyWifiPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && hasLocation && hasNotification) doStartBroadcast(discovery, hotspot, connection, session)
    }
    val notificationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && hasLocation && hasNearbyWifi) doStartBroadcast(discovery, hotspot, connection, session)
    }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) engine.playFile(uri)
    }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickAudio.launch(arrayOf("audio/*")) else permissionDenied = true
    }

    LaunchedEffect(isEngineStarted) {
        while (isEngineStarted || playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.PAUSED) {
            engine.getLatencyMs(); delay(500)
        }
    }

    var hotspotIp by remember { mutableStateOf("0.0.0.0") }
    LaunchedEffect(hotspotInfo.isRunning) {
        if (hotspotInfo.isRunning) {
            hotspotIp = getHotspotIpAddress()
            var retries = 0
            while (hotspotIp == "0.0.0.0" && retries < 10) {
                delay(500)
                hotspotIp = getHotspotIpAddress()
                retries++
            }
        } else {
            hotspotIp = "0.0.0.0"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Режим лидера", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        val hotspotError = hotspotInfo.error
        if (!hotspotInfo.isRunning && !hotspotInfo.isStarting && hotspotError == null) {
            if (!hasLocation) {
                PermissionCard(
                    icon = Icons.Filled.LocationOn,
                    title = "Геолокация",
                    reason = "Необходима для создания Wi-Fi точки доступа",
                    onGrant = { locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )
            }
            if (!hasNearbyWifi) {
                PermissionCard(
                    icon = Icons.Filled.Wifi,
                    title = "Устройства рядом (Wi-Fi)",
                    reason = "Необходимо для управления точкой доступа на Android 13+",
                    onGrant = { nearbyWifiPermLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES) }
                )
            }
            if (!hasNotification) {
                PermissionCard(
                    icon = Icons.Filled.Notifications,
                    title = "Уведомления",
                    reason = "Для отображения статуса синхронизации",
                    onGrant = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text("Начать трансляцию", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text("Ведомые устройства сканируют QR-код\nи подключаются автоматически", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    Button(
                        onClick = {
                            if (!hasLocation) {
                                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                return@Button
                            }
                            if (!hasNearbyWifi) {
                                nearbyWifiPermLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                                return@Button
                            }
                            if (!hasNotification) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@Button
                            }
                            doStartBroadcast(discovery, hotspot, connection, session)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape,
                        enabled = canStart
                    ) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запустить", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else if (hotspotInfo.isStarting) {
            Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                    Text("Запуск точки доступа...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Подготовка Wi-Fi и генерация QR-кода", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (hotspotError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.WifiOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Text(hotspotInfo.errorTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(hotspotInfo.errorDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
                    if (hotspotInfo.needsSettings) {
                        Button(
                            onClick = { hotspotInfo.error.openSettingsIntent(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SmallShape
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Открыть настройки")
                        }
                    }
                    OutlinedButton(
                        onClick = { hotspot.stopHotspot() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Попробовать снова")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Точка доступа активна", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    if (hotspotInfo.isRunning) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoChip(Icons.Filled.Wifi, "SSID", hotspotInfo.ssid)
                            if (hotspotInfo.passphrase.isNotEmpty()) InfoChip(Icons.Filled.Key, "Пароль", hotspotInfo.passphrase)
                        }
                    }

                    if (hotspotIp != "0.0.0.0" && hotspotIp.isNotEmpty()) {
                        val qrData = "audiosync://$hotspotIp:${ConnectionManager.PORT}"
                        val qrBitmap = remember(qrData) { QrCodeGenerator.generate(qrData, 512) }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Покажите QR ведомым:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        qrBitmap?.let { bm ->
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                                Image(bitmap = bm.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(240.dp).padding(12.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Router, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$hotspotIp:${ConnectionManager.PORT}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    val address = "$hotspotIp:${ConnectionManager.PORT}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AudioSync IP", address))
                                    scope.launch { snackbarHostState.showSnackbar("IP скопирован: $address", duration = SnackbarDuration.Short) }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Определение IP-адреса...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            discovery.stopAll(); hotspot.stopHotspot(); connection.stop()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Остановить")
                    }
                }
            }

            val clientCount = connectionState.connectedClients.size
            AnimatedVisibility(visible = clientCount > 0) {
                Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(Icons.Filled.People, "Ведомые ($clientCount)")
                        connectionState.connectedClients.forEach { name ->
                            Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = clientCount == 0) {
                Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ожидание ведомых...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(Icons.Filled.MusicNote, "Воспроизведение")

                PlaybackStatusCard(playbackState, isEngineStarted, currentInfo, positionFrames, latencyMs)

                currentInfo?.let { info ->
                    if (info.sampleRate > 0 && info.totalFrames > 0) {
                        val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(PillShape))
                    }
                }

                Button(
                    onClick = {
                        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) pickAudio.launch(arrayOf("audio/*"))
                        else { permissionDenied = false; audioPermLauncher.launch(perm) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = SmallShape
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать аудиофайл", style = MaterialTheme.typography.labelLarge)
                }

                PlaybackControls(playbackState, isEngineStarted, isSineOn, engine) { started, sine ->
                    isEngineStarted = started; isSineOn = sine
                }

                if (connectionState.connectedClients.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Синхронное управление", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                engine.start()
                                connection.broadcast(ru.audiosynchronizer.protocol.Message.Control(ru.audiosynchronizer.protocol.ControlMessage(ru.audiosynchronizer.protocol.ControlMessage.ACTION_PLAY)))
                            }, modifier = Modifier.weight(1f), shape = SmallShape
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Играть все")
                        }
                        OutlinedButton(
                            onClick = {
                                engine.pause()
                                connection.broadcast(ru.audiosynchronizer.protocol.Message.Control(ru.audiosynchronizer.protocol.ControlMessage(ru.audiosynchronizer.protocol.ControlMessage.ACTION_PAUSE)))
                            }, modifier = Modifier.weight(1f), shape = SmallShape
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Пауза все")
                        }
                    }
                }
            }
        }

        if (permissionDenied) {
            PermissionDeniedCard {
                val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                audioPermLauncher.launch(perm)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun doStartBroadcast(
    discovery: DiscoveryManager,
    hotspot: HotspotManager,
    connection: ConnectionManager,
    session: SyncSession
) {
    discovery.registerService(ConnectionManager.PORT)
    hotspot.startHotspot()
    session.setLeader(true)
    connection.startServer()
}

@Composable
fun FollowerScreen(
    engine: AudioEngine,
    connection: ConnectionManager,
    clockSync: ClockSynchronizer,
    session: SyncSession,
    discovery: DiscoveryManager,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val playbackState by engine.playbackState.collectAsState()
    val currentInfo by engine.currentInfo.collectAsState()
    val positionFrames by engine.positionFrames.collectAsState()
    val latencyMs by engine.latencyMs.collectAsState()
    val connectionState by connection.state.collectAsState()
    val sessionState by session.sessionState.collectAsState()
    val syncState by clockSync.state.collectAsState()
    val discoveredDevices by discovery.discoveredDevices.collectAsState()
    val isDiscovering by discovery.isDiscovering.collectAsState()

    var manualIp by remember { mutableStateOf("192.168.") }
    var showQrScanner by remember { mutableStateOf(false) }
    var isEngineStarted by remember { mutableStateOf(false) }

    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasNearbyWifi = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showQrScanner = true
    }
    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) discovery.startDiscovery()
    }
    val nearbyWifiPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) discovery.startDiscovery()
    }

    val isConnected = sessionState.state != SessionState.DISCONNECTED

    LaunchedEffect(hasLocation) {
        if (hasLocation && !isConnected) discovery.startDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            discovery.stopDiscovery()
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) discovery.stopDiscovery()
    }

    LaunchedEffect(connectionState.isClientConnected) {
        if (connectionState.isClientConnected && sessionState.state == SessionState.CONNECTING) {
            session.setState(SessionState.CLOCK_SYNCING)
        }
    }

    LaunchedEffect(syncState.isStable) {
        if (syncState.isStable && (sessionState.state == SessionState.CLOCK_SYNCING || sessionState.state == SessionState.CONNECTING)) {
            session.setState(SessionState.READY)
        }
    }

    LaunchedEffect(sessionState.state) {
        if (sessionState.state == SessionState.CONNECTING) {
            delay(8000)
            if (sessionState.state == SessionState.CONNECTING) {
                connection.stop()
                session.setState(SessionState.DISCONNECTED)
                session.setError("Таймаут подключения")
            }
        }
    }

    if (showQrScanner) {
        QrCodeScannerScreen(
            onScanned = { qrData ->
                showQrScanner = false
                val host = qrData.removePrefix("audiosync://").substringBefore(":")
                connectToFollower(connection, clockSync, session, host)
            },
            onDismiss = { showQrScanner = false }
        )
        return
    }

    val stateLabel = when (sessionState.state) {
        SessionState.DISCONNECTED -> "Отключено"
        SessionState.CONNECTING -> "Подключение..."
        SessionState.CLOCK_SYNCING -> "Синхронизация часов"
        SessionState.FILE_TRANSFER -> "Получение файла"
        SessionState.READY -> "Готов к воспроизведению"
        SessionState.PLAYING -> "Воспроизведение"
        SessionState.PAUSED -> "Пауза"
    }

    val pairingStep = when (sessionState.state) {
        SessionState.DISCONNECTED -> -1
        SessionState.CONNECTING -> 0
        SessionState.CLOCK_SYNCING -> 1
        SessionState.FILE_TRANSFER -> 2
        SessionState.READY -> 3
        SessionState.PLAYING -> 3
        SessionState.PAUSED -> 3
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Режим ведомого", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (!isConnected) {
            if (!hasCamera) {
                PermissionCard(
                    icon = Icons.Filled.Videocam,
                    title = "Камера",
                    reason = "Для сканирования QR-кода лидера",
                    onGrant = { cameraPermLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
            if (!hasLocation) {
                PermissionCard(
                    icon = Icons.Filled.LocationOn,
                    title = "Геолокация",
                    reason = "Для поиска лидеров поблизости по Wi-Fi",
                    onGrant = { locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )
            }
            if (!hasNearbyWifi) {
                PermissionCard(
                    icon = Icons.Filled.Wifi,
                    title = "Устройства рядом (Wi-Fi)",
                    reason = "Для обнаружения и подключения к лидеру на Android 13+",
                    onGrant = { nearbyWifiPermLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES) }
                )
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.tertiary,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onTertiary)
                    }
                    Text("Подключиться к лидеру", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text("Наведите камеру на QR-код\nили выберите найденное устройство", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp)

                    Button(
                        onClick = {
                            if (hasCamera) showQrScanner = true
                            else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сканировать QR-код", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (discoveredDevices.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulsingDot(MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Найдены устройства", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        discoveredDevices.forEach { device ->
                            Card(
                                onClick = {
                                    connectToFollower(connection, clockSync, session, device.host)
                                },
                                shape = SmallShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiary), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.SurroundSound, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiary)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.serviceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Text(device.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            } else if (hasLocation && isDiscovering) {
                Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Поиск лидеров поблизости...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("или введите IP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("IP лидера") },
                        placeholder = { Text("192.168.1.100") },
                        leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = SmallShape
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            connectToFollower(connection, clockSync, session, manualIp)
                            scope.launch { snackbarHostState.showSnackbar("Подключение к $manualIp...", duration = SnackbarDuration.Short) }
                        },
                        enabled = manualIp.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Подключиться", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            val sessionError = sessionState.error
            sessionError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = CardShape
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        FilledTonalButton(
                            onClick = { session.reset() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SmallShape
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Попробовать снова")
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StepIndicator(
                        step = pairingStep,
                        total = 4,
                        label = stateLabel
                    )

                    if (syncState.isStable) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoChip(Icons.Filled.AccessTime, "Смещение", "${"%.2f".format(syncState.offsetMs)} мс")
                            InfoChip(Icons.Filled.Speed, "Дрейф", "${"%.2f".format(syncState.driftPpm)} ppm")
                            InfoChip(Icons.Filled.SwapHoriz, "RTT", "${"%.2f".format(syncState.rttMs)} мс")
                        }
                    }

                    OutlinedButton(
                        onClick = { connection.stop(); session.reset(); clockSync.stop() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отключиться")
                    }
                }
            }

            if (sessionState.state == SessionState.PLAYING || sessionState.state == SessionState.PAUSED || sessionState.state == SessionState.READY) {
                Card(modifier = Modifier.fillMaxWidth(), shape = CardShape) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(Icons.Filled.MusicNote, "Воспроизведение")

                        PlaybackStatusCard(playbackState, isEngineStarted, currentInfo, positionFrames, latencyMs)

                        currentInfo?.let { info ->
                            if (info.sampleRate > 0 && info.totalFrames > 0) {
                                val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(PillShape))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (playbackState == PlaybackState.PAUSED) {
                                Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f), shape = SmallShape) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Продолжить")
                                }
                            }
                            OutlinedButton(onClick = { engine.stopPlayback(); isEngineStarted = false }, modifier = Modifier.weight(1f), shape = SmallShape) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Стоп")
                            }
                        }

                        if (sessionState.state == SessionState.READY && !isEngineStarted) {
                            FilledTonalButton(
                                onClick = { isEngineStarted = engine.start() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = SmallShape
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Запустить аудиодвижок", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        connectionState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Не удалось подключиться", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    FilledTonalButton(
                        onClick = { connection.stop(); session.reset() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Попробовать снова")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun connectToFollower(
    connection: ConnectionManager,
    clockSync: ClockSynchronizer,
    session: SyncSession,
    host: String
) {
    session.setLeader(false)
    session.setState(SessionState.CONNECTING)
    clockSync.startFollower(host)
    connection.connectToLeader(host)
}

@Composable
private fun PlaybackStatusCard(
    playbackState: PlaybackState,
    isEngineStarted: Boolean,
    currentInfo: ru.audiosynchronizer.audio.AudioFileInfo?,
    positionFrames: Long,
    latencyMs: Double
) {
    val stateColor = when (playbackState) {
        PlaybackState.PLAYING -> MaterialTheme.colorScheme.primaryContainer
        PlaybackState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
        PlaybackState.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = when (playbackState) {
        PlaybackState.PLAYING -> MaterialTheme.colorScheme.primary
        PlaybackState.PAUSED -> MaterialTheme.colorScheme.tertiary
        PlaybackState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stateIcon = when (playbackState) {
        PlaybackState.PLAYING -> Icons.Filled.PlayArrow
        PlaybackState.PAUSED -> Icons.Filled.Pause
        PlaybackState.STOPPED -> if (isEngineStarted) Icons.Filled.Stop else Icons.Filled.FiberManualRecord
    }
    val stateLabel = when (playbackState) {
        PlaybackState.STOPPED -> if (isEngineStarted) "Остановлен" else "Готов"
        PlaybackState.PLAYING -> "Воспроизведение"
        PlaybackState.PAUSED -> "Пауза"
    }

    Surface(shape = SmallShape, color = stateColor) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Icon(stateIcon, contentDescription = null, modifier = Modifier.size(28.dp), tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stateLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                currentInfo?.let { info ->
                    Text(info.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    val posSec = if (info.sampleRate > 0) positionFrames / info.sampleRate else 0L
                    val durSec = info.durationMs / 1000
                    Text("%d:%02d / %d:%02d".format(posSec / 60, posSec % 60, durSec / 60, durSec % 60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (latencyMs >= 0) {
                    Text("Задержка: ${"%.1f".format(latencyMs)} мс", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    playbackState: PlaybackState,
    isEngineStarted: Boolean,
    isSineOn: Boolean,
    engine: AudioEngine,
    onUpdate: (isEngineStarted: Boolean, isSineOn: Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (playbackState) {
            PlaybackState.PLAYING -> {
                OutlinedButton(onClick = { engine.pause() }, modifier = Modifier.weight(1f), shape = SmallShape) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Пауза")
                }
                OutlinedButton(onClick = { engine.stopPlayback(); onUpdate(false, isSineOn) }, modifier = Modifier.weight(1f), shape = SmallShape) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Стоп")
                }
            }
            PlaybackState.PAUSED -> {
                Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f), shape = SmallShape) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Продолжить")
                }
                OutlinedButton(onClick = { engine.stopPlayback(); onUpdate(false, isSineOn) }, modifier = Modifier.weight(1f), shape = SmallShape) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Стоп")
                }
            }
            PlaybackState.STOPPED -> {
                if (!isEngineStarted) {
                    Button(onClick = { onUpdate(engine.start(), isSineOn) }, modifier = Modifier.weight(1f), shape = SmallShape) {
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
            val started = if (!isEngineStarted) engine.start() else isEngineStarted
            val newSine = !isSineOn
            engine.enableSine(newSine)
            onUpdate(started, newSine)
        },
        enabled = isEngineStarted || playbackState != PlaybackState.STOPPED,
        modifier = Modifier.fillMaxWidth(),
        shape = SmallShape
    ) {
        Icon(if (isSineOn) Icons.Filled.Stop else Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isSineOn) "Стоп синус 440 Гц" else "Тест синус 440 Гц")
    }
}

@Composable
private fun PermissionDeniedCard(onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = CardShape) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Без доступа к аудио нельзя выбрать файл", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onRequest, shape = SmallShape) { Text("Предоставить доступ") }
        }
    }
}
