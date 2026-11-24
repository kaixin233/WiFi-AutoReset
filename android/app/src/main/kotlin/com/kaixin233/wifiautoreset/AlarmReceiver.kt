package com.kaixin233.wifiautoreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收闹钟广播：智能模式下先检测忙闲，空闲才执行恢复出厂；繁忙则延缓重试。
 * 完成后按正常周期排下一次任务。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        /** 繁忙时每次延缓 5 分钟后再检测。 */
        private const val DEFER_MINUTES = 5
        /** 最多延缓 6 次（共 30 分钟），仍繁忙则强制执行，避免任务被无限推迟。 */
        private const val MAX_DEFER = 6
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_RESET) return
        val app = context.applicationContext
        AppLog.init(app)
        val pending = goAsync()
        Utils.asyncUi(
            {
                try {
                    runTask(app)
                } catch (e: Exception) {
                    AppLog.append(app, LogEntry(LogEntry.now(), "恢复出厂设置", "定时", "失败", e.message ?: "未知异常"))
                    // 异常也要排下一次，避免任务彻底停止。
                    AlarmScheduler.schedule(app)
                }
            },
            { pending.finish() },
        )
    }

    private fun runTask(app: Context) {
        val prefs = AlarmScheduler.prefs(app)

        // 智能模式：检测当前是否繁忙。
        if (AlarmScheduler.isSmart(app)) {
            val state = IdleDetector.detect(app)
            if (state.supported && state.busy) {
                val deferCount = prefs.getInt(AlarmScheduler.KEY_DEFER_COUNT, 0) + 1
                if (deferCount <= MAX_DEFER) {
                    prefs.edit().putInt(AlarmScheduler.KEY_DEFER_COUNT, deferCount).apply()
                    AppLog.append(
                        app,
                        LogEntry(
                            LogEntry.now(), "智能调度", "定时", "延缓",
                            "${state.summary}；达到 500 KB/s 繁忙阈值，${DEFER_MINUTES} 分钟后重试（第 $deferCount/$MAX_DEFER 次）",
                        ),
                    )
                    AlarmScheduler.scheduleDefer(app, DEFER_MINUTES)
                    return
                }
                // 已达最大延缓次数：记录并强制执行，避免任务永不执行。
                AppLog.append(
                    app,
                    LogEntry(
                        LogEntry.now(), "智能调度", "定时", "强制",
                        "${state.summary}；已连续延缓 $MAX_DEFER 次仍繁忙，本次强制执行",
                    ),
                )
            } else {
                val reason = if (state.supported) {
                    "${state.summary}；低于 500 KB/s 阈值，判定为空闲，执行重置"
                } else {
                    "${state.summary}，按准时模式执行重置"
                }
                AppLog.append(
                    app,
                    LogEntry(LogEntry.now(), "智能调度", "定时", "执行", reason),
                )
            }
            prefs.edit().remove(AlarmScheduler.KEY_DEFER_COUNT).apply()
        }

        // 执行真实恢复出厂。
        val body = MifiClient.factoryReset()
        val result = if (body.contains("success", ignoreCase = true)) "成功" else "已发送"
        AppLog.append(app, LogEntry(LogEntry.now(), "恢复出厂设置", "定时", result, body.take(300)))
        AlarmScheduler.schedule(app)
    }
}
