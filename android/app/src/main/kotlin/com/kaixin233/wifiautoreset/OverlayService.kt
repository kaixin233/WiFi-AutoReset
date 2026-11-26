package com.kaixin233.wifiautoreset

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * 实时网速悬浮窗：前台服务承载一个半透明小窗，显示下载 / 上传速率。
 *
 * - 权限：SYSTEM_ALERT_WINDOW，未授权时不会启动。
 * - 交互：可拖动；双击悬浮窗自动关闭并清除开关状态。
 * - 省电：仅每秒采样一次 TrafficStats，无网络请求、无常驻轮询线程。
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var monitor: SpeedMonitor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        showOverlay()
        // 悬浮窗由用户显式开启；系统回收后不自动重建，避免意外一直悬浮。
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifier.OVERLAY_NOTIFICATION_ID,
                Notifier.overlayNotification(this),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifier.OVERLAY_NOTIFICATION_ID, Notifier.overlayNotification(this))
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            setEnabled(this, false)
            stopSelf()
            return
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_speed, null)
        val down = view.find<TextView>(R.id.overlay_down)
        val up = view.find<TextView>(R.id.overlay_up)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
            // 半透明，避免遮挡背后画面；窗口本身很小且可拖动。
            alpha = 0.82f
        }

        var anchorX = 0f
        var anchorY = 0f
        var baseX = 0
        var baseY = 0
        var moved = false
        var lastTapAt = 0L

        view.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val touched = v ?: return false
                return when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        anchorX = event.rawX
                        anchorY = event.rawY
                        baseX = lp.x
                        baseY = lp.y
                        moved = false
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - anchorX).toInt()
                        val dy = (event.rawY - anchorY).toInt()
                        if (abs(dx) > 6 || abs(dy) > 6) moved = true
                        lp.x = baseX + dx
                        lp.y = baseY + dy
                        wm.updateViewLayout(touched, lp)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTapAt < 350L) {
                                setEnabled(this@OverlayService, false)
                                stopSelf()
                            } else {
                                lastTapAt = now
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
        })

        wm.addView(view, lp)
        windowManager = wm
        overlayView = view

        monitor = SpeedMonitor { rx, tx ->
            down.text = "↓ ${Utils.formatSpeed(rx)}"
            up.text = "↑ ${Utils.formatSpeed(tx)}"
        }
        monitor?.start()
    }

    override fun onDestroy() {
        monitor?.stop()
        monitor = null
        try {
            overlayView?.let { windowManager?.removeViewImmediate(it) }
        } catch (_: Exception) {
            // 窗口可能已被系统移除，忽略。
        }
        overlayView = null
        windowManager = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.kaixin233.wifiautoreset.ACTION_STOP_OVERLAY"
        private const val PREF_OVERLAY_ENABLED = "overlay_enabled"

        fun isEnabled(context: Context): Boolean =
            AlarmScheduler.prefs(context).getBoolean(PREF_OVERLAY_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            AlarmScheduler.prefs(context).edit().putBoolean(PREF_OVERLAY_ENABLED, enabled).apply()
        }

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            setEnabled(context, true)
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            setEnabled(context, false)
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            context.stopService(intent)
        }
    }
}
