package com.beautysense.app.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.beautysense.app.MainActivity
import com.beautysense.app.vision.FrameStore
import com.beautysense.app.vision.toJpeg
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class WearForegroundService : LifecycleService(), SensorEventListener {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val boardExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraAnalysis: ImageAnalysis? = null
    private var sensorManager: SensorManager? = null
    private var boardMode = false
    private var mirrorMode = false
    private var sensorsStarted = false
    private var boardStarted = false
    @Volatile private var boardPaused = false
    private var lastBoardFallCounter: Long? = null
    private var lastFrameAt = 0L
    private var lastFallAt = 0L
    private var angularVelocity = 0f

    override fun onCreate() {
        super.onCreate()
        boardMode = BoardClient(this).configured()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_PAUSE -> {
                if (mirrorMode) return Service.START_STICKY
                boardPaused = true
                cameraAnalysis?.let { cameraProvider?.unbind(it) }
                if (boardMode) FrameStore.clear()
                updateNotification("已暂停摄像头，安全传感器仍在运行")
            }
            ACTION_RESUME -> {
                boardPaused = false
                if (!boardMode && !mirrorMode) startCamera()
                updateNotification(if (mirrorMode) "前置摄像头与对话正在运行" else if (boardMode) "头戴设备正在连接" else "摄像头和安全感知正在运行")
            }
            ACTION_MIRROR -> {
                mirrorMode = true
                boardPaused = true
                cameraAnalysis?.let { cameraProvider?.unbind(it) }
                cameraAnalysis = null
                startForegroundForMode("前置摄像头与对话正在运行")
                startSensors()
            }
            else -> {
                mirrorMode = false
                boardPaused = false
                startForegroundForMode(if (boardMode) "头戴设备正在连接" else "摄像头和安全感知正在运行")
                if (boardMode) startBoard() else {
                    startSensors()
                    startCamera()
                }
            }
        }
        return if (mirrorMode) Service.START_NOT_STICKY else Service.START_STICKY
    }

    private fun startForegroundForMode(text: String) {
        val type = if (boardMode && !mirrorMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else if (boardMode && !mirrorMode) {
            0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(text), type)
    }

    override fun onDestroy() {
        cameraAnalysis?.let { cameraProvider?.unbind(it) }
        sensorManager?.unregisterListener(this)
        cameraExecutor.shutdown()
        boardExecutor.shutdownNow()
        if (boardMode) FrameStore.clear()
        WearEvents.emit(WearEvent.ServiceStopped)
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                angularVelocity = sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val acceleration = sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
                val accelerationG = acceleration / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (accelerationG >= 2.6f && angularVelocity >= 2.2f && now - lastFallAt > 12_000) {
                    lastFallAt = now
                    vibrateRisk()
                    WearEvents.emit(WearEvent.FallCandidate(accelerationG, angularVelocity))
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startSensors() {
        if (sensorsStarted) return
        sensorsStarted = true
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun startCamera() {
        if (mirrorMode || boardMode || boardPaused) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (mirrorMode || boardPaused) return@addListener
            val provider = future.get()
            cameraProvider = provider
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(720, 960))
                .build()
            analysis.setAnalyzer(cameraExecutor) { image ->
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrameAt >= 650) {
                        image.toJpeg()?.let(FrameStore::update)
                        lastFrameAt = now
                    }
                } finally {
                    image.close()
                }
            }
            provider.unbindAll()
            cameraAnalysis = analysis
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startBoard() {
        if (boardStarted) return
        boardStarted = true
        val board = BoardClient(this)
        boardExecutor.execute {
            var connected = false
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val status = board.status()
                    val fallCount = status.optLong("fallCounter")
                    val newFall = lastBoardFallCounter != fallCount && fallCount > 0 &&
                        status.optLong("fallAgeMs", Long.MAX_VALUE) < 12_000
                    lastBoardFallCounter = fallCount
                    if (newFall) {
                        WearEvents.emit(WearEvent.FallCandidate(
                            status.optDouble("fallAccelG").toFloat(),
                            status.optDouble("fallGyroRadS").toFloat()
                        ))
                    }
                    if (!boardPaused && !mirrorMode) {
                        check(status.optBoolean("camera")) { "设备摄像头不可用" }
                        FrameStore.update(board.frame())
                    }
                    if (!connected) {
                        connected = true
                        WearEvents.emit(WearEvent.BoardStatus(true, "设备已连接"))
                        updateNotification(if (boardPaused) "头戴设备已连接，摄像头已暂停" else "头戴设备画面已连接")
                    }
                    Thread.sleep(850)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    if (!mirrorMode) FrameStore.clear()
                    if (connected) updateNotification("头戴设备连接中断")
                    connected = false
                    if (!mirrorMode) WearEvents.emit(WearEvent.BoardStatus(false, error.message ?: "设备未连接"))
                    try { Thread.sleep(3000) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "妆伴佩戴模式", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示摄像头、麦克风和安全感知运行状态"
                setShowBadge(false)
            }
        )
    }

    private fun notification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WearForegroundService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WearForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (mirrorMode) "妆伴魔镜运行中" else "妆伴正在佩戴")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
        if (!mirrorMode) builder.addAction(android.R.drawable.ic_media_pause, "暂停摄像头", pauseIntent)
        return builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "结束", stopIntent).build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun vibrateRisk() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 350, 150, 350, 150, 500)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    companion object {
        const val ACTION_STOP = "com.beautysense.app.STOP_WEAR"
        const val ACTION_PAUSE = "com.beautysense.app.PAUSE_WEAR"
        const val ACTION_RESUME = "com.beautysense.app.RESUME_WEAR"
        const val ACTION_START = "com.beautysense.app.START_WEAR"
        const val ACTION_MIRROR = "com.beautysense.app.START_MIRROR"
        private const val CHANNEL_ID = "beautysense_wear"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WearForegroundService::class.java).setAction(ACTION_START))
        }

        fun startMirror(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WearForegroundService::class.java).setAction(ACTION_MIRROR))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WearForegroundService::class.java))
        }
    }
}
