package com.beautysense.app.v2

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val MirrorPaper=Color(0xfffffaf6)
private val MirrorInk=Color(0xff302a2d)
private val MirrorRose=Color(0xff865364)

@OptIn(ExperimentalComposeUiApi::class)
@Composable fun MirrorScreen(model:MirrorViewModel,synthetic:android.graphics.Bitmap?=null) {
 val u by model.ui.collectAsState(); val context=LocalContext.current
 val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
 var valid by remember { mutableStateOf(false) }; var hint by remember { mutableStateOf("请将完整面部放入椭圆内") }
 var restoreFocus by remember(u.flow.epoch) {mutableStateOf<String?>(null)}
 var detail by remember(u.flow.epoch) { mutableStateOf<MirrorDetail?>(null) }
 fun closeDetail() {restoreFocus=detail?.product?.optString("id") ?: when(detail?.title) {"完整报告"->"report";"小妆回复"->"assistant";else->null};detail=null}
 fun showDetail(title:String,body:String) {if(model.ui.value.flow.epoch==u.flow.epoch) detail=MirrorDetail(u.flow.epoch,title,body)}
 val activeDetail=detail?.takeIf {it.belongsTo(u.flow.epoch) && it.belongsTo(model.ui.value.flow.epoch)}
 var expanded by remember { mutableStateOf(true) }; var chat by remember { mutableStateOf(false) }; var text by remember { mutableStateOf("") }; var resetConfirm by remember { mutableStateOf(false) }
 val settings=remember { context.getSharedPreferences("mirror-v2",0) }
 var showServer by remember { mutableStateOf(false) };var server by remember { mutableStateOf(settings.getString("server",MirrorApi().base).orEmpty()) };var serverError by remember { mutableStateOf("") }
 LaunchedEffect(Unit) { if(server!=MirrorApi().base) model.changeServer(server) }
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) model.start() else model.message("请在系统设置允许摄像头，然后重试开始") }
 fun submitText(value:String) { if(model.ui.value.flow.stage==Stage.HOME && value.trim() in listOf("开始","开始体验")) camera.launch(Manifest.permission.CAMERA) else model.dialogue(value) }
 val voice=remember { CloudVoice(context) };val scope=rememberCoroutineScope()
 var recording by remember { mutableStateOf(false) };var cloudJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
 fun stopVoice() {model.cancelAssistant();cloudJob?.cancel();cloudJob=null;voice.cancel();recording=false}
 fun listen() {try {stopVoice();voice.start {recording=false;model.message("录音已超时取消，请重新录音")};recording=true} catch(e:Exception) {model.message(e.message ?: "无法启动麦克风")}}
 val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {if(it) listen() else model.message("麦克风未授权，点击和文字功能仍可使用")}
 DisposableEffect(voice) {model.stopAudio={stopVoice()};onDispose {model.stopAudio={};cloudJob?.cancel();voice.cancel()}}
 LaunchedEffect(u.flow.epoch) {stopVoice();chat=false;expanded=true;detail=null}
 LaunchedEffect(u.flow.stage) {expanded=true}
 val colors=MaterialTheme.colorScheme.copy(primary=MirrorRose,onPrimary=Color.White,primaryContainer=Color(0xffeee2e5),onPrimaryContainer=MirrorInk,secondary=MirrorRose,secondaryContainer=Color(0xffeee6e2),onSecondaryContainer=MirrorInk,background=MirrorPaper,surface=MirrorPaper,onSurface=MirrorInk,surfaceVariant=Color(0xfff2e9e7),onSurfaceVariant=Color(0xff796870),outline=Color(0xffd9c9cf))
 val type=MaterialTheme.typography.copy(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=22.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=21.sp),bodySmall=androidx.compose.ui.text.TextStyle(fontSize=12.sp,lineHeight=18.sp),titleLarge=androidx.compose.ui.text.TextStyle(fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold),titleMedium=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold),labelLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,fontWeight=FontWeight.Medium))
 MaterialTheme(colorScheme=colors,typography=type) {
 Column(Modifier.fillMaxSize().background(MirrorPaper).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().semantics {testTagsAsResourceId=true}) {
  Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   TextButton(onClick={if(u.flow.stage==Stage.HOME) showServer=true else model.reset(true)}) {Text(if(u.flow.stage==Stage.HOME) "设置" else "退出")}
   Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {Text("妆伴  BEAUTYSENSE",fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(stageLabel(u.flow.stage),fontSize=10.sp,color=Color(0xff7b6871))}
   OutlinedButton(onClick={detail=null;chat=!chat;expanded=true},contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.height(38.dp),shape=RoundedCornerShape(20.dp)){Text("小妆",fontSize=12.sp)}
  }
  HorizontalDivider(color=Color(0xffe8deda))
  BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
   val geometry=mirrorLayout(maxWidth.value,maxHeight.value,LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,!expanded && !chat && !resetConfirm && !u.confirmReanalysis && activeDetail==null)
   if(u.flow.stage==Stage.HOME) {
    Column(Modifier.fillMaxSize().background(Color(0xfff1e6e2)).padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
     Text("PROYA × BEAUTYSENSE",fontSize=11.sp,color=MirrorRose);Spacer(Modifier.height(20.dp));Text("发现适合你的美",fontSize=30.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(14.dp));Text("了解肌肤与面部特点，选择珀莱雅产品，\n在镜中看见属于自己的妆效。",color=Color(0xff7b6871));Spacer(Modifier.height(30.dp));Button(onClick={camera.launch(Manifest.permission.CAMERA)},modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(14.dp)){Text("开始")};Spacer(Modifier.height(18.dp));Text("确认分析时将选取面部画面提交给 AI 服务。\n摄像头仅在当前体验中开启，麦克风按需授权。",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871))
     if(u.message.isNotBlank()) Text(u.message,color=MaterialTheme.colorScheme.error)
    }
   } else {
    Box(Modifier.offset(geometry.camera.x.dp,geometry.camera.y.dp).size(geometry.camera.width.dp,geometry.camera.height.dp).clipToBounds().background(Color(0xffe9e1db)).testTag("face-viewport")) {
     val onFrame:(LiveFrame)->Unit={ f->
      val inside=u.flow.stage!=Stage.ALIGN || faceInGuide(f.points.map {it.x to it.y},f.width,f.height,geometry)
      val accepted=f.valid && inside
      model.frame(f.copy(valid=accepted));valid=accepted
      hint=if(f.valid && !inside) "请调整距离，将额头到下巴完整放入椭圆" else if(synthetic!=null && accepted) "面部已就位，点击确定" else f.hint
     }
     if(synthetic!=null) DebugPortraitInput.Preview(synthetic,Modifier.fillMaxSize(),u.effect,u.intensity,u.original,onFrame,model::message)
     else LiveCamera(Modifier.fillMaxSize(),u.effect,u.intensity,u.original,SessionSafety.requiresContinuity(u.flow.stage),onFrame,{model.reset();model.message("面部连续性已中断，请重新分析")},model::message)
     Canvas(Modifier.offset(geometry.oval.x.dp,geometry.oval.y.dp).size(geometry.oval.width.dp,geometry.oval.height.dp).testTag("face-safe-area").semantics {contentDescription="完整人脸保留区域"}) {
      if(u.flow.stage==Stage.ALIGN) drawOval(Color.White.copy(alpha=.9f),style=Stroke(width=1.5.dp.toPx()))
     }
    }
   }
   if(u.flow.stage!=Stage.HOME || chat) Surface(Modifier.offset(geometry.panel.x.dp,geometry.panel.y.dp).size(geometry.panel.width.dp,geometry.panel.height.dp).testTag(if(chat) "assistant-panel" else "info-panel"),color=MirrorPaper.copy(alpha=.94f),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),tonalElevation=0.dp,border=BorderStroke(1.dp,Color.White.copy(alpha=.8f))) {
    if(activeDetail!=null) MirrorDetailPane(activeDetail,u,model,::closeDetail) else Column(Modifier.padding(horizontal=16.dp)) {
     if(chat) {
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("小妆",style=MaterialTheme.typography.titleMedium);TextButton(onClick={stopVoice();chat=false}){Text("关闭",fontSize=12.sp)}}
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {CompactReply(u.reply,restoreFocus=="assistant",{restoreFocus=null}) {showDetail("小妆回复",u.reply)};if(u.message.isNotBlank()) Text(u.message,color=MaterialTheme.colorScheme.error)}
      OutlinedTextField(text,{text=it},label={Text("输入问题或操作")},modifier=Modifier.fillMaxWidth(),maxLines=2,textStyle=MaterialTheme.typography.bodyMedium)
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
       TextButton(onClick={if(recording) {val epoch=model.ui.value.flow.epoch;try {val bytes=voice.finish();recording=false;cloudJob=scope.launch {try {val transcript=model.transcribe(bytes,epoch);if(model.ui.value.flow.epoch==epoch) submitText(transcript)} catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){model.message(e.message ?: "识别失败，请重试")}}} catch(e:Exception){stopVoice();model.message(e.message ?: "录音失败")}} else microphone.launch(Manifest.permission.RECORD_AUDIO)}){Text(if(recording) "完成识别" else "语音输入",fontSize=12.sp)}
       TextButton(onClick={stopVoice();val epoch=model.ui.value.flow.epoch;val generation=voice.generation;cloudJob=scope.launch {try {val url=model.speechUrl(u.reply,epoch);if(model.ui.value.flow.epoch==epoch) voice.play(url,generation)} catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){model.message(e.message ?: "朗读失败，请重试")}}},enabled=u.reply.isNotBlank()){Text("朗读",fontSize=12.sp)}
       TextButton(onClick={stopVoice()}){Text("停止",fontSize=12.sp)}
       Button(onClick={submitText(text);text="";focus.clearFocus();keyboard?.hide()},enabled=text.isNotBlank()){Text("发送",fontSize=12.sp)}
      }
     } else if(resetConfirm||u.confirmReanalysis) {
      Spacer(Modifier.height(20.dp));Text("重新分析？",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(10.dp));Text("将清空当前选品与试妆效果，重新定位面部。",Modifier.weight(1f));Row(Modifier.padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={resetConfirm=false;model.dismissReanalysis()},modifier=Modifier.weight(1f)){Text("取消")};Button(onClick={resetConfirm=false;model.reset()},modifier=Modifier.weight(1f)){Text("确定")}}
     } else {
      if(u.flow.stage !in listOf(Stage.ALIGN,Stage.ANALYZE)) TextButton(onClick={expanded=!expanded},modifier=Modifier.align(Alignment.CenterHorizontally).height(34.dp)){Text(if(expanded) "⌄ 收起面板" else "⌃ 展开面板",fontSize=11.sp,color=Color(0xff7b6871))} else Spacer(Modifier.height(14.dp))
      if(expanded) {
       Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(remember(u.flow.stage) {ScrollState(0)}).testTag("panel-scroll")) {
        if(u.message.isNotBlank()) Text(u.message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        if(u.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when(u.flow.stage) {
         Stage.ALIGN->{Text("面部定位",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp));Text(hint);Text("请正对镜头，保持光线均匀。",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871))}
         Stage.ANALYZE->{Text("正在分析",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp));Text("请稍候")}
         Stage.RESULTS->{
          CompactReport(u.analysis,restoreFocus=="report",{restoreFocus=null}) {showDetail("完整报告",MirrorPresentation.analysisReport(u.analysis))}
          if(u.analysis?.optString("explanationStatus")=="failed") TextButton(onClick=model::retryExplanation){Text("重试解读")}

         }
         Stage.SELECT,Stage.GENERATE,Stage.LIVE->{
          if(u.flow.stage==Stage.LIVE) {
           Text(if(u.effect?.optBoolean("unchanged")==true) "护理产品已选好" else "你的妆效预览",style=MaterialTheme.typography.titleLarge)
           Text(if(u.effect?.optBoolean("unchanged")==true) "护理选品 · 保持原貌" else "调整浓淡，查看妆感",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871))
           Row(verticalAlignment=Alignment.CenterVertically){Text("妆效浓淡");Slider(value=u.intensity,onValueChange=model::intensity,colors=SliderDefaults.colors(thumbColor=MirrorRose,activeTrackColor=MirrorRose,inactiveTrackColor=Color(0xffe6dcda)),modifier=Modifier.weight(1f).padding(start=12.dp))}
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Row(Modifier.clickable {model.original(!u.original)},verticalAlignment=Alignment.CenterVertically){Checkbox(u.original,model::original,colors=CheckboxDefaults.colors(checkedColor=MirrorRose,uncheckedColor=Color(0xff9c8d93),checkmarkColor=Color.White));Text("原貌对照")};TextButton(onClick=model::clearEffect){Text("清除妆效",fontSize=12.sp)}}
          } else if(u.flow.stage==Stage.GENERATE){Text("正在生成",style=MaterialTheme.typography.titleLarge);Text("请稍候")}
          else {CompactCatalog(u,model,restoreFocus,{restoreFocus=null}) {product,picking->
           if(model.ui.value.flow.epoch==u.flow.epoch) detail=MirrorDetail(u.flow.epoch,if(picking) "选择规格" else "产品详情",productDetail(u,product),product,picking)
          }}
          Text("已选 ${u.flow.items.size} 款 · 每类最多一款",modifier=Modifier.testTag("selection-count"),fontSize=11.sp,lineHeight=14.sp,color=Color(0xff7b6871))

         }
         else->Unit
        }
       }
       HorizontalDivider(color=Color(0xffe8deda));Spacer(Modifier.height(8.dp))
       Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(u.analysis!=null) TextButton(onClick={resetConfirm=true}){Text("重新分析",fontSize=12.sp)}
        when(u.flow.stage) {
         Stage.ALIGN->CompactAction("确定",enabled=valid&&!u.busy,modifier=Modifier.weight(1f).height(46.dp),help={showDetail("操作说明","面部就位后，点击确定开始分析。")},click=model::analyze)
         Stage.RESULTS->CompactAction("下一步",enabled=!u.busy,modifier=Modifier.weight(1f).height(46.dp),help={showDetail("操作说明","进入珀莱雅选品，可按分类选择。")},click=model::next)
         Stage.SELECT->CompactAction(if(u.flow.canGenerate) "查看效果" else "请选择产品",enabled=u.flow.canGenerate&&!u.busy,modifier=Modifier.weight(1f).height(46.dp),help={showDetail("操作说明","至少选择一款产品后生成妆效。")},click=model::generate)
         Stage.GENERATE->OutlinedButton(onClick=model::clearEffect,modifier=Modifier.weight(1f)){Text("取消生成")}
         Stage.LIVE->CompactAction("更换产品",enabled=true,modifier=Modifier.weight(1f).height(46.dp),help={showDetail("操作说明","返回选品，调整产品与规格。")},click=model::edit)
         else->Unit
        }
       }
      }
     }
    }
   }
  }
 }
 if(showServer) AlertDialog(onDismissRequest={showServer=false},title={Text("服务设置")},text={Column {Text("在同一网络下可填写电脑服务地址。");OutlinedTextField(server,{server=it},label={Text("服务地址")},singleLine=true);Text(serverError,color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={val uri=runCatching {java.net.URI(server.trim())}.getOrNull();if(uri==null || uri.scheme !in listOf("http","https") || uri.host.isNullOrBlank() || uri.userInfo!=null || uri.query!=null || uri.fragment!=null) serverError="请输入有效的服务地址" else {val value=server.trim().trimEnd('/');settings.edit().putString("server",value).apply();model.changeServer(value);showServer=false;serverError=""}}){Text("保存")}},dismissButton={TextButton(onClick={showServer=false}){Text("取消")}})

 }
}
private fun stageLabel(stage:Stage)=when(stage) {
 Stage.HOME->"妆伴";Stage.ALIGN->"面部定位";Stage.ANALYZE->"正在分析";Stage.RESULTS->"面部与肌肤报告";Stage.SELECT->"珀莱雅选品";Stage.GENERATE->"正在生成妆效";Stage.LIVE->"你的妆效预览"
}
