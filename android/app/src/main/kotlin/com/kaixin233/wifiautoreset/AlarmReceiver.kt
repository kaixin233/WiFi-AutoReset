package com.kaixin233.wifiautoreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * 接收闹钟广播：智能模式下先判断忙闲，空闲才执行恢复出厂；繁忙则延缓重试。
 * 完成后按正常周期排下一次任务。
 *
 * 用 WakeLock（带超时自动释放）保底后台执行，替代 goAsync 的约 10 秒窗口，
 * 避免采样/网络请求超时导致任务被系统判定结束、日志丢失。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        /** 繁忙时每次延缓 5 分钟后再检测。 */
        private const val DEFER_MINUTES = 5
        /** 最多延缓 6 次（共 30 分钟），仍繁忙则强制执行，避免任务被无限推迟。 */
        private const val MAX_DEFER = 6
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
        val prefs = AlarmScheduler.prefs(app)

        // 智能模式：基于「自上次重置以来的平均网速」判断忙闲（零 sleep、零额外唤醒）。
        if (AlarmScheduler.isSmart(app)) {
            val average = ResetSnapshot.averageSinceReset(app)
            val state = IdleDetector.detect(app, average)
            if (state.supported && state.busy) {
                val deferCount = prefs.getInt(AlarmScheduler.KEY_DEFER_COUNT, 0) + 1
                if (deferCount <= MAX_DEFER) {
                    prefs.edit().putInt(AlarmScheduler.KEY_DEFER_COUNT, deferCount).apply()
                    AppLog.append(
                        app,
                        LogEntry(
                            LogEntry.now(), "智能调度", "定时", "延缓",
                            "${state.summary}；周期平均达到 500 KB/s 繁忙阈值，${DEFER_MINUTES} 分钟后重试（第 $deferCount/$MAX_DEFER 次）",
                        ),
                    )
                    // 延缓不弹通知，避免频繁打扰；仅在日志中记录。
                    AlarmScheduler.scheduleDefer(app, DEFER_MINUTES)
                    return
                }
                // 已达最大延缓次数：强制执行，避免任务永不执行，并通知用户。
                AppLog.append(
                    app,
                    LogEntry(
                        LogEntry.now(), "智能调度", "定时", "强制",
                        "${state.summary}；已连续延缓 $MAX_DEFER 次仍繁忙，本次强制执行",
                    ),
                )
                Notifier.notify(app, "定时重置（强制执行）", "设备持续繁忙，已按最大延缓次数强制执行")
            } else {
                val reason = if (state.supported) {
                    "${state.summary}；周期平均低于 500 KB/s，判定为空闲，执行重置"
                } else {
                    "${state.summary}，按准时模式执行重置"
                }
                AppLog.append(app, LogEntry(LogEntry.now(), "智能调度", "定时", "执行", reason))
            }
            prefs.edit().remove(AlarmScheduler.KEY_DEFER_COUNT).apply()
        }

        // 执行真实恢复出厂。
        val body = MifiClient.factoryReset()
        val result = if (body.contains("success", ignoreCase = true)) "成功" else "已发送"
        AppLog.append(app, LogEntry(LogEntry.now(), "恢复出厂设置", "定时", result, body.take(300)))

        // 重置成功后记录新的基准快照，作为下次智能判断的起点。
        ResetSnapshot.mark(app)
        Notifier.notify(app, "定时重置已执行", "随身 WiFi 已恢复出厂设置，设备即将重启断网")

        AlarmScheduler.schedule(app)
    }
}
