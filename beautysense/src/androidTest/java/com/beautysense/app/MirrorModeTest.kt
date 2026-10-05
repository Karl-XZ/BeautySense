package com.beautysense.app

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.beautysense.app.vision.FrameStore
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MirrorModeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun frontCameraStaysLiveAcrossNavigationAndStopsOnExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.CAMERA)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.RECORD_AUDIO)

        composeRule.onNodeWithText("开启妆伴魔镜").performClick()
        composeRule.onNodeWithText("小妆对话").assertExists()
        composeRule.waitUntil(20_000) { FrameStore.latest() != null }
        val firstFrameAt = FrameStore.capturedAt
        assertTrue(FrameStore.latest()!!.size > 1_000)
        val previewBounds = composeRule.onNodeWithTag("mirror-camera-preview").fetchSemanticsNode().boundsInRoot
        val chatBounds = composeRule.onNodeWithText("小妆对话").fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithText("妆伴魔镜").fetchSemanticsNode().boundsInRoot
        assertTrue("Preview fills the area behind the collapsed chat", previewBounds.bottom > chatBounds.top)
        assertTrue("Preview must stay below header", previewBounds.top >= titleBounds.bottom)
        composeRule.onNodeWithText("输入消息").assertDoesNotExist()
        composeRule.onNodeWithText("展开对话 ▴").performClick()
        composeRule.onNodeWithText("输入消息").assertExists()
        assertTrue("Opening chat must not resize camera", previewBounds == composeRule.onNodeWithTag("mirror-camera-preview").fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithText("收起对话 ▾").performClick()
        composeRule.onNodeWithText("输入消息").assertDoesNotExist()

        composeRule.onNodeWithText("记录").performClick()
        composeRule.waitUntil(10_000) { FrameStore.capturedAt > firstFrameAt }
        composeRule.onNodeWithText("魔镜").performClick()
        composeRule.onNodeWithText("退出魔镜").performClick()
        composeRule.waitUntil(10_000) { FrameStore.latest() == null }
        composeRule.onNodeWithText("开启妆伴魔镜").assertExists()
    }
}
