package com.beautysense.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.beautysense.app.ui.BeautySenseRoot
import com.beautysense.app.ui.BeautySenseTheme
import com.beautysense.app.vision.FrameStore
import com.beautysense.app.wear.WearForegroundService

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<BeautySenseViewModel>()
    private var startAfterPermission = false
    private var startMirrorAfterPermission = false

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val camera = grants[Manifest.permission.CAMERA] == true || hasPermission(Manifest.permission.CAMERA)
        val microphone = grants[Manifest.permission.RECORD_AUDIO] == true || hasPermission(Manifest.permission.RECORD_AUDIO)
        if (startAfterPermission && camera && microphone) beginWearMode()
        if (startMirrorAfterPermission) {
            if (camera && microphone) beginMirrorMode()
            else viewModel.reportMirrorCameraError("魔镜需要前置摄像头和麦克风权限")
        }
        startAfterPermission = false
        startMirrorAfterPermission = false
    }

    private val imageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            contentResolver.openInputStream(uri)?.use { FrameStore.update(it.readBytes(), live = false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeautySenseTheme {
                BeautySenseRoot(
                    viewModel = viewModel,
                    onStartWear = ::requestWearPermissions,
                    onStartMirror = ::requestMirrorPermissions,
                    onImportImage = { imageLauncher.launch("image/*") },
                    onStopWear = {
                        WearForegroundService.stop(this)
                        if (viewModel.state.value.mirrorActive) viewModel.stopMirror() else viewModel.markWearing(false)
                    },
                    onStopMirror = ::stopMirrorMode
                )
            }
        }
    }

    private fun requestWearPermissions() {
        if (viewModel.boardConfigured()) {
            beginWearMode()
            return
        }
        if (hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            beginWearMode()
            return
        }
        startAfterPermission = true
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun beginWearMode() {
        if (viewModel.state.value.mirrorActive) viewModel.stopMirror()
        WearForegroundService.start(this)
        viewModel.markWearing(true)
        viewModel.startCalibration()
    }

    private fun requestMirrorPermissions() {
        if (hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            beginMirrorMode()
            return
        }
        startMirrorAfterPermission = true
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun beginMirrorMode() {
        WearForegroundService.startMirror(this)
        viewModel.startMirror()
    }

    private fun stopMirrorMode() {
        WearForegroundService.stop(this)
        viewModel.stopMirror()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
