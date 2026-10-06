package com.beautysense.app.model

import org.json.JSONArray
import org.json.JSONObject

enum class UserMode(val wireValue: String) {
    BEAUTY("beauty"),
    ACCESSIBLE("accessible")
}

enum class AppScreen {
    HOME,
    CALIBRATION,
    WEAR,
    MIRROR,
    GUIDE,
    SKIN,
    PREVIEW,
    LOGS,
    SETTINGS,
    SAFETY
}

enum class TaskPhase {
    IDLE,
    ROUTING,
    PLANNING,
    PLAN_READY,
    GUIDING,
    VERIFYING,
    OPTIONAL_REPAIR,
    REQUIRED_REPAIR,
    AWAITING_FOLLOW_UP,
    SAFETY_PENDING,
    ERROR
}

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val role: String,
    val text: String,
    val alert: Boolean = false
)

data class MakeupStep(
    val id: String,
    val title: String,
    val instruction: String,
    val spokenInstruction: String,
    val productName: String,
    val completionCriterion: String,
    val raw: JSONObject
)

data class MakeupLookConfig(
    val version: Int = 1,
    val styleId: String = "natural",
    val styleName: String = "自然清透",
    val foundationType: String = "liquid",
    val foundationTypeName: String = "粉底液",
    val foundationFinish: String = "natural",
    val foundationFinishName: String = "自然",
    val foundationCoverage: String = "medium",
    val foundationCoverageName: String = "中等遮盖",
    val blushColorName: String = "柔粉",
    val blushColorHex: String = "#D88F96",
    val eyeStyle: String = "soft",
    val eyeStyleName: String = "柔和眼妆",
    val eyeColorName: String = "暖棕",
    val eyeColorHex: String = "#8A6556",
    val lipType: String = "lipstick",
    val lipTypeName: String = "口红",
    val lipColorName: String = "豆沙色",
    val lipColorHex: String = "#A95F65",
    val lipFinish: String = "satin",
    val lipFinishName: String = "缎光",
    val source: String = "default"
) {
    fun spokenSummary(): String =
        "$styleName，$foundationTypeName、$foundationFinishName、$foundationCoverageName，${eyeColorName}${eyeStyleName}、${blushColorName}腮红、${lipColorName}${lipFinishName}${lipTypeName}"

    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("style", JSONObject().put("id", styleId).put("name", styleName))
        .put(
            "foundation",
            JSONObject()
                .put("type", foundationType)
                .put("typeName", foundationTypeName)
                .put("finish", foundationFinish)
                .put("finishName", foundationFinishName)
                .put("coverage", foundationCoverage)
                .put("coverageName", foundationCoverageName)
        )
        .put("blush", JSONObject().put("colorName", blushColorName).put("colorHex", blushColorHex))
        .put(
            "eye",
            JSONObject()
                .put("style", eyeStyle)
                .put("styleName", eyeStyleName)
                .put("colorName", eyeColorName)
                .put("colorHex", eyeColorHex)
        )
        .put(
            "lip",
            JSONObject()
                .put("type", lipType)
                .put("typeName", lipTypeName)
                .put("colorName", lipColorName)
                .put("colorHex", lipColorHex)
                .put("finish", lipFinish)
                .put("finishName", lipFinishName)
        )
        .put("source", source)
}

data class MakeupPlan(
    val title: String,
    val summary: String,
    val spokenIntro: String,
    val estimatedMinutes: Int,
    val steps: List<MakeupStep>,
    val lookConfig: MakeupLookConfig,
    val raw: JSONObject
)

data class SkinMetric(val key: String, val label: String, val score: Int)

data class SkinReport(
    val summary: String,
    val metrics: List<SkinMetric>,
    val raw: JSONObject
)

data class Verification(
    val status: String,
    val repairLevel: String,
    val speech: String,
    val repairInstruction: String,
    val recaptureInstruction: String,
    val confidence: Double,
    val hapticCue: String
)

data class RuntimeLog(
    val time: Long = System.currentTimeMillis(),
    val category: String,
    val title: String,
    val detail: String = ""
)

data class UiState(
    val screen: AppScreen = AppScreen.HOME,
    val mode: UserMode = UserMode.BEAUTY,
    val phase: TaskPhase = TaskPhase.IDLE,
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val messages: List<ChatMessage> = listOf(
        ChatMessage(role = "assistant", text = "设备已经启动。今天要去什么场合，我帮您搭配个妆容？")
    ),
    val logs: List<RuntimeLog> = emptyList(),
    val evidence: JSONObject? = null,
    val plan: MakeupPlan? = null,
    val lookConfig: MakeupLookConfig = MakeupLookConfig(),
    val lookConfigDirty: Boolean = false,
    val currentStepIndex: Int = -1,
    val skinReport: SkinReport? = null,
    val skincareAdvice: JSONObject? = null,
    val pendingSkinSave: Boolean = false,
    val previewBefore: ByteArray? = null,
    val previewAfter: ByteArray? = null,
    val baselineFace: ByteArray? = null,
    val currentFrame: ByteArray? = null,
    val lastVerification: Verification? = null,
    val optionalRepairPending: Boolean = false,
    val activeSearchTarget: String? = null,
    val calibrationStep: Int = 0,
    val busy: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val wearing: Boolean = false,
    val mirrorActive: Boolean = false,
    val boardConnected: Boolean = false,
    val boardStatus: String = "未配置",
    val serverReady: Boolean = false,
    val statusText: String = "等待连接",
    val inputText: String = "",
    val errorText: String? = null,
    val safetyMessage: String? = null
) {
    val currentStep: MakeupStep?
        get() = plan?.steps?.getOrNull(currentStepIndex)
}

fun JSONObject.toMakeupPlan(): MakeupPlan {
    val stepsJson = optJSONArray("steps") ?: JSONArray()
    val steps = buildList {
        for (index in 0 until stepsJson.length()) {
            val item = stepsJson.optJSONObject(index) ?: continue
            val product = item.optString("productName")
                .ifBlank { item.optJSONObject("product")?.optString("name").orEmpty() }
            val instruction = item.optString("instruction")
                .ifBlank { item.optString("action") }
            val criteria = item.optJSONArray("completionCriteria")?.let { array ->
                buildList {
                    for (criteriaIndex in 0 until array.length()) {
                        array.optString(criteriaIndex).takeIf(String::isNotBlank)?.let(::add)
                    }
                }.joinToString("；")
            }.orEmpty().ifBlank { item.optString("completionCriterion") }
            add(
                MakeupStep(
                    id = item.optString("id", "step_${index + 1}"),
                    title = item.optString("title", "第 ${index + 1} 步"),
                    instruction = instruction,
                    spokenInstruction = item.optString("spokenInstruction").ifBlank { instruction },
                    productName = product,
                    completionCriterion = criteria,
                    raw = item
                )
            )
        }
    }
    return MakeupPlan(
        title = optString("title", "今日妆容"),
        summary = optString("summary"),
        spokenIntro = optString("spokenIntro").ifBlank { optString("summary") },
        estimatedMinutes = optInt("estimatedMinutes", 8),
        steps = steps,
        lookConfig = optJSONObject("lookConfig")?.toMakeupLookConfig() ?: MakeupLookConfig(),
        raw = this
    )
}

fun JSONObject.toMakeupLookConfig(): MakeupLookConfig {
    val style = optJSONObject("style") ?: JSONObject()
    val foundation = optJSONObject("foundation") ?: JSONObject()
    val blush = optJSONObject("blush") ?: JSONObject()
    val eye = optJSONObject("eye") ?: JSONObject()
    val lip = optJSONObject("lip") ?: JSONObject()
    return MakeupLookConfig(
        version = optInt("version", 1),
        styleId = style.optString("id", "natural"),
        styleName = style.optString("name", "自然清透"),
        foundationType = foundation.optString("type", "liquid"),
        foundationTypeName = foundation.optString("typeName", "粉底液"),
        foundationFinish = foundation.optString("finish", "natural"),
        foundationFinishName = foundation.optString("finishName", "自然"),
        foundationCoverage = foundation.optString("coverage", "medium"),
        foundationCoverageName = foundation.optString("coverageName", "中等遮盖"),
        blushColorName = blush.optString("colorName", "柔粉"),
        blushColorHex = blush.optString("colorHex", "#D88F96"),
        eyeStyle = eye.optString("style", "soft"),
        eyeStyleName = eye.optString("styleName", "柔和眼妆"),
        eyeColorName = eye.optString("colorName", "暖棕"),
        eyeColorHex = eye.optString("colorHex", "#8A6556"),
        lipType = lip.optString("type", "lipstick"),
        lipTypeName = lip.optString("typeName", "口红"),
        lipColorName = lip.optString("colorName", "豆沙色"),
        lipColorHex = lip.optString("colorHex", "#A95F65"),
        lipFinish = lip.optString("finish", "satin"),
        lipFinishName = lip.optString("finishName", "缎光"),
        source = optString("source", "agent")
    )
}

fun JSONObject.toVerification(): Verification = Verification(
    status = optString("status", "UNCERTAIN"),
    repairLevel = optString("repairLevel", "NONE"),
    speech = optString("speech"),
    repairInstruction = optString("repairInstruction"),
    recaptureInstruction = optString("recaptureInstruction"),
    confidence = optDouble("confidence", 0.0),
    hapticCue = optString("hapticCue", "none")
)

fun JSONObject.toSkinReport(): SkinReport {
    val scoreObject = optJSONObject("scores") ?: optJSONObject("metrics") ?: JSONObject()
    val labels = linkedMapOf(
        "hydration" to "水润度",
        "uniformity" to "均匀度",
        "oiliness" to "油光",
        "redness" to "泛红",
        "texture" to "平滑度",
        "pores" to "毛孔",
        "blemishes" to "瑕疵",
        "pigment" to "色素沉着",
        "brightness" to "明亮度"
    )
    val metrics = labels.mapNotNull { (key, label) ->
        if (!scoreObject.has(key)) null else SkinMetric(key, label, scoreObject.optDouble(key, 0.0).toInt())
    }
    return SkinReport(
        summary = optString("summary").ifBlank { optString("speech", "皮肤状态分析完成。") },
        metrics = metrics,
        raw = this
    )
}
