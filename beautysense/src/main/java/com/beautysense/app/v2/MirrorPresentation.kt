package com.beautysense.app.v2

import org.json.JSONArray
import org.json.JSONObject

data class MirrorMetric(val key:String,val label:String,val value:Float)
data class MirrorDetail(val epoch:Long,val title:String,val body:String,val product:JSONObject?=null,val picking:Boolean=false) {
 fun belongsTo(currentEpoch:Long)=epoch==currentEpoch
}
object MirrorPresentation {
 private val labels=linkedMapOf("redness" to "泛红","texture" to "纹理","pores" to "毛孔","blemishes" to "斑点","pigment" to "色素","oiliness" to "油光")
 private val number=Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
 fun metrics(input:JSONObject?):List<MirrorMetric> = labels.mapNotNull { (key,label)->
  val raw=input?.opt(key);val value=if(raw is JSONObject) raw.opt("value") else raw
  val n=when(value) {is Number->value.toDouble();is String->value.trim().takeIf {number.matches(it)}?.toDoubleOrNull();else->null}
  n?.takeIf {it.isFinite() && it in 0.0..100.0}?.let {MirrorMetric(key,label,it.toFloat())}
 }
 fun assistantPreview(value:String):String {
  val clean=value.replace(Regex("\\s+")," ").trim()
  return if(clean.codePointCount(0,clean.length)<=90) clean else clean.substring(0,clean.offsetByCodePoints(0,89))+"…"
 }
 fun summary(value:String?):String = compact(value.orEmpty(),20)
 fun tags(value:Any?):List<String> {
  val items=when(value) {is JSONArray->(0 until value.length()).map {value.opt(it)};is JSONObject->value.keys().asSequence().map {value.opt(it)}.toList();is String->listOf(value);else->emptyList()}
  return items.mapNotNull {when(it) {is String->it;is JSONObject->(it.opt("observation") as? String)?.takeIf {text->text.isNotBlank()} ?: (it.opt("name") as? String);else->null}}.filter {it.isNotBlank()}.take(3).map {compact(it,12)}
 }
 fun productName(value:String)=value.replace(Regex("(?i)^\\s*PROYA\\s*"),"").replace(Regex("^珀莱雅\\s*"),"").trim()
 private fun compact(value:String,limit:Int):String {val clean=value.replace(Regex("\\s+")," ").trim();return if(clean.codePointCount(0,clean.length)<=limit) clean else clean.substring(0,clean.offsetByCodePoints(0,limit-1))+"…"}
 /** User report contains only known observations; service diagnostics never enter this view. */
 fun analysisReport(analysis:JSONObject?):String {
  if(analysis==null) return ""
  val metricText=metrics(analysis.optJSONObject("skinMetrics")).joinToString("\n") {
   val number=if(it.value%1f==0f) it.value.toInt().toString() else it.value.toString()
   "${it.label}：$number"
  }
  return listOf(
   "概览" to (analysis.opt("summary") as? String).orEmpty(),
   "图像特征强度（0–100）" to metricText,
   "面部特点" to humanObservations(analysis.opt("faceFeatures")),
   "选妆建议" to humanObservations(analysis.opt("selectionAdvice"))
  ).filter {it.second.isNotBlank()}.joinToString("\n\n") {"${it.first}\n${it.second}"}
 }
 private fun humanObservations(value:Any?):String = when(value) {
  is String->value
  is JSONArray->(0 until value.length()).map {humanObservations(value.opt(it))}.filter {it.isNotBlank()}.joinToString("\n")
  is JSONObject->listOf("name","observation","description","meaning").mapNotNull {(value.opt(it) as? String)?.takeIf {text->text.isNotBlank()}}.joinToString(" · ")
  else->""
 }
 fun report(value:Any?):String = when(value) {
  is JSONArray->(0 until value.length()).joinToString("\n") {report(value.opt(it))}
  is JSONObject->value.keys().asSequence().filter {it !in listOf("id","explanationStatus")}.joinToString("\n") {key->"${labels[key] ?: mapOf("summary" to "概览","skinMetrics" to "图像特征强度（0–100）","selectionAdvice" to "选妆建议","faceFeatures" to "面部特点","name" to "特点","description" to "说明","observation" to "观察","value" to "数值","meaning" to "解读","unit" to "单位")[key] ?: key}：${report(value.opt(key))}"}
  null,JSONObject.NULL->""
  else->value.toString()
 }
}

internal data class ThumbnailBounds(val x:Int,val y:Int,val width:Int,val height:Int)

/** Bounds include every nontransparent pixel, including faint product edges and shadows. */
internal fun thumbnailAlphaBounds(pixels:IntArray,width:Int,height:Int):ThumbnailBounds? {
 if(width<=0 || height<=0 || width.toLong()*height>pixels.size) return null
 var left=width;var top=height;var right=-1;var bottom=-1
 for(y in 0 until height) for(x in 0 until width) {
  if(pixels[y*width+x] ushr 24!=0) {
   if(x<left) left=x
   if(x>right) right=x
   if(y<top) top=y
   if(y>bottom) bottom=y
  }
 }
 return if(right<left) null else ThumbnailBounds(left,top,right-left+1,bottom-top+1)
}
