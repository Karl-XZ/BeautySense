package com.beautysense.app.wear

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.sqrt

class BoardClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("beautysense_board", Context.MODE_PRIVATE)

    fun address(): String = prefs.getString("address", "").orEmpty()

    fun configured(): Boolean = validAddress(address()) && token() != null

    fun save(address: String, token: String) {
        require(validAddress(address)) { "请输入设备在本地网络中的 IPv4 地址" }
        require(token.length in 24..128 && token.all { it.code in 33..126 }) { "设备令牌长度需要 24 至 128 位 ASCII 字符" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit()
            .putString("address", address)
            .putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_data", Base64.encodeToString(cipher.doFinal(token.toByteArray()), Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun status(): JSONObject = JSONObject(request("GET", "/v1/status").decodeToString()).also {
        check(it.optInt("protocol") == 1) { "设备协议版本不匹配" }
    }

    fun frame(): ByteArray = request("GET", "/v1/frame.jpg").also {
        check(it.size >= 4 && it[0] == 0xff.toByte() && it[1] == 0xd8.toByte()) { "设备画面格式不正确" }
    }

    fun motion(): Pair<Float, Float> {
        val response = JSONObject(request("GET", "/v1/imu").decodeToString())
        check(response.optInt("protocol") == 1)
        val accel = response.getJSONArray("accelG")
        val gyro = response.getJSONArray("gyroRadS")
        val acceleration = sqrt((0..2).sumOf { accel.getDouble(it) * accel.getDouble(it) }).toFloat()
        val angularVelocity = sqrt((0..2).sumOf { gyro.getDouble(it) * gyro.getDouble(it) }).toFloat()
        check(acceleration.isFinite() && angularVelocity.isFinite()) { "设备运动数据无效" }
        return acceleration to angularVelocity
    }

    fun microphone(durationMs: Int): ByteArray {
        require(durationMs in 250..10000)
        return request("GET", "/v1/mic.wav?durationMs=$durationMs")
    }

    fun haptic(side: String) {
        require(side in setOf("left", "right", "both"))
        request("POST", "/v1/haptic?side=$side", ByteArray(0))
    }

    fun playPcm(data: ByteArray) {
        require(data.isNotEmpty() && data.size <= 64_000 && data.size % 2 == 0)
        request("POST", "/v1/audio.pcm", data)
    }

    fun stopAudio() {
        request("POST", "/v1/audio/stop", ByteArray(0))
    }

    private fun request(method: String, path: String, body: ByteArray? = null): ByteArray {
        val currentAddress = address()
        require(validAddress(currentAddress)) { "设备地址无效" }
        val currentToken = token() ?: error("设备令牌未设置")
        val nonce = ByteArray(16).also(SecureRandom()::nextBytes).toHex()
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(body ?: ByteArray(0)).toHex()
        val signature = signature(currentToken, method, path, nonce, bodyHash)
        val connection = URL("http://$currentAddress:8787$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 2500
            connection.readTimeout = 4000
            connection.setRequestProperty("X-BS-Nonce", nonce)
            connection.setRequestProperty("X-BS-Body-SHA256", bodyHash)
            connection.setRequestProperty("X-BS-Signature", signature)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            check(connection.responseCode == 200) { "设备请求失败：HTTP ${connection.responseCode}" }
            connection.inputStream.use { stream ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(result.size() + count <= 1_000_000) { "设备返回内容过大" }
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun token(): String? = runCatching {
        val iv = Base64.decode(prefs.getString("token_iv", null), Base64.NO_WRAP)
        val encrypted = Base64.decode(prefs.getString("token_data", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).decodeToString()
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("beautysense_board_token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                "beautysense_board_token",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    companion object {
        internal fun signature(token: String, method: String, path: String, nonce: String, bodyHash: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal("$method\n$path\n$nonce\n$bodyHash".toByteArray(Charsets.UTF_8)).toHex()
        }

        private fun ByteArray.toHex(): String {
            val digits = "0123456789abcdef"
            return buildString(size * 2) {
                for (byte in this@toHex) {
                    append(digits[(byte.toInt() ushr 4) and 15])
                    append(digits[byte.toInt() and 15])
                }
            }
        }

        fun validAddress(address: String): Boolean {
            val parts = address.split('.')
            if (parts.size != 4 || parts.any {
                it.isEmpty() || it.length > 3 || (it.length > 1 && it[0] == '0') || it.any { c -> c !in '0'..'9' }
            }) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            return octets[0] == 10 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
        }
    }
}
