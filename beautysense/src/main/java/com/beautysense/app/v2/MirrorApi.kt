package com.beautysense.app.v2

import com.beautysense.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MirrorApi {
 companion object { @Volatile var serverOverride:String?=null }
 var token:String=""
 val base=(serverOverride ?: BuildConfig.DEFAULT_SERVER_URL).trimEnd('/')
 suspend fun call(path:String,body:JSONObject?=null,method:String=if(body==null) "GET" else "POST"):JSONObject=withContext(Dispatchers.IO) {
  val connection=URL("$base/api/v2/$path").openConnection() as HttpURLConnection
  try {
   connection.requestMethod=method; connection.connectTimeout=15000; connection.readTimeout=60000
   if(token.isNotEmpty()) connection.setRequestProperty("Authorization","Bearer $token")
   if(body!=null) { connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json"); connection.outputStream.use { it.write(body.toString().toByteArray()) } }
   val code=connection.responseCode
   val result=JSONObject((if(code in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() })
   if(code !in 200..299 || !result.optBoolean("ok")) error(result.optJSONObject("error")?.optString("message") ?: "服务请求失败 ($code)")
   result
  } finally { connection.disconnect() }
 }
 suspend fun open() { token=call("sessions",JSONObject()).getJSONObject("session").getString("token") }
 suspend fun close() { if(token.isNotEmpty()) try { call("sessions",method="DELETE") } finally { token="" } }
}
