package com.beautysense.app.v2

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private val Ink=Color(0xff302a2d)
private val Rose=Color(0xff865364)

@Composable internal fun CompactReport(analysis:JSONObject?,restoreFocus:Boolean=false,restored:()->Unit={},open:()->Unit) {
 val reportFocus=remember {FocusRequester()}
 LaunchedEffect(restoreFocus) {if(restoreFocus) {reportFocus.requestFocus();restored()}}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
  Text("肌肤 · 面部",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
  TextButton(onClick=open,contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.focusRequester(reportFocus).heightIn(min=48.dp).testTag("report-detail")){Text("完整报告",fontSize=12.sp,lineHeight=16.sp)}
 }
 val summary=MirrorPresentation.summary(analysis?.opt("summary") as? String)
 if(summary.isNotEmpty()) Text(summary,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp,lineHeight=18.sp)
 Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
  MirrorPresentation.tags(analysis?.opt("faceFeatures")).forEach {Text(it,Modifier.background(Color(0xffeee6e2),RoundedCornerShape(12.dp)).padding(horizontal=9.dp,vertical=4.dp),fontSize=11.sp,lineHeight=14.sp)}
 }
 Text("图像特征 · 0–100",fontSize=11.sp,lineHeight=13.sp,color=Color.Gray)
 val metrics=MirrorPresentation.metrics(analysis?.optJSONObject("skinMetrics"))
 metrics.chunked(2).forEach {pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {pair.forEach {m->Column(Modifier.weight(1f).padding(vertical=3.dp).testTag("metric-${m.key}")) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(m.label,fontSize=11.sp,lineHeight=14.sp);Text(if(m.value%1f==0f) m.value.toInt().toString() else m.value.toString(),fontSize=11.sp,lineHeight=14.sp)}
  Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xffe6dcda))) {Box(Modifier.fillMaxWidth(m.value/100f).fillMaxHeight().background(Rose))}
 }};if(pair.size==1) Spacer(Modifier.weight(1f))}}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun CompactCatalog(u:MirrorUi,model:MirrorViewModel,restoreFocus:String?=null,restored:()->Unit={},open:(JSONObject,Boolean)->Unit) {
 val catalog=u.catalog ?: return
 val categories=catalog.optJSONArray("categories") ?: return
 val products=catalog.optJSONArray("products") ?: return
 val visible=(0 until products.length()).mapNotNull {products.optJSONObject(it)}.filter {it.optString("brand")=="PROYA"}
 val groups=(0 until categories.length()).mapNotNull {categories.optJSONObject(it)}.filter {c->visible.any {it.optString("categoryId")==c.optString("id")}}
 val active=u.category ?: groups.firstOrNull()?.optString("id")
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
  groups.forEach {c->val id=c.optString("id");val selected=active==id
   Box(Modifier.heightIn(min=48.dp).widthIn(min=48.dp).selectable(selected=selected,role=Role.Tab,onClick={model.category(id)}).testTag("category-$id"),contentAlignment=Alignment.Center) {
    Text(c.optString("name"),Modifier.background(if(selected) Ink else Color(0xffeee6e2),RoundedCornerShape(18.dp)).padding(horizontal=14.dp,vertical=9.dp),color=if(selected) Color.White else Ink,fontSize=12.sp)
   }
  }
 }
 Spacer(Modifier.height(4.dp))
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("product-rail"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
  visible.filter {it.optString("categoryId")==active}.forEach {p->key(p.optString("id")) {
   val productFocus=remember {FocusRequester()}
   val id=p.optString("id");val category=p.optString("categoryId");val chosen=u.flow.items[category]?.productId==id
   LaunchedEffect(restoreFocus,id) {if(restoreFocus==id) {productFocus.requestFocus();restored()}}
   Box(Modifier.size(width=96.dp,height=102.dp)) {
    Column(Modifier.fillMaxSize().focusRequester(productFocus).border(if(chosen) 2.dp else 1.dp,if(chosen) Rose else Color(0xffe7ddda),RoundedCornerShape(12.dp)).background(Color.White,RoundedCornerShape(12.dp)).semantics {selected=chosen;contentDescription=p.optString("name");stateDescription=if(chosen) "已选" else "未选"}.combinedClickable(role=Role.Button,onLongClickLabel="查看产品详情",onClick={
     val variants=p.optJSONArray("variants")
     if(variants?.length()==1) {if(chosen) model.remove(category) else model.select(Item(category,id,variants.getJSONObject(0).getString("id")))} else open(p,true)
    },onLongClick={open(p,false)}).padding(6.dp).testTag("product-$id"),horizontalAlignment=Alignment.CenterHorizontally) {
     Box {ProductThumbnail(p.optString("image"));if(chosen) Text("✓",Modifier.align(Alignment.TopEnd).background(Rose,RoundedCornerShape(10.dp)).padding(horizontal=4.dp),color=Color.White,fontSize=12.sp)}
     Text(MirrorPresentation.productName(p.optString("name")),fontSize=11.sp,lineHeight=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth().height(26.dp).padding(end=28.dp))
    }
    Box(Modifier.align(Alignment.BottomEnd).size(48.dp).clickable(role=Role.Button,onClickLabel="查看产品详情",onClick={open(p,false)}).semantics {contentDescription="${p.optString("name")}，查看详情"}.testTag("detail-$id"),contentAlignment=Alignment.BottomEnd) {
     Text("详情",Modifier.padding(end=7.dp,bottom=9.dp),fontSize=11.sp,lineHeight=13.sp,color=Rose)
    }
   }
  }}
 }
}

@Composable internal fun MirrorDetailPane(detail:MirrorDetail,u:MirrorUi,model:MirrorViewModel,close:()->Unit) {
 val closeFocus=remember {FocusRequester()}
 LaunchedEffect(detail) {closeFocus.requestFocus()}
 BackHandler(onBack=close)
 Column(Modifier.fillMaxSize().testTag("detail-sheet").padding(horizontal=16.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(detail.title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium);TextButton(onClick=close,modifier=Modifier.focusRequester(closeFocus).testTag("detail-close")){Text("关闭")}}
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
   Text(detail.body,style=MaterialTheme.typography.bodyMedium)
   if(detail.picking) detail.product?.let {p->
    Text("选择规格",style=MaterialTheme.typography.titleMedium)
    val variants=p.optJSONArray("variants")
    if(variants!=null) for(i in 0 until variants.length()) {
     val v=variants.getJSONObject(i);val category=p.optString("categoryId");val chosen=u.flow.items[category]?.let {it.productId==p.optString("id") && it.variantId==v.optString("id")}==true
     OutlinedButton(onClick={if(model.ui.value.flow.epoch==detail.epoch) {if(chosen) model.remove(category) else model.select(Item(category,p.getString("id"),v.getString("id")));close()}},modifier=Modifier.fillMaxWidth()) {Text((if(chosen) "✓ " else "")+v.optString("name"))}
    }
   }
   Spacer(Modifier.height(12.dp))
  }
 }
}

internal fun productDetail(u:MirrorUi,p:JSONObject):String {
 val id=p.optString("id")
 val reason=(0 until u.recommendations.length()).mapNotNull {u.recommendations.optJSONObject(it)}.firstOrNull {it.optString("productId")==id}?.optString("reason").orEmpty()
 return listOf(p.optString("name"),reason,p.optString("description"),p.opt("ingredients")?.takeIf {it!=JSONObject.NULL}?.let {"成分：${MirrorPresentation.report(it)}"}.orEmpty(),p.optString("usage"),p.optString("source"),p.optString("sourceUrl"),p.optString("officialUrl")).filter {it.isNotBlank()}.joinToString("\n\n")
}

@Composable private fun ProductThumbnail(path:String) {
 var bitmap by remember(path) {mutableStateOf<android.graphics.Bitmap?>(null)}
 var attempt by remember(path) {mutableStateOf(0)};var failed by remember(path) {mutableStateOf(false)}
 LaunchedEffect(path,attempt) {
  val result=withContext(Dispatchers.IO) {runCatching {val url=if(path.startsWith("http://")||path.startsWith("https://")) path else MirrorApi().base+"/"+path.trimStart('/');URL(url).openConnection().apply {connectTimeout=10000;readTimeout=10000}.getInputStream().use {BitmapFactory.decodeStream(it)?.let(::trimThumbnailTransparency)}}.getOrNull()}
  bitmap=result;failed=result==null
 }
 Box(Modifier.size(64.dp),contentAlignment=Alignment.Center) {if(bitmap!=null) Image(bitmap!!.asImageBitmap(),"官方商品实图",Modifier.fillMaxSize()) else if(failed) Text("重试图片",Modifier.clickable {attempt++}.padding(4.dp),fontSize=11.sp) else Text("…",color=Color.Gray)}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun CompactAction(label:String,enabled:Boolean=true,modifier:Modifier=Modifier,help:()->Unit,click:()->Unit) {
 Box(modifier.background(if(enabled) Rose else Color(0xffcbbfc1),RoundedCornerShape(13.dp)).combinedClickable(role=Role.Button,enabled=enabled,onClick=click,onLongClick=help).padding(horizontal=12.dp),contentAlignment=Alignment.Center) {Text(label,color=Color.White,fontSize=14.sp)}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun CompactReply(reply:String,restoreFocus:Boolean,restored:()->Unit,open:()->Unit) {
 val replyFocus=remember {FocusRequester()}
 LaunchedEffect(restoreFocus) {if(restoreFocus && reply.isNotBlank()) {replyFocus.requestFocus();restored()}}
 Text(if(reply.isBlank()) "可以问我分析或选品。" else MirrorPresentation.assistantPreview(reply),
  modifier=Modifier.testTag("assistant-reply").combinedClickable(enabled=reply.isNotBlank(),onClick=open,onLongClick=open),maxLines=3,overflow=TextOverflow.Ellipsis)
 if(reply.isNotBlank()) TextButton(onClick=open,modifier=Modifier.focusRequester(replyFocus).heightIn(min=48.dp).testTag("assistant-full"),contentPadding=PaddingValues(0.dp)) {Text("查看全文",fontSize=12.sp)}
}

private fun trimThumbnailTransparency(bitmap:android.graphics.Bitmap):android.graphics.Bitmap {
 if(!bitmap.hasAlpha()) return bitmap
 val pixels=IntArray(bitmap.width*bitmap.height)
 bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
 val bounds=thumbnailAlphaBounds(pixels,bitmap.width,bitmap.height) ?: return bitmap
 if(bounds.x==0 && bounds.y==0 && bounds.width==bitmap.width && bounds.height==bitmap.height) return bitmap
 return android.graphics.Bitmap.createBitmap(bitmap,bounds.x,bounds.y,bounds.width,bounds.height)
}
