package ru.audiosynchronizer.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrCodeScannerScreen(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val isScanned = remember { AtomicBoolean(false) }
    val scanner = remember { BarcodeScanning.getClient() }

    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                cameraProvider = future.get()
            }, ContextCompat.getMainExecutor(context))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { cameraProvider?.unbindAll() } catch (_: Exception) {}
            scanner.close()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Сканирование QR-кода",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
            }
        }

        Text(
            "Наведите камеру на QR-код лидера",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        var isBound by remember { mutableStateOf(false) }

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            update = { previewView ->
                val provider = cameraProvider ?: return@AndroidView
                if (isBound) return@AndroidView

                try { provider.unbindAll() } catch (_: Exception) {}

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analyzer.setAnalyzer(ContextCompat.getMainExecutor(previewView.context)) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !isScanned.get()) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull()?.rawValue
                                if (value != null && value.startsWith("audiosync://") && isScanned.compareAndSet(false, true)) {
                                    onScanned(value)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analyzer
                    )
                    isBound = true
                } catch (_: Exception) {}
            }
        )

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}
