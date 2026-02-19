package com.example.helloworld

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Camera-based Face Distance Tracker using ML Kit.
 * Estimates distance based on the pixel distance between eye landmarks.
 */
class FaceDistanceTracker(private val context: Context, private val lifecycleOwner: LifecycleOwner) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // ML Kit Face Detector
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
    )

    // Average Interpupillary Distance (IPD) in cm
    private val AVG_IPD_CM = 6.3f
    
    // Calibration constant (focal length * physical_dist / pixel_dist)
    // This varies by camera, but ~500f-600f is common for android front cams at 640x480
    private var focalLengthEquivalent = 550f 

    // Last update time for throttling
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL_MS = 2000L // 2 seconds

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindAnalysis()
            SettingsState.update { it.copy(trackerActive = true) }
        }, ContextCompat.getMainExecutor(context))
        
        Log.i("FaceDistanceTracker", "Starting tracker...")
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        detector.close()
        SettingsState.update { it.copy(trackerActive = false, liveDistanceCm = -1f) }
        Log.i("FaceDistanceTracker", "Tracker stopped.")
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindAnalysis() {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val currentTime = System.currentTimeMillis()
            
            // Throttle: Only process if interval has passed
            if (currentTime - lastUpdateTime < UPDATE_INTERVAL_MS) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        lastUpdateTime = System.currentTimeMillis() // Update time only on success/attempt

                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
                            val rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)

                            if (leftEye != null && rightEye != null) {
                                val p1 = leftEye.position
                                val p2 = rightEye.position
                                val pixelDist = sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
                                
                                // Distance = (FocalLength * ActualIPD) / PixelIPD
                                val estimatedDistCm = (focalLengthEquivalent * AVG_IPD_CM) / pixelDist
                                
                                // Smooth update: update directly
                                SettingsState.update { it.copy(liveDistanceCm = estimatedDistCm) }
                            }
                        } else {
                            // No face detected - don't reset immediately to avoid flickering
                            // Only reset if we fail detection multiple times or similar, 
                            // but for 10s interval, let's keep the last value or set to -1.
                            // User asked to "keep the value consistent". 
                            // If we set -1 here, the overlay shows "--". 
                            // Let's set -1 only if we REALLY lose the face.
                            SettingsState.update { it.copy(liveDistanceCm = -1f) }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
        } catch (exc: Exception) {
            Log.e("FaceDistanceTracker", "Use case binding failed", exc)
        }
    }
}
