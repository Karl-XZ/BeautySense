package com.beautysense.app.v2

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Recording is private cache data, deleted on explicit submit, cancel, exit or timeout. */
class CloudVoice(private val context:Context) {
 private var recorder:MediaRecorder?=null
 private var file:File?=null
 private var player:MediaPlayer?=null
 var generation=0L; private set
 fun start(onTimeout:()->Unit) {
  cancel()
  val target=File.createTempFile("v2-voice-",".m4a",context.cacheDir)
  file=target
  try {
   recorder=(if(Build.VERSION.SDK_INT>=31) MediaRecorder(context) else MediaRecorder()).apply {
    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioSamplingRate(16000); setAudioEncodingBitRate(64000)
    setOutputFile(target.absolutePath); setMaxDuration(15000)
    setOnInfoListener { _,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) { cancel(); onTimeout() } }
    prepare(); start()
   }
  } catch(e:Exception) { cancel(); throw e }
 }
 fun finish():ByteArray {
  val target=file ?: error("请先开始录音")
  try { recorder?.stop(); recorder?.release(); recorder=null; return target.readBytes().also { require(it.size>=600) {"录音太短，请重试"} } }
  finally { recorder?.release(); recorder=null; target.delete(); file=null }
 }
 fun play(url:String,expected:Long) {
  if(generation!=expected) return
  player?.release()
  player=MediaPlayer().apply { setDataSource(url); setOnPreparedListener { if(generation==expected) it.start() else it.release() }; setOnCompletionListener { it.release(); if(player===it) player=null }; setOnErrorListener { p,_,_->p.release(); if(player===p) player=null; true }; prepareAsync() }
 }
 fun cancel() {
  generation++
  recorder?.runCatching { stop() }; recorder?.release(); recorder=null; file?.delete(); file=null
  player?.runCatching { stop() }; player?.release(); player=null
 }
}
