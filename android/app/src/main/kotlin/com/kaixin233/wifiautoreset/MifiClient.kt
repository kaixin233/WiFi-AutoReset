package com.kaixin233.wifiautoreset

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * 随身 WiFi（Marvell / LTE Wireless Router mifi 固件）的轻量控制客户端。
 *
 * 固件特征（2026-11 实机抓包确认）：
 * - /xml_action.cgi 是唯一有效端点，/cgi/xml_action.cgi 返回 404。
 * - 固件当前对读接口不强制校验 Authorization，但写接口保留标准 Digest 头以兼容。
 * - challenge 从 login.cgi 的 WWW-Authenticate 头获取。
 */
object MifiClient {
    private const val BASE = "http://192.168.1.1"
    private const val USER = "admin"
    private const val PASSWORD = "admin"
    /** Digest 签名与 Authorization 头里的 uri 必须与真实请求路径一致。 */
    private const val URI = "/xml_action.cgi"

    private val counter = AtomicInteger(1)

    private data class Challenge(val realm: String, val nonce: String, val qop: String)

    class ApiException(message: String, val httpCode: Int = -1) : IOException(message)

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** 每次请求独立获取 challenge，避免多个请求并发时互相覆盖 realm/nonce。 */
    private fun fetchChallenge(): Challenge {
        val conn = URL("$BASE/login.cgi").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val header = try {
            conn.getHeaderField("WWW-Authenticate").orEmpty()
        } finally {
            conn.disconnect()
        }
        val realm = Regex("realm=\"([^\"]+)\"").find(header)?.groupValues?.get(1) ?: "Highwmg"
        val nonce = Regex("nonce=\"([^\"]+)\"").find(header)?.groupValues?.get(1) ?: ""
        // 固件可能返回 qop="auth" 或 qop=auth，统一取出 token。
        val qop = Regex("qop=\"?([A-Za-z0-9]+)\"?").find(header)?.groupValues?.get(1) ?: "auth"
        return Challenge(realm, nonce, qop)
    }

    private fun authorization(method: String, challenge: Challenge): String {
        val nc = "%08x".format(counter.getAndIncrement())
        val cnonce = md5("${(0..100000).random()}${System.currentTimeMillis()}").take(16)
        val ha1 = md5("$USER:${challenge.realm}:$PASSWORD")
        val ha2 = md5("$method:$URI")
        val response = md5(
            "$ha1:${challenge.nonce}:$nc:$cnonce:${challenge.qop}:$ha2",
        )
        return "Digest username=\"$USER\", realm=\"${challenge.realm}\", " +
            "nonce=\"${challenge.nonce}\", uri=\"$URI\", response=\"$response\", " +
            "qop=${challenge.qop}, nc=$nc, cnonce=\"$cnonce\""
    }

    /**
     * 串行执行，防止固件侧 nonce 状态被并发请求打乱。
     * 失败时抛出带 HTTP 状态码与响应片段的异常，便于在界面/日志中定位。
     */
    @Synchronized
    private fun request(action: String, method: String, body: String?): String {
        val file = if (action.startsWith("json_")) action else "json_$action"
        val url = "$BASE$URI?method=${method.lowercase()}&module=duster" +
            "&file=$file&_t=${System.currentTimeMillis()}"

        var lastError: Exception? = null
        // 固件偶发无响应/连接复位，做一次重试。
        repeat(2) { attempt ->
            try {
                val challenge = fetchChallenge()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.setRequestProperty("Authorization", authorization(method, challenge))
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = try {
                    stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } finally {
                    conn.disconnect()
                }
                if (code !in 200..299) {
                    throw ApiException(
                        "$file HTTP $code：${text.take(120).ifBlank { "无响应内容" }}",
                        code,
                    )
                }
                if (text.isBlank()) {
                    throw ApiException("$file 返回空内容（HTTP $code）", code)
                }
                AppLog.appendGlobal(
                    LogEntry(
                        LogEntry.now(),
                        "API 请求",
                        if (attempt == 0) "成功" else "重试成功",
                        file,
                        "HTTP $code · ${text.take(200)}",
                    ),
                )
                lastError = null
                return text
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: ApiException("$file 请求失败")
    }

    fun deviceInfo(): String = request("homepage_info", "GET", null)

    /**
     * 只读实时状态：电量百分比、充电状态、蜂窝信号等级、RSSI、在线设备数、运行时长。
     * 对应固件首页的 json_status_info，不含任何写操作。
     */
    fun statusInfo(): String = request("status_info", "GET", null)

    /** 真·恢复出厂设置：固件中 lt_rm_btnResetFactory 对应的 json_device_restore_factory。 */
    fun factoryReset(): String = request("device_restore_factory", "GET", null)

    fun reboot(): String = request("device_restart", "GET", null)

    fun resetTraffic(): String = request("statistics_set", "POST", "{\"reset\":\"1\"}")

    fun reachable(): Boolean = try {
        val conn = URL(BASE).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.responseCode in 200..399
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        false
    }
}
