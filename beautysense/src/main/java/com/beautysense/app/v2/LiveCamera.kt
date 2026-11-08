package com.beautysense.app.v2

import android.content.Context
import android.graphics.*
import android.util.Base64
import android.view.View
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beautysense.app.vision.toJpeg
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

data class LiveFrame(val bytes:ByteArray,val capturedAt:Long,val points:List<PointF>,val width:Int,val height:Int,val valid:Boolean,val hint:String)
class MeshView(context:Context):View(context) {
 var frame:LiveFrame?=null
 var effect:JSONObject?=null
 var texture:Bitmap?=null
 var strength=0.7f
 var original=false
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 init { setLayerType(LAYER_TYPE_SOFTWARE,null) }
 fun install(value:JSONObject?) {
  texture?.recycle(); texture=null; effect=value
  value?.optString("textureDataUrl")?.takeIf { it.contains(',') }?.let {
   val bytes=Base64.decode(it.substringAfter(','),Base64.DEFAULT); texture=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
  }; invalidate()
 }
 override fun onDraw(canvas:Canvas) {
  val f=frame ?: return; val e=effect ?: return; val bitmap=texture ?: return
  postInvalidateDelayed(150)
  if(!f.valid || System.currentTimeMillis()-f.capturedAt>500 || original || f.points.size!=478) return
  val source=e.optJSONArray("sourceLandmarks") ?: return
  val triangles=e.optJSONArray("triangles") ?: return
  if(source.length()!=478 || triangles.length()%3!=0) return
  val scale=min(width.toFloat()/f.width,height.toFloat()/f.height)
  val dx=(width-f.width*scale)/2; val dy=(height-f.height*scale)/2
  val vertices=FloatArray(triangles.length()*2); val uv=FloatArray(vertices.size)
  for(i in 0 until triangles.length()) {
   val index=triangles.getInt(i); if(index !in 0..477) return
   vertices[i*2]=dx+(1-f.points[index].x)*f.width*scale; vertices[i*2+1]=dy+f.points[index].y*f.height*scale
   uv[i*2]=source.getJSONArray(index).getDouble(0).toFloat()*bitmap.width
   uv[i*2+1]=source.getJSONArray(index).getDouble(1).toFloat()*bitmap.height
  }
  paint.shader=BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP); paint.alpha=(255*strength).toInt()
  canvas.drawVertices(Canvas.VertexMode.TRIANGLES,vertices.size,vertices,0,uv,0,null,0,null,0,0,paint)
 }
}

@Composable fun LiveCamera(modifier:Modifier,effect:JSONObject?,strength:Float,original:Boolean,hasAnalysis:Boolean,onFrame:(LiveFrame)->Unit,onDiscontinuity:()->Unit,onError:(String)->Unit) {
 val context=LocalContext.current; val owner=LocalLifecycleOwner.current
 val analyzed=rememberUpdatedState(hasAnalysis); val latestFrame=rememberUpdatedState(onFrame); val latestLost=rememberUpdatedState(onDiscontinuity); val latestError=rememberUpdatedState(onError)
 val preview=remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FIT_CENTER; implementationMode=PreviewView.ImplementationMode.COMPATIBLE } }
 val mesh=remember { MeshView(context) }
 AndroidView(factory={ android.widget.FrameLayout(context).apply { addView(preview); addView(mesh) } },modifier=modifier,update={ if(mesh.effect!==effect) mesh.install(effect); mesh.strength=strength; mesh.original=original; mesh.invalidate() })
 DisposableEffect(owner) {
  val executor=Executors.newSingleThreadExecutor(); val disposed=AtomicBoolean(false); var provider:ProcessCameraProvider?=null
  var face:FaceLandmarker?=null; var hand:HandLandmarker?=null
  val future=ProcessCameraProvider.getInstance(context)
  future.addListener({
   if(!disposed.get()) executor.execute { if(!disposed.get()) try {
    provider=future.get()
    face=FaceLandmarker.createFromOptions(context,FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()).setRunningMode(RunningMode.VIDEO).setNumFaces(2).setMinFaceDetectionConfidence(0.7f).setMinFacePresenceConfidence(0.7f).build())
    hand=HandLandmarker.createFromOptions(context,HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()).setRunningMode(RunningMode.VIDEO).setNumHands(2).build())
    val camera=Preview.Builder().build().also { it.surfaceProvider=preview.surfaceProvider }
    val analysis=ImageAnalysis.Builder().setTargetResolution(android.util.Size(480,640)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    var hadFace=false; var lastSeen=0L; var prior:PointF?=null; var lastTime=0L; var handFrames=0; var handsPresent=false
    analysis.setAnalyzer(executor) { image ->
     try {
      val now=android.os.SystemClock.uptimeMillis()
      if(now-lastTime>=33) {
       lastTime=now
       val bytes=image.toJpeg(mirrorHorizontally=false)
       if(bytes!=null) {
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size); val mp=BitmapImageBuilder(bitmap).build()
        val faces=face!!.detectForVideo(mp,now).faceLandmarks(); if(handFrames++ % 3==0) handsPresent=hand!!.detectForVideo(mp,now).landmarks().isNotEmpty()
        val points=faces.singleOrNull()?.map { PointF(it.x(),it.y()) } ?: emptyList()
        val center=points.getOrNull(1)
        val discontinuity=SessionSafety.endForTracking(analyzed.value,faces.size) || hadFace && (faces.size>1 || (center!=null && prior!=null && hypot(center.x-prior!!.x,center.y-prior!!.y)>0.16f) || now-lastSeen>700)
        if(discontinuity) { hadFace=false; prior=null; ContextCompat.getMainExecutor(context).execute { latestLost.value() } }
        if(center!=null && !discontinuity) { hadFace=true; lastSeen=now; prior=center }
        val complete=points.size==478 && points.all { it.x in 0.02f..0.98f && it.y in 0.02f..0.98f }
        var light=0L; var count=0
        for(y in 0 until bitmap.height step 16) for(x in 0 until bitmap.width step 16) { val p=bitmap.getPixel(x,y); light+=(Color.red(p)+Color.green(p)+Color.blue(p))/3; count++ }
        val bright=light/count.coerceAtLeast(1)
        val valid=complete && !handsPresent && bright in 35..235 && !discontinuity
        val hint=when { faces.isEmpty()->"请将完整面部放入椭圆内"; faces.size>1->"请保持仅一人入镜"; handsPresent->"请移开手部遮挡"; !complete->"请调整距离，完整露出面部"; bright<35->"光线偏暗，请调整位置"; bright>235->"光线过强，请调整位置"; else->"请正对镜头，点击确定" }
        val f=LiveFrame(bytes,System.currentTimeMillis(),points,bitmap.width,bitmap.height,valid,hint)
        ContextCompat.getMainExecutor(context).execute { if(!disposed.get()) { mesh.frame=f; mesh.invalidate(); latestFrame.value(f) } }
        mp.close(); bitmap.recycle()
       }
      }
     } catch(e:Exception) { ContextCompat.getMainExecutor(context).execute { latestError.value("跟踪不可用：${e.message}") } } finally { image.close() }
    }
    ContextCompat.getMainExecutor(context).execute { if(!disposed.get()) try { provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,camera,analysis) } catch(e:Exception) { latestError.value("无法打开前置摄像头：${e.message}") } }
   } catch(e:LinkageError) { latestError.value("此设备不支持原生面部跟踪，请使用 ARM Android 设备") } catch(e:Exception) { latestError.value("无法打开前置摄像头：${e.message}") }
   }
  },ContextCompat.getMainExecutor(context))
  onDispose { disposed.set(true); provider?.unbindAll(); executor.execute { face?.close(); hand?.close() }; executor.shutdown(); mesh.install(null) }
 }
}
