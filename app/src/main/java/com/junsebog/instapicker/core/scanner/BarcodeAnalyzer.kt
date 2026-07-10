package com.junsebog.instapicker.core.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/**
 * Streams frames from CameraX into ML Kit, restricted to EAN-13, and hands the raw
 * decoded string back via [onBarcode]. It only throttles: the same detection
 * repeats across many frames, so a [COOLDOWN_MS] window keeps a mismatch from flooding
 * the reducer with duplicate intents while still allowing a re-scan after feedback.
 *
 * Analysis stops the moment the scanner leaves composition (the camera is unbound),
 * which is how "stop after a valid match" is enforced one layer up.
 *
 * The ML Kit client holds native resources, so this is [Closeable]: the caller must
 * [close] it when the scanner goes away, alongside unbinding the camera.
 *
 * @param clock injected time source, keeps the cooldown testable.
 */
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
    clock: () -> Long = System::currentTimeMillis,
) : ImageAnalysis.Analyzer, Closeable {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13)
            .build(),
    )

    private val throttle = ScanThrottle(windowMs = COOLDOWN_MS, clock = clock)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes -> barcodes.firstNotNullOfOrNull { it.rawValue }?.let(::emit) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun emit(raw: String) {
        if (throttle.allow()) onBarcode(raw)
    }

    /** Releases the ML Kit detector's native resources. Call from the camera's onDispose. */
    override fun close() {
        scanner.close()
    }

    private companion object {
        const val COOLDOWN_MS = 1_500L
    }
}
