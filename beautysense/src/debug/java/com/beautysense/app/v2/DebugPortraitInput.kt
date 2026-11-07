package com.beautysense.app.v2

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Debug only: adb run-as supplies this one fixed synthetic fixture; no arbitrary path is accepted. */
object DebugPortraitInput {
 fun load(context:Context,intent:Intent):Bitmap? {
  if(!intent.getBooleanExtra("synthetic_portrait",false)) return null
  val fixed=File(context.filesDir,"v2-test/person-a.png")
  check(fixed.isFile && fixed.length()<=8*1024*1024) { "缺少app私有合成测试图" }
  return BitmapFactory.decodeFile(fixed.absolutePath) ?: error("合成测试图无效")
 }
 @Composable fun Preview(bitmap:Bitmap,modifier:Modifier,effect:JSONObject?,strength:Float,original:Boolean,onFrame:(LiveFrame)->Unit,onError:(String)->Unit) {
  val context=LocalContext.current; val callback=rememberUpdatedState(onFrame)
  val mesh=remember { MeshView(context) }
  val bytes=remember(bitmap) { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG,92,it) }.toByteArray() }
  AndroidView(factory={FrameLayout(context).apply {
   addView(ImageView(context).apply { setImageBitmap(bitmap); scaleType=ImageView.ScaleType.FIT_CENTER; scaleX=-1f },FrameLayout.LayoutParams(-1,-1))
   addView(mesh,FrameLayout.LayoutParams(-1,-1))
  }},modifier=modifier,update={ if(mesh.effect!==effect) mesh.install(effect); mesh.strength=strength; mesh.original=original; mesh.invalidate() })
  LaunchedEffect(bitmap) {
   try {
    val points=withContext(Dispatchers.Default) {
     FaceLandmarker.createFromOptions(context,FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()).setNumFaces(2).build()).use { tracker->
      val detectionBitmap=bitmap.copy(Bitmap.Config.ARGB_8888,false)
      val image=BitmapImageBuilder(detectionBitmap).build()
      try { tracker.detect(image).faceLandmarks().single().map { PointF(it.x(),it.y()) }.also { check(it.size==478) } } finally { image.close() }
     }
    }
    while(true) { val frame=LiveFrame(bytes,System.currentTimeMillis(),points,bitmap.width,bitmap.height,true,"测试图输入 · 真实478点检测"); mesh.frame=frame; mesh.invalidate(); callback.value(frame); delay(200) }
   } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(e:Exception) { onError("测试图检测失败：${e.message}") }
  }
  DisposableEffect(mesh) { onDispose {mesh.install(null)} }
 }
}
