package com.beautysense.app.v2

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MirrorPresentationTest {
 @Test fun metricsAcceptOnlyKnownFiniteInRangeValues() {
  val input=JSONObject().put("redness",12).put("texture"," 23.5 ").put("pores",JSONObject().put("value",100)).put("blemishes",0).put("pigment",-1).put("oiliness","NaN").put("unknown",50)
  assertEquals(listOf("redness","texture","pores","blemishes"),MirrorPresentation.metrics(input).map {it.key})
  assertEquals(listOf(12f,23.5f,100f,0f),MirrorPresentation.metrics(input).map {it.value})
  assertEquals("泛红",MirrorPresentation.metrics(input).first().label)
 }
 @Test fun malformedMetricsRemainAbsent() {
  for(value in listOf<Any>(true,"12%","",101,"Infinity",JSONArray(),JSONObject().put("value",JSONObject().put("value",2)),JSONObject.NULL)) {
   assertTrue(MirrorPresentation.metrics(JSONObject().put("redness",value)).isEmpty())
  }
  assertTrue(MirrorPresentation.metrics(null).isEmpty())
 }
 @Test fun compactContentDoesNotInventFacts() {
  assertEquals("肌".repeat(19)+"…",MirrorPresentation.summary("肌".repeat(50)))
  assertEquals("🙂".repeat(19)+"…",MirrorPresentation.summary("🙂".repeat(50)))
  assertEquals("",MirrorPresentation.summary(null))
  assertEquals(listOf("鹅蛋脸","眉形自然","轮廓柔和"),MirrorPresentation.tags(JSONArray().put("鹅蛋脸").put(JSONObject().put("name","眉形自然").put("description","完整描述")).put("轮廓柔和").put("第四条")))
  assertEquals("红宝石面霜",MirrorPresentation.productName("PROYA 珀莱雅红宝石面霜"))
 }
 @Test fun catalogSourceAndObservedTagsArePreserved() {
  assertEquals(listOf("轮廓柔和"),MirrorPresentation.tags(JSONArray().put(JSONObject().put("name","脸型").put("observation","轮廓柔和"))))
  val source="https://www.proya.com/product"
  assertTrue(productDetail(MirrorUi(),JSONObject().put("name","官方产品").put("source",source)).contains(source))
 }
 @Test fun assistantPreviewLimitsCodepointsWithoutSplittingEmoji() {
  val short="已有完整回复"
  assertEquals(short,MirrorPresentation.assistantPreview(short))
  val long="🙂".repeat(100)
  val preview=MirrorPresentation.assistantPreview(long)
  assertEquals(90,preview.codePointCount(0,preview.length))
  assertEquals("🙂".repeat(89)+"…",preview)
  assertEquals(200,long.length)
 }
 @Test fun alphaBoundsTrimOnlyFullyTransparentMargins() {
  val pixels=IntArray(5*4)
  pixels[1*5+2]=0x01000000
  pixels[2*5+3]=0xffabcdef.toInt()
  assertEquals(ThumbnailBounds(2,1,2,2),thumbnailAlphaBounds(pixels,5,4))
  assertNull(thumbnailAlphaBounds(IntArray(20),5,4))
  assertEquals(ThumbnailBounds(0,0,5,4),thumbnailAlphaBounds(IntArray(20) {0xffffffff.toInt()},5,4))
  assertEquals(ThumbnailBounds(0,0,1,1),thumbnailAlphaBounds(intArrayOf(0x01000000),1,1))
 }
 @Test fun fullAnalysisReportAllowsOnlyHumanSectionsAndKnownNestedFields() {
  val analysis=JSONObject().put("summary","完整概览").put("skinMetrics",JSONObject().put("redness",12).put("pores",101).put("internalMetric",24))
   .put("faceFeatures",JSONArray().put(JSONObject().put("name","脸型").put("observation","完整面部观察").put("internalField","隐藏字段")))
   .put("selectionAdvice",JSONArray().put("完整选妆建议").put(JSONObject().put("debug","隐藏建议")))
   .put("model","隐藏模型").put("id","隐藏标识").put("quality",JSONObject().put("brightness",155).put("sharpness",90).put("skinCoverage",0.5)).put("warnings",JSONArray().put("隐藏调试警告"))
  assertEquals("概览\n完整概览\n\n图像特征强度（0–100）\n泛红：12\n\n面部特点\n脸型 · 完整面部观察\n\n选妆建议\n完整选妆建议",MirrorPresentation.analysisReport(analysis))
  assertEquals("",MirrorPresentation.analysisReport(null))
 }
 @Test fun detailOwnershipRejectsPreviousEpoch() {
  assertTrue(MirrorDetail(3,"报告","私密内容").belongsTo(3))
  assertFalse(MirrorDetail(3,"报告","私密内容").belongsTo(4))
 }
}

