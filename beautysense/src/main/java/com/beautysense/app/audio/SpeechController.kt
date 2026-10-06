package com.beautysense.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import com.beautysense.app.network.BeautySenseApi
import com.beautysense.app.wear.BoardClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SpeechController(
    private val context: Context,
    private val api: BeautySenseApi
) {
    private val board = BoardClient(context)
    private val stopExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var lastBoardStop: Future<*>? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var boardRecordingStartedAt: Long? = null
    private var player: MediaPlayer? = null
    private val generation = AtomicLong(0)
    @Volatile var preferLocalAudio: Boolean = false

    fun startRecording() {
        stopPlayback()
        stopRecordingSilently()
        if (board.configured() && !preferLocalAudio) {
            boardRecordingStartedAt = System.currentTimeMillis()
            return
        }
        val file = File(context.cacheDir, "speech-${System.nanoTime()}.m4a")
        val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        next.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        next.setAudioEncodingBitRate(64_000)
        next.setAudioSamplingRate(16_000)
        next.setOutputFile(file.absolutePath)
        next.prepare()
        next.start()
        recordingFile = file
        recorder = next
    }

    fun recordingAmplitude(): Int = recorder?.maxAmplitude ?: if (boardRecordingStartedAt != null) Int.MAX_VALUE else 0

    fun cancelRecording() = stopRecordingSilently()

    suspend fun stopAndTranscribe(sessionId: String, mode: String): String = withContext(Dispatchers.IO) {
        val boardStart = boardRecordingStartedAt
        if (boardStart != null) {
            boardRecordingStartedAt = null
            val duration = (System.currentTimeMillis() - boardStart).toInt().coerceIn(250, 10000)
            val audio = Base64.encodeToString(board.microphone(duration), Base64.NO_WRAP)
            return@withContext api.post(
                "/api/speech/transcribe",
                JSONObject().put("sessionId", sessionId).put("mode", mode)
                    .put("audioDataUrl", "data:audio/wav;base64,$audio")
            ).optString("transcript")
        }
        val file = recordingFile ?: return@withContext ""
        try {
            recorder?.stop()
        } finally {
            recorder?.release()
            recorder = null
            recordingFile = null
        }
        try {
            if (file.length() < 600) return@withContext ""
            val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            api.post(
                "/api/speech/transcribe",
                JSONObject()
                    .put("sessionId", sessionId)
                    .put("mode", mode)
                    .put("audioDataUrl", "data:audio/mp4;base64,$data")
            ).optString("transcript")
        } finally {
            file.delete()
        }
    }

    suspend fun speak(sessionId: String, mode: String, text: String, onState: (Boolean) -> Unit) {
        if (text.isBlank()) return
        val myGeneration = generation.incrementAndGet()
        stopPlayerOnly()
        onState(true)
        try {
            val response = api.post(
                "/api/speech/synthesize",
                JSONObject().put("sessionId", sessionId).put("mode", mode).put("text", text)
            )
            if (generation.get() != myGeneration) return
            val url = response.optString("audioUrl")
            if (url.isBlank()) return
            if (board.configured() && !preferLocalAudio) {
                try {
                    withContext(Dispatchers.IO) { speakOnBoard(url, myGeneration) }
                    onState(false)
                    return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep spoken guidance available through the phone if the board link fails.
                }
            }
            withContext(Dispatchers.Main) {
                if (generation.get() != myGeneration) return@withContext
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .build()
                    )
                    setDataSource(url)
                    setOnPreparedListener { if (generation.get() == myGeneration) it.start() }
                    setOnCompletionListener {
                        it.release()
                        if (player === it) player = null
                        onState(false)
                    }
                    setOnErrorListener { mediaPlayer, _, _ ->
                        mediaPlayer.release()
                        if (player === mediaPlayer) player = null
                        onState(false)
                        true
                    }
                    prepareAsync()
                }
            }
        } catch (_: Exception) {
            onState(false)
        }
    }

    fun stopPlayback() {
        generation.incrementAndGet()
        stopPlayerOnly()
        if (board.configured() && !preferLocalAudio) lastBoardStop = stopExecutor.submit { runCatching { board.stopAudio() } }
    }

    fun release() {
        stopPlayback()
        stopRecordingSilently()
        stopExecutor.shutdown()
    }

    private fun stopPlayerOnly() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    private fun stopRecordingSilently() {
        boardRecordingStartedAt = null
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        recordingFile?.delete()
        recordingFile = null
    }

    private fun speakOnBoard(url: String, myGeneration: Long) {
        val pcm = BoardPcmDecoder.decode(url)
        lastBoardStop?.get(5, TimeUnit.SECONDS)
        var offset = 0
        while (offset < pcm.size && generation.get() == myGeneration) {
            val end = (offset + 32_000).coerceAtMost(pcm.size)
            board.playPcm(pcm.copyOfRange(offset, end))
            val deadline = System.currentTimeMillis() + 5000
            while (generation.get() == myGeneration && board.status().optBoolean("speaking")) {
                check(System.currentTimeMillis() < deadline) { "设备播报超时" }
                Thread.sleep(80)
            }
            offset = end
        }
    }
}
