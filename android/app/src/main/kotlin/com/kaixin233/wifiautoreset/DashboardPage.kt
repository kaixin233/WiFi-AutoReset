package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
    private var carrier = "--"
    private var active = false

    private val statusTick = object : Runnable {
        override fun run() {
            if (!active) return
            refreshStatus()
            refreshDevice()
            refreshGateway()
            handler.postDelayed(this, 10_000)
        }
    }

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

        view.find<Button>(R.id.btn_refresh).setOnClickListener { refreshAll() }
        view.find<Button>(R.id.btn_access).setOnClickListener {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.find<Button>(R.id.btn_factory).setOnClickListener {
            confirm("恢复出厂设置", "此操作会清空随身 WiFi 全部配置并重启，手机将断网。确认继续？") { MifiClient.factoryReset() }
        }
        view.find<Button>(R.id.btn_reboot).setOnClickListener {
            confirm("重启设备", "设备会短暂断网后重新上线，配置不会丢失。确认继续？") { MifiClient.reboot() }
        }
        view.find<Button>(R.id.btn_traffic).setOnClickListener {
            confirm("重置流量统计", "仅清零设备上的流量统计计数，不会修改账号或 WiFi 密码。确认继续？") { MifiClient.resetTraffic() }
        }
    }

    override fun onShow() {
        if (active) return
        active = true
        monitor?.start()
        refreshAll()
        handler.post(statusTick)
        handler.post(foregroundTick)
    }

    override fun onHide() {
        active = false
        monitor?.stop()
        handler.removeCallbacks(statusTick)
        handler.removeCallbacks(foregroundTick)
    }

    override fun onDestroy() {
        onHide()
        monitor = null
    }

    private fun refreshAll() {
        refreshStatus()
        refreshDevice()
        refreshGateway()
        refreshForeground()
    }

    private fun refreshGateway() {
        Utils.async(MifiClient::reachable) { ok, error ->
            view.find<TextView>(R.id.text_gateway).text = when {
                error != null -> "网关检测失败"
                ok == true -> "网关在线"
                else -> "网关不可达"
            }
        }
    }

    private fun refreshStatus() {
        Utils.async(MifiClient::statusInfo) { raw, error ->
            if (error != null) {
                view.find<TextView>(R.id.text_api_status).text = "状态读取失败"
                return@async
            }
            val s = DeviceStatus.from(raw)
            val charging = when {
                s.fullyCharged -> "已充满"
                s.charging -> "充电中"
                else -> "未充电"
            }
            view.find<TextView>(R.id.text_battery).text =
                "电量：${if (s.hasBattery) "${s.batteryPercent}%" else "--"} · $charging"
            view.find<TextView>(R.id.text_signal).text =
                "信号：${s.signalLabel}${if (s.rssi != 0) " · ${s.rssi} dBm" else ""}"
            view.find<TextView>(R.id.text_carrier).text = "运营商：$carrier"
            view.find<TextView>(R.id.text_clients_uptime).text =
                "在线 ${s.wifiClients} 台 · 已运行 ${s.uptimeLabel}"
            view.find<TextView>(R.id.text_api_status).text = "状态已更新"
        }
    }

    private fun refreshDevice() {
        Utils.async(MifiClient::deviceInfo) { raw, error ->
            if (error != null) {
                view.find<TextView>(R.id.text_api_status).text = "信息读取失败"
                return@async
            }
            val o = Utils.jsonObject(raw) ?: run {
                view.find<TextView>(R.id.text_api_status).text = "信息解析失败"
                return@async
            }
            carrier = Utils.localizeCarrier(o.optString("network_name"))
            val ssid = Utils.decodeUtf16Hex(o.optString("ssid"))
            view.find<TextView>(R.id.text_device_info).text = buildString {
                appendLine("设备型号：${o.optString("device_name", "--")}")
                appendLine("固件版本：${o.optString("sw_version", "--")}")
                appendLine("Wi-Fi 名称：${if (ssid.isEmpty()) "--" else ssid}")
                appendLine("运营商：$carrier")
                appendLine("IMEI：${o.optString("imei", "--")}")
                appendLine("局域网 IP：${o.optString("lan_ip", "--")}")
                append("广域网 IP：${o.optString("wan_ip", "--")}")
            }
            view.find<TextView>(R.id.text_carrier).text = "运营商：$carrier"
        }
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
                        refreshAll()
                    },
                )
            }
            .show()
    }
}
