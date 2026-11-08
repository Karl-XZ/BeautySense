package com.beautysense.app

import android.Manifest
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.camera.view.PreviewView
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.beautysense.app.v2.MeshView
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class MirrorModeTest {
 private fun find(root:View,type:Class<out View>):View? {
  if(type.isInstance(root)) return root
  if(root is ViewGroup) for(i in 0 until root.childCount) find(root.getChildAt(i),type)?.let {return it}
  return null
 }
 @Test fun frontCameraStreamsAnalyzedFramesAndStopsOnExit() {
  val inst=InstrumentationRegistry.getInstrumentation()
  inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,Manifest.permission.CAMERA)
  inst.uiAutomation.executeShellCommand("am start -n com.beautysense.app/.MainActivity").use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
  fun findText(root:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo? {
   if(root==null) return null
   if(root.text?.toString()==text && root.packageName?.toString()==inst.targetContext.packageName) return root
   for(i in 0 until root.childCount) findText(root.getChild(i),text)?.let {return it}
   return null
  }
  fun click(text:String) {
   val until=android.os.SystemClock.elapsedRealtime()+10_000
   while(android.os.SystemClock.elapsedRealtime()<until) {
    val node=findText(inst.uiAutomation.rootInActiveWindow,text)
    if(node!=null) {var target:AccessibilityNodeInfo=node;while(!target.isClickable) {target=target.parent ?: break};if(target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return}
    Thread.sleep(100)
   }
   error("Missing action: "+text)
  }
  val activity=AtomicReference<MainActivity>()
  val until=android.os.SystemClock.elapsedRealtime()+10_000
  while(activity.get()==null && android.os.SystemClock.elapsedRealtime()<until) {inst.runOnMainSync {activity.set(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull())};Thread.sleep(100)}
  assertTrue("MainActivity resumed",activity.get()!=null)
  click("开始")
  var streaming=false;var stamp=0L;var frameWidth=0;var frameHeight=0;var faces=0;var delivered=0L;var rendered=0L
  fun inspect() {inst.runOnMainSync {
   val root=activity.get().window.decorView
   streaming=(find(root,PreviewView::class.java) as? PreviewView)?.previewStreamState?.value==PreviewView.StreamState.STREAMING
   val mesh=find(root,MeshView::class.java) as? MeshView
   delivered=mesh?.deliveredFrames ?: 0L;rendered=mesh?.renderedFrames ?: 0L
   val frame=mesh?.frame
   stamp=frame?.capturedAt ?: 0L;frameWidth=frame?.width ?: 0;frameHeight=frame?.height ?: 0;faces=if(frame?.points?.size==478) 1 else 0
  }}
  val deadline=android.os.SystemClock.elapsedRealtime()+30_000
  while(android.os.SystemClock.elapsedRealtime()<deadline) {inspect();if(streaming && stamp>0) break;Thread.sleep(50)}
  assertTrue("Actual front preview streams",streaming);assertTrue("Actual camera frames are analyzed",stamp>0)
  val cameraInfo=CameraSelector.DEFAULT_FRONT_CAMERA.filter(ProcessCameraProvider.getInstance(inst.targetContext).get().availableCameraInfos).first()
  assertTrue("Underlying front camera is open",cameraInfo.cameraState.value?.type==CameraState.Type.OPEN)
  val seconds=InstrumentationRegistry.getArguments().getString("sampleSeconds")?.toIntOrNull()?.coerceIn(10,900) ?: 10
  val firstCount=delivered;val sampleStart=android.os.SystemClock.elapsedRealtime();val samples=mutableListOf<Long>();var faceFrames=0;val stop=sampleStart+seconds*1000L
  var maxAge=0L
  while(android.os.SystemClock.elapsedRealtime()<stop) {
   inspect()
   val age=System.currentTimeMillis()-stamp;maxAge=maxOf(maxAge,age)
   assertTrue("Analyzed frames must keep updating throughout the run",streaming && age in 0..2_000)
   if(stamp>0 && samples.lastOrNull()!=stamp) {samples.add(stamp);faceFrames+=faces}
   Thread.sleep(20)
  }
  val exactFps=(delivered-firstCount)*1000.0/(android.os.SystemClock.elapsedRealtime()-sampleStart)
  assertTrue("Camera continues delivering throughout the measurement",streaming && delivered-firstCount>=seconds*3)
  inst.sendStatus(0,android.os.Bundle().apply {putString("maxAnalyzedFrameAgeMs",maxAge.toString());putString("deliveredAnalysisFps",exactFps.toString());putString("deliveredFrames",(delivered-firstCount).toString());putString("renderedFrames",rendered.toString());putString("cameraFrames",samples.size.toString());putString("analysisFps",((samples.size-1)*1000.0/(samples.last()-samples.first())).toString());putString("frameSize","$frameWidth x $frameHeight");putString("faceFrames",faceFrames.toString())})
  if(InstrumentationRegistry.getArguments().getString("saveCameraProbe")=="true") inst.runOnMainSync {
   val root=activity.get().window.decorView
   (find(root,MeshView::class.java) as? MeshView)?.frame?.bytes?.let {inst.targetContext.cacheDir.resolve("v2-camera-probe.jpg").writeBytes(it)}
   (find(root,PreviewView::class.java) as? PreviewView)?.bitmap?.let {bitmap->inst.targetContext.cacheDir.resolve("v2-preview-probe.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
  }
  // Lower-pane motion must never replace the live camera or interrupt its analyzer.
  val previewBefore=AtomicReference<View>();val meshBefore=AtomicReference<View>()
  inst.runOnMainSync {
   previewBefore.set(find(activity.get().window.decorView,PreviewView::class.java))
   meshBefore.set(find(activity.get().window.decorView,MeshView::class.java))
  }
  repeat(3) {
   for(action in listOf("小妆","关闭")) {
    val before=delivered;click(action);inspect()
    inst.runOnMainSync {
     assertTrue("Pane changes preserve the same camera view",previewBefore.get()===find(activity.get().window.decorView,PreviewView::class.java))
     assertTrue("Pane changes preserve the same mesh view",meshBefore.get()===find(activity.get().window.decorView,MeshView::class.java))
    }
    assertTrue("Camera stays open during pane transitions",streaming && cameraInfo.cameraState.value?.type==CameraState.Type.OPEN)
    assertTrue("Camera analyzer survives pane transitions",delivered>=before && System.currentTimeMillis()-stamp in 0..2_000)
   }
  }
  inst.sendStatus(0,android.os.Bundle().apply {putString("panelCameraContinuity","passed")})
  click("退出")
  val end=android.os.SystemClock.elapsedRealtime()+10_000
  while(android.os.SystemClock.elapsedRealtime()<end) {inspect();if(!streaming && stamp==0L && cameraInfo.cameraState.value?.type==CameraState.Type.CLOSED) break;Thread.sleep(50)}
  assertTrue("Camera closes when leaving",!streaming && stamp==0L)
  assertTrue("Underlying front camera is released",cameraInfo.cameraState.value?.type==CameraState.Type.CLOSED)
 }
}
