package com.beautysense.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beautysense.app.BeautySenseViewModel
import com.beautysense.app.model.AppScreen
import com.beautysense.app.model.ChatMessage
import com.beautysense.app.model.RuntimeLog
import com.beautysense.app.model.TaskPhase
import com.beautysense.app.model.UiState
import com.beautysense.app.model.UserMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Rose = Color(0xFFD98A9B)
private val RoseDark = Color(0xFFB96A7D)
private val RoseDeep = Color(0xFF9E5468)
private val Lavender = Color(0xFF9C8BD0)
private val LavenderSoft = Color(0xFFE9E3F6)
private val Canvas = Color(0xFFFBF8F5)
private val SurfaceWarm = Color(0xFFFFFFFF)
private val Outline = Color(0xFFE5DCE6)
private val Ink = Color(0xFF3A2F42)
private val Muted = Color(0xFF7C7286)
private val Coral = Color(0xFFC25B63)
private val Blue = Lavender
private val Gold = Color(0xFFB98A3C)
private val Green = Color(0xFF6E9E7F)
private val Forest = RoseDeep
private val ForestDark = Ink
private val AppBackground = Brush.verticalGradient(listOf(Color(0xFFFBF8F5), Color(0xFFF6F2F8), Color(0xFFF1ECF6)))
private val PrimaryGradient = Brush.horizontalGradient(listOf(Rose, RoseDark))
private val DarkBackground = Brush.verticalGradient(listOf(Color(0xFF3B2F44), Color(0xFF241C2B), Color(0xFF1B1521)))

@Composable
fun BeautySenseTheme(content: @Composable () -> Unit) {
    val scheme = androidx.compose.material3.lightColorScheme(
        primary = RoseDark,
        onPrimary = Color.White,
        secondary = Lavender,
        error = Coral,
        background = Canvas,
        surface = SurfaceWarm,
        onBackground = Ink,
        onSurface = Ink,
        outline = Outline
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = androidx.compose.material3.Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 27.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold),
            labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        ),
        content = content
    )
}

@Composable
fun BeautySenseRoot(
    viewModel: BeautySenseViewModel,
    onStartWear: () -> Unit,
    onStartMirror: () -> Unit,
    onImportImage: () -> Unit,
    onStopWear: () -> Unit,
    onStopMirror: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    BackHandler(state.screen != AppScreen.HOME) { viewModel.setScreen(AppScreen.HOME) }

    Box(Modifier.fillMaxSize()) {
        if (state.mirrorActive) {
            MirrorCameraPreview(Modifier.fillMaxSize(), viewModel::reportMirrorCameraError)
        }
        when (state.screen) {
            AppScreen.CALIBRATION -> CalibrationScreen(state, viewModel::nextCalibrationStep) { viewModel.setScreen(AppScreen.HOME) }
            AppScreen.SAFETY -> SafetyScreen(state, viewModel::resolveSafety)
            else -> MainScaffold(state, viewModel, onStartWear, onStartMirror, onImportImage, onStopWear, onStopMirror)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    state: UiState,
    viewModel: BeautySenseViewModel,
    onStartWear: () -> Unit,
    onStartMirror: () -> Unit,
    onImportImage: () -> Unit,
    onStopWear: () -> Unit,
    onStopMirror: () -> Unit
) {
    val darkChrome = state.screen == AppScreen.WEAR || state.screen == AppScreen.MIRROR
    var showHelp by remember(state.screen) { mutableStateOf(false) }
    val title = when (state.screen) {
        AppScreen.HOME -> "开始使用"
        AppScreen.WEAR -> "正在佩戴"
        AppScreen.MIRROR -> "妆伴魔镜"
        AppScreen.GUIDE -> state.plan?.title ?: "妆容指导"
        AppScreen.SKIN -> "今日皮肤状态"
        AppScreen.PREVIEW -> "AI 妆后预览"
        AppScreen.LOGS -> "实时日志"
        AppScreen.SETTINGS -> "设置"
        else -> "妆伴"
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("妆伴", color = if (darkChrome) Color(0xFFD9A8B4) else RoseDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text(title, color = if (darkChrome) Color.White else Ink, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    StatusPill(state.statusText, state.serverReady)
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.HelpOutline, "使用帮助", tint = if (darkChrome) Color(0xFFD9A8B4) else RoseDeep)
                    }
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (darkChrome) Color(0xFF3B2F44) else Color(0xFFFBF8F5))
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(if (state.screen == AppScreen.MIRROR) Color(0xFF1B1521) else Color.Transparent)) {
                Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (darkChrome) Color(0xFF2A202F) else Color.White.copy(alpha = 0.96f),
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (darkChrome) Color.White.copy(alpha = 0.10f) else Outline)
                ) {
                    NavigationBar(containerColor = Color.Transparent) {
                    NavItem("首页", Icons.Default.Home, state.screen == AppScreen.HOME, darkChrome) { viewModel.setScreen(AppScreen.HOME) }
                    NavItem(if (state.mirrorActive) "魔镜" else "佩戴", Icons.Default.Mic, state.screen == if (state.mirrorActive) AppScreen.MIRROR else AppScreen.WEAR, darkChrome) {
                        viewModel.setScreen(if (state.mirrorActive) AppScreen.MIRROR else AppScreen.WEAR)
                    }
                    NavItem("记录", Icons.Default.History, state.screen == AppScreen.LOGS, darkChrome) { viewModel.setScreen(AppScreen.LOGS) }
                    NavItem("设置", Icons.Default.Settings, state.screen == AppScreen.SETTINGS, darkChrome) { viewModel.setScreen(AppScreen.SETTINGS) }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (state.screen == AppScreen.MIRROR) Modifier else Modifier.background(AppBackground)).padding(padding)) {
            when (state.screen) {
                AppScreen.HOME -> HomeScreen(state, viewModel::setMode, onStartWear, onStartMirror, onImportImage, viewModel::setScreen)
                AppScreen.WEAR -> WearScreen(state, viewModel)
                AppScreen.MIRROR -> MirrorScreen(state, viewModel, onStopMirror)
                AppScreen.GUIDE -> GuideScreen(state, viewModel)
                AppScreen.SKIN -> SkinScreen(state, viewModel::setScreen)
                AppScreen.PREVIEW -> PreviewScreen(state, viewModel)
                AppScreen.LOGS -> LogsScreen(state.logs)
                AppScreen.SETTINGS -> SettingsScreen(state, viewModel, onStopWear)
                else -> Unit
            }
        }
    }
    if (showHelp) {
        val help = screenHelp(state.screen)
        HelpDialog(help.first, help.second) { showHelp = false }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onMode: (UserMode) -> Unit,
    onStartWear: () -> Unit,
    onStartMirror: () -> Unit,
    onImportImage: () -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("今天想怎么使用？", style = MaterialTheme.typography.headlineMedium)
        ModeSelector(state.mode, onMode)
        Button(
            onClick = onStartWear,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RoseDark)
        ) {
            Icon(Icons.Default.VolumeUp, null)
            Spacer(Modifier.width(10.dp))
            Text(if (state.wearing) "重新校准佩戴" else "开始佩戴模式")
        }
        OutlinedButton(
            onClick = { if (state.mirrorActive) onNavigate(AppScreen.MIRROR) else onStartMirror() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(10.dp))
            Text(if (state.mirrorActive) "返回妆伴魔镜" else "开启妆伴魔镜")
        }
        SectionTitle("设备状态")
        Surface(shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline), shadowElevation = 2.dp) {
            Column {
                DeviceRow(if (state.mirrorActive) "前置摄像头" else "后置摄像头", state.currentFrame != null, if (state.currentFrame != null) "有画面" else "等待启动")
                DeviceRow("智能助手", state.serverReady, if (state.serverReady) "已连接" else "未连接")
                DeviceRow("语音输出", true, if (state.speaking) "正在播报" else "待命")
                DeviceRow("挂颈安全感知", state.wearing, if (state.wearing) "运行中" else "未启动")
            }
        }
        SectionTitle("快捷功能")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("皮肤状态", Icons.Default.AutoAwesome, Modifier.weight(1f)) { onNavigate(AppScreen.SKIN) }
            QuickAction("妆造搭配", Icons.Default.Palette, Modifier.weight(1f)) { onNavigate(AppScreen.PREVIEW) }
        }
        QuickAction("实时日志", Icons.Default.History, Modifier.fillMaxWidth()) { onNavigate(AppScreen.LOGS) }
        OutlinedButton(onClick = onImportImage, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
            Text("从相册选择画面")
        }
        Text("最近对话", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        state.messages.takeLast(2).forEach { MessageBubble(it) }
    }
}

@Composable
private fun ModeSelector(mode: UserMode, onMode: (UserMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModeButton("普通增强", mode == UserMode.BEAUTY, Modifier.weight(1f)) { onMode(UserMode.BEAUTY) }
        ModeButton("无障碍辅助", mode == UserMode.ACCESSIBLE, Modifier.weight(1f)) { onMode(UserMode.ACCESSIBLE) }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(104.dp).clickable(onClick = onClick),
        color = if (selected) Color(0xFFFDF1F3) else Color.White.copy(alpha = 0.88f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (selected) Rose else Outline),
        shadowElevation = if (selected) 6.dp else 1.dp
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(if (selected) Rose else LavenderSoft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (text == "普通增强") Icons.Default.AutoAwesome else Icons.Default.Shield, null, tint = if (selected) Color.White else Lavender)
                }
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Default.Check, null, tint = RoseDark, modifier = Modifier.size(19.dp))
            }
            Text(text, color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CalibrationScreen(state: UiState, onNext: () -> Unit, onBack: () -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FrameImage(state.currentFrame, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 42.dp, start = 12.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))) {
            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
        }
        IconButton(onClick = { showHelp = true }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 42.dp, end = 12.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))) {
            Icon(Icons.Default.HelpOutline, "使用帮助", tint = Color.White)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(AppBackground).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("镜面", "桌面", "正前方").forEachIndexed { index, label ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(if (index <= state.calibrationStep) Rose else Outline, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(6.dp))
                        Text(label, fontSize = 13.sp, color = if (index <= state.calibrationStep) Forest else Muted)
                    }
                }
            }
            Text(
                when (state.calibrationStep) {
                    0 -> "让镜子位于身体正前方"
                    1 -> "让常用产品进入画面"
                    else -> "向前站立，确认行走视角"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDark)) {
                Text(if (state.calibrationStep == 2) "完成校准" else "视角合适，继续")
            }
        }
        if (showHelp) HelpDialog(
            "佩戴校准",
            "镜面：调整挂绳长度，让镜中面部完整出现。桌面：低头确认桌沿和常用化妆品进入画面。正前方：确认镜头能看到身体前方和脚边近处。每一步调整好后点击继续。"
        ) { showHelp = false }
    }
}

@Composable
private fun WearScreen(state: UiState, viewModel: BeautySenseViewModel) {
    val messageListState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) messageListState.animateScrollToItem(0)
    }
    Column(Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).background(if (state.wearing) Color(0xFF8FCFA5) else Color(0xFFA799B3), CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(if (state.wearing) "挂颈设备运行中" else "佩戴服务未启动", color = Color(0xFFE9E1EE), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(if (state.speaking) "正在播报" else if (state.listening) "正在听" else if (state.wearing) "呼唤小妆" else "待命", color = Color(0xFFD9A8B4), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(104.dp).background(Brush.radialGradient(listOf(Rose.copy(alpha = 0.38f), Lavender.copy(alpha = 0.12f))), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, null, tint = Color(0xFFF3D9DF), modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(if (state.listening) "我在听" else if (state.speaking) "正在回应" else "小妆在这里", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            state = messageListState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            reverseLayout = true
        ) {
            items(state.messages.asReversed(), key = { it.id }) { MessageBubble(it, dark = true) }
        }
        InputBar(state, viewModel, dark = true)
    }
}

@Composable
private fun MirrorScreen(state: UiState, viewModel: BeautySenseViewModel, onStopMirror: () -> Unit) {
    val messageListState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) messageListState.animateScrollToItem(0)
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(2f)) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (state.currentFrame == null) "正在连接前置摄像头…" else "前置摄像头 · 实时镜面",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onStopMirror,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(14.dp))
                ) { Text("退出魔镜", color = Color.White) }
            }
            if (state.errorText != null && state.currentFrame == null) {
                Text(
                    state.errorText,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp)
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(14.dp)).padding(14.dp)
                )
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF1B1521))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (state.listening) Rose else Color(0xFF8FCFA5), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("小妆对话", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(if (state.speaking) "正在播报" else if (state.listening) "正在听" else "随时提问", color = Color(0xFFD9A8B4), fontSize = 12.sp)
            }
            LazyColumn(
                state = messageListState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                reverseLayout = true
            ) {
                items(state.messages.asReversed(), key = { it.id }) { MessageBubble(it, dark = true) }
            }
            InputBar(state, viewModel, dark = true, compact = true)
        }
    }
}

@Composable
private fun InputBar(state: UiState, viewModel: BeautySenseViewModel, dark: Boolean = false, compact: Boolean = false) {
    val fieldColors = if (dark) {
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color(0xFFE9E1EE),
            cursorColor = Rose,
            focusedBorderColor = Rose,
            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
            focusedPlaceholderColor = Color(0xFFA799B3),
            unfocusedPlaceholderColor = Color(0xFFA799B3)
        )
    } else OutlinedTextFieldDefaults.colors()
    Column(Modifier.fillMaxWidth().background(if (dark) Color(0xFF1B1521) else SurfaceWarm).padding(if (compact) 6.dp else 12.dp)) {
        OutlinedTextField(
            value = state.inputText,
            onValueChange = viewModel::updateInput,
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = if (compact) 2 else 3,
            placeholder = { Text("输入消息") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!state.busy) viewModel.submitInput() }),
            trailingIcon = {
                IconButton(onClick = viewModel::submitInput, enabled = state.inputText.isNotBlank() && !state.busy) {
                    Icon(Icons.Default.Send, "发送")
                }
            },
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Button(
            onClick = { if (state.listening) viewModel.finishVoiceInput() else viewModel.startVoiceInput() },
            modifier = Modifier.fillMaxWidth().height(if (compact) 40.dp else 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (state.listening) Coral else RoseDark),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(if (state.listening) Icons.Default.Stop else Icons.Default.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.listening) "说完了，发送" else "开始说话")
        }
    }
}

@Composable
private fun GuideScreen(state: UiState, viewModel: BeautySenseViewModel) {
    val plan = state.plan
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        FrameImage(state.currentFrame, Modifier.fillMaxWidth().height(220.dp), ContentScale.Crop)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                plan == null -> {
                    Text("还没有正式方案", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { viewModel.setScreen(AppScreen.WEAR) }, modifier = Modifier.fillMaxWidth()) { Text("开始对话") }
                }

                state.currentStepIndex < 0 -> {
                    Text(plan.title, style = MaterialTheme.typography.headlineMedium)
                    Text(plan.summary, color = Muted)
                    Text("共 ${plan.steps.size} 步 · 约 ${plan.estimatedMinutes} 分钟", color = Forest, fontWeight = FontWeight.Bold)
                    plan.steps.forEachIndexed { index, step ->
                        Surface(border = androidx.compose.foundation.BorderStroke(1.dp, Outline), shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(30.dp).background(PrimaryGradient, CircleShape), contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column { Text(step.title, fontWeight = FontWeight.Bold); Text(step.instruction, color = Muted) }
                            }
                        }
                    }
                    Button(onClick = viewModel::acceptPlan, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDark)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("开始第一步")
                    }
                    OutlinedButton(onClick = { viewModel.setScreen(AppScreen.PREVIEW) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.Palette, null); Spacer(Modifier.width(8.dp)); Text("调整搭配与预览")
                    }
                    OutlinedButton(onClick = { viewModel.setScreen(AppScreen.WEAR) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("用语音调整方案") }
                }

                else -> {
                    val step = state.currentStep
                    if (step != null) {
                        StepProgress(state.currentStepIndex, plan.steps.size)
                        Text("第 ${state.currentStepIndex + 1} 步，共 ${plan.steps.size} 步", color = Forest, fontWeight = FontWeight.Bold)
                        Text(step.title, style = MaterialTheme.typography.headlineMedium)
                        Text(step.instruction, fontSize = 18.sp, lineHeight = 29.sp)
                        if (step.productName.isNotBlank()) {
                            Surface(color = Color(0xFFF7EDD8), shape = RoundedCornerShape(16.dp)) {
                                Text("本步用品：${step.productName}", Modifier.padding(14.dp), color = Color(0xFF5D4726), fontWeight = FontWeight.Bold)
                            }
                        }
                        state.lastVerification?.takeIf { state.phase in listOf(TaskPhase.OPTIONAL_REPAIR, TaskPhase.REQUIRED_REPAIR) }?.let { result ->
                            Surface(color = if (result.repairLevel == "OPTIONAL") LavenderSoft else Color(0xFFF6E2E4), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (result.repairLevel == "OPTIONAL") "可选微调" else "需要调整", fontWeight = FontWeight.Bold)
                                    Text(result.speech)
                                }
                            }
                        }
                        if (state.optionalRepairPending) {
                            Button(onClick = viewModel::continueOptionalRepair, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDark)) { Text("继续微调") }
                            OutlinedButton(onClick = viewModel::acceptOptionalRepair, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("保持现在，进入下一步") }
                        } else {
                            Button(onClick = viewModel::verifyCurrentStep, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDark)) {
                                Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("完成并检查")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = viewModel::repeatCurrentStep, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(18.dp)) {
                                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("重听")
                                }
                                OutlinedButton(onClick = viewModel::skipCurrentStep, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(18.dp)) {
                                    Icon(Icons.Default.SkipNext, null); Spacer(Modifier.width(5.dp)); Text("跳过")
                                }
                            }
                        }
                    } else {
                        Text("本次妆容步骤已完成", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = { viewModel.setScreen(AppScreen.WEAR) }, modifier = Modifier.fillMaxWidth()) { Text("继续对话") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinScreen(state: UiState, onNavigate: (AppScreen) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FrameImage(state.currentFrame, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)), ContentScale.Crop)
        val report = state.skinReport
        if (report == null) {
            Text("还没有今日分析", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { onNavigate(AppScreen.WEAR) }, modifier = Modifier.fillMaxWidth()) { Text("开始分析") }
        } else {
            Text(report.summary.ifBlank { "整体状态已分析" }, style = MaterialTheme.typography.titleLarge)
            report.metrics.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { metric -> MetricCard(metric.label, metric.score, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            state.skincareAdvice?.let { advice ->
                Surface(color = LavenderSoft, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("今天的护理重点", fontWeight = FontWeight.Bold)
                        Text(advice.optString("summary").ifBlank { advice.optString("speech") }, color = Color(0xFF3F5C6E))
                    }
                }
            }
            if (state.pendingSkinSave) Text("是否保存本次记录？", color = Forest, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewScreen(state: UiState, viewModel: BeautySenseViewModel) {
    val look = state.lookConfig
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("妆造工作室", style = MaterialTheme.typography.headlineMedium)

        state.previewAfter?.let {
            Text("当前预览", fontWeight = FontWeight.Bold)
            FrameImage(it, Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp)), ContentScale.Crop)
        } ?: FrameImage(state.currentFrame, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp)), ContentScale.Crop)

        LookSection("妆容风格") {
            ChoiceRow(
                listOf("natural" to "自然", "commuter" to "通勤", "french" to "法式", "korean" to "韩系", "retro" to "复古", "stage" to "舞台"),
                look.styleId,
                viewModel::updateLookStyle
            )
        }
        LookSection("粉底类型") {
            ChoiceRow(listOf("liquid" to "粉底液", "cushion" to "气垫", "powder" to "粉饼", "toneup" to "素颜霜"), look.foundationType, viewModel::updateFoundationType)
        }
        LookSection("底妆质感") {
            ChoiceRow(listOf("matte" to "哑光", "natural" to "自然", "dewy" to "水光"), look.foundationFinish, viewModel::updateFoundationFinish)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(listOf("light" to "轻薄", "medium" to "中等", "full" to "高遮盖"), look.foundationCoverage, viewModel::updateFoundationCoverage)
        }
        LookSection("眼妆") {
            ChoiceRow(listOf("none" to "不画", "soft" to "柔和", "defined" to "清晰", "smoky" to "烟熏"), look.eyeStyle, viewModel::updateEyeStyle)
            Spacer(Modifier.height(10.dp))
            ColorChoiceRow(
                listOf("暖棕" to "#8A6556", "蜜桃" to "#C77D72", "玫瑰" to "#9A5C69", "灰紫" to "#706273"),
                look.eyeColorHex,
                viewModel::updateEyeColor
            )
        }
        LookSection("腮红") {
            ColorChoiceRow(
                listOf("柔粉" to "#D88F96", "蜜桃" to "#E59A82", "珊瑚" to "#D9776C", "玫瑰" to "#B86C7A"),
                look.blushColorHex,
                viewModel::updateBlushColor
            )
        }
        LookSection("口红") {
            ChoiceRow(listOf("lipstick" to "口红", "tint" to "唇釉", "balm" to "润色唇膏"), look.lipType, viewModel::updateLipType)
            Spacer(Modifier.height(10.dp))
            ColorChoiceRow(
                listOf("裸粉" to "#B87978", "豆沙色" to "#A95F65", "珊瑚色" to "#D36F61", "正红色" to "#C52F35", "浆果色" to "#7E334F", "红棕色" to "#8B443A"),
                look.lipColorHex,
                viewModel::updateLipColor
            )
            Spacer(Modifier.height(10.dp))
            ChoiceRow(listOf("matte" to "哑光", "satin" to "缎光", "glossy" to "水光"), look.lipFinish, viewModel::updateLipFinish)
            CustomLipColor(look.lipColorHex, viewModel::updateLipColor)
        }

        Surface(color = Color(0xFFFDF1F3), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Rose.copy(alpha = 0.45f))) {
            Column(Modifier.padding(14.dp)) {
                Text("当前搭配", fontWeight = FontWeight.Bold)
                Text(look.spokenSummary(), color = RoseDeep)
                if (state.lookConfigDirty) Text("有未应用的更改", color = Gold, fontWeight = FontWeight.Bold)
            }
        }
        Button(onClick = viewModel::previewLook, enabled = !state.busy && state.currentFrame != null, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDark)) {
            Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("更新妆后预览")
        }
        OutlinedButton(onClick = viewModel::commitLookDesign, enabled = !state.busy && state.currentFrame != null && state.currentStep == null, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text(if (state.plan == null) "采用并生成方案" else "采用并更新方案")
        }
    }
}

@Composable
private fun LookSection(title: String, content: @Composable () -> Unit) {
    Surface(color = Color.White.copy(alpha = 0.90f), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            content()
        }
    }
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            val active = selected == id
            Surface(
                modifier = Modifier.height(44.dp).clickable { onSelect(id, label) },
                color = if (active) RoseDark else SurfaceWarm,
                contentColor = if (active) Color.White else Ink,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (active) RoseDark else Outline),
                shape = RoundedCornerShape(15.dp)
            ) { Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(label, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) } }
        }
    }
}

@Composable
private fun ColorChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (name, hex) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(name, hex) }) {
                Box(
                    Modifier.size(44.dp).background(parseColor(hex), CircleShape)
                        .border(if (selected.equals(hex, true)) 3.dp else 1.dp, if (selected.equals(hex, true)) RoseDeep else Outline, CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                Text(name, fontSize = 12.sp, color = if (selected.equals(hex, true)) RoseDeep else Muted)
            }
        }
    }
}

@Composable
private fun CustomLipColor(current: String, onSelect: (String, String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = { value = it.take(7) }, label = { Text("自定义色值") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp))
        OutlinedButton(onClick = { onSelect("自定义", value) }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("应用") }
    }
}

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFFA95F65))

@Composable
private fun LogsScreen(logs: List<RuntimeLog>) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(logs.asReversed()) { log ->
            Surface(border = androidx.compose.foundation.BorderStroke(1.dp, Outline), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.90f)) {
                Column(Modifier.padding(12.dp)) {
                    Row { Text(log.category.uppercase(), color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(time(log.time), color = Muted, fontSize = 12.sp) }
                    Text(log.title, fontWeight = FontWeight.Bold)
                    if (log.detail.isNotBlank()) Text(log.detail.take(500), color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, viewModel: BeautySenseViewModel, onStopWear: () -> Unit) {
    ValueHolder(viewModel.serverUrl()) { serverUrl, update ->
        var boardAddress by remember { mutableStateOf(viewModel.boardAddress()) }
        var boardToken by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("服务连接", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = serverUrl,
                onValueChange = update,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("妆伴服务地址") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp)
            )
            Button(onClick = { viewModel.updateServerUrl(serverUrl) }, modifier = Modifier.fillMaxWidth()) { Text("保存并重新连接") }
            Text("头戴设备", style = MaterialTheme.typography.titleLarge)
            SettingLine("画面来源", if (viewModel.boardConfigured()) "头戴设备" else "手机模拟")
            SettingLine("连接状态", state.boardStatus)
            OutlinedTextField(
                value = boardAddress,
                onValueChange = { boardAddress = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("设备本地 IP 地址") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )
            OutlinedTextField(
                value = boardToken,
                onValueChange = { boardToken = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("设备配对令牌") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Button(onClick = {
                viewModel.saveBoard(boardAddress, boardToken)
                boardToken = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("保存设备") }
            OutlinedButton(onClick = { viewModel.clearBoard() }, modifier = Modifier.fillMaxWidth()) { Text("使用手机模拟") }
            SettingLine("运行模式", if (state.mode == UserMode.ACCESSIBLE) "无障碍辅助" else "普通增强")
            SettingLine("摄像头", if (state.boardConnected) "头戴设备画面" else if (state.wearing) "前台服务运行中" else "未启动")
            SettingLine("人脸裁切", "ML Kit 本地检测")
            SettingLine("语音识别与播报", "阿里云")
            SettingLine("智能服务", if (state.serverReady) "已配置" else "未连接")
            OutlinedButton(onClick = onStopWear, enabled = state.wearing, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)) {
                Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("结束佩戴模式")
            }
        }
    }
}

@Composable
private fun SafetyScreen(state: UiState, resolve: (Boolean) -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(DarkBackground)) {
        IconButton(onClick = { showHelp = true }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 42.dp, end = 12.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))) {
            Icon(Icons.Default.HelpOutline, "使用帮助", tint = Color.White)
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Shield, null, tint = Color(0xFFFFE17A), modifier = Modifier.size(84.dp))
            Spacer(Modifier.height(24.dp))
            Text("检测到剧烈晃动", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(state.safetyMessage.orEmpty(), color = Color(0xFFE9E1EE), fontSize = 18.sp, lineHeight = 29.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(34.dp))
            Button(onClick = { resolve(true) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = RoseDark)) { Text("我很好，解除警报") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { resolve(false) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("我需要帮助") }
        }
        if (showHelp) HelpDialog("安全确认", "确认身体情况。无恙时解除警报，需要协助时点击求助。") { showHelp = false }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, dark: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            color = when {
                message.alert -> if (dark) Color(0xFF5A2D38) else Color(0xFFF6E2E4)
                dark && message.role == "user" -> Color.White.copy(alpha = 0.08f)
                dark -> Rose.copy(alpha = 0.18f)
                message.role == "user" -> LavenderSoft
                else -> SurfaceWarm
            },
            shape = RoundedCornerShape(17.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (message.alert) Color(0xFFE8AAA3) else if (dark) Color.White.copy(alpha = 0.11f) else Outline)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (message.role == "user") "您" else "小妆", color = if (dark) Color(0xFFD9A8B4) else RoseDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(message.text, color = if (dark) Color(0xFFF6E7EB) else Ink, fontSize = 17.sp, lineHeight = 27.sp)
            }
        }
    }
}

@Composable
private fun FrameImage(bytes: ByteArray?, modifier: Modifier, contentScale: ContentScale) {
    val bitmap: ImageBitmap? = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    if (bitmap == null) {
        Box(modifier.background(LavenderSoft), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(38.dp))
                Text("等待摄像头画面", color = Muted)
            }
        }
    } else {
        Image(bitmap, null, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
private fun StepProgress(index: Int, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count.coerceAtLeast(1)) { position ->
            Box(Modifier.weight(1f).height(5.dp).background(if (position <= index) Rose else Outline, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun MetricCard(label: String, score: Int, modifier: Modifier) {
    Surface(modifier = modifier, border = androidx.compose.foundation.BorderStroke(1.dp, Outline), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.90f)) {
        Column(Modifier.padding(12.dp)) {
            Text(score.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(Outline)) {
                Box(Modifier.fillMaxWidth((score.coerceIn(0, 100) / 100f)).fillMaxHeight().background(PrimaryGradient))
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, ready: Boolean) {
    Text(
        text,
        modifier = Modifier.padding(end = 10.dp).background(if (ready) Color(0xFFE4F0E8) else Color(0xFFF6E2E4), RoundedCornerShape(50.dp)).padding(horizontal = 11.dp, vertical = 7.dp),
        color = if (ready) Green else Coral,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DeviceRow(name: String, ready: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(if (ready) Color(0xFFF5F0F6) else Color(0xFFF6E2E4), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).background(if (ready) Green else Coral, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Text(name)
        Spacer(Modifier.weight(1f))
        Text(detail, color = if (ready) Green else Coral, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun QuickAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.height(76.dp).clickable(onClick = onClick), border = androidx.compose.foundation.BorderStroke(1.dp, Outline), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.90f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(LavenderSoft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Lavender)
            }
            Spacer(Modifier.width(9.dp))
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)

@Composable
private fun SettingLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(value, color = Muted)
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = RoseDark,
            selectedTextColor = if (dark) Color(0xFFF3D9DF) else RoseDeep,
            indicatorColor = if (dark) Rose.copy(alpha = 0.20f) else Color(0xFFFDF1F3),
            unselectedIconColor = if (dark) Color(0xFFA799B3) else Muted,
            unselectedTextColor = if (dark) Color(0xFFA799B3) else Muted
        )
    )
}

private fun screenHelp(screen: AppScreen): Pair<String, String> = when (screen) {
    AppScreen.HOME -> "开始使用" to "普通增强提供完整画面分析和实时建议；无障碍辅助使用语音与触觉引导。选好模式后点击开始佩戴模式。"
    AppScreen.WEAR -> "佩戴模式" to "佩戴后说“小妆”即可唤醒，也可以直接说“小妆，帮我看看”。点击开始说话或输入文字同样可用。"
    AppScreen.MIRROR -> "妆伴魔镜" to "前置摄像头会持续显示镜像画面，下方三分之一用于与小妆对话。可以语音或文字提问，其他功能可从导航栏进入；点击退出魔镜才会关闭摄像头。"
    AppScreen.GUIDE -> "妆容指导" to "确认方案后按顺序完成。每一步都可以检查、重听或跳过。"
    AppScreen.SKIN -> "皮肤状态" to "在佩戴页说“看看我的皮肤状态”。分析完成后可以保存当天记录。"
    AppScreen.PREVIEW -> "妆后预览" to "选择妆容、底妆、眼妆、腮红和唇妆。更新预览后，点击采用即可生成正式方案。"
    AppScreen.LOGS -> "实时日志" to "按时间查看识别、规划和语音任务的运行状态。"
    AppScreen.SETTINGS -> "设置" to "在这里修改服务地址、查看设备状态或结束佩戴模式。"
    else -> "使用帮助" to "按页面上的主要按钮继续。"
}

@Composable
private fun HelpDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, color = Muted, lineHeight = 24.sp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        shape = RoundedCornerShape(22.dp),
        containerColor = SurfaceWarm
    )
}

@Composable
private fun ValueHolder(initial: String, content: @Composable (String, (String) -> Unit) -> Unit) {
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }
    content(state.value) { state.value = it }
}

private fun time(value: Long): String = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(value))
