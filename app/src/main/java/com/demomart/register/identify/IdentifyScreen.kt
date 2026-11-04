package com.demomart.register.identify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.register.BuildConfig
import com.demomart.register.money.formatEur

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    amount: Long,
    onBack: () -> Unit,
    onIdentified: () -> Unit,
    model: IdentifyViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraAllowed = it
    }
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!requested && !cameraAllowed) {
            requested = true
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(model) { model.navigateToSale.collect { onIdentified() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Scan loyalty card") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sale amount · ${formatEur(amount)}", style = MaterialTheme.typography.titleMedium)
            when (state.nfc) {
                NfcAvailability.Ready -> NfcPrompt()
                NfcAvailability.Disabled -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NFC is off")
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text("Turn on") }
                }
                NfcAvailability.Unsupported -> Text("NFC not available on this device, use QR")
            }
            Text(if (state.nfc == NfcAvailability.Ready) "or scan the QR code" else "Scan the QR code")
            if (cameraAllowed) {
                QrPreview(model::onScanned)
            } else {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).background(
                        MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
                        Text("Camera permission needed")
                    }
                }
            }
            when (val status = state.status) {
                IdentifyStatus.Waiting -> Unit
                IdentifyStatus.Checking -> Text("Checking card…")
                is IdentifyStatus.Error -> Text(status.text, color = MaterialTheme.colorScheme.error)
            }
            if (BuildConfig.DEBUG) {
                var code by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    code, { code = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste code") }, singleLine = true,
                )
                Button(onClick = { model.onScanned(code.trim()) }, enabled = code.startsWith("DM") && state.status != IdentifyStatus.Checking) {
                    Text("Check code")
                }
            }
            TextButton(onClick = model::skipCard, enabled = state.status != IdentifyStatus.Checking) {
                Text("No card — continue")
            }
        }
    }
}

@Composable
private fun NfcPrompt() {
    val pulse by rememberInfiniteTransition(label = "NFC pulse").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "NFC ring",
    )
    Box(
        Modifier.size(68.dp).border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulse), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("NFC", color = MaterialTheme.colorScheme.primary) }
    Text("Hold customer's phone here")
}

@Composable
private fun QrPreview(onText: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onText)
    val view = remember { PreviewView(context) }
    var unavailable by remember { mutableStateOf(false) }

    DisposableEffect(owner, view) {
        val executor = ContextCompat.getMainExecutor(context)
        val camera = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var active = true
        var provider: ProcessCameraProvider? = null
        val analyzer = QrAnalyzer { text -> executor.execute { if (active) callback(text) } }
        analysis.setAnalyzer(executor, analyzer)
        camera.addListener({
            if (active) {
                runCatching {
                    provider = camera.get()
                    provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onFailure { unavailable = true }
            }
        }, executor)
        onDispose {
            active = false
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            analyzer.close()
        }
    }

    if (unavailable) Text("Camera not available, use NFC")
    else AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(190.dp))
}
