package com.junsebog.instapicker.core.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A reusable, self-contained camera scanner: it asks for the CAMERA permission, shows
 * a live preview, and reports every decoded EAN-13 via [onBarcode].
 *
 * The camera is bound to the composition's [LifecycleOwner] and explicitly unbound in
 * `onDispose`, so leaving the scanner releases the hardware immediately.
 */
@Composable
fun CameraBarcodeScanner(onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> granted = isGranted },
    )
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        CameraPreview(onBarcode = onBarcode, modifier = modifier)
    } else {
        PermissionRationale(onGrant = { launcher.launch(Manifest.permission.CAMERA) }, modifier = modifier)
    }
}

@Composable
private fun CameraPreview(onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    // Always call the freshest callback, even though the analyzer is remembered once.
    val currentOnBarcode = rememberUpdatedState(onBarcode)
    val analyzer = remember { BarcodeAnalyzer(onBarcode = { raw -> currentOnBarcode.value(raw) }) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener(
            {
                val cameraProvider = future.get()
                provider = cameraProvider
                // The future can resolve after onDispose (fast open/close during cold start);
                // don't bind a scanner we've already torn down — just release the camera.
                if (disposed) {
                    cameraProvider.unbindAll()
                } else {
                    cameraProvider.bindScanner(
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        executor = executor,
                        analyzer = analyzer,
                    )
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.shutdown()
            analyzer.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun ProcessCameraProvider.bindScanner(
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    executor: Executor,
    analyzer: BarcodeAnalyzer,
) {
    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { it.setAnalyzer(executor, analyzer) }
    unbindAll()
    bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
}

@Composable
private fun PermissionRationale(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(all = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera access is needed to scan the item's barcode.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGrant) { Text(text = "Grant camera access") }
    }
}
