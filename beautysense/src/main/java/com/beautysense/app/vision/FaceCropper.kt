package com.beautysense.app.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class FaceCropper {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.08f)
            .build()
    )

    suspend fun crop(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext bytes
        try {
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 4, TimeUnit.SECONDS)
            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                ?: return@withContext bytes
            val box = expand(face.boundingBox, bitmap.width, bitmap.height)
            val crop = Bitmap.createBitmap(bitmap, box.left, box.top, box.width(), box.height())
            try {
                ByteArrayOutputStream().use { stream ->
                    crop.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                    stream.toByteArray()
                }
            } finally {
                crop.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun expand(source: Rect, width: Int, height: Int): Rect {
        val horizontal = (source.width() * 0.28f).toInt()
        val top = (source.height() * 0.35f).toInt()
        val bottom = (source.height() * 0.20f).toInt()
        return Rect(
            (source.left - horizontal).coerceAtLeast(0),
            (source.top - top).coerceAtLeast(0),
            (source.right + horizontal).coerceAtMost(width),
            (source.bottom + bottom).coerceAtMost(height)
        )
    }
}
