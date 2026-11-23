package com.kaixin233.wifiautoreset

import android.os.Handler
import android.os.Looper
import android.view.View
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/** 简写 findViewById。 */
internal fun <T : View> View.find(id: Int): T = findViewById(id)

/** 通用工具：线程切换、固件返回解析、显示格式化。 */
object Utils {
    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    /** 后台执行 work，完成后回主线程回调；异常统一传回，不崩溃。 */
    fun asyncUi(work: () -> Unit, ui: (Exception?) -> Unit) {
        io.execute {
            var error: Exception? = null
            try {
                work()
            } catch (e: Exception) {
                error = e
            }
            main.post { ui(error) }
        }
    }

    /** 后台执行并返回结果，完成后回主线程。value 与 error 必有一个为 null。 */
    fun <T> async(work: () -> T, ui: (T?, Exception?) -> Unit) {
        io.execute {
            var value: T? = null
            var error: Exception? = null
            try {
                value = work()
            } catch (e: Exception) {
                error = e
            }
            main.post { ui(value, error) }
        }
    }

    fun jsonObject(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    fun formatSpeed(bytes: Long): String = when {
        bytes <= 0 -> "0 B/s"
        bytes < 1024 -> "$bytes B/s"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB/s", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.2f MB/s", bytes / (1024.0 * 1024.0))
    }

    /** 固件返回英文运营商名，这里映射为国内常用中文名。 */
    fun localizeCarrier(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return "--"
        if (trimmed.any { it.code in 0x4E00..0x9FFF }) return trimmed
        val normalized = trimmed.uppercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
        return when (normalized) {
            "CHN UNICOM", "CHINA UNICOM", "UNICOM", "CU" -> "中国联通"
            "CHN MOBILE", "CHINA MOBILE", "CHINA MOBILE COMMUNICATIONS", "CMCC", "MOBILE" -> "中国移动"
            "CHN TELECOM", "CHINA TELECOM", "TELECOM", "CT" -> "中国电信"
            else -> trimmed
        }
    }

    /** 固件的 SSID 是 UTF-16 code unit 的 4 位十六进制拼接。 */
    fun decodeUtf16Hex(hex: String?): String {
        if (hex.isNullOrEmpty()) return ""
        val chars = mutableListOf<Char>()
        var i = 0
        while (i + 3 < hex.length) {
            val value = hex.substring(i, i + 4).toIntOrNull(16)
            if (value != null) chars.add(value.toChar())
            i += 4
        }
        return chars.joinToString("")
    }

    /** 按 Unix 纪元对齐到周期起点后 5 秒，与 WorkManager 排程严格一致。 */
    fun nextRunMillis(intervalMinutes: Int, now: Long = System.currentTimeMillis()): Long {
        val slot = intervalMinutes * 60_000L
        return ((now / slot) + 1) * slot + 5_000L
    }

    fun countdownText(intervalMinutes: Int, now: Long = System.currentTimeMillis()): String {
        val diff = nextRunMillis(intervalMinutes, now) - now
        if (diff <= 0) return "即将执行"
        val hours = diff / 3_600_000
        val minutes = (diff % 3_600_000) / 60_000
        val seconds = (diff % 60_000) / 1000
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun intervalLabel(minutes: Int): String = when {
        minutes == 1440 -> "每天"
        minutes >= 60 && minutes % 60 == 0 -> "每 ${minutes / 60} 小时"
        else -> "每 $minutes 分钟"
    }
}

/** 固件 json_status_info 的只读快照。 */
data class DeviceStatus(
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val fullyCharged: Boolean = false,
    val signalQuality: Int = 0,
    val totalSignalQuality: Int = 0,
    val rssi: Int = 0,
    val wifiClients: Int = 0,
    val runSeconds: Int = 0,
) {
    val hasBattery: Boolean get() = batteryPercent in 0..100
    val hasSignal: Boolean get() = totalSignalQuality > 0

    val signalLabel: String
        get() {
            if (!hasSignal) return "未知"
            if (signalQuality <= 0) return "无服务"
            val ratio = signalQuality.toFloat() / totalSignalQuality
            return when {
                ratio >= 0.8f -> "极好"
                ratio >= 0.6f -> "良好"
                ratio >= 0.4f -> "一般"
                else -> "较弱"
            }
        }

    val uptimeLabel: String
        get() {
            if (runSeconds <= 0) return "--"
            val days = runSeconds / 86400
            val hours = (runSeconds % 86400) / 3600
            val minutes = (runSeconds % 3600) / 60
            return when {
                days > 0 -> "$days 天 $hours 小时"
                hours > 0 -> "$hours 小时 $minutes 分"
                else -> "$minutes 分钟"
            }
        }

    companion object {
        fun from(raw: String?): DeviceStatus {
            val o = Utils.jsonObject(raw) ?: return DeviceStatus()
            fun str(key: String) = o.optString(key, "")
            fun num(key: String, fallback: Int = 0) = str(key).toIntOrNull() ?: fallback
            val charge = str("battery_charge")
            return DeviceStatus(
                batteryPercent = num("battery_percent", -1),
                charging = charge == "1" || str("battery_charging") == "1",
                fullyCharged = charge == "2" || charge == "3",
                signalQuality = num("signal_quality"),
                totalSignalQuality = num("total_signal_quality"),
                rssi = num("rssi"),
                wifiClients = num("wifi_clients_num"),
                runSeconds = num("run_seconds"),
            )
        }
    }
}
