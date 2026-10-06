package com.beautysense.app.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beautysense.app.vision.FrameStore
import com.beautysense.app.vision.toJpeg
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun MirrorCameraPreview(modifier: Modifier = Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError = rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.testTag("mirror-camera-preview"))
    DisposableEffect(context, lifecycleOwner, previewView) {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val disposed = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed.get()) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val livePreview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val liveAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(720, 960))
                    .build()
                var lastFrameAt = 0L
                liveAnalysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastFrameAt >= 650) {
                            image.toJpeg(mirrorHorizontally = true)?.let(FrameStore::update)
                            lastFrameAt = now
                        }
                    } finally {
                        image.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, livePreview, liveAnalysis)
                preview = livePreview
                analysis = liveAnalysis
            } catch (error: Exception) {
                currentOnError.value(error.message ?: "无法打开前置摄像头")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed.set(true)
            val active = listOfNotNull(preview, analysis)
            if (active.isNotEmpty()) provider?.unbind(*active.toTypedArray())
            cameraExecutor.shutdownNow()
        }
    }
}
