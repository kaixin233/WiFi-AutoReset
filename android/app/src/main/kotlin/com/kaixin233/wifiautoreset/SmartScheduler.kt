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
 * 两道安全闸门：
 * 1. 仅在 MiFi-281F（固件指纹 realm=Highwmg）下检测与重置，其它 WiFi 完全不介入；
 * 2. 重置后 30 分钟保护期 + 成效核验，连续无效则指数退避，避免频繁无效重置。
 *
 * 采样用非精确闹钟 + 内核计数器差值，无轮询线程；设备指纹探测带缓存。
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
    /** 重置后保护期：这段时间内必定正常网速，既不检测也不重置。 */
    const val PROTECT_MS = 30 * 60_000L
    /** 基础冷却 = 保护期；核验失败后按 2 的幂延长，上限 12 小时。 */
    const val BASE_COOLDOWN_MS = PROTECT_MS
    const val MAX_COOLDOWN_MS = 12 * 60 * 60_000L
    const val MAX_INVALID_STREAK = 5
    const val MAX_WAIT_MS = 15 * 60_000L
    const val DAILY_LIMIT = 3
    /** 设备指纹缓存：命中时 15 分钟；未命中时 2 分钟，便于设备重启后快速恢复。 */
    const val TARGET_TTL_MS = 15 * 60_000L
    const val TARGET_FAIL_TTL_MS = 2 * 60_000L

    private const val REQ_SAMPLE = 2101
    private const val REQ_VERIFY = 2102
    private const val REQ_CHECK = 2103
    private const val REQ_FINAL = 2104
    private const val REQ_CANCEL = 2105

    const val KEY_SMART_ENABLED = "smart_enabled"
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
    const val KEY_INVALID_STREAK = "smart_invalid_streak"
    const val KEY_PROTECT_UNTIL = "smart_protect_until"
    const val KEY_VERIFY_PENDING = "smart_verify_pending"
    const val KEY_TARGET_OK = "smart_target_ok"
    const val KEY_TARGET_AT = "smart_target_at"

    fun prefs(context: Context) = AlarmScheduler.prefs(context)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SMART_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SMART_ENABLED, enabled).apply()
    }

    /** 当前网络是否为 WiFi；不是 WiFi 时不做任何限速判断。 */
    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 是否连在目标设备 MiFi-281F 上：WiFi + 固件指纹匹配。
     * 指纹结果带缓存；探测失败只缓存 2 分钟，避免设备重启后长时间误判为"非目标"。
     */
    fun isTargetNetwork(context: Context): Boolean {
        if (!isWifi(context)) return false
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val at = prefs.getLong(KEY_TARGET_AT, 0L)
        val ok = prefs.getBoolean(KEY_TARGET_OK, false)
        val ttl = if (ok) TARGET_TTL_MS else TARGET_FAIL_TTL_MS
        if (at > 0L && now - at < ttl) return ok
        val fresh = MifiClient.isTargetDevice()
        prefs.edit()
            .putBoolean(KEY_TARGET_OK, fresh)
            .putLong(KEY_TARGET_AT, now)
            .apply()
        return fresh
    }

    /** UI 展示用：只读缓存，绝不发网络请求（状态每秒刷新）。 */
    fun isTargetCached(context: Context): Boolean {
        if (!isWifi(context)) return false
        val prefs = prefs(context)
        val at = prefs.getLong(KEY_TARGET_AT, 0L)
        if (at <= 0L) return true
        val ok = prefs.getBoolean(KEY_TARGET_OK, false)
        val ttl = if (ok) TARGET_TTL_MS else TARGET_FAIL_TTL_MS
        return if (System.currentTimeMillis() - at < ttl) ok else true
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
            .putInt(KEY_INVALID_STREAK, 0)
            .putLong(KEY_PROTECT_UNTIL, 0L)
            .putBoolean(KEY_VERIFY_PENDING, false)
            .apply()
    }

    fun suspectSince(context: Context): Long =
        prefs(context).getLong(KEY_SUSPECT_SINCE, 0L)

    fun invalidStreak(context: Context): Int =
        prefs(context).getInt(KEY_INVALID_STREAK, 0)

    fun protectUntil(context: Context): Long =
        prefs(context).getLong(KEY_PROTECT_UNTIL, 0L)

    /** 连续核验失败越多，下次允许重置的间隔越长（30 分 → 1 时 → 2 时 … 上限 12 时）。 */
    fun cooldownMs(context: Context): Long {
        val streak = invalidStreak(context).coerceIn(0, MAX_INVALID_STREAK)
        return (BASE_COOLDOWN_MS shl streak).coerceAtMost(MAX_COOLDOWN_MS)
    }

    /** 保护期内、冷却中或今日已达上限时，不允许执行自动重置。 */
    fun canReset(context: Context): Boolean {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (now < prefs.getLong(KEY_PROTECT_UNTIL, 0L)) return false
        val last = prefs.getLong(KEY_LAST_SMART_RESET, 0L)
        if (now - last < cooldownMs(context)) return false
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (prefs.getString(KEY_DAILY_DAY, "") != today) return true
        return prefs.getInt(KEY_DAILY_COUNT, 0) < DAILY_LIMIT
    }

    /** 记录一次智能重置：进入保护期，并安排在保护期末做成效核验。 */
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
            .putLong(KEY_PROTECT_UNTIL, now + PROTECT_MS)
            .putBoolean(KEY_VERIFY_PENDING, true)
            .putLong(KEY_LAST_TOTAL, totalBytes())
            .putLong(KEY_LAST_AT, now)
            .apply()
        AlarmScheduler.markReset(context, now)
        // 智能重置是固定周期之外的提前重置：同步把周期基准推到此刻，
        // 并重新排程固定闹钟，避免旧的周期闹钟在保护期内重复触发。
        if (AlarmScheduler.isEnabled(context)) AlarmScheduler.schedule(context)
        scheduleSample(context, PROTECT_MS)
    }

    /** 手动重置或固定周期重置后：刷新保护期与计数，并从新的起点开始核验。 */
    fun noteExternalReset(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KEY_LAST_SMART_RESET, now)
            .putInt(KEY_LOW_STREAK, 0)
            .putLong(KEY_SUSPECT_SINCE, 0L)
            .putBoolean(KEY_PENDING_RESET, false)
            .putInt(KEY_INVALID_STREAK, 0)
            .putLong(KEY_PROTECT_UNTIL, now + PROTECT_MS)
            .putBoolean(KEY_VERIFY_PENDING, true)
            .putLong(KEY_LAST_TOTAL, totalBytes())
            .putLong(KEY_LAST_AT, now)
            .apply()
        if (isEnabled(context)) scheduleSample(context, PROTECT_MS)
    }

    /** 核验成功：网速已恢复，退避计数归零。 */
    fun markVerified(context: Context) {
        prefs(context).edit()
            .putInt(KEY_INVALID_STREAK, 0)
            .putBoolean(KEY_VERIFY_PENDING, false)
            .apply()
    }

    /** 核验失败：网速仍低，累加退避计数并返回当前冷却分钟数。 */
    fun markUnrecovered(context: Context): Int {
        val prefs = prefs(context)
        val streak = (prefs.getInt(KEY_INVALID_STREAK, 0) + 1).coerceAtMost(MAX_INVALID_STREAK)
        prefs.edit()
            .putInt(KEY_INVALID_STREAK, streak)
            .putBoolean(KEY_VERIFY_PENDING, false)
            .apply()
        return (cooldownMs(context) / 60_000L).toInt()
    }

    /** 仪表盘展示用状态。智能策略是固定周期之外的辅助检测。 */
    fun statusText(context: Context): String {
        if (!isEnabled(context)) return "智能辅助 · 未启用"
        if (!isTargetCached(context)) return "非目标 WiFi · 未监测"
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val protect = prefs.getLong(KEY_PROTECT_UNTIL, 0L)
        if (protect > 0L && now < protect) {
            return "保护期 · 剩余 ${(protect - now) / 60_000L + 1} 分钟"
        }
        val last = prefs.getLong(KEY_LAST_SMART_RESET, 0L)
        val remaining = cooldownMs(context) - (now - last)
        if (last > 0L && remaining > 0L) {
            val extra = if (invalidStreak(context) > 0) "（重置未恢复，已延长）" else ""
            return "冷却中 · 剩余 ${remaining / 60_000L + 1} 分钟$extra"
        }
        val suspect = suspectSince(context)
        if (suspect > 0L) {
            val waited = now - suspect
            return "疑似限速 · 等待空闲 ${waited / 60_000L + 1} 分钟"
        }
        return "正常 · 正在监测"
    }
}
