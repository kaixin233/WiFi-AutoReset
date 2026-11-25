package com.kaixin233.wifiautoreset

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 用系统 AlarmManager 按自定义周期调度恢复出厂任务。
 *
 * 计时基准：以【最后一次恢复出厂的时间】为起点，下次执行 = 基准 + 一个周期。
 * 每次重置（手动或定时）成功后都会刷新基准，形成链式周期；不再按整点对齐。
 *
 * 兼容性要点：Android 12(API 31)+ 且 targetSdk>=31 时，setExact* 需要
 * SCHEDULE_EXACT_ALARM 权限；用户未授予时自动降级为 setAndAllowWhileIdle，
 * 避免抛 SecurityException 导致「保存设置」闪退。
 */
object AlarmScheduler {
    const val PREFS = "wifi_auto_reset"
    const val KEY_ENABLED = "enabled"
    const val KEY_INTERVAL_MINUTES = "interval_minutes"
    /** 最后一次恢复出厂的时间戳（毫秒），是倒计时与排程的唯一基准。 */
    const val KEY_LAST_RESET_AT = "last_reset_at"
    const val DEFAULT_INTERVAL_MINUTES = 60
    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 1440
    const val ACTION_RESET = "com.kaixin233.wifiautoreset.ACTION_RESET"

    private const val REQUEST_CODE = 1001

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun getIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    /** 最后一次重置时间；0 表示尚未记录过。 */
    fun lastResetAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_RESET_AT, 0L)

    /** 记录一次重置发生的时间，作为下一周期的计时起点。 */
    fun markReset(context: Context, atMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_RESET_AT, atMillis).apply()
    }

    /** 下次执行时间 = 最后重置时间 + 一个周期；无基准时按当前时间起算。 */
    fun nextRunMillis(context: Context, now: Long = System.currentTimeMillis()): Long {
        val base = lastResetAt(context).let { if (it > 0L) it else now }
        return base + getIntervalMinutes(context) * 60_000L
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_RESET)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /**
     * 排下一次任务。若从未重置过则以当前时间建立基准；
     * 若基准 + 周期已过期（例如设备关机很久），延后 1 秒立即执行，执行后再刷新基准。
     */
    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val now = System.currentTimeMillis()
        if (lastResetAt(context) <= 0L) markReset(context, now)
        val delay = nextRunMillis(context, now) - now
        setAlarm(context, delay)
    }

    /** 统一设置闹钟：优先精确闹钟，无权限时降级，永不抛 SecurityException。 */
    private fun setAlarm(context: Context, delayMillis: Long) {
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(1_000L)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarm.canScheduleExactAlarms()
            if (canExact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // 无精确闹钟权限：系统仍会在 delay 后唤醒，但可能延迟数分钟。
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // 部分定制 ROM 即使检查通过仍抛异常，兜底降级保证不崩溃。
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }
}
