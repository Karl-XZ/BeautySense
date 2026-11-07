package com.beautysense.app.v2

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class MirrorUi(val flow:MirrorState=MirrorState(),val message:String="",val analysis:JSONObject?=null,val catalog:JSONObject?=null,val recommendations:JSONArray=JSONArray(),val effect:JSONObject?=null,val reply:String="",val busy:Boolean=false,val original:Boolean=false,val intensity:Float=0.7f,val category:String?=null,val confirmReanalysis:Boolean=false)
class MirrorViewModel:ViewModel() {
 private val mutable=MutableStateFlow(MirrorUi()); val ui=mutable.asStateFlow()
 var stopAudio:()->Unit = {}
 private var api=MirrorApi(); private var frame:LiveFrame?=null; private var job:Job?=null; private var assistantJob:Job?=null; private var explanationJob:Job?=null; private var generationJobId:String?=null; private var generationApi:MirrorApi?=null; private val mainOwnership=OperationOwnership(); private val assistantOwnership=OperationOwnership()
 suspend fun transcribe(bytes:ByteArray,epoch:Long):String {
  check(mutable.value.flow.epoch==epoch)
  if(api.token.isEmpty()) api.open()
  val result=api.call("asr",JSONObject().put("audioDataUrl","data:audio/mp4;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)))
  check(mutable.value.flow.epoch==epoch)
  return result.optString("transcript")
 }
 suspend fun speechUrl(text:String,epoch:Long):String {
  check(mutable.value.flow.epoch==epoch)
  if(api.token.isEmpty()) api.open()
  val result=api.call("tts",JSONObject().put("text",text))
  check(mutable.value.flow.epoch==epoch)
  return result.getString("audioUrl")
 }
 fun changeServer(url:String) { MirrorApi.serverOverride=url; reset(true) }
 fun frame(value:LiveFrame) { frame=value }
 fun original(value:Boolean) { mutable.value=mutable.value.copy(original=value) }
 fun intensity(value:Float) { mutable.value=mutable.value.copy(intensity=value.coerceIn(0f,1f)) }
 fun message(text:String) { mutable.value=mutable.value.copy(message=text) }
 fun start() { mutable.value=mutable.value.copy(flow=mutable.value.flow.start()); run { if(api.token.isEmpty()) api.open(); val catalog=api.call("catalog").getJSONObject("catalog"); mutable.value=mutable.value.copy(catalog=catalog) } }
 private fun cancelMain() {
  mainOwnership.invalidate(); job?.cancel(); job=null
  val id=generationJobId; val ownerApi=generationApi; generationJobId=null; generationApi=null
  if(id!=null && ownerApi!=null) viewModelScope.launch { try { ownerApi.call("jobs/$id/cancel",JSONObject()); if(com.beautysense.app.BuildConfig.DEBUG) android.util.Log.i("V2Cancel","server_cancel_confirmed") } catch(_:Exception) {} }
 }
 private fun run(block:suspend ()->Unit) {
  cancelMain(); val owner=mainOwnership.begin(); mutable.value=mutable.value.copy(busy=true,message="")
  job=viewModelScope.launch { try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { if(mainOwnership.owns(owner)) message(e.message ?: "服务暂不可用，请重试") } finally { if(mainOwnership.owns(owner)) mutable.value=mutable.value.copy(busy=false) } }
 }
 fun cancelAssistant() { assistantOwnership.invalidate(); assistantJob?.cancel(); assistantJob=null }
 private fun assist(block:suspend ()->Unit) {
  assistantOwnership.begin(); assistantJob?.cancel()
  assistantJob=viewModelScope.launch { try {block()} catch(e:CancellationException) {throw e} catch(e:Exception) {message(e.message ?: "小妆暂不可用，请重试")} }
 }
 fun dismissReanalysis() { mutable.value=mutable.value.copy(confirmReanalysis=false) }
 private fun currentFrame():LiveFrame { val f=frame ?: error("请等待摄像头画面"); check(f.valid && System.currentTimeMillis()-f.capturedAt in 0..3000) { "请重新正对镜头并移开遮挡" }; return f }
 private fun body(f:LiveFrame)=JSONObject().put("frameDataUrl","data:image/jpeg;base64,"+Base64.encodeToString(f.bytes,Base64.NO_WRAP)).put("capturedAt",f.capturedAt)
 fun analyze() { if(!SessionSafety.canAnalyze(mutable.value.flow.stage,mutable.value.analysis!=null)) { mutable.value=mutable.value.copy(confirmReanalysis=true); return }; if(mutable.value.busy) return; run {
  val f=currentFrame(); mutable.value=mutable.value.copy(flow=mutable.value.flow.copy(stage=Stage.ANALYZE),effect=null)
  try { val a=api.call("analysis",body(f)).getJSONObject("analysis"); mutable.value=mutable.value.copy(analysis=a,flow=mutable.value.flow.copy(stage=Stage.RESULTS)) }
  catch(e:CancellationException) { throw e } catch(e:Exception) { mutable.value=mutable.value.copy(flow=mutable.value.flow.copy(stage=Stage.ALIGN)); throw e }
 } }
 fun retryExplanation() {
  if(explanationJob?.isActive==true) return
  val analysis=mutable.value.analysis ?: return
  val analysisId=analysis.optString("id").takeIf {it.isNotBlank()} ?: return
  val epoch=mutable.value.flow.epoch; val requestApi=api
  explanationJob?.cancel()
  explanationJob=viewModelScope.launch {
   try {
    val updated=requestApi.call("explanation",JSONObject().put("analysisId",analysisId)).getJSONObject("analysis")
    check(updated.optString("id")==analysisId) {"解读响应与当前分析不匹配"}
    if(mutable.value.flow.epoch==epoch && mutable.value.analysis?.optString("id")==analysisId) mutable.value=mutable.value.copy(analysis=updated,message="")
   } catch(e:CancellationException) {throw e}
   catch(e:Exception) {if(mutable.value.flow.epoch==epoch && mutable.value.analysis?.optString("id")==analysisId) message(e.message ?: "解读暂不可用，可重试或继续选品")}
  }
 }
 fun next() { run { val a=mutable.value.analysis ?: error("请先分析"); val r=api.call("recommendations",JSONObject().put("analysisId",a.getString("id"))).getJSONArray("recommendations"); mutable.value=mutable.value.copy(recommendations=r,flow=mutable.value.flow.copy(stage=Stage.SELECT)) } }
 fun select(item:Item) { if(mutable.value.analysis==null || mutable.value.flow.stage !in listOf(Stage.SELECT,Stage.GENERATE,Stage.LIVE)) { message("请先完成分析并进入选品"); return }; cancelMain(); mutable.value=mutable.value.copy(flow=mutable.value.flow.select(item),effect=null,busy=false) }
 fun remove(category:String) { cancelMain(); mutable.value=mutable.value.copy(flow=mutable.value.flow.remove(category),effect=null,busy=false) }
 fun edit() { cancelMain(); mutable.value=mutable.value.copy(busy=false,flow=mutable.value.flow.copy(stage=Stage.SELECT,revision=mutable.value.flow.revision+1)) }
 fun category(id:String) { if(mutable.value.analysis==null) {message("请先完成分析"); return}; val categories=mutable.value.catalog?.optJSONArray("categories") ?: return; if((0 until categories.length()).any { categories.getJSONObject(it).optString("id")==id }) { edit(); mutable.value=mutable.value.copy(category=id) } }
 fun clearEffect() { cancelMain(); mutable.value=mutable.value.copy(effect=null,busy=false,flow=mutable.value.flow.copy(stage=Stage.SELECT,revision=mutable.value.flow.revision+1)) }
 private fun items()=JSONArray().also { a->mutable.value.flow.items.values.forEach { a.put(JSONObject().put("productId",it.productId).put("variantId",it.variantId)) } }
 fun generate() { if(!mutable.value.flow.canGenerate || mutable.value.busy) return; run {
  val generationOwner=mainOwnership.current(); val f=currentFrame(); val snapshot=mutable.value.flow.copy(revision=mutable.value.flow.revision+1); val analysis=mutable.value.analysis ?: error("请先分析")
  mutable.value=mutable.value.copy(flow=snapshot.copy(stage=Stage.GENERATE),effect=null)
  try {
   val requestApi=api
   val requestBody=body(f).put("analysisId",analysis.getString("id")).put("selectionRevision",snapshot.revision).put("items",items())
   val id=withContext(NonCancellable) { requestApi.call("generate",requestBody).getJSONObject("job").getString("id") }
   if(!mainOwnership.owns(generationOwner) || !mutable.value.flow.accepts(snapshot.epoch,snapshot.revision) || mutable.value.flow.stage!=Stage.GENERATE) {
    withContext(NonCancellable) { try {requestApi.call("jobs/$id/cancel",JSONObject())} catch(_:Exception) {} }; return@run
   }
   generationJobId=id; generationApi=requestApi
   generationDeadline(90000,
    isCurrent={mainOwnership.owns(generationOwner) && mutable.value.flow.accepts(snapshot.epoch,snapshot.revision)},
    onTimeout={
     cancelMain()
     mutable.value=mutable.value.copy(busy=false,message="生成超时，请重试",flow=mutable.value.flow.copy(stage=Stage.SELECT))
    }
   ) { while(true) { delay(1000); val result=api.call("jobs/$id").getJSONObject("job"); if(!mutable.value.flow.accepts(snapshot.epoch,snapshot.revision)) return@generationDeadline
    when(result.getString("status")) { "completed"->{ generationJobId=null;generationApi=null; check(result.getLong("selectionRevision")==snapshot.revision); mutable.value=mutable.value.copy(effect=result.getJSONObject("effect"),flow=mutable.value.flow.copy(stage=Stage.LIVE)); return@generationDeadline }; "failed"->{generationJobId=null;generationApi=null; error(result.opt("error")?.toString() ?: "生成失败，请重试") } }
   } }
  } catch(e:CancellationException) { throw e } catch(e:Exception) { if(mainOwnership.owns(generationOwner) && mutable.value.flow.accepts(snapshot.epoch,snapshot.revision)) mutable.value=mutable.value.copy(flow=mutable.value.flow.copy(stage=Stage.SELECT)); throw e }
 } }
 fun reset(exit:Boolean=false) {
  stopAudio()
  cancelMain(); assistantOwnership.invalidate(); assistantJob?.cancel(); explanationJob?.cancel(); frame=null; val old=api; api=MirrorApi(); mutable.value=MirrorUi(flow=mutable.value.flow.reset().copy(stage=if(exit) Stage.HOME else Stage.ALIGN))
  viewModelScope.launch { try { old.close() } catch(_:Exception) {} }
  if(!exit) start()
 }
 fun suspendCamera() { stopAudio(); reset(true) }
 fun dialogue(text:String) { if(text.isBlank()) return; assist {
  if(api.token.isEmpty()) api.open()
  val assistantOwner=assistantOwnership.begin(); val u=mutable.value; val response=api.call("dialogue",JSONObject().put("text",text).put("stage",u.flow.stage.name.lowercase()).put("analysisId",u.analysis?.optString("id")).put("items",items()).put("visibleProductIds",JSONArray().also { ids->for(i in 0 until u.recommendations.length()) ids.put(u.recommendations.getJSONObject(i).getString("productId")) }))
  if(!assistantOwnership.owns(assistantOwner) || !mutable.value.flow.accepts(u.flow.epoch,u.flow.revision) || mutable.value.flow.stage!=u.flow.stage) return@assist
  mutable.value=mutable.value.copy(reply=response.getString("reply"))
  val a=response.optJSONObject("action") ?: return@assist
  // Actions use the same entry points as touch; unknown actions never guess a product.
  viewModelScope.launch { delay(10); if(!assistantOwnership.owns(assistantOwner) || !mutable.value.flow.accepts(u.flow.epoch,u.flow.revision) || mutable.value.flow.stage!=u.flow.stage) return@launch; when(a.optString("type")) {
   "analyze","confirm"->analyze(); "next","recommend"->next(); "generate"->generate(); "clear"->clearEffect(); "start"->start(); "exit"->reset(true)
   "select"->{ val ps=mutable.value.catalog?.optJSONArray("products") ?: return@launch; for(i in 0 until ps.length()) { val p=ps.getJSONObject(i); if(p.getString("id")==a.optString("productId")) { val vs=p.getJSONArray("variants"); for(j in 0 until vs.length()) if(vs.getJSONObject(j).getString("id")==a.optString("variantId")) select(Item(p.getString("categoryId"),p.getString("id"),a.getString("variantId"))) } } }
   "category"->category(a.optString("categoryId")); "products"->{ if(mutable.value.analysis==null) message("请先完成分析") else if(mutable.value.recommendations.length()==0) next() else clearEffect() }
   "remove"->{ val category=a.optString("categoryId").ifBlank { mutable.value.flow.items.values.find { it.productId==a.optString("productId") }?.categoryId.orEmpty() }; if(category.isNotBlank()) remove(category) }
   "original"->original(a.optBoolean("value",true)); "intensity"->intensity(a.optDouble("value",0.7).toFloat())
  } }
 } }
 override fun onCleared() { stopAudio(); frame=null; val old=api; CoroutineScope(Dispatchers.IO).launch { try { old.close() } catch(_:Exception) {} } }
}
