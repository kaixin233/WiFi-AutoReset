package com.kaixin233.wifiautoreset

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
 * - 交互：可拖动（可进入顶部状态栏区域，但绝不会移出屏幕）；双击关闭。
 * - 旋转：使用 WindowManager 实时屏幕尺寸钳制坐标，配置变化时移除并重建窗口。
 * - 自愈：每秒随网速刷新检查窗口是否仍附加、坐标是否越界，异常立即恢复。
 * - 恢复：服务被系统回收后，重新进入应用会自动补拉，避免开关与显示状态不一致。
 * - 省电：仅每秒采样一次 TrafficStats，无网络请求、无常驻轮询线程。
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var monitor: SpeedMonitor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 兜底：少数 ROM 在配置变化时会重建服务，此时 onStartCommand 不会再次调用。
        if (isEnabled(this) && Settings.canDrawOverlays(this)) {
            startForegroundNotification()
            showOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isEnabled(this) || !Settings.canDrawOverlays(this)) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        showOverlay()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转时系统会重排悬浮窗口，甚至临时 detach；移除后按当前屏幕尺寸重建。
        rebuildOverlay()
        // 旋转动画期间屏幕指标可能尚未稳定，延迟再校正一次。
        overlayView?.postDelayed({ ensureVisible() }, 600L)
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
            x = prefs.getInt(PREF_X, edgePx())
            y = prefs.getInt(PREF_Y, statusBarHeightPx() + edgePx())
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
                        lp.x = baseX + dx
                        lp.y = baseY + dy
                        // 用实时屏幕尺寸钳制，拖动永远不会把窗口移到屏幕外。
                        clampToScreen(lp, touched.width, touched.height, wm)
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
        // 记忆的位置可能来自上一次不同屏幕方向，先钳制回当前可见范围。
        if (clampToScreen(lp, view.width, view.height, wm)) {
            try {
                wm.updateViewLayout(view, lp)
            } catch (_: Exception) {
                // 忽略：自检会再次校正。
            }
        }

        monitor = SpeedMonitor { rx, tx ->
            down.text = "↓ ${Utils.formatSpeedFixed(rx)}"
            up.text = "↑ ${Utils.formatSpeedFixed(tx)}"
            // 每秒自检：窗口被系统移除或坐标越界时立即自愈。
            ensureVisible()
        }
        monitor?.start()
    }

    /**
     * 旋转后重建窗口：复用同一个 LayoutParams（拖动监听器持有它），只重算坐标。
     * 单纯 updateViewLayout 在部分 ROM 上对旋转无效，remove + addView 最可靠。
     */
    private fun rebuildOverlay() {
        val view = overlayView ?: return
        val wm = windowManager ?: return
        val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
        try {
            wm.removeViewImmediate(view)
        } catch (_: Exception) {
            // 窗口可能已被系统移除，忽略。
        }
        clampToScreen(lp, view.width, view.height, wm)
        try {
            wm.addView(view, lp)
            viewAttached = true
        } catch (_: Exception) {
            viewAttached = false
        }
    }

    /** 随每秒网速刷新自检：窗口未附加则重新挂载，坐标越界则钳制。 */
    private fun ensureVisible() {
        val view = overlayView ?: return
        val wm = windowManager ?: return
        val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (!view.isAttachedToWindow) {
            try {
                wm.addView(view, lp)
                viewAttached = true
            } catch (_: Exception) {
                // 权限可能被撤销，下次刷新再试；界面侧也会同步清理开关。
            }
            return
        }
        if (clampToScreen(lp, view.width, view.height, wm)) {
            try {
                wm.updateViewLayout(view, lp)
            } catch (_: Exception) {
                // 忽略：下一次刷新会再次校正。
            }
        }
    }

    /** 严格把窗口限制在当前屏幕内；仅允许顶部进入状态栏区域。 */
    private fun clampToScreen(
        lp: WindowManager.LayoutParams,
        viewW: Int,
        viewH: Int,
        wm: WindowManager,
    ): Boolean {
        val (screenW, screenH) = screenSize(wm)
        val density = resources.displayMetrics.density
        // 窗口首次 addView 前后 width 可能为 0，用布局固定尺寸兜底。
        val w = if (viewW > 0) viewW else (104 * density).toInt()
        val h = if (viewH > 0) viewH else (48 * density).toInt()
        val minX = 0
        val maxX = (screenW - w).coerceAtLeast(minX)
        val minY = -statusBarHeightPx()
        val maxY = (screenH - h).coerceAtLeast(minY)
        val nx = lp.x.coerceIn(minX, maxX)
        val ny = lp.y.coerceIn(minY, maxY)
        val changed = nx != lp.x || ny != lp.y
        lp.x = nx
        lp.y = ny
        return changed
    }

    /** 用 WindowManager 的实时屏幕指标，避免 Service 的 resources 在旋转后滞后。 */
    private fun screenSize(wm: WindowManager): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        @Suppress("DEPRECATION")
        val point = android.graphics.Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(point)
        return point.x to point.y
    }

    private fun edgePx(): Int = (12 * resources.displayMetrics.density).toInt()

    private fun statusBarHeightPx(): Int =
        resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }
            ?.let { resources.getDimensionPixelSize(it) }
            ?: 0

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
