package com.kaixin233.wifiautoreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后恢复已启用的定时任务（WorkManager 原本自带该能力）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext
            if (AlarmScheduler.isEnabled(app)) {
                // 任务启用时同步拉起前台守护服务，保住闹钟不被系统策略清除。
                KeepAliveService.start(app)
                AlarmScheduler.schedule(app)
            }
        }
    }
}
