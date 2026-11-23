package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/** 仪表盘页面控制器（纯原生 View，无 Fragment）。 */
class DashboardPage(
    private val activity: Activity,
    override val view: View,
) : PageController {

    private var monitor: SpeedMonitor? = null
    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    private val foregroundTick = object : Runnable {
        override fun run() {
            if (!active) return
            refreshForeground()
            handler.postDelayed(this, 3_000)
        }
    }

    init {
        monitor = SpeedMonitor { rx, tx ->
            activity.runOnUiThread {
                view.find<TextView>(R.id.text_speed_down).text = Utils.formatSpeed(rx)
                view.find<TextView>(R.id.text_speed_up).text = Utils.formatSpeed(tx)
            }
        }

        view.find<Button>(R.id.btn_access).setOnClickListener {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.find<Button>(R.id.btn_factory).setOnClickListener {
            confirm("恢复出厂设置", "此操作会清空随身 WiFi 全部配置并重启，手机将断网。确认继续？") { MifiClient.factoryReset() }
        }
        view.find<Button>(R.id.btn_reboot).setOnClickListener {
            confirm("重启设备", "设备会短暂断网后重新上线，配置不会丢失。确认继续？") { MifiClient.reboot() }
        }
        view.find<Button>(R.id.btn_open_web).setOnClickListener { openManagementPage() }
    }

    /** 用系统浏览器打开随身 WiFi 管理页，不再在 App 内嵌 WebView。 */
    private fun openManagementPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://192.168.1.1"))
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onShow() {
        if (active) return
        active = true
        monitor?.start()
        refreshForeground()
        handler.post(foregroundTick)
    }

    override fun onHide() {
        active = false
        monitor?.stop()
        handler.removeCallbacks(foregroundTick)
    }

    override fun onDestroy() {
        onHide()
        monitor = null
    }

    private fun refreshForeground() {
        val ctx = activity
        Utils.async({
            val detector = ForegroundAppDetector(ctx)
            val granted = detector.hasPermission()
            val pkg = detector.currentPackage()
            Triple(granted, pkg, detector.labelOf(pkg))
        }) { result, error ->
            if (error != null) return@async
            val (granted, pkg, label) = result ?: return@async
            view.find<Button>(R.id.btn_access).visibility = if (granted) View.GONE else View.VISIBLE
            val hasApp = granted && !pkg.isNullOrBlank()
            view.find<TextView>(R.id.text_foreground_label).text = if (hasApp) label else "等待窗口事件"
            view.find<TextView>(R.id.text_foreground_pkg).text =
                if (hasApp) pkg else "切换一次应用即可获取包名"
            view.find<TextView>(R.id.text_foreground_time).text =
                if (hasApp) "更新于 ${ForegroundAppDetector(ctx).lastTime()}" else "--"
        }
    }

    private fun confirm(title: String, message: String, action: () -> String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认执行") { _, _ ->
                Toast.makeText(activity, "正在执行…", Toast.LENGTH_SHORT).show()
                Utils.asyncUi(
                    {
                        val body = action()
                        AppLog.append(
                            activity,
                            LogEntry(LogEntry.now(), title, "手动", "已发送", body.take(300)),
                        )
                    },
                    { error ->
                        val text = if (error == null) "已执行" else "失败：${error.message}"
                        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
                    },
                )
            }
            .show()
    }
}
