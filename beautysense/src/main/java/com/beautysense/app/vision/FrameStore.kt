package com.beautysense.app.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

object FrameStore {
    private val mutableFrame = MutableStateFlow<ByteArray?>(null)
    val frame: StateFlow<ByteArray?> = mutableFrame

    @Volatile
    var capturedAt: Long = 0L
        private set

    @Volatile
    var live: Boolean = true
        private set

    fun update(bytes: ByteArray, live: Boolean = true) {
        capturedAt = System.currentTimeMillis()
        this.live = live
        mutableFrame.value = bytes
    }

    fun clear() {
        capturedAt = 0L
        live = true
        mutableFrame.value = null
    }

    fun latest(): ByteArray? = mutableFrame.value

    fun latestDataUrl(): String? = mutableFrame.value?.toDataUrl()
}

fun ByteArray.toDataUrl(mime: String = "image/jpeg"): String =
    "data:$mime;base64,${Base64.encodeToString(this, Base64.NO_WRAP)}"

fun ImageProxy.toJpeg(quality: Int = 86, mirrorHorizontally: Boolean = false): ByteArray? {
    if (format != ImageFormat.YUV_420_888) return null
    val width = width
    val height = height
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    val nv21 = ByteArray(width * height + width * height / 2)

    copyPlane(yPlane, width, height, nv21, 0, 1)
    var offset = width * height
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    for (row in 0 until height / 2) {
        val uRow = row * uPlane.rowStride
        val vRow = row * vPlane.rowStride
        for (column in 0 until width / 2) {
            nv21[offset++] = vBuffer.get(vRow + column * vPlane.pixelStride)
            nv21[offset++] = uBuffer.get(uRow + column * uPlane.pixelStride)
        }
    }

    val stream = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, width, height, null)
        .compressToJpeg(Rect(0, 0, width, height), quality, stream)
    val raw = stream.toByteArray()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0 && !mirrorHorizontally) return raw
    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
        postRotate(rotation.toFloat())
        if (mirrorHorizontally) postScale(-1f, 1f)
    }, true)
    if (rotated !== bitmap) bitmap.recycle()
    val rotatedStream = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, quality, rotatedStream)
    rotated.recycle()
    return rotatedStream.toByteArray()
}

private fun copyPlane(
    plane: ImageProxy.PlaneProxy,
    width: Int,
    height: Int,
    output: ByteArray,
    outputOffset: Int,
    outputStride: Int
) {
    val buffer = plane.buffer
    var outputIndex = outputOffset
    for (row in 0 until height) {
        val rowStart = row * plane.rowStride
        for (column in 0 until width) {
            output[outputIndex] = buffer.get(rowStart + column * plane.pixelStride)
            outputIndex += outputStride
        }
    }
}
