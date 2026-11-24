package com.kaixin233.wifiautoreset

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 用系统 AlarmManager 按自定义周期调度恢复出厂任务，替代沉重的 WorkManager。
 * 排程规则与界面倒计时一致：每个周期起点后 5 秒执行。
 *
 * 兼容性要点：Android 12(API 31)+ 且 targetSdk>=31 时，setExact* 需要
 * SCHEDULE_EXACT_ALARM 权限；用户未授予时自动降级为 setAndAllowWhileIdle，
 * 避免抛 SecurityException 导致「保存设置」闪退。
 */
object AlarmScheduler {
    const val PREFS = "wifi_auto_reset"
    const val KEY_ENABLED = "enabled"
    const val KEY_INTERVAL_MINUTES = "interval_minutes"
    const val KEY_SMART = "smart_enabled"
    const val KEY_DEFER_COUNT = "defer_count"
    const val DEFAULT_INTERVAL_MINUTES = 60
    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 1440
    const val ACTION_RESET = "com.kaixin233.wifiautoreset.ACTION_RESET"

    private const val REQUEST_CODE = 1001

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    /** 智能调度默认开启：繁忙时自动延缓，空闲时执行。 */
    fun isSmart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SMART, true)

    fun getIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    fun nextRunMillis(context: Context): Long = Utils.nextRunMillis(getIntervalMinutes(context))

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_RESET)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /** 按正常周期排下一次任务（对齐周期起点后 5 秒）。 */
    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val delay = nextRunMillis(context) - System.currentTimeMillis()
        setAlarm(context, delay.coerceAtLeast(1_000L))
    }

    /** 智能模式繁忙时：安排一次短延迟后重新检测（不重置 defer 计数）。 */
    fun scheduleDefer(context: Context, delayMinutes: Int) {
        if (!isEnabled(context)) return
        setAlarm(context, delayMinutes * 60_000L)
    }

    /** 统一的闹钟设置：优先精确闹钟，无权限时降级，永不抛 SecurityException。 */
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
        prefs(context).edit().remove(KEY_DEFER_COUNT).apply()
    }
}
