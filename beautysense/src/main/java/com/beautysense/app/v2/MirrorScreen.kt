package com.beautysense.app.v2

import android.Manifest
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private val MirrorPaper=Color(0xfffffaf6)
private val MirrorInk=Color(0xff302a2d)
private val MirrorRose=Color(0xff865364)

@OptIn(ExperimentalComposeUiApi::class)
@Composable fun MirrorScreen(model:MirrorViewModel,synthetic:android.graphics.Bitmap?=null) {
 val u by model.ui.collectAsState(); val context=LocalContext.current
 val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
 var valid by remember { mutableStateOf(false) }; var hint by remember { mutableStateOf("请将完整面部放入椭圆内") }
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
 LaunchedEffect(u.flow.epoch) {stopVoice();chat=false;expanded=true}
 LaunchedEffect(u.flow.stage) {expanded=true}
 val colors=MaterialTheme.colorScheme.copy(primary=MirrorRose,onPrimary=Color.White,background=MirrorPaper,surface=MirrorPaper,onSurface=MirrorInk,surfaceVariant=Color(0xfff2e9e7),onSurfaceVariant=Color(0xff796870),outline=Color(0xffd9c9cf))
 val type=MaterialTheme.typography.copy(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=22.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=21.sp),bodySmall=androidx.compose.ui.text.TextStyle(fontSize=12.sp,lineHeight=18.sp),titleLarge=androidx.compose.ui.text.TextStyle(fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold),titleMedium=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold),labelLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,fontWeight=FontWeight.Medium))
 MaterialTheme(colorScheme=colors,typography=type) {
 Column(Modifier.fillMaxSize().background(MirrorPaper).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().semantics {testTagsAsResourceId=true}) {
  Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   TextButton(onClick={if(u.flow.stage==Stage.HOME) showServer=true else model.reset(true)}) {Text(if(u.flow.stage==Stage.HOME) "设置" else "退出")}
   Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {Text("妆伴  BEAUTYSENSE",fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(stageLabel(u.flow.stage),fontSize=10.sp,color=Color(0xff7b6871))}
   OutlinedButton(onClick={chat=!chat;expanded=true},contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.height(38.dp),shape=RoundedCornerShape(20.dp)){Text("小妆",fontSize=12.sp)}
  }
  HorizontalDivider(color=Color(0xffe8deda))
  BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
   val geometry=mirrorLayout(maxWidth.value,maxHeight.value,LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,!expanded && !chat && !resetConfirm && !u.confirmReanalysis)
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
    Column(Modifier.padding(horizontal=16.dp)) {
     if(chat) {
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("小妆",style=MaterialTheme.typography.titleMedium);TextButton(onClick={stopVoice();chat=false}){Text("关闭",fontSize=12.sp)}}
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {Text(u.reply.ifEmpty {"可以解释分析、介绍产品，或帮你执行当前步骤。"});if(u.message.isNotBlank()) Text(u.message,color=MaterialTheme.colorScheme.error)}
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
         Stage.ALIGN->{Text("找到最清晰的你",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp));Text(hint);Text("请正对镜头，保持光线均匀。",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871))}
         Stage.ANALYZE->{Text("正在了解你的肌肤与面部",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp));Text("镜面保持开启，正在整理本次检测与选妆建议…")}
         Stage.RESULTS->{
          Text("你的面部与肌肤报告",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp));Text(u.analysis?.optString("summary").orEmpty());Spacer(Modifier.height(10.dp));Text("皮肤观察",style=MaterialTheme.typography.titleMedium);Text(readable(u.analysis?.opt("skinMetrics")));Text("数值为图像特征强度，非医学评分。",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871));Spacer(Modifier.height(10.dp));Text("选妆重点",style=MaterialTheme.typography.titleMedium);Text(readable(u.analysis?.opt("selectionAdvice")));Spacer(Modifier.height(10.dp));Text("面部特点",style=MaterialTheme.typography.titleMedium);Text(readable(u.analysis?.opt("faceFeatures")));if(u.analysis?.optString("explanationStatus")=="failed") TextButton(onClick=model::retryExplanation){Text("重试解读")}
         }
         Stage.SELECT,Stage.GENERATE,Stage.LIVE->{
          if(u.flow.stage==Stage.LIVE) {
           Text(if(u.effect?.optBoolean("unchanged")==true) "护理产品已选好" else "你的妆效预览",style=MaterialTheme.typography.titleLarge)
           Text(if(u.effect?.optBoolean("unchanged")==true) "所选产品用于日常护理，本次保持原貌。" else "保留属于你的五官，试试不同的妆感。",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871))
           Row(verticalAlignment=Alignment.CenterVertically){Text("妆效浓淡");Slider(value=u.intensity,onValueChange=model::intensity,modifier=Modifier.weight(1f).padding(start=12.dp))}
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Row(Modifier.clickable {model.original(!u.original)},verticalAlignment=Alignment.CenterVertically){Checkbox(u.original,model::original);Text("原貌对照")};TextButton(onClick=model::clearEffect){Text("清除妆效",fontSize=12.sp)}}
          } else if(u.flow.stage==Stage.GENERATE){Text("正在为你生成妆效",style=MaterialTheme.typography.titleLarge);Text("镜面保持开启，完成后将应用所选产品的妆感。")}
          else {Text("适合你的珀莱雅选品",style=MaterialTheme.typography.titleLarge);Catalog(u,model)}
          Text("已选 ${u.flow.items.size} 款 · 每类最多一款",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871))
          if(u.flow.stage==Stage.SELECT) u.flow.items.values.forEach {i->Row(verticalAlignment=Alignment.CenterVertically){Text(itemLabel(u.catalog,i),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={model.remove(i.categoryId)}){Text("移除",fontSize=12.sp)}}}
         }
         else->Unit
        }
       }
       HorizontalDivider(color=Color(0xffe8deda));Spacer(Modifier.height(8.dp))
       Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(u.analysis!=null) TextButton(onClick={resetConfirm=true}){Text("重新分析",fontSize=12.sp)}
        when(u.flow.stage) {
         Stage.ALIGN->Button(onClick=model::analyze,enabled=valid&&!u.busy,modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(13.dp)){Text("确定")}
         Stage.RESULTS->Button(onClick=model::next,enabled=!u.busy,modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(13.dp)){Text("下一步")}
         Stage.SELECT->Button(onClick=model::generate,enabled=u.flow.canGenerate&&!u.busy,modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(13.dp)){Text(if(u.flow.canGenerate) "查看效果" else "请至少选择一款")}
         Stage.GENERATE->OutlinedButton(onClick=model::clearEffect,modifier=Modifier.weight(1f)){Text("取消生成")}
         Stage.LIVE->Button(onClick=model::edit,modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(13.dp)){Text("更换产品")}
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
@Composable private fun Catalog(u:MirrorUi,model:MirrorViewModel) {
 val catalog=u.catalog ?: return
 val categories=catalog.optJSONArray("categories") ?: return
 val products=catalog.optJSONArray("products") ?: return
 val recommended=(0 until u.recommendations.length()).map {u.recommendations.getJSONObject(it)}.associateBy {it.optString("productId")}
 val visible=(0 until products.length()).map {products.getJSONObject(it)}.filter {it.optString("brand")=="PROYA"}
 val groups=(0 until categories.length()).map {categories.getJSONObject(it)}.filter { c->visible.any {it.optString("categoryId")==c.optString("id")} }
 val activeCategory=u.category ?: groups.firstOrNull()?.optString("id")
 Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {groups.forEach {c->FilterChip(selected=activeCategory==c.getString("id"),onClick={model.category(c.getString("id"))},label={Text(c.getString("name")+(if(u.flow.items.containsKey(c.getString("id"))) " · 1" else ""),fontSize=12.sp)})}}
 groups.filter {activeCategory==it.getString("id")}.forEach { category->
  val id=category.getString("id")
  key(id) {
   visible.filter {it.getString("categoryId")==id}.forEach { p->
    key(p.getString("id")) {
     var detail by remember { mutableStateOf(false) }
     Card(Modifier.fillMaxWidth().padding(vertical=5.dp),colors=CardDefaults.cardColors(containerColor=Color.White.copy(alpha=.72f)),border=BorderStroke(1.dp,if(u.flow.items[id]?.productId==p.getString("id")) MirrorRose else Color(0xffe5dcd8)),shape=RoundedCornerShape(14.dp)) {
      Column(Modifier.padding(10.dp)) {
       Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        ProductImage(p.optString("image"))
        Column(Modifier.weight(1f)) {
         Text(p.getString("name"),fontSize=14.sp,fontWeight=FontWeight.Medium)
         Text(recommended[p.getString("id")]?.optString("reason") ?: "官方目录商品，可按偏好选择。",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871),maxLines=2,overflow=TextOverflow.Ellipsis)
         val variants=p.getJSONArray("variants")
         (0 until variants.length()).map {variants.getJSONObject(it)}.forEach {variant->
          val chosen=u.flow.items[id]?.let {it.productId==p.getString("id") && it.variantId==variant.getString("id")}==true
          OutlinedButton(onClick={if(chosen) model.remove(id) else model.select(Item(id,p.getString("id"),variant.getString("id")))},modifier=Modifier.heightIn(min=36.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp),shape=RoundedCornerShape(10.dp)){Text((if(chosen) "已选 · " else "选择 · ")+variant.getString("name"),fontSize=12.sp)}
         }
        }
       }
       TextButton(onClick={detail=!detail},modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=0.dp)){Text(if(detail) "收起详情" else "了解产品",fontSize=12.sp)}
       if(detail) {Text(recommended[p.getString("id")]?.optString("reason").orEmpty(),style=MaterialTheme.typography.bodySmall);Text(p.optString("description"),style=MaterialTheme.typography.bodySmall);Text(p.optString("usage"),style=MaterialTheme.typography.bodySmall);Text("完整成分与使用方法请以商品包装和官方资料为准。",style=MaterialTheme.typography.bodySmall,color=Color(0xff7b6871))}
      }
     }
    }
   }
  }
 }
}
@Composable private fun ProductImage(path:String) {
 var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }; var failed by remember(path){mutableStateOf(false)}; var attempt by remember{mutableStateOf(0)}
 LaunchedEffect(path,attempt) { withContext(Dispatchers.IO) { try { val url=if(path.startsWith("https://")||path.startsWith("http://")) path else MirrorApi().base+"/"+path.trimStart('/'); URL(url).openConnection().apply {connectTimeout=10000;readTimeout=10000}.getInputStream().use {bitmap=BitmapFactory.decodeStream(it)}; failed=bitmap==null } catch(_:Exception){failed=true} } }
 if(bitmap!=null) Image(bitmap!!.asImageBitmap(),"官方商品实图",Modifier.size(72.dp)) else if(failed) TextButton(onClick={attempt++}){Text("图片加载失败，重试")}
}


private fun readable(value:Any?):String = when(value) {
 is org.json.JSONArray -> (0 until value.length()).joinToString("\n") { readable(value.opt(it)) }
 is JSONObject -> if(value.has("name")) listOf("name","observation","description","value","unit","meaning").mapNotNull { value.opt(it)?.takeIf { it!=JSONObject.NULL }?.toString() }.joinToString(" · ") else value.keys().asSequence().joinToString("\n") { "${metricLabel(it)}：${readable(value.opt(it))}" }
 null,JSONObject.NULL -> "暂无可用观察"
 else -> value.toString()
}



















private fun stageLabel(stage:Stage)=when(stage) {
 Stage.HOME->"妆伴";Stage.ALIGN->"面部定位";Stage.ANALYZE->"正在分析";Stage.RESULTS->"面部与肌肤报告";Stage.SELECT->"珀莱雅选品";Stage.GENERATE->"正在生成妆效";Stage.LIVE->"你的妆效预览"
}
private fun metricLabel(key:String)=mapOf("redness" to "泛红","texture" to "纹理","pores" to "毛孔","blemishes" to "斑点","pigment" to "色素","oiliness" to "油光")[key] ?: key
private fun itemLabel(catalog:JSONObject?,item:Item):String {
 val products=catalog?.optJSONArray("products") ?: return "已选产品"
 for(i in 0 until products.length()) { val p=products.getJSONObject(i); if(p.optString("id")==item.productId) {
  val variants=p.optJSONArray("variants"); var name=""
  if(variants!=null) for(j in 0 until variants.length()) {val v=variants.getJSONObject(j); if(v.optString("id")==item.variantId) name=v.optString("name")}
  return listOf(p.optString("name"),name).filter {it.isNotBlank()}.joinToString(" · ")
 } }
 return "已选产品"
}
