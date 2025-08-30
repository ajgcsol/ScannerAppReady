package com.yourorg.scanner.scanner

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class CameraXScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    companion object {
        private const val TAG = "CameraXScanner"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val barcodeScanner = BarcodeScanning.getClient()
    
    private var scanCallback: ((com.yourorg.scanner.scanner.ScanResult) -> Unit)? = null
    private var isScanning = false
    private var autoDetectEnabled = false
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var preview: Preview? = null

    suspend fun initialize(onScanResult: (com.yourorg.scanner.scanner.ScanResult) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            scanCallback = onScanResult
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    setupCamera()
                    continuation.resume(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Camera initialization failed", e)
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
            
        } catch (e: Exception) {
            Log.e(TAG, "Scanner initialization failed", e)
            continuation.resumeWithException(e)
        }
    }

    private fun setupCamera() {
        val cameraProvider = this.cameraProvider ?: return

        // Preview use case
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image analysis use case for barcode scanning
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(1280, 720))
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (isScanning || autoDetectEnabled) {
                        processImageProxy(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                }
            }

        // Use the current camera selector (defaults to back camera)
        val cameraSelector = if (cameraProvider.hasCamera(currentCameraSelector)) {
            currentCameraSelector
        } else {
            // Fallback to any available camera
            when {
                cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> null
            }
        }
        
        Log.d(TAG, "Using camera: ${if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"}")

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            if (cameraSelector != null) {
                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                Log.d(TAG, "Camera setup completed successfully")
            } else {
                Log.e(TAG, "No camera available")
                throw Exception("No camera available on device")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            // Try with just preview (no analysis) as fallback
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector ?: CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
                Log.d(TAG, "Camera setup completed with preview only")
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback camera setup also failed", e2)
            }
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && (isScanning || autoDetectEnabled)) {
                            val symbology = when (barcode.format) {
                                Barcode.FORMAT_QR_CODE -> "QR_CODE"
                                Barcode.FORMAT_CODE_128 -> "CODE_128"
                                Barcode.FORMAT_CODE_39 -> "CODE_39"
                                Barcode.FORMAT_EAN_13 -> "EAN_13"
                                Barcode.FORMAT_EAN_8 -> "EAN_8"
                                Barcode.FORMAT_UPC_A -> "UPC_A"
                                Barcode.FORMAT_UPC_E -> "UPC_E"
                                Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
                                Barcode.FORMAT_PDF417 -> "PDF417"
                                Barcode.FORMAT_AZTEC -> "AZTEC"
                                else -> "UNKNOWN"
                            }
                            
                            val result = com.yourorg.scanner.scanner.ScanResult(
                                code = rawValue,
                                symbology = symbology,
                                timestamp = System.currentTimeMillis()
                            )
                            
                            // Stop scanning to prevent duplicate scans
                            isScanning = false
                            autoDetectEnabled = false
                            
                            Log.d(TAG, "Barcode detected: ${result.code} (${result.symbology})")
                            
                            // Add a delay before invoking callback to prevent rapid duplicate scans
                            GlobalScope.launch {
                                delay(500) // Half second delay
                                scanCallback?.invoke(result)
                            }
                            break // Only process first barcode found
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    suspend fun triggerScan() {
        if (cameraProvider == null) {
            throw IllegalStateException("Camera not initialized")
        }
        
        isScanning = true
        Log.d(TAG, "Scan triggered - camera will detect barcodes")
        
        // Reset scanning after 10 seconds to allow re-triggering
        kotlinx.coroutines.delay(10000)
        if (isScanning) {
            isScanning = false
            Log.d(TAG, "Scan timeout - ready for next scan")
        }
    }

    fun stopScanning() {
        isScanning = false
    }
    
    fun setAutoDetect(enabled: Boolean) {
        autoDetectEnabled = enabled
        Log.d(TAG, "Auto-detect ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun switchCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // Re-setup camera with new selector
        setupCamera()
        Log.d(TAG, "Switched to ${if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"} camera")
    }
    
    fun isUsingFrontCamera(): Boolean {
        return currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
    }

    fun release() {
        try {
            isScanning = false
            autoDetectEnabled = false
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            barcodeScanner.close()
            Log.d(TAG, "Camera resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
    }
}