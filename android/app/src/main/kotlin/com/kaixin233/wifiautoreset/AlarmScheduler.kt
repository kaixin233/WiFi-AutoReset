package com.kaixin233.wifiautoreset

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 用系统 AlarmManager 按自定义周期调度恢复出厂任务，替代沉重的 WorkManager。
 * 排程规则与界面倒计时一致：每个周期起点后 5 秒执行。
 */
object AlarmScheduler {
    const val PREFS = "wifi_auto_reset"
    const val KEY_ENABLED = "enabled"
    const val KEY_INTERVAL_MINUTES = "interval_minutes"
    const val DEFAULT_INTERVAL_MINUTES = 60
    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 1440
    const val ACTION_RESET = "com.kaixin233.wifiautoreset.ACTION_FACTORY_RESET"

    private const val REQUEST_CODE = 1001

    fun getIntervalMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    fun nextRunMillis(context: Context): Long = Utils.nextRunMillis(getIntervalMinutes(context))

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_RESET)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun schedule(context: Context) {
        val enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        if (!enabled) return
        val delay = nextRunMillis(context) - System.currentTimeMillis()
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delay.coerceAtLeast(1_000L),
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }
}
