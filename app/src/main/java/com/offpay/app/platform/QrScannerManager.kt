package com.offpay.app.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Manages CameraX camera preview and ML Kit barcode scanning for QR codes.
 *
 * Supports:
 * - Real-time camera scanning with rear-facing camera
 * - Zoom ratio control (1.0x to 3.0x)
 * - Gallery image decoding (JPEG, PNG, GIF, WebP)
 */
class QrScannerManager {

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraControl: CameraControl? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val scannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    private val barcodeScanner = BarcodeScanning.getClient(scannerOptions)

    /**
     * Binds camera preview and barcode analysis to the given lifecycle.
     * Uses the rear-facing camera.
     *
     * @param lifecycleOwner Lifecycle to bind the camera to
     * @param previewView The PreviewView to display the camera feed
     * @param onResult Callback invoked with the decoded QR string on detection
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onResult: (String) -> Unit
    ) {
        val context = previewView.context
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var resultDelivered = false

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                @androidx.camera.core.ExperimentalGetImage
                val mediaImage = imageProxy.image
                if (mediaImage != null && !resultDelivered) {
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            val qrValue = barcodes.firstOrNull()?.rawValue
                            if (qrValue != null && !resultDelivered) {
                                resultDelivered = true
                                onResult(qrValue)
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            cameraControl = camera.cameraControl
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Sets the camera zoom ratio.
     *
     * @param ratio Zoom level clamped between 1.0x and 3.0x
     */
    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(MIN_ZOOM, MAX_ZOOM)
        cameraControl?.setZoomRatio(clamped)
    }

    /**
     * Unbinds all camera use cases and releases the camera.
     */
    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraControl = null
    }

    /**
     * Decodes a QR code from a gallery image URI.
     * Supports JPEG, PNG, GIF, and WebP formats.
     *
     * @param context Android context for content resolver access
     * @param uri URI of the image to decode
     * @return The decoded QR string, or null if no QR code was found
     */
    suspend fun decodeFromUri(context: Context, uri: Uri): String? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val qrValue = barcodes.firstOrNull()?.rawValue
                        if (continuation.isActive) {
                            continuation.resume(qrValue)
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    companion object {
        const val MIN_ZOOM = 1.0f
        const val MAX_ZOOM = 3.0f
    }
}
