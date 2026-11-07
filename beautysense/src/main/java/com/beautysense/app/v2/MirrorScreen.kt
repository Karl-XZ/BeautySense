package com.beautysense.app.v2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

@Composable fun MirrorScreen(model:MirrorViewModel,synthetic:android.graphics.Bitmap?=null) {
 val u by model.ui.collectAsState(); val context=LocalContext.current
 var valid by remember { mutableStateOf(false) }; var hint by remember { mutableStateOf("请将面部放入圆框内") }
 var expanded by remember { mutableStateOf(true) }; var chat by remember { mutableStateOf(false) }; var text by remember { mutableStateOf("") }; var resetConfirm by remember { mutableStateOf(false) }
 val settings=remember { context.getSharedPreferences("mirror-v2",0) }
 var showServer by remember { mutableStateOf(false) }
 var server by remember { mutableStateOf(settings.getString("server",MirrorApi().base).orEmpty()) }
 var serverError by remember { mutableStateOf("") }
 LaunchedEffect(Unit) { if(server!=MirrorApi().base) model.changeServer(server) }
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) model.start() else model.message("请在系统设置允许摄像头，然后重试开始") }
 fun submitText(value:String) { if(model.ui.value.flow.stage==Stage.HOME && value.trim() in listOf("开始","开始体验")) camera.launch(Manifest.permission.CAMERA) else model.dialogue(value) }
 val voice=remember { CloudVoice(context) }; val scope=rememberCoroutineScope()
 var recording by remember { mutableStateOf(false) }; var cloudJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
 fun stopVoice() { model.cancelAssistant(); cloudJob?.cancel(); cloudJob=null; voice.cancel(); recording=false }
 fun listen() { try { stopVoice(); voice.start { recording=false; model.message("录音已超时取消，请重新录音") }; recording=true } catch(e:Exception) { model.message(e.message ?: "无法启动麦克风") } }
 val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) listen() else model.message("麦克风未授权，点击和文字功能仍可使用") }
 DisposableEffect(voice) { model.stopAudio={ stopVoice() }; onDispose { model.stopAudio={}; cloudJob?.cancel(); voice.cancel() } }
 LaunchedEffect(u.flow.epoch) { stopVoice(); chat=false }
 BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff15191a)).imePadding()) {
  val landscape=maxWidth>maxHeight
  if(u.flow.stage!=Stage.HOME) {
   if(synthetic!=null) DebugPortraitInput.Preview(synthetic,Modifier.fillMaxSize(),u.effect,u.intensity,u.original,{model.frame(it);valid=it.valid;hint=it.hint},model::message) else LiveCamera(Modifier.fillMaxSize(),u.effect,u.intensity,u.original,SessionSafety.requiresContinuity(u.flow.stage),{ model.frame(it); valid=it.valid; hint=it.hint },{ model.reset(); model.message("面部连续性已中断，请重新分析") },model::message)
   if(u.flow.stage==Stage.ALIGN) Box(Modifier.align(Alignment.Center).size(250.dp).clip(CircleShape).border(2.dp,Color.White,CircleShape))
  }
  if(u.flow.stage==Stage.HOME) Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
   Text("妆伴",style=MaterialTheme.typography.displayMedium,color=Color.White); Text("珀莱雅 · 了解肌肤，找到适合自己的妆容",color=Color.White)
   Spacer(Modifier.height(24.dp)); Text("面部分析及生成会向服务端提交选取的面部帧；实时跟踪使用本机摄像头。",color=Color.LightGray)
   Button(onClick={ camera.launch(Manifest.permission.CAMERA) }) { Text("开始") }
   TextButton(onClick={chat=true}) { Text("小妆") }; TextButton(onClick={showServer=true}) { Text("服务设置") }; Text(u.message,color=Color.White)
  } else {
   Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha=0.55f)).padding(top=28.dp),horizontalArrangement=Arrangement.SpaceBetween) { TextButton(onClick={model.reset(true)}){Text("退出")}; Text((if(synthetic!=null) "测试图输入 · " else "")+stageLabel(u.flow.stage),color=Color.White,modifier=Modifier.padding(12.dp)); TextButton(onClick={chat=true}){Text("小妆")} }
   Surface(modifier=Modifier.align(if(landscape) Alignment.CenterEnd else Alignment.BottomCenter).then(if(landscape) Modifier.width(maxWidth*0.42f).fillMaxHeight(0.85f) else Modifier.fillMaxWidth().heightIn(max=maxHeight*0.45f)),color=Color(0xeefff8f2),shape=MaterialTheme.shapes.large) {
    Column(Modifier.padding(16.dp)) {
     TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "收起面板" else "展开面板") }
     if(u.message.isNotEmpty()) Text(u.message,color=MaterialTheme.colorScheme.error)
     if(u.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
     if(expanded) Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState())) {
      when(u.flow.stage) {
       Stage.ALIGN->Text(hint)
       Stage.ANALYZE->Text("正在联合分析面部和皮肤，镜面持续播放…")
       Stage.RESULTS->{ Text("本次面部与皮肤概况",style=MaterialTheme.typography.titleLarge); Text(u.analysis?.optString("summary").orEmpty()); Text("选妆重点"); Text(readable(u.analysis?.opt("selectionAdvice"))); Text("皮肤观察"); Text(readable(u.analysis?.opt("skinMetrics"))); Text("面部特点"); Text(readable(u.analysis?.opt("faceFeatures"))); if(u.analysis?.optString("explanationStatus")=="failed") TextButton(onClick=model::retryExplanation){Text("重试解读")} }
       Stage.SELECT,Stage.GENERATE,Stage.LIVE->{
        if(u.flow.stage==Stage.GENERATE) Text("正在生成所选产品对应的妆效…")
        if(u.flow.stage==Stage.LIVE) { if(synthetic!=null) Text("测试图 · 源${u.effect?.optJSONArray("sourceLandmarks")?.length() ?: 0}点 · ${(u.effect?.optJSONArray("triangles")?.length() ?: 0)/3}三角形"); Text(if(u.effect?.optBoolean("unchanged")==true) "所选护肤品无即时妆效，保留原貌" else "已生成妆效 · 遮挡或丢脸时隐藏"); Text("浓淡"); Slider(value=u.intensity,onValueChange=model::intensity); Row(Modifier.clickable {model.original(!u.original)}) { Checkbox(u.original,model::original); Text("原貌对照") }; TextButton(onClick=model::clearEffect){Text("清除效果")} }
        if(u.flow.stage!=Stage.LIVE) Catalog(u,model)
        Text("已选 ${u.flow.items.size} 款"); u.flow.items.values.forEach { i->Row { Text(itemLabel(u.catalog,i),Modifier.weight(1f)); TextButton(onClick={model.remove(i.categoryId)}){Text("删除")} } }
       }
       else->Unit
      }
     }
     when(u.flow.stage) {
      Stage.ALIGN->Button(onClick=model::analyze,enabled=valid&&!u.busy,modifier=Modifier.fillMaxWidth()){Text("确定")}
      Stage.RESULTS->Button(onClick=model::next,enabled=!u.busy,modifier=Modifier.fillMaxWidth()){Text("下一步")}
      Stage.SELECT,Stage.GENERATE->Button(onClick=model::generate,enabled=u.flow.canGenerate&&!u.busy,modifier=Modifier.fillMaxWidth()){Text(if(u.flow.canGenerate) "查看效果" else "请至少选择一款产品")}
      Stage.LIVE->Button(onClick=model::edit,modifier=Modifier.fillMaxWidth()){Text("更换产品")}
      else->Unit
     }
     if(u.flow.stage==Stage.GENERATE) TextButton(onClick=model::clearEffect){Text("取消生成")}
     if(u.analysis!=null) TextButton(onClick={resetConfirm=true}){Text("重新分析")}
    }
   }
  }
 }
 if(showServer) AlertDialog(onDismissRequest={showServer=false},title={Text("服务设置")},text={Column { Text("PC模拟器使用127.0.0.1并通过USB转发；同一WiFi可填写电脑局域网地址。"); OutlinedTextField(server,{server=it},label={Text("服务地址")},singleLine=true); Text(serverError,color=MaterialTheme.colorScheme.error) }},confirmButton={TextButton(onClick={
  val uri=runCatching { java.net.URI(server.trim()) }.getOrNull()
  if(uri==null || uri.scheme !in listOf("http","https") || uri.host.isNullOrBlank() || uri.userInfo!=null || uri.query!=null || uri.fragment!=null) serverError="请输入有效的http或https服务地址"
  else { val value=server.trim().trimEnd('/'); settings.edit().putString("server",value).apply(); model.changeServer(value); showServer=false; serverError="" }
 }){Text("保存")}},dismissButton={TextButton(onClick={showServer=false}){Text("取消")}})
 if(resetConfirm||u.confirmReanalysis) AlertDialog(onDismissRequest={resetConfirm=false;model.dismissReanalysis()},title={Text("重新分析")},text={Text("将重新分析并清空当前选品与试妆效果")},confirmButton={TextButton(onClick={resetConfirm=false; model.reset()}){Text("确定")}},dismissButton={TextButton(onClick={resetConfirm=false;model.dismissReanalysis()}){Text("取消")}})
 if(chat) AlertDialog(onDismissRequest={stopVoice();chat=false},title={Text("小妆")},text={Column { Text(u.reply.ifEmpty { "可以解释分析、介绍产品，或帮你执行当前步骤。" }); OutlinedTextField(text,{text=it},label={Text("输入问题或操作")}); Row { TextButton(onClick={ if(recording) {
 val epoch=model.ui.value.flow.epoch
 try { val bytes=voice.finish(); recording=false; cloudJob=scope.launch { try { val transcript=model.transcribe(bytes,epoch); if(model.ui.value.flow.epoch==epoch) submitText(transcript) } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(e:Exception) { model.message(e.message ?: "识别失败，请重试") } } } catch(e:Exception) { stopVoice(); model.message(e.message ?: "录音失败") }
} else microphone.launch(Manifest.permission.RECORD_AUDIO) }){Text(if(recording) "完成识别" else "语音输入")}; TextButton(onClick={ stopVoice(); val epoch=model.ui.value.flow.epoch; val generation=voice.generation; cloudJob=scope.launch { try { val url=model.speechUrl(u.reply,epoch); if(model.ui.value.flow.epoch==epoch) voice.play(url,generation) } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(e:Exception) { model.message(e.message ?: "朗读失败，请重试") } } },enabled=u.reply.isNotBlank()){Text("朗读")}; TextButton(onClick={stopVoice()}){Text("停止")}} }},confirmButton={TextButton(onClick={submitText(text);text=""},enabled=text.isNotBlank()){Text("发送")}},dismissButton={TextButton(onClick={stopVoice();chat=false}){Text("关闭")}})
}
@Composable private fun Catalog(u:MirrorUi,model:MirrorViewModel) {
 val catalog=u.catalog ?: return
 val categories=catalog.optJSONArray("categories") ?: return
 val products=catalog.optJSONArray("products") ?: return
 val recommended=(0 until u.recommendations.length()).map {u.recommendations.getJSONObject(it)}.associateBy {it.optString("productId")}
 val visible=(0 until products.length()).map {products.getJSONObject(it)}.filter {it.optString("brand")=="PROYA"}
 val groups=(0 until categories.length()).map {categories.getJSONObject(it)}.filter { c->visible.any {it.optString("categoryId")==c.optString("id")} }
 Row(Modifier.horizontalScroll(rememberScrollState())) { groups.forEach { c->TextButton(onClick={model.category(c.getString("id"))}){Text(c.getString("name"))} } }
 groups.filter {u.category==null || u.category==it.getString("id")}.forEach { category->
  val id=category.getString("id")
  key(id) {
   Text(category.getString("name")+if(u.flow.items.containsKey(id)) " · 已选1" else "",style=MaterialTheme.typography.titleMedium)
   visible.filter {it.getString("categoryId")==id}.forEach { p->
    key(p.getString("id")) {
     var detail by remember { mutableStateOf(false) }
     Card(Modifier.fillMaxWidth().padding(vertical=5.dp)) {
      Column(Modifier.padding(10.dp)) {
       ProductImage(p.optString("image")); Text(p.getString("name")); Text(recommended[p.getString("id")]?.optString("reason") ?: "官方目录商品，可按偏好选择；暂无个性化推荐理由")
       TextButton(onClick={detail=!detail}){Text("了解产品")}
       if(detail) Text(listOf("description","ingredients","usage","source").joinToString("\n") { p.opt(it)?.toString().orEmpty() })
       val variants=p.getJSONArray("variants")
       (0 until variants.length()).map {variants.getJSONObject(it)}.forEach { variant->
        val chosen=u.flow.items[id]?.let {it.productId==p.getString("id") && it.variantId==variant.getString("id")}==true
        OutlinedButton(onClick={if(chosen) model.remove(id) else model.select(Item(id,p.getString("id"),variant.getString("id")))}) {Text((if(chosen) "已选 · " else "选择 · ")+variant.getString("name"))}
       }
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
 if(bitmap!=null) Image(bitmap!!.asImageBitmap(),"官方商品实图",Modifier.size(96.dp)) else if(failed) TextButton(onClick={attempt++}){Text("图片加载失败，重试")}
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
