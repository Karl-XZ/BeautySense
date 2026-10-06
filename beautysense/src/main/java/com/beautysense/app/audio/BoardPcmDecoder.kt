package com.beautysense.app.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.floor

object BoardPcmDecoder {
    fun decode(url: String): ByteArray {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(url)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("语音文件没有音轨")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("语音格式未知")
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val decoded = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: error("语音解码缓冲区不可用")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = decoder.outputFormat
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                    }
                    else -> if (outputIndex >= 0) {
                        check(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT && channelCount in 1..2) {
                            "设备暂不支持当前语音编码"
                        }
                        val output = decoder.getOutputBuffer(outputIndex)?.duplicate()
                            ?.order(ByteOrder.LITTLE_ENDIAN) ?: error("语音输出缓冲区不可用")
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        while (output.remaining() >= 2 * channelCount) {
                            val first = output.short.toInt()
                            val mono = if (channelCount == 2) (first + output.short.toInt()) / 2 else first
                            decoded.write(mono and 0xff)
                            decoded.write((mono ushr 8) and 0xff)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        check(decoded.size() <= 12_000_000) { "语音长度超出设备上限" }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            return resample(decoded.toByteArray(), sampleRate)
        } finally {
            runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }

    internal fun resample(input: ByteArray, sourceRate: Int): ByteArray {
        require(sourceRate in 8_000..192_000 && input.size >= 2)
        if (sourceRate == 16_000) return input
        val sourceFrames = input.size / 2
        val targetFrames = (sourceFrames.toLong() * 16_000 / sourceRate).toInt()
        val result = ByteArray(targetFrames * 2)
        for (i in 0 until targetFrames) {
            val position = i.toDouble() * sourceRate / 16_000
            val left = floor(position).toInt().coerceAtMost(sourceFrames - 1)
            val right = (left + 1).coerceAtMost(sourceFrames - 1)
            val first = sample(input, left)
            val second = sample(input, right)
            val value = (first + (second - first) * (position - left)).toInt().coerceIn(-32768, 32767)
            result[2 * i] = value.toByte()
            result[2 * i + 1] = (value ushr 8).toByte()
        }
        return result
    }

    private fun sample(bytes: ByteArray, index: Int): Int =
        ((bytes[2 * index].toInt() and 0xff) or (bytes[2 * index + 1].toInt() shl 8)).toShort().toInt()
}
