package com.kaixin233.wifiautoreset

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 智能策略调控：以「上下行之和 < 500 KB/s」判定云控限速，
 * 并只在用户空闲时执行恢复出厂，尽量不打断使用。
 *
 * 采样用非精确闹钟 + 内核计数器差值，无网络请求、无常驻轮询线程。
 */
object SmartScheduler {
    const val ACTION_SAMPLE = "com.kaixin233.wifiautoreset.ACTION_SMART_SAMPLE"
    const val ACTION_VERIFY = "com.kaixin233.wifiautoreset.ACTION_SMART_VERIFY"
    const val ACTION_CHECK = "com.kaixin233.wifiautoreset.ACTION_SMART_CHECK"
    const val ACTION_FINAL = "com.kaixin233.wifiautoreset.ACTION_SMART_FINAL"
    const val ACTION_CANCEL = "com.kaixin233.wifiautoreset.ACTION_SMART_CANCEL"

    /** 限速阈值：500 KB/s（字节）。 */
    const val LOW_BYTES_PER_SEC = 500L * 1024L
    /** 有效窗口：2 分钟内至少 10 MB 流量，排除待机与轻浏览。 */
    const val VALID_WINDOW_BYTES = 10L * 1024L * 1024L
    /** 恢复判定：1.5 MB/s。 */
    const val RECOVER_BYTES_PER_SEC = 1536L * 1024L
    /** 判定为空闲的低流量门槛：30 KB/s。 */
    const val IDLE_BYTES_PER_SEC = 30L * 1024L

    const val SAMPLE_INTERVAL_MS = 2 * 60_000L
    const val VERIFY_DELAY_MS = 30_000L
    const val CHECK_INTERVAL_MS = 30_000L
    const val USER_DELAY_MS = 10_000L
    const val COOLDOWN_MS = 10 * 60_000L
    const val MAX_WAIT_MS = 15 * 60_000L
    const val DAILY_LIMIT = 3

    private const val REQ_SAMPLE = 2101
    private const val REQ_VERIFY = 2102
    private const val REQ_CHECK = 2103
    private const val REQ_FINAL = 2104
    private const val REQ_CANCEL = 2105

    const val KEY_SMART_ENABLED = "smart_enabled"
    const val KEY_MODE = "automation_mode"
    const val KEY_LAST_TOTAL = "smart_last_total"
    const val KEY_LAST_AT = "smart_last_at"
    const val KEY_LOW_STREAK = "smart_low_streak"
    const val KEY_SUSPECT_SINCE = "smart_suspect_since"
    const val KEY_CHECK_TOTAL = "smart_check_total"
    const val KEY_CHECK_AT = "smart_check_at"
    const val KEY_DAILY_COUNT = "smart_daily_count"
    const val KEY_DAILY_DAY = "smart_daily_day"
    const val KEY_PENDING_RESET = "smart_pending_reset"
    const val KEY_LAST_SMART_RESET = "smart_last_reset_at"

    fun prefs(context: Context) = AlarmScheduler.prefs(context)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SMART_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SMART_ENABLED, enabled).apply()
    }

    fun isSmartMode(context: Context): Boolean =
        prefs(context).getString(KEY_MODE, "fixed") == "smart"

    fun setMode(context: Context, smart: Boolean) {
        prefs(context).edit().putString(KEY_MODE, if (smart) "smart" else "fixed").apply()
    }

    /** 当前网络是否为 WiFi；不是 WiFi 时不做任何限速判断。 */
    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** 总收发字节；设备不支持时返回 -1。 */
    fun totalBytes(): Long {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        return if (rx < 0 || tx < 0) -1L else rx + tx
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, SmartReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }

    private fun alarm(
        context: Context,
        action: String,
        code: Int,
        delayMillis: Long,
    ) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + delayMillis.coerceAtLeast(1_000L)
        // 智能检测不需要精确到秒，允许系统合并唤醒以省电。
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending(context, action, code))
    }

    fun scheduleSample(context: Context, delayMillis: Long = SAMPLE_INTERVAL_MS) =
        alarm(context, ACTION_SAMPLE, REQ_SAMPLE, delayMillis)

    fun scheduleVerify(context: Context, delayMillis: Long = VERIFY_DELAY_MS) =
        alarm(context, ACTION_VERIFY, REQ_VERIFY, delayMillis)

    fun scheduleCheck(context: Context, delayMillis: Long = CHECK_INTERVAL_MS) =
        alarm(context, ACTION_CHECK, REQ_CHECK, delayMillis)

    fun scheduleFinal(context: Context, delayMillis: Long = USER_DELAY_MS) =
        alarm(context, ACTION_FINAL, REQ_FINAL, delayMillis)

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            pending(context, ACTION_SAMPLE, REQ_SAMPLE),
            pending(context, ACTION_VERIFY, REQ_VERIFY),
            pending(context, ACTION_CHECK, REQ_CHECK),
            pending(context, ACTION_FINAL, REQ_FINAL),
            pending(context, ACTION_CANCEL, REQ_CANCEL),
        ).forEach { manager.cancel(it) }
    }

    /** 清空一次判定周期的全部状态。 */
    fun resetState(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_TOTAL, -1L)
            .putLong(KEY_LAST_AT, 0L)
            .putInt(KEY_LOW_STREAK, 0)
            .putLong(KEY_SUSPECT_SINCE, 0L)
            .putLong(KEY_CHECK_TOTAL, -1L)
            .putLong(KEY_CHECK_AT, 0L)
            .putBoolean(KEY_PENDING_RESET, false)
            .apply()
    }

    fun suspectSince(context: Context): Long =
        prefs(context).getLong(KEY_SUSPECT_SINCE, 0L)

    /** 冷却中或今日已达上限时，不允许执行自动重置。 */
    fun canReset(context: Context): Boolean {
        val prefs = prefs(context)
        val last = prefs.getLong(KEY_LAST_SMART_RESET, 0L)
        if (System.currentTimeMillis() - last < COOLDOWN_MS) return false
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (prefs.getString(KEY_DAILY_DAY, "") != today) return true
        return prefs.getInt(KEY_DAILY_COUNT, 0) < DAILY_LIMIT
    }

    /** 记录一次实际执行，刷新冷却与每日计数，并建立新的采样基准。 */
    fun noteReset(context: Context) {
        val prefs = prefs(context)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val sameDay = prefs.getString(KEY_DAILY_DAY, "") == today
        val count = if (sameDay) prefs.getInt(KEY_DAILY_COUNT, 0) + 1 else 1
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_LAST_SMART_RESET, now)
            .putString(KEY_DAILY_DAY, today)
            .putInt(KEY_DAILY_COUNT, count)
            .putInt(KEY_LOW_STREAK, 0)
            .putLong(KEY_SUSPECT_SINCE, 0L)
            .putBoolean(KEY_PENDING_RESET, false)
            .putLong(KEY_LAST_TOTAL, totalBytes())
            .putLong(KEY_LAST_AT, now)
            .apply()
        AlarmScheduler.markReset(context, now)
        scheduleSample(context)
    }

    /** 手动重置或固定周期重置后也要刷新智能模式的冷却与采样基准。 */
    fun noteExternalReset(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KEY_LAST_SMART_RESET, now)
            .putInt(KEY_LOW_STREAK, 0)
            .putLong(KEY_SUSPECT_SINCE, 0L)
            .putBoolean(KEY_PENDING_RESET, false)
            .putLong(KEY_LAST_TOTAL, totalBytes())
            .putLong(KEY_LAST_AT, now)
            .apply()
        if (isEnabled(context)) scheduleSample(context)
    }

    /** 仪表盘展示用状态。 */
    fun statusText(context: Context): String {
        if (!isEnabled(context)) return "智能策略 · 未启用"
        val prefs = prefs(context)
        val last = prefs.getLong(KEY_LAST_SMART_RESET, 0L)
        val remaining = COOLDOWN_MS - (System.currentTimeMillis() - last)
        if (last > 0L && remaining > 0L) {
            return "冷却中 · 剩余 ${remaining / 60_000 + 1} 分钟"
        }
        val suspect = suspectSince(context)
        if (suspect > 0L) {
            val waited = System.currentTimeMillis() - suspect
            return "疑似限速 · 等待空闲 ${waited / 60_000 + 1} 分钟"
        }
        return "正常 · 正在监测"
    }
}
