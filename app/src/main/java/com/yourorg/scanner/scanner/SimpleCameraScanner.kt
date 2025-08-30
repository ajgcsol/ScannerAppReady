package com.yourorg.scanner.scanner

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SimpleCameraScanner(
    private val context: Context,
    private val surfaceView: SurfaceView
) {
    companion object {
        private const val TAG = "SimpleCameraScanner"
    }

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var imageReader: ImageReader? = null
    private val barcodeScanner = BarcodeScanning.getClient()
    
    private var scanCallback: ((ScanResult) -> Unit)? = null
    private var isScanning = false

    suspend fun initialize(onScanResult: (ScanResult) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            scanCallback = onScanResult
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            
            startBackgroundThread()
            
            // Find available camera
            val cameraId = findCamera() ?: throw Exception("No camera found")
            
            openCamera(cameraId) { success ->
                if (success) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(Exception("Failed to open camera"))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Camera initialization failed", e)
            continuation.resumeWithException(e)
        }
    }

    private fun findCamera(): String? {
        return try {
            val cameraManager = this.cameraManager ?: return null
            val cameraIds = cameraManager.cameraIdList
            
            // Try to find any available camera
            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                Log.d(TAG, "Found camera $cameraId with facing $facing")
                
                // Return first available camera
                return cameraId
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding camera", e)
            null
        }
    }

    private fun openCamera(cameraId: String, callback: (Boolean) -> Unit) {
        try {
            cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened: $cameraId")
                    cameraDevice = camera
                    createCameraPreview()
                    callback(true)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.d(TAG, "Camera disconnected")
                    camera.close()
                    cameraDevice = null
                    callback(false)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                    callback(false)
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted", e)
            callback(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            callback(false)
        }
    }

    private fun createCameraPreview() {
        try {
            val surface = surfaceView.holder.surface
            val reader = createImageReader()
            
            val surfaces = listOfNotNull(surface, reader?.surface)
            
            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startPreview(session, surface)
                        Log.d(TAG, "Camera preview started")
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating camera preview", e)
        }
    }

    private fun createImageReader(): ImageReader? {
        return try {
            val reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 1)
            reader.setOnImageAvailableListener({ reader ->
                if (isScanning) {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        processImage(image)
                        image.close()
                    }
                }
            }, backgroundHandler)
            imageReader = reader
            reader
        } catch (e: Exception) {
            Log.e(TAG, "Error creating image reader", e)
            null
        }
    }

    private fun processImage(image: android.media.Image) {
        try {
            val inputImage = InputImage.fromMediaImage(image, 0)
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && isScanning) {
                            val symbology = when (barcode.format) {
                                Barcode.FORMAT_QR_CODE -> "QR_CODE"
                                Barcode.FORMAT_CODE_128 -> "CODE_128"
                                Barcode.FORMAT_CODE_39 -> "CODE_39"
                                Barcode.FORMAT_EAN_13 -> "EAN_13"
                                else -> "UNKNOWN"
                            }
                            
                            val result = ScanResult(
                                code = rawValue,
                                symbology = symbology
                            )
                            
                            isScanning = false
                            scanCallback?.invoke(result)
                            break
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scanning failed", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        }
    }

    private fun startPreview(session: CameraCaptureSession, surface: Surface) {
        try {
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder?.addTarget(surface)
            imageReader?.let { requestBuilder?.addTarget(it.surface) }
            
            val request = requestBuilder?.build()
            if (request != null) {
                session.setRepeatingRequest(request, null, backgroundHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview", e)
        }
    }

    fun triggerScan() {
        isScanning = true
        Log.d(TAG, "Scan triggered")
    }

    fun stopScanning() {
        isScanning = false
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also {
            it.start()
            backgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    fun release() {
        stopScanning()
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        stopBackgroundThread()
        barcodeScanner.close()
        Log.d(TAG, "Camera resources released")
    }
}