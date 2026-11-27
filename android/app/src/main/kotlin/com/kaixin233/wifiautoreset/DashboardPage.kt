package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** 单页仪表盘：实时网速、悬浮窗、定时任务、设备操作、后台保活与执行日志。 */
class DashboardPage(
    private val activity: Activity,
    override val view: View,
) : PageController {

    private var monitor: SpeedMonitor? = null
    private var automation: AutomationController? = null
    private var logs: LogSectionController? = null
    private var pendingOverlayPermission = false
    private var updatingOverlaySwitch = false

    init {
        monitor = SpeedMonitor { rx, tx ->
            activity.runOnUiThread {
                view.find<TextView>(R.id.text_speed_down).text = Utils.formatSpeed(rx)
                view.find<TextView>(R.id.text_speed_up).text = Utils.formatSpeed(tx)
                syncOverlaySwitch()
            }
        }

        view.find<Button>(R.id.btn_factory).setOnClickListener {
            confirm("恢复出厂设置", "此操作会清空随身 WiFi 全部配置并重启，手机将断网。确认继续？") {
                val body = MifiClient.factoryReset()
                // 手动重置成功后刷新计时基准并重新排程：倒计时从这一刻重新开始。
                val app = activity.applicationContext
                AlarmScheduler.markReset(app)
                SmartScheduler.noteExternalReset(app)
                if (AlarmScheduler.isEnabled(app)) AlarmScheduler.schedule(app)
                body
            }
        }
        view.find<Button>(R.id.btn_open_web).setOnClickListener { openManagementPage() }

        setupOverlaySwitch()
        automation = AutomationController(activity, view)
        logs = LogSectionController(activity, view)
    }

    override fun onShow() {
        monitor?.start()
        automation?.onShow()
        logs?.onShow()
        syncOverlaySwitch()
        // 划掉任务卡片后服务可能被回收：开关仍开时补拉，避免开关显示打开却没有悬浮窗。
        OverlayService.ensureRunning(activity)
    }

    override fun onHide() {
        monitor?.stop()
        automation?.onHide()
    }

    override fun onResume() {
        automation?.onResume()
        logs?.onResume()
        syncOverlaySwitch()
        if (pendingOverlayPermission) {
            pendingOverlayPermission = false
            if (Settings.canDrawOverlays(activity)) {
                setOverlaySwitch(true)
                OverlayService.start(activity)
                Toast.makeText(activity, "悬浮窗已开启", Toast.LENGTH_SHORT).show()
            } else {
                setOverlaySwitch(false)
                Toast.makeText(activity, "未获得悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        onHide()
        automation?.onDestroy()
        automation = null
        logs = null
        monitor = null
    }

    private fun setupOverlaySwitch() {
        val switch = view.find<Switch>(R.id.switch_overlay)
        setOverlaySwitch(OverlayService.isEnabled(activity) && Settings.canDrawOverlays(activity))
        switch.setOnCheckedChangeListener { _, checked ->
            if (updatingOverlaySwitch) return@setOnCheckedChangeListener
            if (!checked) {
                OverlayService.stop(activity)
                return@setOnCheckedChangeListener
            }
            if (Settings.canDrawOverlays(activity)) {
                OverlayService.start(activity)
            } else {
                pendingOverlayPermission = true
                Toast.makeText(activity, "请允许本应用显示悬浮窗", Toast.LENGTH_LONG).show()
                try {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    pendingOverlayPermission = false
                    setOverlaySwitch(false)
                    Toast.makeText(activity, "未找到悬浮窗设置页", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun syncOverlaySwitch() {
        val enabled = OverlayService.isEnabled(activity)
        val permitted = Settings.canDrawOverlays(activity)
        // 用户在系统设置里撤销权限后，及时清理残留的前台服务与开关状态。
        if (enabled && !permitted) OverlayService.stop(activity)
        setOverlaySwitch(enabled && permitted)
    }

    private fun setOverlaySwitch(checked: Boolean) {
        val switch = view.find<Switch>(R.id.switch_overlay)
        if (switch.isChecked == checked) return
        updatingOverlaySwitch = true
        switch.isChecked = checked
        updatingOverlaySwitch = false
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
