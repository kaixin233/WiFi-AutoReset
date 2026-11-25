package com.kaixin233.wifiautoreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * 接收闹钟广播：到点直接执行恢复出厂，成功后刷新计时基准并排下一次任务。
 *
 * 用 WakeLock（带超时自动释放）保底后台执行，避免网络重试超时导致任务被系统
 * 判定结束、日志丢失。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        /** WakeLock 最长持有 5 分钟，异常时系统也会自动释放。 */
        private const val WAKELOCK_TIMEOUT_MS = 5 * 60_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_RESET) return
        val app = context.applicationContext
        AppLog.init(app)

        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val lock = power?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WiFiAutoReset:alarm",
        )
        lock?.acquire(WAKELOCK_TIMEOUT_MS)

        Utils.asyncUi(
            {
                try {
                    runTask(app)
                } catch (e: Exception) {
                    val msg = e.message ?: "未知异常"
                    AppLog.append(app, LogEntry(LogEntry.now(), "恢复出厂设置", "定时", "失败", msg))
                    Notifier.notify(app, "定时重置失败", msg)
                    // 异常也要排下一次，避免任务彻底停止。
                    AlarmScheduler.schedule(app)
                } finally {
                    releaseLock(lock)
                }
            },
            { /* 结果已在后台线程处理，UI 回调无需操作 */ },
        )
    }

    private fun releaseLock(lock: PowerManager.WakeLock?) {
        try {
            if (lock != null && lock.isHeld) lock.release()
        } catch (e: Exception) {
            // 超时后系统已自动释放，忽略重复释放异常。
        }
    }

    private fun runTask(app: Context) {
        // 执行真实恢复出厂。
        val body = MifiClient.factoryReset()
        val result = if (body.contains("success", ignoreCase = true)) "成功" else "已发送"
        AppLog.append(app, LogEntry(LogEntry.now(), "恢复出厂设置", "定时", result, body.take(300)))

        // 成功后刷新计时基准：下次执行 = 本次重置时间 + 一个周期。
        AlarmScheduler.markReset(app)
        Notifier.notify(app, "定时重置已执行", "随身 WiFi 已恢复出厂设置，设备即将重启断网")

        AlarmScheduler.schedule(app)
    }
}
