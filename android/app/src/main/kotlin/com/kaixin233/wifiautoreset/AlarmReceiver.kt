package com.kaixin233.wifiautoreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收闹钟广播，执行真实恢复出厂并排下一次任务。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_RESET) return
        val app = context.applicationContext
        AppLog.init(app)
        val pending = goAsync()
        Utils.asyncUi(
            {
                try {
                    val body = MifiClient.factoryReset()
                    val result = if (body.contains("success", ignoreCase = true)) "成功" else "已发送"
                    AppLog.append(app, LogEntry(LogEntry.now(), "恢复出厂设置", "定时", result, body.take(300)))
                } catch (e: Exception) {
                    AppLog.append(app, LogEntry(LogEntry.now(), "恢复出厂设置", "定时", "失败", e.message ?: "未知异常"))
                }
                AlarmScheduler.schedule(app)
            },
            { pending.finish() },
        )
    }
}
