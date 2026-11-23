package com.kaixin233.wifiautoreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后恢复已启用的定时任务（WorkManager 原本自带该能力）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.schedule(context.applicationContext)
        }
    }
}
