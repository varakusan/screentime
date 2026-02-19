package com.example.helloworld

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Camera-based Face Distance Tracker using ML Kit.
 * Uses a SELF-MANAGED LifecycleOwner so CameraX stays bound to THIS class
 * rather than the service or activity. This means the camera keeps running
 * even when the main app is closed, as long as the OverlayService is alive.
 */
class FaceDistanceTracker(private val context: Context) {

    // ── Self-managed lifecycle ────────────────────────────────────
    // We do NOT rely on the service's LifecycleOwner so the camera
    // cannot be accidentally stopped by activity destruction.
    private val cameraLifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
    )

    private val AVG_IPD_CM = 6.3f
    private val focalLengthEquivalent = 550f

    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL_MS = 2000L

    private var isRunning = false

    // ── Public API ────────────────────────────────────────────────

    fun start() {
        if (isRunning) {
            Log.i(TAG, "Already running, skipping start.")
            return
        }

        // Ensure executor is alive
        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        // Move our lifecycle to STARTED — CameraX will bind to this
        ContextCompat.getMainExecutor(context).execute {
            try {
                if (cameraLifecycleOwner.registry.currentState == Lifecycle.State.DESTROYED) {
                    // Cannot restart a destroyed lifecycle; app needs to be restarted
                    Log.w(TAG, "Lifecycle already destroyed, cannot restart.")
                    return@execute
                }
                cameraLifecycleOwner.registry.currentState = Lifecycle.State.STARTED
            } catch (e: Exception) {
                Log.e(TAG, "Could not set lifecycle to STARTED", e)
                return@execute
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindAnalysis()
                    isRunning = true
                    SettingsState.update { it.copy(trackerActive = true) }
                    Log.i(TAG, "Camera bound successfully.")
                } catch (e: Exception) {
                    Log.e(TAG, "Camera initialization failed", e)
                    SettingsState.update { it.copy(trackerActive = false) }
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /** Pauses tracking (e.g., screen off). Camera stays bound so restart is instant. */
    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Unbind error: ${e.message}")
        }
        isRunning = false
        SettingsState.update { it.copy(trackerActive = false, liveDistanceCm = -1f) }
        Log.i(TAG, "Tracker paused (camera unbound).")
    }

    /** Full cleanup — call ONLY from OverlayService.onDestroy(). */
    fun destroy() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Unbind on destroy: ${e.message}")
        }
        try {
            // Destroy our self-managed lifecycle so CameraX fully releases resources
            ContextCompat.getMainExecutor(context).execute {
                try {
                    cameraLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        isRunning = false
        if (!cameraExecutor.isShutdown) cameraExecutor.shutdown()
        try { detector.close() } catch (_: Exception) {}
        SettingsState.update { it.copy(trackerActive = false, liveDistanceCm = -1f) }
        Log.i(TAG, "Tracker destroyed.")
    }

    // ── Private ───────────────────────────────────────────────────

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindAnalysis() {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime < UPDATE_INTERVAL_MS) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        lastUpdateTime = System.currentTimeMillis()
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val leftEye  = face.getLandmark(FaceLandmark.LEFT_EYE)
                            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                            if (leftEye != null && rightEye != null) {
                                val p1 = leftEye.position
                                val p2 = rightEye.position
                                val pixelDist = sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
                                val distCm = (focalLengthEquivalent * AVG_IPD_CM) / pixelDist
                                SettingsState.update { it.copy(liveDistanceCm = distCm) }
                            }
                        } else {
                            SettingsState.update { it.copy(liveDistanceCm = -1f) }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                cameraLifecycleOwner,          // ← our self-managed lifecycle
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            isRunning = false
        }
    }

    companion object {
        private const val TAG = "FaceDistanceTracker"
    }
}
