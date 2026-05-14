package ru.audiosynchronizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.audio.PlaybackState
import ru.audiosynchronizer.network.ConnectionManager
import ru.audiosynchronizer.network.DiscoveryManager
import ru.audiosynchronizer.network.HotspotManager
import ru.audiosynchronizer.sync.ClockSynchronizer
import ru.audiosynchronizer.sync.SessionState
import ru.audiosynchronizer.sync.SyncSession
import kotlinx.coroutines.delay

@Composable
fun ModeSelectScreen(
    onSelectLeader: () -> Unit,
    onSelectFollower: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.SurroundSound,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "АудиоСинхронизатор",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Синхронное воспроизведение на нескольких устройствах",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))

        Card(
            onClick = onSelectLeader,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.WifiTethering, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Лидер", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Создаю точку доступа, выбираю музыку,\nуправляю воспроизведением", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            onClick = onSelectFollower,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ведомый", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Подключаюсь к лидеру по QR или IP,\nслушаю синхронно", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun LeaderScreen(
    engine: AudioEngine,
    discovery: DiscoveryManager,
    hotspot: HotspotManager,
    connection: ConnectionManager,
    session: SyncSession,
    clockSync: ClockSynchronizer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by engine.playbackState.collectAsState()
    val currentInfo by engine.currentInfo.collectAsState()
    val positionFrames by engine.positionFrames.collectAsState()
    val latencyMs by engine.latencyMs.collectAsState()
    val hotspotInfo by hotspot.hotspotInfo.collectAsState()
    val isRegistered by discovery.isRegistered.collectAsState()
    val connectionState by connection.state.collectAsState()
    val sessionState by session.sessionState.collectAsState()

    var isEngineStarted by remember { mutableStateOf(false) }
    var isSineOn by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var hotspotStarted by remember { mutableStateOf(false) }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) engine.playFile(uri)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickAudio.launch(arrayOf("audio/*")) else permissionDenied = true
    }

    LaunchedEffect(isEngineStarted) {
        while (isEngineStarted || playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.PAUSED) {
            engine.getLatencyMs(); delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Лидер", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (!hotspotStarted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запустить точку доступа", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Ведомые подключатся к вашей Wi-Fi сети", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            discovery.registerService(ConnectionManager.PORT)
                            hotspot.startHotspot()
                            hotspotStarted = true
                            session.setLeader(true)
                            connection.startServer()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запустить")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Точка доступа активна", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (hotspotInfo.isRunning) {
                        MetricRow("SSID", hotspotInfo.ssid)
                        if (hotspotInfo.passphrase.isNotEmpty()) MetricRow("Пароль", hotspotInfo.passphrase)
                    }

                    val wifiIp = getWifiIpAddress(context)
                    if (wifiIp != "0.0.0.0" && wifiIp.isNotEmpty()) {
                        val qrData = "audiosync://$wifiIp:${ConnectionManager.PORT}"
                        val qrBitmap = remember(qrData) { QrCodeGenerator.generate(qrData, 400) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("QR для подключения ведомых", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        qrBitmap?.let { bm ->
                            Card(modifier = Modifier.padding(4.dp)) {
                                Image(bitmap = bm.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(180.dp).padding(4.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Router, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$wifiIp:${ConnectionManager.PORT}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    OutlinedButton(
                        onClick = { discovery.stopAll(); hotspot.stopHotspot(); hotspotStarted = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Остановить точку доступа")
                    }
                }
            }
        }

        if (connectionState.connectedClients.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ведомые (${connectionState.connectedClients.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

        HorizontalDivider()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Воспроизведение", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                PlaybackStatusCard(playbackState, isEngineStarted, currentInfo, positionFrames, latencyMs)

                currentInfo?.let { info ->
                    if (info.sampleRate > 0 && info.totalFrames > 0) {
                        val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }

                Button(
                    onClick = {
                        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) pickAudio.launch(arrayOf("audio/*"))
                        else { permissionDenied = false; permLauncher.launch(perm) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать аудиофайл")
                }

                PlaybackControls(playbackState, isEngineStarted, isSineOn, engine) { started, sine ->
                    isEngineStarted = started; isSineOn = sine
                }

                if (connectionState.connectedClients.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Управление всеми", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            engine.start()
                            connection.broadcast(ru.audiosynchronizer.protocol.Message.Control(ru.audiosynchronizer.protocol.ControlMessage(ru.audiosynchronizer.protocol.ControlMessage.ACTION_PLAY)))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Играть все")
                        }
                        OutlinedButton(onClick = {
                            engine.pause()
                            connection.broadcast(ru.audiosynchronizer.protocol.Message.Control(ru.audiosynchronizer.protocol.ControlMessage(ru.audiosynchronizer.protocol.ControlMessage.ACTION_PAUSE)))
                        }, modifier = Modifier.weight(1f)) {
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
                permLauncher.launch(perm)
            }
        }
    }
}

@Composable
fun FollowerScreen(
    engine: AudioEngine,
    connection: ConnectionManager,
    clockSync: ClockSynchronizer,
    session: SyncSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by engine.playbackState.collectAsState()
    val currentInfo by engine.currentInfo.collectAsState()
    val positionFrames by engine.positionFrames.collectAsState()
    val latencyMs by engine.latencyMs.collectAsState()
    val connectionState by connection.state.collectAsState()
    val sessionState by session.sessionState.collectAsState()
    val syncState by clockSync.state.collectAsState()

    var manualIp by remember { mutableStateOf("192.168.") }
    var showQrScanner by remember { mutableStateOf(false) }
    var isEngineStarted by remember { mutableStateOf(false) }

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showQrScanner = true
    }

    if (showQrScanner) {
        QrCodeScannerScreen(
            onScanned = { qrData ->
                showQrScanner = false
                val host = qrData.removePrefix("audiosync://").substringBefore(":")
                session.setLeader(false)
                clockSync.startFollower(host)
                connection.connectToLeader(host)
            },
            onDismiss = { showQrScanner = false }
        )
        return
    }

    val isConnected = sessionState.state != SessionState.DISCONNECTED

    val stateLabel = when (sessionState.state) {
        SessionState.DISCONNECTED -> "Отключено"
        SessionState.CONNECTING -> "Подключение..."
        SessionState.CLOCK_SYNCING -> "Синхронизация часов"
        SessionState.FILE_TRANSFER -> "Получение файла"
        SessionState.READY -> "Готов к воспроизведению"
        SessionState.PLAYING -> "Воспроизведение"
        SessionState.PAUSED -> "Пауза"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ведомый", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (!isConnected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Подключение к лидеру", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    FilledTonalButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showQrScanner = true
                            else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сканировать QR-код лидера")
                    }

                    HorizontalDivider()

                    Text("Или введите IP вручную", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onClick = {
                            session.setLeader(false)
                            clockSync.startFollower(manualIp)
                            connection.connectToLeader(manualIp)
                        },
                        enabled = manualIp.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Подключиться")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (sessionState.state == SessionState.PLAYING) MaterialTheme.colorScheme.primaryContainer
                    else if (sessionState.state == SessionState.READY) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (sessionState.state) {
                                SessionState.PLAYING -> Icons.Filled.PlayCircle
                                SessionState.READY -> Icons.Filled.CheckCircle
                                SessionState.CLOCK_SYNCING -> Icons.Filled.Sync
                                else -> Icons.Filled.Link
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    if (syncState.isStable) {
                        MetricRow("Смещение", "${"%.2f".format(syncState.offsetMs)} мс")
                        MetricRow("Дрейф", "${"%.2f".format(syncState.driftPpm)} ppm")
                        MetricRow("RTT", "${"%.2f".format(syncState.rttMs)} мс")
                    }

                    OutlinedButton(
                        onClick = { connection.stop(); session.reset(); clockSync.stop() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отключиться")
                    }
                }
            }

            if (sessionState.state == SessionState.PLAYING || sessionState.state == SessionState.PAUSED || sessionState.state == SessionState.READY) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Воспроизведение", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                        PlaybackStatusCard(playbackState, isEngineStarted, currentInfo, positionFrames, latencyMs)

                        currentInfo?.let { info ->
                            if (info.sampleRate > 0 && info.totalFrames > 0) {
                                val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (playbackState == PlaybackState.PAUSED) {
                                Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Продолжить")
                                }
                            }
                            OutlinedButton(onClick = { engine.stopPlayback(); isEngineStarted = false }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Стоп")
                            }
                        }

                        if (sessionState.state == SessionState.READY && !isEngineStarted) {
                            FilledTonalButton(
                                onClick = { isEngineStarted = engine.start() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Запустить аудиодвижок")
                            }
                        }
                    }
                }
            }
        }

        connectionState.error?.let { error ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun PlaybackStatusCard(
    playbackState: PlaybackState,
    isEngineStarted: Boolean,
    currentInfo: ru.audiosynchronizer.audio.AudioFileInfo?,
    positionFrames: Long,
    latencyMs: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            when (playbackState) {
                PlaybackState.PLAYING -> Icons.Filled.PlayArrow
                PlaybackState.PAUSED -> Icons.Filled.Pause
                PlaybackState.STOPPED -> if (isEngineStarted) Icons.Filled.Stop else Icons.Filled.FiberManualRecord
            },
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = when (playbackState) {
                PlaybackState.PLAYING -> MaterialTheme.colorScheme.primary
                PlaybackState.PAUSED -> MaterialTheme.colorScheme.tertiary
                PlaybackState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Column {
            Text(
                when (playbackState) {
                    PlaybackState.STOPPED -> if (isEngineStarted) "Остановлен" else "Готов"
                    PlaybackState.PLAYING -> "Воспроизведение"
                    PlaybackState.PAUSED -> "Пауза"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
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
                OutlinedButton(onClick = { engine.pause() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Пауза")
                }
                OutlinedButton(onClick = { engine.stopPlayback(); onUpdate(false, isSineOn) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Стоп")
                }
            }
            PlaybackState.PAUSED -> {
                Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Продолжить")
                }
                OutlinedButton(onClick = { engine.stopPlayback(); onUpdate(false, isSineOn) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Стоп")
                }
            }
            PlaybackState.STOPPED -> {
                if (!isEngineStarted) {
                    Button(onClick = { onUpdate(engine.start(), isSineOn) }, modifier = Modifier.weight(1f)) {
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(if (isSineOn) Icons.Filled.Stop else Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isSineOn) "Стоп синус 440 Гц" else "Тест синус 440 Гц")
    }
}

@Composable
private fun PermissionDeniedCard(onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Без доступа к аудио нельзя выбрать файл", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onRequest) { Text("Предоставить доступ") }
        }
    }
}

private fun getWifiIpAddress(context: android.content.Context): String {
    return try {
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val dhcpInfo = wifiManager?.dhcpInfo ?: return "0.0.0.0"
        val ip = dhcpInfo.ipAddress
        if (ip == 0) return "0.0.0.0"
        "%d.%d.%d.%d".format(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    } catch (_: Exception) { "0.0.0.0" }
}
