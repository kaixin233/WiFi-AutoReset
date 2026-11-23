package com.kaixin233.wifiautoreset

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * 随身 WiFi（Marvell / LTE Wireless Router mifi 固件）的轻量控制客户端。
 * 固件使用 HTTP Digest 认证，所有操作走 /xml_action.cgi。
 */
object MifiClient {
    private const val BASE = "http://192.168.1.1"
    private const val USER = "admin"
    private const val PASSWORD = "admin"
    private const val URI = "/cgi/xml_action.cgi"

    private val counter = AtomicInteger(1)

    private data class Challenge(val realm: String, val nonce: String, val qop: String)

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
            conn.getHeaderField("WWW-Authenticate") ?: ""
        } finally {
            conn.disconnect()
        }
        val realm = Regex("realm=\"([^\"]+)\"").find(header)?.groupValues?.get(1) ?: "Highwmg"
        val nonce = Regex("nonce=\"([^\"]+)\"").find(header)?.groupValues?.get(1) ?: ""
        val qop = Regex("qop=\"?(\\w+)\"?").find(header)?.groupValues?.get(1) ?: "auth"
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

    /** 串行执行，防止固件侧 nonce 状态被并发请求打乱。 */
    @Synchronized
    private fun request(action: String, method: String, body: String?): String {
        val challenge = fetchChallenge()
        val file = if (action.startsWith("json_")) action else "json_$action"
        // 时间戳作为独立参数，不能拼进 file 值，否则等于请求了不存在的 action。
        val url = "$BASE/xml_action.cgi?method=${method.lowercase()}&module=duster" +
            "&file=$file&_t=${System.currentTimeMillis()}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", authorization(method, challenge))
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = try {
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
        return text
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
