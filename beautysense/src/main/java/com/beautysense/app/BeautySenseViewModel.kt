package com.beautysense.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beautysense.app.audio.SpeechController
import com.beautysense.app.audio.WakeWord
import com.beautysense.app.model.AppScreen
import com.beautysense.app.model.ChatMessage
import com.beautysense.app.model.MakeupLookConfig
import com.beautysense.app.model.RuntimeLog
import com.beautysense.app.model.TaskPhase
import com.beautysense.app.model.UiState
import com.beautysense.app.model.UserMode
import com.beautysense.app.model.toMakeupLookConfig
import com.beautysense.app.model.toMakeupPlan
import com.beautysense.app.model.toSkinReport
import com.beautysense.app.model.toVerification
import com.beautysense.app.network.ApiException
import com.beautysense.app.network.BeautySenseApi
import com.beautysense.app.vision.FaceCropper
import com.beautysense.app.vision.FrameStore
import com.beautysense.app.vision.toDataUrl
import com.beautysense.app.wear.WearEvent
import com.beautysense.app.wear.WearEvents
import com.beautysense.app.wear.BoardClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

class BeautySenseViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("beautysense", Context.MODE_PRIVATE)
    private val api = BeautySenseApi(preferences.getString("server_url", BuildConfig.DEFAULT_SERVER_URL).orEmpty())
    private val speech = SpeechController(application, api)
    private val wakeSpeech = SpeechController(application, api)
    private val board = BoardClient(application)
    private val faceCropper = FaceCropper()
    private val turnGeneration = AtomicLong(0)
    private var activeTurn: Job? = null
    private var wakeJob: Job? = null
    private var wakeActive = false

    private val mutableState = MutableStateFlow(UiState(
        lookConfig = loadLookConfig(),
        boardStatus = if (board.configured()) "已配置，开始佩戴时连接" else "未配置"
    ))
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            FrameStore.frame.collect { frame ->
                mutableState.update { it.copy(currentFrame = frame) }
            }
        }
        viewModelScope.launch {
            WearEvents.events.collect { event ->
                when (event) {
                    is WearEvent.FallCandidate -> handleFallCandidate(event)
                    is WearEvent.BoardStatus -> mutableState.update {
                        it.copy(boardConnected = event.connected, boardStatus = event.detail)
                    }
                    WearEvent.ServiceStopped -> {
                        val wasMirror = mutableState.value.mirrorActive
                        mutableState.update {
                            it.copy(wearing = false, mirrorActive = false, listening = false,
                                screen = if (it.screen == AppScreen.MIRROR) AppScreen.HOME else it.screen)
                        }
                        if (wasMirror) {
                            stopWakeLoop()
                            speech.cancelRecording()
                            speech.preferLocalAudio = false
                            wakeSpeech.preferLocalAudio = false
                            FrameStore.clear()
                        }
                    }
                }
            }
        }
        checkServer()
    }

    fun setMode(mode: UserMode) {
        mutableState.update { it.copy(mode = mode) }
        log("state", "切换模式", mode.wireValue)
    }

    fun setScreen(screen: AppScreen) {
        mutableState.update { it.copy(screen = screen) }
    }

    fun updateLookStyle(id: String, name: String) = updateLook { copy(styleId = id, styleName = name, source = "app") }

    fun updateFoundationType(id: String, name: String) = updateLook {
        copy(foundationType = id, foundationTypeName = name, source = "app")
    }

    fun updateFoundationFinish(id: String, name: String) = updateLook {
        copy(foundationFinish = id, foundationFinishName = name, source = "app")
    }

    fun updateFoundationCoverage(id: String, name: String) = updateLook {
        copy(foundationCoverage = id, foundationCoverageName = name, source = "app")
    }

    fun updateLipType(id: String, name: String) = updateLook { copy(lipType = id, lipTypeName = name, source = "app") }

    fun updateLipColor(name: String, colorHex: String) {
        val normalized = colorHex.trim().uppercase()
        if (!Regex("^#[0-9A-F]{6}$").matches(normalized)) return
        updateLook { copy(lipColorName = name, lipColorHex = normalized, source = "app") }
    }

    fun updateLipFinish(id: String, name: String) = updateLook {
        copy(lipFinish = id, lipFinishName = name, source = "app")
    }

    fun updateBlushColor(name: String, colorHex: String) = updateLook {
        copy(blushColorName = name, blushColorHex = colorHex, source = "app")
    }

    fun updateEyeStyle(id: String, name: String) = updateLook { copy(eyeStyle = id, eyeStyleName = name, source = "app") }

    fun updateEyeColor(name: String, colorHex: String) = updateLook {
        copy(eyeColorName = name, eyeColorHex = colorHex, source = "app")
    }

    private fun updateLook(transform: MakeupLookConfig.() -> MakeupLookConfig) {
        mutableState.update { state ->
            val updated = state.lookConfig.transform().copy(version = state.lookConfig.version + 1)
            persistLookConfig(updated)
            state.copy(lookConfig = updated, lookConfigDirty = true, screen = AppScreen.PREVIEW)
        }
    }

    fun previewLook() = launchLookAction { generation -> createPreview(generation) }

    fun commitLookDesign() = launchLookAction { generation ->
        val snapshot = mutableState.value
        if (snapshot.currentStep != null) {
            respond("现在正在化妆。先完成或跳过这一步，再调整整套妆容。")
            return@launchLookAction
        }
        val instruction = "按我选好的妆造更新正式方案：${snapshot.lookConfig.spokenSummary()}。"
        if (snapshot.plan == null) createPlan(instruction, generation) else revisePlan(instruction, generation)
    }

    private fun launchLookAction(block: suspend (Long) -> Unit) {
        if (mutableState.value.busy) return
        speech.stopPlayback()
        val generation = turnGeneration.incrementAndGet()
        activeTurn?.cancel()
        mutableState.update { it.copy(busy = true, errorText = null, statusText = "正在处理妆造") }
        activeTurn = viewModelScope.launch {
            try {
                block(generation)
            } catch (_: CancellationException) {
                log("system", "旧回合已取消")
            } catch (error: Exception) {
                if (isCurrent(generation)) recoverFromError(error)
            } finally {
                if (isCurrent(generation)) mutableState.update { it.copy(busy = false, statusText = "等待输入") }
            }
        }
    }

    fun updateInput(text: String) {
        mutableState.update { it.copy(inputText = text) }
    }

    fun updateServerUrl(value: String) {
        val error = runCatching { api.updateBaseUrl(value) }.exceptionOrNull()
        if (error != null) {
            mutableState.update { it.copy(statusText = error.message ?: "服务地址无效") }
            return
        }
        preferences.edit().putString("server_url", api.currentBaseUrl()).apply()
        mutableState.update { it.copy(statusText = "正在检查服务") }
        checkServer()
    }

    fun serverUrl(): String = api.currentBaseUrl()

    fun boardAddress(): String = board.address()

    fun boardConfigured(): Boolean = board.configured()

    fun saveBoard(address: String, token: String) {
        runCatching { board.save(address.trim(), token.trim()) }
            .onSuccess {
                mutableState.update { it.copy(boardStatus = if (it.wearing) "已保存，重新开始佩戴后连接" else "已配置，开始佩戴时连接") }
                log("board", "已保存设备连接", address.trim())
            }
            .onFailure { error ->
                mutableState.update { it.copy(boardStatus = error.message ?: "设备配置无效") }
            }
    }

    fun clearBoard() {
        board.clear()
        mutableState.update { it.copy(boardConnected = false, boardStatus = "未配置") }
    }

    fun startCalibration() {
        mutableState.update { it.copy(screen = AppScreen.CALIBRATION, calibrationStep = 0) }
    }

    fun nextCalibrationStep() {
        val current = mutableState.value.calibrationStep
        if (current >= 2) {
            mutableState.update { it.copy(screen = AppScreen.WEAR, wearing = true, calibrationStep = 2) }
        } else {
            mutableState.update { it.copy(calibrationStep = current + 1) }
        }
    }

    fun markWearing(value: Boolean) {
        if (mutableState.value.mirrorActive) FrameStore.clear()
        speech.preferLocalAudio = false
        wakeSpeech.preferLocalAudio = false
        mutableState.update { it.copy(wearing = value, mirrorActive = false, screen = if (value) AppScreen.WEAR else AppScreen.HOME) }
        if (value) startWakeLoop() else stopWakeLoop()
    }

    fun startMirror() {
        stopWakeLoop()
        speech.stopPlayback()
        speech.cancelRecording()
        wakeSpeech.cancelRecording()
        speech.preferLocalAudio = true
        wakeSpeech.preferLocalAudio = true
        FrameStore.clear()
        mutableState.update { it.copy(screen = AppScreen.MIRROR, mirrorActive = true, wearing = true, boardConnected = false) }
        log("camera", "妆伴魔镜已开启", "前置摄像头")
        startWakeLoop()
    }

    fun stopMirror() {
        if (!mutableState.value.mirrorActive) return
        speech.stopPlayback()
        speech.cancelRecording()
        wakeSpeech.cancelRecording()
        mutableState.update { it.copy(screen = AppScreen.HOME, mirrorActive = false, wearing = false, listening = false, speaking = false, currentFrame = null) }
        stopWakeLoop()
        speech.preferLocalAudio = false
        wakeSpeech.preferLocalAudio = false
        FrameStore.clear()
        log("camera", "妆伴魔镜已退出")
    }

    fun reportMirrorCameraError(message: String) {
        mutableState.update { it.copy(statusText = message, errorText = message) }
        log("camera", "前置摄像头不可用", message)
    }

    fun submitInput() {
        val text = mutableState.value.inputText.trim()
        if (text.isBlank()) return
        mutableState.update { it.copy(inputText = "") }
        sendUserText(text)
    }

    fun sendUserText(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        speech.stopPlayback()
        val generation = turnGeneration.incrementAndGet()
        activeTurn?.cancel()
        mutableState.update {
            it.copy(
                messages = it.messages + ChatMessage(role = "user", text = normalized),
                busy = true,
                speaking = false,
                errorText = null,
                phase = TaskPhase.ROUTING,
                statusText = "正在理解"
            )
        }
        log("user", "用户输入", normalized)
        activeTurn = viewModelScope.launch {
            try {
                val route = route(normalized)
                if (!isCurrent(generation)) return@launch
                dispatch(route, normalized, generation)
            } catch (_: CancellationException) {
                log("system", "旧回合已取消")
            } catch (error: Exception) {
                if (isCurrent(generation)) recoverFromError(error)
            } finally {
                if (isCurrent(generation)) {
                    mutableState.update { it.copy(busy = false, statusText = if (it.serverReady) "等待输入" else "服务不可用") }
                }
            }
        }
    }

    fun startVoiceInput() {
        if (mutableState.value.listening) return
        if (board.configured() && !mutableState.value.mirrorActive && !mutableState.value.boardConnected) {
            respond("头戴设备还没有连上，先到设置里确认连接状态。")
            return
        }
        stopWakeLoop()
        runCatching { speech.startRecording() }
            .onSuccess {
                mutableState.update { it.copy(listening = true, statusText = "正在听") }
                log("asr", "开始录音")
            }
            .onFailure {
                recoverFromError(it)
                startWakeLoop()
            }
    }

    fun finishVoiceInput() {
        val snapshot = mutableState.value
        if (!snapshot.listening) return
        mutableState.update { it.copy(listening = false, busy = true, statusText = "正在识别") }
        viewModelScope.launch {
            try {
                val transcript = speech.stopAndTranscribe(snapshot.sessionId, snapshot.mode.wireValue).trim()
                log("asr", "识别完成", transcript)
                if (transcript.isBlank()) respond("抱歉，我没有听清，可以再说一遍吗？") else sendUserText(transcript)
            } catch (_: Exception) {
                respond("抱歉，我没有听清，可以再说一遍吗？")
            } finally {
                mutableState.update { it.copy(listening = false, busy = false) }
                startWakeLoop()
            }
        }
    }

    private fun startWakeLoop() {
        if (!mutableState.value.wearing || wakeJob?.isActive == true) return
        wakeJob = viewModelScope.launch {
            while (isActive && mutableState.value.wearing) {
                val snapshot = mutableState.value
                if (snapshot.listening || snapshot.speaking || snapshot.busy ||
                    (board.configured() && !snapshot.mirrorActive && !snapshot.boardConnected)) {
                    delay(350)
                    continue
                }
                try {
                    wakeSpeech.startRecording()
                    delay(3500)
                    if (!isActive || mutableState.value.listening || mutableState.value.speaking || mutableState.value.busy) {
                        wakeSpeech.cancelRecording()
                        continue
                    }
                    if (wakeSpeech.recordingAmplitude() < 700) {
                        wakeSpeech.cancelRecording()
                        continue
                    }
                    val current = mutableState.value
                    val transcript = wakeSpeech.stopAndTranscribe(current.sessionId, current.mode.wireValue).trim()
                    if (transcript.isBlank() || current.busy || current.listening || current.speaking) continue
                    val utterance = WakeWord.parse(transcript)
                    if (utterance.addressed) {
                        wakeActive = true
                        log("asr", "唤醒词识别", transcript)
                        if (utterance.command.isBlank()) respond("我在，你说。")
                        else sendUserText(utterance.command)
                    } else if (wakeActive) {
                        sendUserText(utterance.command)
                    }
                } catch (cancelled: CancellationException) {
                    wakeSpeech.cancelRecording()
                    throw cancelled
                } catch (error: Exception) {
                    wakeSpeech.cancelRecording()
                    log("asr", "唤醒监听暂不可用", error.message.orEmpty())
                    delay(1500)
                }
            }
        }
    }

    private fun stopWakeLoop() {
        wakeJob?.cancel()
        wakeJob = null
        wakeSpeech.cancelRecording()
        if (!mutableState.value.wearing) wakeActive = false
    }

    fun acceptPlan() {
        val plan = mutableState.value.plan ?: return
        if (plan.steps.isEmpty()) return
        mutableState.update { it.copy(currentStepIndex = 0, phase = TaskPhase.GUIDING, screen = AppScreen.GUIDE) }
        guideCurrentStep()
    }

    fun skipCurrentStep() {
        if (mutableState.value.currentStep == null) return
        speech.stopPlayback()
        advanceStep("好，这一步先跳过。")
    }

    fun repeatCurrentStep() {
        val step = mutableState.value.currentStep ?: return
        respond("第 ${mutableState.value.currentStepIndex + 1} 步，${step.spokenInstruction.ifBlank { step.instruction }}")
    }

    fun verifyCurrentStep() {
        sendUserText("我完成了，请帮我检查")
    }

    fun acceptOptionalRepair() {
        if (!mutableState.value.optionalRepairPending) return
        mutableState.update { it.copy(optionalRepairPending = false) }
        advanceStep("好，保留现在的自然效果。")
    }

    fun continueOptionalRepair() {
        val instruction = mutableState.value.lastVerification?.repairInstruction.orEmpty()
        if (instruction.isBlank()) return
        mutableState.update { it.copy(phase = TaskPhase.OPTIONAL_REPAIR) }
        respond("$instruction。调好后告诉我，我再帮你看。")
    }

    fun resolveSafety(isSafe: Boolean) {
        if (isSafe) {
            mutableState.update {
                it.copy(
                    phase = if (it.currentStep != null) TaskPhase.GUIDING else TaskPhase.IDLE,
                    screen = if (it.currentStep != null) AppScreen.GUIDE else if (it.mirrorActive) AppScreen.MIRROR else AppScreen.WEAR,
                    safetyMessage = null
                )
            }
            respond("好，警报已经解除。动作慢一点，我们接着刚才的进度。")
        } else {
            respond("我会保持警报。请先不要移动，并呼叫身边的人帮助。")
        }
    }

    private suspend fun route(text: String): JSONObject {
        val snapshot = mutableState.value
        log("agent", "调用意图路由", text)
        val response = api.post(
            "/api/agent/route",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("text", text)
                .put("context", dialogueContext(snapshot))
        )
        val route = response.optJSONObject("route") ?: JSONObject().put("intent", "dialogue")
        log("agent", "路由结果", route.toString())
        return route
    }

    private suspend fun dispatch(route: JSONObject, text: String, generation: Long) {
        when (val intent = route.optString("intent", "dialogue")) {
            "create_plan" -> createPlan(text, generation)
            "revise_plan" -> revisePlan(text, generation)
            "customize_look" -> customizeLook(text, generation)
            "accept_plan" -> if (mutableState.value.plan == null) createPlan(text, generation) else acceptPlan()
            "step_done" -> verifyStep(generation)
            "skip_step" -> skipCurrentStep()
            "repeat_step" -> repeatCurrentStep()
            "continue_repair" -> continueOptionalRepair()
            "accept_result" -> acceptOptionalRepair()
            "visual_dialogue", "analyze_frame" -> visualDialogue(route.optString("visualTask", "general"), text, route.optString("target"), generation)
            "skin_analysis" -> analyzeSkin(text, generation)
            "skin_advice" -> createSkinAdvice(text, generation)
            "product_recommendation" -> recommendProduct(text, generation)
            "save_skin_record" -> saveSkinRecord()
            "decline_skin_record" -> declineSkinRecord()
            "makeup_preview" -> createPreview(generation)
            "remember_object" -> rememberObject(route, text)
            "recall_object" -> recallObject(route, text)
            "finish_search" -> finishSearch(route)
            "safety_ok" -> resolveSafety(true)
            "safety" -> triggerSafety("检测到可能的风险，请先停下。")
            "adaptive_task" -> adaptiveTask(text, generation)
            "end_task" -> endTask()
            else -> dialogue(route.optString("intentLabel", intent), text, generation)
        }
    }

    private suspend fun dialogue(intent: String, text: String, generation: Long) {
        val snapshot = mutableState.value
        val payload = JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("intent", intent)
                .put("text", text)
                .put("context", dialogueContext(snapshot))
        val response = api.post("/api/dialogue", payload)
        if (isCurrent(generation)) respond(response.optString("reply", "你慢慢说，我听着。"))
    }

    private suspend fun visualDialogue(task: String, text: String, target: String, generation: Long) {
        val frame = requireFrame() ?: return
        respond("好的，让我仔细观察一下。")
        val snapshot = mutableState.value
        val response = api.post(
            "/api/vision/dialogue",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("intent", task)
                .put("text", text)
                .put("target", target)
                .put("products", JSONArray())
                .put("objectMemories", objectMemories())
                .put("imageDataUrl", frame.toDataUrl())
                .put("clientQuality", quality())
                .put("context", dialogueContext(snapshot))
        )
        if (!isCurrent(generation)) return
        if (task == "object_search") {
            mutableState.update { it.copy(activeSearchTarget = target.ifBlank { response.optString("targetName") }) }
        }
        hapticCue(response.optString("hapticCue", "none"))
        respond(response.optString("reply", "这次没有看清，请稍微调整一下角度。"))
    }

    private suspend fun createPlan(text: String, generation: Long) {
        val frame = requireFrame() ?: return
        mutableState.update { it.copy(phase = TaskPhase.PLANNING, screen = if (it.mirrorActive) AppScreen.MIRROR else AppScreen.WEAR, statusText = "正在生成方案") }
        respond("好，我帮你仔细看一下，再给你一套合适的方案。")
        val baseline = faceCropper.crop(frame)
        val skinAnalysis = runCatching { analyzeSkinRaw(frame) }.getOrNull()
        val snapshot = mutableState.value
        val response = api.post(
            "/api/plan/from-image",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("prompt", text)
                .put("products", JSONArray())
                .put("preferences", JSONObject().put("lookConfig", snapshot.lookConfig.toJson()))
                .put("lookConfig", snapshot.lookConfig.toJson())
                .put("imageDataUrl", frame.toDataUrl())
                .put("clientQuality", quality())
                .put("skinAnalysis", skinAnalysis ?: JSONObject.NULL)
        )
        if (!isCurrent(generation)) return
        val planJson = response.getJSONObject("plan")
        val plan = planJson.toMakeupPlan()
        persistLookConfig(plan.lookConfig)
        mutableState.update {
            it.copy(
                evidence = response.optJSONObject("evidence"),
                plan = plan,
                lookConfig = plan.lookConfig,
                lookConfigDirty = false,
                baselineFace = baseline,
                currentStepIndex = -1,
                phase = TaskPhase.PLAN_READY,
                screen = AppScreen.GUIDE,
                skinReport = skinAnalysis?.toSkinReport(),
                optionalRepairPending = false
            )
        }
        respond(plan.spokenIntro.ifBlank { plan.summary })
    }

    private suspend fun revisePlan(text: String, generation: Long) {
        val plan = mutableState.value.plan
        if (plan == null) {
            createPlan(text, generation)
            return
        }
        mutableState.update { it.copy(phase = TaskPhase.PLANNING) }
        val snapshot = mutableState.value
        val response = api.post(
            "/api/plan/revise",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("instruction", text)
                .put("products", JSONArray())
                .put("evidence", snapshot.evidence ?: JSONObject())
                .put("lookConfig", snapshot.lookConfig.toJson())
                .put("plan", plan.raw)
        )
        if (!isCurrent(generation)) return
        val revised = response.getJSONObject("plan").toMakeupPlan()
        persistLookConfig(revised.lookConfig)
        mutableState.update {
            it.copy(
                plan = revised,
                lookConfig = revised.lookConfig,
                lookConfigDirty = false,
                currentStepIndex = -1,
                phase = TaskPhase.PLAN_READY,
                screen = AppScreen.GUIDE
            )
        }
        respond("改好了。${revised.spokenIntro.ifBlank { revised.summary }}")
    }

    private suspend fun customizeLook(text: String, generation: Long) {
        val snapshot = mutableState.value
        if (snapshot.currentStep != null) {
            respond("现在这一步还在进行。先完成或跳过这一步，再调整整套妆容。")
            return
        }
        val response = api.post(
            "/api/look/customize",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("instruction", text)
                .put("lookConfig", snapshot.lookConfig.toJson())
                .put("plan", snapshot.plan?.raw ?: JSONObject.NULL)
        )
        if (!isCurrent(generation)) return
        val config = response.getJSONObject("lookConfig").toMakeupLookConfig()
        persistLookConfig(config)
        mutableState.update { it.copy(lookConfig = config, lookConfigDirty = true) }
        if (snapshot.plan != null && snapshot.currentStepIndex < 0) {
            revisePlan("按照刚才的搭配修改：${config.spokenSummary()}。", generation)
        } else {
            respond("已经换成${config.spokenSummary()}。可以先看预览，也可以按这套生成方案。")
        }
    }

    private fun guideCurrentStep() {
        val state = mutableState.value
        val step = state.currentStep ?: return
        if (state.mode == UserMode.ACCESSIBLE && step.productName.isNotBlank() && state.activeSearchTarget == null) {
            sendUserText("请先帮我找到${step.productName}")
            return
        }
        mutableState.update { it.copy(screen = AppScreen.GUIDE, phase = TaskPhase.GUIDING) }
        respond("第 ${state.currentStepIndex + 1} 步，${step.spokenInstruction.ifBlank { step.instruction }}")
    }

    @SuppressLint("SuspiciousIndentation") // Lint misreads the closing braces of the status branches.
    private suspend fun verifyStep(generation: Long) {
        val snapshot = mutableState.value
        val step = snapshot.currentStep
        if (step == null) {
            respond("我们还没开始第一步。你说开始，我就带你做。")
            return
        }
        val current = requireFrame() ?: return
        val before = snapshot.baselineFace ?: faceCropper.crop(current)
        val after = faceCropper.crop(current)
        mutableState.update { it.copy(phase = TaskPhase.VERIFYING, statusText = "正在检查") }
        respond("好的，让我仔细观察一下。")
        val response = api.post(
            "/api/step/verify",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("beforeFaceImageDataUrl", before.toDataUrl())
                .put("currentFaceImageDataUrl", after.toDataUrl())
                .put("beforeFaceCrop", JSONObject().put("method", "mlkit-face-detection"))
                .put("faceCrop", JSONObject().put("method", "mlkit-face-detection"))
                .put("evidence", snapshot.evidence ?: JSONObject())
                .put("step", step.raw)
                .put("verificationAttempt", 1)
        )
        if (!isCurrent(generation)) return
        val verification = response.getJSONObject("result").toVerification()
        hapticCue(verification.hapticCue)
        log("vision", "步骤复核", "${verification.status}/${verification.repairLevel}: ${verification.speech}")
        mutableState.update { it.copy(lastVerification = verification) }
        when (verification.status) {
            "PASS" -> {
                respond(verification.speech)
                advanceStep("")
            }
            "REPAIR" -> {
                val optional = verification.repairLevel == "OPTIONAL"
                mutableState.update {
                    it.copy(
                        phase = if (optional) TaskPhase.OPTIONAL_REPAIR else TaskPhase.REQUIRED_REPAIR,
                        optionalRepairPending = optional
                    )
                }
                if (verification.hapticCue == "none") haptic(if (optional) "soft" else "required")
                respond(verification.speech.ifBlank { verification.repairInstruction })
            }
            "BLOCKED" -> {
                mutableState.update { it.copy(phase = TaskPhase.REQUIRED_REPAIR) }
                if (verification.hapticCue == "none") haptic("required")
                respond(verification.speech.ifBlank { "这一步还需要调整。你也可以直接说跳过。" })
            }
            else -> {
                mutableState.update { it.copy(phase = TaskPhase.GUIDING) }
                respond(verification.speech.ifBlank { verification.recaptureInstruction })
            }
        }
    }

    private fun advanceStep(leadIn: String) {
        val snapshot = mutableState.value
        val plan = snapshot.plan ?: return
        val next = snapshot.currentStepIndex + 1
        if (next >= plan.steps.size) {
            mutableState.update {
                it.copy(currentStepIndex = plan.steps.size, optionalRepairPending = false, phase = TaskPhase.AWAITING_FOLLOW_UP)
            }
            reviewFinalLook(leadIn)
            return
        }
        mutableState.update {
            it.copy(currentStepIndex = next, optionalRepairPending = false, phase = TaskPhase.GUIDING, activeSearchTarget = null)
        }
        if (leadIn.isNotBlank()) respond(leadIn)
        guideCurrentStep()
    }

    private fun reviewFinalLook(leadIn: String) {
        val generation = turnGeneration.get()
        viewModelScope.launch {
            try {
                val snapshot = mutableState.value
                val frame = requireFrame() ?: return@launch
                val face = faceCropper.crop(frame)
                respond("好的，让我仔细观察一下。")
                val response = api.post(
                    "/api/final/review",
                    JSONObject()
                        .put("sessionId", snapshot.sessionId)
                        .put("mode", snapshot.mode.wireValue)
                        .put("beforeFaceImageDataUrl", (snapshot.baselineFace ?: face).toDataUrl())
                        .put("currentFaceImageDataUrl", face.toDataUrl())
                        .put("plan", snapshot.plan?.raw ?: JSONObject())
                        .put("evidence", snapshot.evidence ?: JSONObject())
                )
                if (!isCurrent(generation)) return@launch
                val result = response.getJSONObject("result")
                mutableState.update { it.copy(phase = TaskPhase.AWAITING_FOLLOW_UP) }
                respond(listOf(leadIn, result.optString("speech")).filter { it.isNotBlank() }.joinToString(" "))
            } catch (error: Exception) {
                recoverFromError(error)
            }
        }
    }

    private suspend fun analyzeSkin(text: String, generation: Long) {
        val frame = requireFrame() ?: return
        respond("好的，让我仔细观察一下。")
        val raw = analyzeSkinRaw(frame)
        if (!isCurrent(generation)) return
        val report = raw.toSkinReport()
        mutableState.update { it.copy(skinReport = report, screen = AppScreen.SKIN) }
        createSkinAdvice(text, generation)
    }

    private suspend fun analyzeSkinRaw(frame: ByteArray): JSONObject {
        val snapshot = mutableState.value
        val response = api.post(
            "/api/skin/analyze",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("imageDataUrl", frame.toDataUrl())
                .put("clientQuality", quality())
        )
        return response.getJSONObject("analysis")
    }

    private suspend fun createSkinAdvice(text: String, generation: Long) {
        val snapshot = mutableState.value
        val analysis = snapshot.skinReport?.raw
        if (analysis == null) {
            analyzeSkin(text, generation)
            return
        }
        val response = api.post(
            "/api/skin/advice",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("text", text)
                .put("skinAnalysis", analysis)
                .put("products", JSONArray())
                .put("evidence", snapshot.evidence ?: JSONObject())
        )
        if (!isCurrent(generation)) return
        val advice = response.getJSONObject("advice")
        mutableState.update { it.copy(skincareAdvice = advice, pendingSkinSave = true, screen = AppScreen.SKIN) }
        respond(advice.optString("speech"))
        respond(advice.optString("saveQuestion"))
    }

    private suspend fun recommendProduct(text: String, generation: Long) {
        val snapshot = mutableState.value
        val response = api.post(
            "/api/products/recommend",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("text", text)
                .put("skinAnalysis", snapshot.skinReport?.raw ?: JSONObject.NULL)
                .put("products", JSONArray())
        )
        if (isCurrent(generation)) respond(response.getJSONObject("recommendation").optString("speech"))
    }

    private fun saveSkinRecord() {
        val report = mutableState.value.skinReport ?: return
        preferences.edit().putString("last_skin_record", report.raw.toString()).apply()
        mutableState.update { it.copy(pendingSkinSave = false) }
        respond("好，今天的皮肤状态已经保存。")
    }

    private fun declineSkinRecord() {
        mutableState.update { it.copy(pendingSkinSave = false) }
        respond("好，这次不保存。")
    }

    private suspend fun createPreview(generation: Long) {
        val snapshot = mutableState.value
        respond("准备拍照，三、二、一。")
        val frame = requireFrame() ?: return
        val response = api.post(
            "/api/makeup/preview",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("imageDataUrl", frame.toDataUrl())
                .put("clientQuality", quality())
                .put("plan", snapshot.plan?.raw ?: JSONObject.NULL)
                .put("lookConfig", snapshot.lookConfig.toJson())
        )
        if (!isCurrent(generation)) return
        val dataUrl = response.getJSONObject("preview").optString("previewImageDataUrl")
        val after = decodeDataUrl(dataUrl)
        mutableState.update { it.copy(previewBefore = frame, previewAfter = after, screen = AppScreen.PREVIEW) }
        respond(if (snapshot.mode == UserMode.ACCESSIBLE) "妆后预览已经生成，可以交给身边的人查看。" else "妆后预览已经生成，可以对比一下整体妆感。")
    }

    private suspend fun adaptiveTask(text: String, generation: Long) {
        val snapshot = mutableState.value
        val response = api.post(
            "/api/agent/adaptive-plan",
            JSONObject()
                .put("sessionId", snapshot.sessionId)
                .put("mode", snapshot.mode.wireValue)
                .put("text", text)
                .put("context", dialogueContext(snapshot))
        )
        if (!isCurrent(generation)) return
        val plan = response.optJSONObject("plan") ?: JSONObject()
        val actions = plan.optJSONArray("actions") ?: JSONArray()
        if (actions.length() == 0) {
            respond(plan.optString("speech", "你可以再具体说一点，我来帮你。"))
            return
        }
        val action = actions.getJSONObject(0)
        val tool = action.optString("tool")
        val route = JSONObject()
            .put("intent", tool)
            .put("visualTask", action.optString("visualTask", "general"))
            .put("target", action.optString("target"))
        dispatch(route, action.optString("instruction", text), generation)
    }

    private fun rememberObject(route: JSONObject, text: String) {
        val name = route.optString("memoryObject").ifBlank { route.optString("target") }
        val location = route.optString("memoryLocation")
        if (name.isBlank() || location.isBlank()) {
            respond("告诉我是什么东西，以及你把它放在哪里，我才能记住。")
            return
        }
        val memories = objectMemories()
        memories.put(name, location)
        preferences.edit().putString("object_memories", memories.toString()).apply()
        respond("好，我记住了。$name 放在$location。")
    }

    private fun recallObject(route: JSONObject, text: String) {
        val name = route.optString("memoryObject").ifBlank { route.optString("target") }.ifBlank { text }
        val location = objectMemories().optString(name)
        respond(if (location.isBlank()) "我还没有记过$name 的位置。" else "上次你让我记住，$name 放在$location。")
    }

    private fun finishSearch(route: JSONObject) {
        val target = mutableState.value.activeSearchTarget ?: "这个东西"
        mutableState.update { it.copy(activeSearchTarget = null) }
        if (route.optString("searchOutcome") == "cancelled") {
            respond("好，已经停止寻找。你可以换一个产品，或者直接跳过这一步。")
        } else {
            respond("好，已经拿到$target。")
            guideCurrentStep()
        }
    }

    private fun endTask() {
        speech.stopPlayback()
        wakeActive = false
        val fresh = UiState(
            mode = mutableState.value.mode,
            screen = if (mutableState.value.mirrorActive) AppScreen.MIRROR else AppScreen.WEAR,
            mirrorActive = mutableState.value.mirrorActive,
            wearing = mutableState.value.wearing,
            boardConnected = mutableState.value.boardConnected,
            boardStatus = mutableState.value.boardStatus,
            serverReady = mutableState.value.serverReady,
            statusText = "等待唤醒",
            messages = mutableState.value.messages + ChatMessage(role = "assistant", text = "好，今天就到这里。有需要再叫我。")
        )
        mutableState.value = fresh
    }

    private fun handleFallCandidate(event: WearEvent.FallCandidate) {
        log("safety", "检测到跌倒候选", "accel=${event.accelerationG}, gyro=${event.angularVelocity}")
        triggerSafety("检测到身体有剧烈晃动。请问您摔倒了吗？身体有没有不舒服？如果没事，请回答“我很好”。")
    }

    private fun triggerSafety(message: String) {
        activeTurn?.cancel()
        turnGeneration.incrementAndGet()
        speech.stopPlayback()
        if (board.configured() && !mutableState.value.mirrorActive) hapticCue("risk")
        mutableState.update {
            it.copy(
                screen = AppScreen.SAFETY,
                phase = TaskPhase.SAFETY_PENDING,
                safetyMessage = message,
                busy = false
            )
        }
        respond(message)
    }

    private fun requireFrame(): ByteArray? {
        val frame = FrameStore.latest()?.takeIf { !FrameStore.live || System.currentTimeMillis() - FrameStore.capturedAt < 10_000 }
        if (frame == null) respond(if (mutableState.value.mode == UserMode.ACCESSIBLE) "我还没有获得前方画面。请确认摄像头权限已经打开。" else "现在还没有摄像头画面，请先开始佩戴模式。")
        return frame
    }

    private fun quality(): JSONObject = JSONObject()
        .put("ok", true)
        .put("reasons", JSONArray())
        .put("capturedAt", FrameStore.capturedAt)

    private fun dialogueContext(snapshot: UiState): JSONObject {
        val recent = JSONArray()
        snapshot.messages.takeLast(10).forEach { recent.put(JSONObject().put("role", it.role).put("text", it.text)) }
        return JSONObject()
            .put("mode", snapshot.mode.wireValue)
            .put("taskState", snapshot.phase.name)
            .put("hasImage", snapshot.currentFrame != null)
            .put("hasEvidence", snapshot.evidence != null)
            .put("hasPlan", snapshot.plan != null)
            .put("planTitle", snapshot.plan?.title.orEmpty())
            .put("planSummary", snapshot.plan?.summary.orEmpty())
            .put("lookConfig", snapshot.lookConfig.toJson())
            .put("lookConfigDirty", snapshot.lookConfigDirty)
            .put("currentStepIndex", snapshot.currentStepIndex)
            .put("currentStep", snapshot.currentStep?.raw ?: JSONObject.NULL)
            .put("recentConversation", recent)
            .put("safetyPending", snapshot.phase == TaskPhase.SAFETY_PENDING)
            .put("awaitingFollowUp", snapshot.phase == TaskPhase.AWAITING_FOLLOW_UP)
            .put("optionalRepairPending", snapshot.optionalRepairPending)
            .put("activeSearch", snapshot.activeSearchTarget?.let { JSONObject().put("target", it) } ?: JSONObject.NULL)
            .put("objectMemories", objectMemories())
            .put("hasSkinAnalysis", snapshot.skinReport != null)
            .put("latestSkinAnalysis", snapshot.skinReport?.raw ?: JSONObject.NULL)
            .put("latestSkincareAdvice", snapshot.skincareAdvice ?: JSONObject.NULL)
            .put("pendingSkinSave", snapshot.pendingSkinSave)
    }

    private fun objectMemories(): JSONObject = runCatching {
        JSONObject(preferences.getString("object_memories", "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    private fun loadLookConfig(): MakeupLookConfig = runCatching {
        val stored = preferences.getString("makeup_look_config", null)
        if (stored.isNullOrBlank()) MakeupLookConfig() else JSONObject(stored).toMakeupLookConfig()
    }.getOrDefault(MakeupLookConfig())

    private fun persistLookConfig(config: MakeupLookConfig) {
        preferences.edit().putString("makeup_look_config", config.toJson().toString()).apply()
    }

    private fun respond(text: String, alert: Boolean = false) {
        val clean = text.trim()
        if (clean.isBlank()) return
        mutableState.update {
            it.copy(messages = it.messages + ChatMessage(role = "assistant", text = clean, alert = alert))
        }
        log("assistant", "小妆回复", clean)
        val snapshot = mutableState.value
        viewModelScope.launch {
            speech.speak(snapshot.sessionId, snapshot.mode.wireValue, clean) { speaking ->
                mutableState.update { it.copy(speaking = speaking) }
            }
        }
    }

    private fun recoverFromError(error: Throwable) {
        val transient = error is SocketTimeoutException ||
            (error is ApiException && (error.status >= 500 || error.code.contains("TIMEOUT")))
        val reply = if (transient) "抱歉，我没有听清，可以再说一遍吗？" else error.message?.takeIf { it.isNotBlank() }
            ?: "这次没有处理成功，请再说一遍。"
        log("error", "请求失败", "${error::class.java.simpleName}: ${error.message}")
        mutableState.update { it.copy(phase = TaskPhase.ERROR, busy = false, errorText = reply) }
        respond(reply, alert = !transient)
    }

    private fun checkServer() {
        viewModelScope.launch {
            runCatching { api.get("/api/status") }
                .onSuccess { response ->
                    mutableState.update { it.copy(serverReady = true, statusText = "服务已连接") }
                    log("network", "服务连接成功", response.optString("vision", "ready"))
                }
                .onFailure {
                    mutableState.update { state -> state.copy(serverReady = false, statusText = "服务未连接") }
                    log("network", "服务连接失败", it.message.orEmpty())
                }
        }
    }

    private fun log(category: String, title: String, detail: String = "") {
        mutableState.update { state ->
            state.copy(logs = (state.logs + RuntimeLog(category = category, title = title, detail = detail)).takeLast(300))
        }
    }

    private fun isCurrent(generation: Long): Boolean = turnGeneration.get() == generation

    private fun decodeDataUrl(value: String): ByteArray? {
        val comma = value.indexOf(',')
        if (comma < 0) return null
        return runCatching { android.util.Base64.decode(value.substring(comma + 1), android.util.Base64.DEFAULT) }.getOrNull()
    }

    private fun haptic(kind: String) {
        if (board.configured() && !mutableState.value.mirrorActive) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { board.haptic("both") }
                    .onFailure { log("board", "设备震动不可用", it.message.orEmpty()) }
            }
            return
        }
        val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = if (kind == "required") longArrayOf(0, 180, 100, 180) else longArrayOf(0, 80)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun hapticCue(cue: String) {
        if (cue !in setOf("left", "right", "both", "risk")) return
        if (board.configured() && !mutableState.value.mirrorActive) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { board.haptic(if (cue == "risk") "both" else cue) }
                    .onFailure { log("board", "方向震动不可用", it.message.orEmpty()) }
            }
        } else {
            haptic(if (cue == "risk") "required" else "soft")
        }
    }

    override fun onCleared() {
        stopWakeLoop()
        wakeSpeech.release()
        activeTurn?.cancel()
        speech.release()
        super.onCleared()
    }
}
