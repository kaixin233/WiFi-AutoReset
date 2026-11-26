package com.kaixin233.wifiautoreset

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * 执行结果通知：定时重置成功 / 失败时提示用户。
 * 无 POST_NOTIFICATIONS 权限（Android 13+）时静默跳过，不影响主流程。
 *
 * 注意：为避免引入 AndroidX 依赖破坏「零依赖纯原生」，这里不使用 NotificationCompat，
 * 直接用系统 Notification.Builder（API 26+ 原生支持渠道，minSdk 26）。
 */
object Notifier {
    private const val CHANNEL_ID = "wifi_auto_reset_result"
    private const val CHANNEL_NAME = "执行结果"
    private const val KEEPALIVE_CHANNEL_ID = "wifi_auto_reset_keepalive"
    private const val KEEPALIVE_CHANNEL_NAME = "后台守护"
    /** 前台服务通知 id 必须固定且区别于结果通知（1001）。 */
    const val KEEPALIVE_NOTIFICATION_ID = 1002
    /** 悬浮窗服务通知 id，需与守护服务区分。 */
    const val OVERLAY_NOTIFICATION_ID = 1003

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager(context).getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                channel.description = "定时恢复出厂设置的执行结果"
                manager(context).createNotificationChannel(channel)
            }
        }
    }

    /** 是否已获得通知权限（Android 13+ 需运行时授予）。 */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notify(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, 0, intent, piFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()
        manager(context).notify(1001, notification)
    }

    /**
     * 前台守护服务的常驻通知：低优先级、静默、不震动。
     * 作用仅是把进程维持在前台优先级，防止 MIUI 划卡时按 force-stop 处理、
     * 连带清除 AlarmManager 闹钟；服务本身不做任何轮询，零额外耗电。
     * 前台服务通知不受 POST_NOTIFICATIONS 影响，无需检查权限。
     */
    fun keepaliveNotification(context: Context): Notification {
        ensureKeepaliveChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, KEEPALIVE_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("定时重置守护中")
            .setContentText("后台保持激活，确保定时重置准时执行")
            .setContentIntent(pi)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun ensureKeepaliveChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager(context).getNotificationChannel(KEEPALIVE_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    KEEPALIVE_CHANNEL_ID,
                    KEEPALIVE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                )
                channel.description = "保持后台激活以确保定时任务执行，可隐藏此通知"
                channel.setSound(null, null)
                channel.enableVibration(false)
                manager(context).createNotificationChannel(channel)
            }
        }
    }

    /** 悬浮窗服务的前台通知：低优先级、静默，双击悬浮窗即可关闭服务。 */
    fun overlayNotification(context: Context): Notification {
        ensureKeepaliveChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, KEEPALIVE_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("实时网速悬浮窗")
            .setContentText("双击悬浮窗即可关闭")
            .setContentIntent(pi)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
