package com.kaixin233.wifiautoreset

import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.Locale
import java.util.concurrent.Executors

/** 简写 findViewById。 */
internal fun <T : View> View.find(id: Int): T = findViewById(id)

/** 通用工具：线程切换与显示格式化。 */
object Utils {
    // 固定线程数，避免轮询与手动刷新叠加时无限创建线程。
    private val io = Executors.newFixedThreadPool(4)
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

    fun formatSpeed(bytes: Long): String = when {
        bytes <= 0 -> "0 B/s"
        bytes < 1024 -> "$bytes B/s"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB/s", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.2f MB/s", bytes / (1024.0 * 1024.0))
    }

    /** 按 Unix 纪元对齐到周期起点后 5 秒，与 AlarmManager 排程一致。 */
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
