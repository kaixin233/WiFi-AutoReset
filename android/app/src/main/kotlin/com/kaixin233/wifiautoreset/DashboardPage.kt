package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/** 仪表盘页面控制器（纯原生 View，无 Fragment）：实时网速 + 设备操作。 */
class DashboardPage(
    private val activity: Activity,
    override val view: View,
) : PageController {

    private var monitor: SpeedMonitor? = null

    init {
        monitor = SpeedMonitor { rx, tx ->
            activity.runOnUiThread {
                view.find<TextView>(R.id.text_speed_down).text = Utils.formatSpeed(rx)
                view.find<TextView>(R.id.text_speed_up).text = Utils.formatSpeed(tx)
            }
        }

        view.find<Button>(R.id.btn_factory).setOnClickListener {
            confirm("恢复出厂设置", "此操作会清空随身 WiFi 全部配置并重启，手机将断网。确认继续？") {
                val body = MifiClient.factoryReset()
                // 手动重置成功后刷新计时基准并重新排程：倒计时从这一刻重新开始。
                val app = activity.applicationContext
                AlarmScheduler.markReset(app)
                if (AlarmScheduler.isEnabled(app)) AlarmScheduler.schedule(app)
                body
            }
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
        monitor?.start()
    }

    override fun onHide() {
        monitor?.stop()
    }

    override fun onDestroy() {
        onHide()
        monitor = null
    }

    private fun confirm(title: String, message: String, action: () -> String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认执行") { _, _ ->
                Toast.makeText(activity, "正在执行…", Toast.LENGTH_SHORT).show()
                Utils.asyncUi(
                    { action() },
                    { error ->
                        if (error == null) {
                            AppLog.append(activity, LogEntry(LogEntry.now(), title, "成功"))
                            Toast.makeText(activity, "已执行", Toast.LENGTH_SHORT).show()
                        } else {
                            AppLog.append(activity, LogEntry(LogEntry.now(), title, "失败"))
                            Toast.makeText(activity, "失败：${error.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
            .show()
    }
}
