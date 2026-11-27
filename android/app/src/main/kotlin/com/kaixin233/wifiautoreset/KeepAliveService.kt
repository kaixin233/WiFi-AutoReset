package com.kaixin233.wifiautoreset

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * 前台守护服务：唯一作用是维持进程的前台优先级。
 *
 * 背景：MIUI / HyperOS 把「从多任务划掉卡片」当作 force-stop，会连带清除
 * AlarmManager 闹钟，导致定时重置失效。常驻的前台服务能让系统把本应用视为
 * 「正在运行」，划卡不再触发 force-stop，闹钟得以保留。
 *
 * 省电设计：服务内没有任何轮询、定时器或网络请求，仅在启动时调用一次
 * startForeground 显示低优先级静默通知，进程闲置时几乎零耗电。
 * 真正的定时执行仍由系统 AlarmReceiver 负责，与本服务解耦。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14(API 34)+ 必须用带 type 的重载，否则抛 MissingForegroundServiceTypeException。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifier.KEEPALIVE_NOTIFICATION_ID,
                Notifier.keepaliveNotification(this),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifier.KEEPALIVE_NOTIFICATION_ID, Notifier.keepaliveNotification(this))
        }
        // 服务被系统重建时补一次排程兜底：若闹钟被清除可立即恢复（已启用才会排）。
        val app = applicationContext
        if (AlarmScheduler.isEnabled(app)) AlarmScheduler.schedule(app)
        if (SmartScheduler.isEnabled(app)) SmartScheduler.scheduleSample(app)
        // 被系统回收后尽可能重建；配合 AlarmManager 双保险。
        return START_STICKY
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
