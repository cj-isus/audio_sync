package ru.audiosynchronizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

private val CardShape = RoundedCornerShape(20.dp)
private val SmallShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
private fun GradientCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier, shape = CardShape, colors = colors, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), content = content)
}

@Composable
private fun SectionTitle(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun StatusChip(icon: ImageVector, text: String, active: Boolean) {
    val bgColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(400)
    )
    val contentColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(400)
    )
    Surface(shape = PillShape, color = bgColor) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}

@Composable
fun ModeSelectScreen(onSelectLeader: () -> Unit, onSelectFollower: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(100.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SurroundSound, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("АудиоСинхронизатор", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Синхронное воспроизведение\nна нескольких устройствах", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(modifier = Modifier.height(36.dp))

        GradientCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.WifiTethering, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Лидер", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Создаю сеть, выбираю музыку,\nуправляю всеми", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                }
                FilledIconButton(onClick = onSelectLeader) { Icon(Icons.Filled.ArrowForward, contentDescription = null) }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        GradientCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.tertiary) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ведомый", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Подключаюсь по QR или IP,\nслушаю синхронно", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                }
                FilledIconButton(onClick = onSelectFollower, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Icon(Icons.Filled.ArrowForward, contentDescription = null) }
            }
        }
    }
}

@Composable
fun LeaderScreen(
    engine: AudioEngine, discovery: DiscoveryManager, hotspot: HotspotManager,
    connection: ConnectionManager, session: SyncSession, clockSync: ClockSynchronizer, onBack: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by engine.playbackState.collectAsState()
    val currentInfo by engine.currentInfo.collectAsState()
    val positionFrames by engine.positionFrames.collectAsState()
    val latencyMs by engine.latencyMs.collectAsState()
    val hotspotInfo by hotspot.hotspotInfo.collectAsState()
    val connectionState by connection.state.collectAsState()

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
        while (isEngineStarted || playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.PAUSED) { engine.getLatencyMs(); delay(500) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.WifiTethering, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text("Лидер", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.weight(1f))
            StatusChip(Icons.Filled.Wifi, if (hotspotStarted) "Активно" else "Оффлайн", hotspotStarted)
        }

        if (!hotspotStarted) {
            GradientCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(Icons.Filled.Wifi, "Точка доступа")
                    Text("Ведомые подключатся к вашей Wi-Fi сети и смогут слушать синхронно", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        discovery.registerService(ConnectionManager.PORT)
                        hotspot.startHotspot()
                        hotspotStarted = true
                        session.setLeader(true)
                        connection.startServer()
                    }, modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запустить")
                    }
                }
            }
        } else {
            GradientCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Точка доступа активна", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (hotspotInfo.isRunning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        MetricRow("SSID", hotspotInfo.ssid)
                        if (hotspotInfo.passphrase.isNotEmpty()) MetricRow("Пароль", hotspotInfo.passphrase)
                    }
                    val wifiIp = getWifiIpAddress(context)
                    if (wifiIp != "0.0.0.0" && wifiIp.isNotEmpty()) {
                        val qrData = "audiosync://$wifiIp:${ConnectionManager.PORT}"
                        val qrBitmap = remember(qrData) { QrCodeGenerator.generate(qrData, 400) }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Покажите этот QR ведомым", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        qrBitmap?.let { bm ->
                            Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(2.dp)) {
                                Image(bitmap = bm.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(200.dp).padding(8.dp))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Router, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$wifiIp:${ConnectionManager.PORT}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(onClick = { discovery.stopAll(); hotspot.stopHotspot(); hotspotStarted = false }, modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Остановить")
                    }
                }
            }
        }

        if (connectionState.connectedClients.isNotEmpty()) {
            GradientCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle(Icons.Filled.People, "Ведомые (${connectionState.connectedClients.size})")
                    Spacer(modifier = Modifier.height(6.dp))
                    connectionState.connectedClients.forEach { name ->
                        Surface(shape = SmallShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        GradientCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(Icons.Filled.MusicNote, "Воспроизведение")
                PlaybackStatusCard(playbackState, isEngineStarted, currentInfo, positionFrames, latencyMs)
                currentInfo?.let { info ->
                    if (info.sampleRate > 0 && info.totalFrames > 0) {
                        val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                        val animProgress by animateFloatAsState(progress, animationSpec = tween(300))
                        LinearProgressIndicator(progress = { animProgress }, modifier = Modifier.fillMaxWidth().clip(SmallShape))
                    }
                }
                Button(onClick = {
                    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) pickAudio.launch(arrayOf("audio/*"))
                    else { permissionDenied = false; permLauncher.launch(perm) }
                }, modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать аудиофайл")
                }
                PlaybackControls(playbackState, isEngineStarted, isSineOn, engine) { started, sine -> isEngineStarted = started; isSineOn = sine }
                if (connectionState.connectedClients.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Управление всеми", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            engine.start()
                            connection.broadcast(ru.audiosynchronizer.protocol.Message.Control(ru.audiosynchronizer.protocol.ControlMessage(ru.audiosynchronizer.protocol.ControlMessage.ACTION_PLAY)))
                        }, modifier = Modifier.weight(1f), shape = SmallShape) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Играть все")
                        }
                        OutlinedButton(onClick = {
                            engine.pause()
                            connection.broadcast(ru.audiosynchronizer.protocol.Message.Control(ru.audiosynchronizer.protocol.ControlMessage(ru.audiosynchronizer.protocol.ControlMessage.ACTION_PAUSE)))
                        }, modifier = Modifier.weight(1f), shape = SmallShape) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Пауза все")
                        }
                    }
                }
            }
        }
        if (permissionDenied) { PermissionDeniedCard { permLauncher.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) } }
    }
}

@Composable
fun FollowerScreen(
    engine: AudioEngine, connection: ConnectionManager, clockSync: ClockSynchronizer, session: SyncSession, onBack: () -> Unit
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
        QrCodeScannerScreen(onScanned = { qrData ->
            showQrScanner = false
            val host = qrData.removePrefix("audiosync://").substringBefore(":")
            session.setLeader(false); clockSync.startFollower(host); connection.connectToLeader(host)
        }, onDismiss = { showQrScanner = false })
        return
    }

    val isConnected = sessionState.state != SessionState.DISCONNECTED
    val stateLabel = when (sessionState.state) {
        SessionState.DISCONNECTED -> "Отключено"
        SessionState.CONNECTING -> "Подключение..."
        SessionState.CLOCK_SYNCING -> "Синхронизация"
        SessionState.FILE_TRANSFER -> "Получение файла"
        SessionState.READY -> "Готов"
        SessionState.PLAYING -> "Воспроизведение"
        SessionState.PAUSED -> "Пауза"
    }
    val stateIcon = when (sessionState.state) {
        SessionState.PLAYING -> Icons.Filled.PlayCircle
        SessionState.READY -> Icons.Filled.CheckCircle
        SessionState.CLOCK_SYNCING -> Icons.Filled.Sync
        SessionState.CONNECTING -> Icons.Filled.HourglassTop
        else -> Icons.Filled.Link
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary) }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text("Ведомый", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.weight(1f))
            StatusChip(stateIcon, stateLabel, isConnected)
        }

        if (!isConnected) {
            GradientCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(Icons.Filled.Link, "Подключение к лидеру")

                    FilledTonalButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showQrScanner = true
                        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }, modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Сканировать QR-код", style = MaterialTheme.typography.bodyLarge)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(" или ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    OutlinedTextField(value = manualIp, onValueChange = { manualIp = it }, label = { Text("IP лидера") }, placeholder = { Text("192.168.1.100") }, leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = SmallShape)
                    Button(onClick = { session.setLeader(false); clockSync.startFollower(manualIp); connection.connectToLeader(manualIp) }, enabled = manualIp.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Подключиться")
                    }
                }
            }
        } else {
            val cardColor = when (sessionState.state) {
                SessionState.PLAYING -> MaterialTheme.colorScheme.primaryContainer
                SessionState.READY -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            GradientCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(stateIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (syncState.isStable) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                MetricRow("Смещение", "${"%.2f".format(syncState.offsetMs)} мс")
                                MetricRow("Дрейф", "${"%.2f".format(syncState.driftPpm)} ppm")
                                MetricRow("RTT", "${"%.2f".format(syncState.rttMs)} мс")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(onClick = { connection.stop(); session.reset(); clockSync.stop() }, modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отключиться")
                    }
                }
            }

            if (sessionState.state == SessionState.PLAYING || sessionState.state == SessionState.PAUSED || sessionState.state == SessionState.READY) {
                GradientCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(Icons.Filled.MusicNote, "Воспроизведение")
                        PlaybackStatusCard(playbackState, isEngineStarted, currentInfo, positionFrames, latencyMs)
                        currentInfo?.let { info ->
                            if (info.sampleRate > 0 && info.totalFrames > 0) {
                                val progress = (positionFrames.toFloat() / info.totalFrames.toFloat()).coerceIn(0f, 1f)
                                val animProgress by animateFloatAsState(progress, animationSpec = tween(300))
                                LinearProgressIndicator(progress = { animProgress }, modifier = Modifier.fillMaxWidth().clip(SmallShape))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (playbackState == PlaybackState.PAUSED) {
                                Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f), shape = SmallShape) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Продолжить")
                                }
                            }
                            OutlinedButton(onClick = { engine.stopPlayback(); isEngineStarted = false }, modifier = Modifier.weight(1f), shape = SmallShape) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Стоп")
                            }
                        }
                        if (sessionState.state == SessionState.READY && !isEngineStarted) {
                            FilledTonalButton(onClick = { isEngineStarted = engine.start() }, modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Запустить аудиодвижок")
                            }
                        }
                    }
                }
            }
        }
        connectionState.error?.let { error ->
            GradientCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onError) }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun PlaybackStatusCard(playbackState: PlaybackState, isEngineStarted: Boolean, currentInfo: ru.audiosynchronizer.audio.AudioFileInfo?, positionFrames: Long, latencyMs: Double) {
    val iconTint = when (playbackState) { PlaybackState.PLAYING -> MaterialTheme.colorScheme.primary; PlaybackState.PAUSED -> MaterialTheme.colorScheme.tertiary; PlaybackState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(shape = SmallShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(when (playbackState) { PlaybackState.PLAYING -> Icons.Filled.PlayArrow; PlaybackState.PAUSED -> Icons.Filled.Pause; PlaybackState.STOPPED -> if (isEngineStarted) Icons.Filled.Stop else Icons.Filled.FiberManualRecord }, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint)
                }
            }
            Column {
                Text(when (playbackState) { PlaybackState.STOPPED -> if (isEngineStarted) "Остановлен" else "Готов"; PlaybackState.PLAYING -> "Воспроизведение"; PlaybackState.PAUSED -> "Пауза" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                currentInfo?.let { info ->
                    Text(info.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    val posSec = if (info.sampleRate > 0) positionFrames / info.sampleRate else 0L; val durSec = info.durationMs / 1000
                    Text("%d:%02d / %d:%02d".format(posSec / 60, posSec % 60, durSec / 60, durSec % 60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (latencyMs >= 0) Text("Задержка: ${"%.1f".format(latencyMs)} мс", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun PlaybackControls(playbackState: PlaybackState, isEngineStarted: Boolean, isSineOn: Boolean, engine: AudioEngine, onUpdate: (Boolean, Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (playbackState) {
            PlaybackState.PLAYING -> {
                OutlinedButton(onClick = { engine.pause() }, modifier = Modifier.weight(1f), shape = SmallShape) { Icon(Icons.Filled.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Пауза") }
                OutlinedButton(onClick = { engine.stopPlayback(); onUpdate(false, isSineOn) }, modifier = Modifier.weight(1f), shape = SmallShape) { Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Стоп") }
            }
            PlaybackState.PAUSED -> {
                Button(onClick = { engine.resume() }, modifier = Modifier.weight(1f), shape = SmallShape) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Продолжить") }
                OutlinedButton(onClick = { engine.stopPlayback(); onUpdate(false, isSineOn) }, modifier = Modifier.weight(1f), shape = SmallShape) { Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Стоп") }
            }
            PlaybackState.STOPPED -> if (!isEngineStarted) { Button(onClick = { onUpdate(engine.start(), isSineOn) }, modifier = Modifier.weight(1f), shape = SmallShape) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Запустить") } }
        }
    }
    OutlinedButton(onClick = { val s = if (!isEngineStarted) engine.start() else isEngineStarted; val sine = !isSineOn; engine.enableSine(sine); onUpdate(s, sine) }, enabled = isEngineStarted || playbackState != PlaybackState.STOPPED, modifier = Modifier.fillMaxWidth(), shape = SmallShape) {
        Icon(if (isSineOn) Icons.Filled.Stop else Icons.Filled.GraphicEq, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (isSineOn) "Стоп синус 440 Гц" else "Тест синус 440 Гц")
    }
}

@Composable
private fun PermissionDeniedCard(onRequest: () -> Unit) {
    GradientCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Без доступа к аудио нельзя выбрать файл", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) }
            TextButton(onClick = onRequest) { Text("Предоставить доступ") }
        }
    }
}

private fun getWifiIpAddress(context: android.content.Context): String {
    return try {
        @Suppress("DEPRECATION")
        val wm = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val dhcp = wm?.dhcpInfo ?: return "0.0.0.0"
        val ip = dhcp.ipAddress; if (ip == 0) return "0.0.0.0"
        "%d.%d.%d.%d".format(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    } catch (_: Exception) { "0.0.0.0" }
}
