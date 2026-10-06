package com.beautysense.app.network

import com.beautysense.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class ApiException(
    val code: String,
    override val message: String,
    val status: Int
) : Exception(message)

class BeautySenseApi(initialBaseUrl: String) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 120_000
    }

    private val baseUrl = AtomicReference(normalize(initialBaseUrl))

    fun updateBaseUrl(value: String) {
        baseUrl.set(normalize(value))
    }

    fun currentBaseUrl(): String = baseUrl.get()

    suspend fun get(path: String): JSONObject = request("GET", path, null)

    suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("${baseUrl.get()}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Origin", "https://openl.work")
            setRequestProperty("Referer", "https://openl.work/BeautySense/")
            if (BuildConfig.CLIENT_TOKEN.isNotBlank()) {
                setRequestProperty("X-BeautySense-Client", BuildConfig.CLIENT_TOKEN)
            }
            useCaches = false
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            val json = runCatching { if (text.isBlank()) JSONObject() else JSONObject(text) }
                .getOrElse {
                    throw ApiException("HTTP_$status", "云服务返回 HTTP $status，请检查连接和访问凭证。", status)
                }
            if (status !in 200..299 || json.optBoolean("ok", true).not()) {
                val error = json.optJSONObject("error")
                throw ApiException(
                    code = error?.optString("code", "HTTP_$status") ?: "HTTP_$status",
                    message = error?.optString("message", "服务暂时不可用。") ?: "服务暂时不可用。",
                    status = status
                )
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun normalize(value: String): String {
        val trimmed = value.trim().ifBlank { BuildConfig.DEFAULT_SERVER_URL }
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val host = uri?.host.orEmpty()
        val privateDebugHost = BuildConfig.DEBUG && (
            host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" ||
                host.startsWith("192.168.") || host.startsWith("10.")
            )
        require(uri != null && host.isNotBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.scheme == "https" || (uri.scheme == "http" && privateDebugHost))) {
            "云服务地址需要使用 HTTPS"
        }
        return trimmed.trimEnd('/')
    }
}
