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
 * 实时网速悬浮窗：前台服务承载一个透明小窗，显示下载 / 上传速率。
 *
 * - 权限：SYSTEM_ALERT_WINDOW，未授权时不会启动。
 * - 交互：可拖动（含顶部状态栏区域）；双击悬浮窗自动关闭并清除开关状态。
 * - 恢复：服务被系统回收后，重新进入应用会自动补拉，避免开关与显示状态不一致。
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
        // 系统重建服务时（START_STICKY / 开机自启），尊重用户开关与当前权限。
        if (!isEnabled(this) || !Settings.canDrawOverlays(this)) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        showOverlay()
        // 用户显式开启的悬浮窗：系统回收后由 onTaskRemoved / 再次进入应用补拉。
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // MIUI 划掉任务卡片时可能顺带清掉进程；只要开关仍开启就尝试恢复。
        if (isEnabled(this) && Settings.canDrawOverlays(this)) ensureRunning(this)
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

        val density = resources.displayMetrics.density
        val edge = (12 * density).toInt()
        val statusBarHeight = resources
            .getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }
            ?.let { resources.getDimensionPixelSize(it) }
            ?: 0
        val prefs = AlarmScheduler.prefs(this)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(PREF_X, edge)
            y = prefs.getInt(PREF_Y, statusBarHeight + edge)
            // 整窗轻微透明；布局本身为全透明背景，不遮挡背后内容。
            alpha = 0.92f
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
                        // 允许进入顶部状态栏区域，但不允许整窗滑出屏幕。
                        val minX = -touched.width + edge
                        val maxX = resources.displayMetrics.widthPixels - edge
                        val minY = -statusBarHeight
                        val maxY = resources.displayMetrics.heightPixels - edge
                        lp.x = (baseX + dx).coerceIn(minX, maxX)
                        lp.y = (baseY + dy).coerceIn(minY, maxY)
                        wm.updateViewLayout(touched, lp)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            prefs.edit()
                                .putInt(PREF_X, lp.x)
                                .putInt(PREF_Y, lp.y)
                                .apply()
                        } else {
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

        windowManager = wm
        overlayView = view
        try {
            wm.addView(view, lp)
        } catch (_: Exception) {
            overlayView = null
            windowManager = null
            setEnabled(this, false)
            stopSelf()
            return
        }
        viewAttached = true

        monitor = SpeedMonitor { rx, tx ->
            down.text = "↓ ${Utils.formatSpeedFixed(rx)}"
            up.text = "↑ ${Utils.formatSpeedFixed(tx)}"
        }
        monitor?.start()
    }

    override fun onDestroy() {
        viewAttached = false
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
        private const val PREF_X = "overlay_x"
        private const val PREF_Y = "overlay_y"

        /** 仅表示本进程内悬浮窗确实已加到 WindowManager，进程被杀后自动归零。 */
        @Volatile
        private var viewAttached = false

        fun isEnabled(context: Context): Boolean =
            AlarmScheduler.prefs(context).getBoolean(PREF_OVERLAY_ENABLED, false)

        /** 供界面判断开关是否真实生效；服务启动到挂上窗口之间短暂为 false。 */
        fun isRunning(): Boolean = viewAttached

        fun setEnabled(context: Context, enabled: Boolean) {
            AlarmScheduler.prefs(context).edit().putBoolean(PREF_OVERLAY_ENABLED, enabled).apply()
        }

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            setEnabled(context, true)
            launch(context)
        }

        /**
         * 再次进入应用时补拉：开关开着、权限还在但窗口没挂上（进程/服务被清理），
         * 就重新启动服务；已正常显示时不做任何事。
         */
        fun ensureRunning(context: Context) {
            if (viewAttached) return
            if (!isEnabled(context) || !Settings.canDrawOverlays(context)) return
            launch(context)
        }

        fun stop(context: Context) {
            setEnabled(context, false)
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            context.stopService(intent)
        }

        private fun launch(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
