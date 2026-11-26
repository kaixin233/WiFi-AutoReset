package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** 自动化页面控制器：周期、开关、倒计时，使用 AlarmManager 调度。 */
class AutomationPage(
    private val activity: Activity,
    override val view: View,
) : PageController {

    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var interval = AlarmScheduler.DEFAULT_INTERVAL_MINUTES
    private var active = false

    // 常用档位：6 / 12 小时已移除，保留到 2 小时 + 自定义。
    private val presets = mapOf(
        R.id.chip_15 to 15,
        R.id.chip_30 to 30,
        R.id.chip_60 to 60,
        R.id.chip_120 to 120,
    )

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            updateCountdown()
            handler.postDelayed(this, 1_000)
        }
    }

    init {
        val prefs = AlarmScheduler.prefs(activity)
        enabled = prefs.getBoolean(AlarmScheduler.KEY_ENABLED, false)
        interval = prefs.getInt(AlarmScheduler.KEY_INTERVAL_MINUTES, AlarmScheduler.DEFAULT_INTERVAL_MINUTES)
            .coerceIn(AlarmScheduler.MIN_INTERVAL_MINUTES, AlarmScheduler.MAX_INTERVAL_MINUTES)

        val switch = view.find<Switch>(R.id.switch_enabled)
        switch.isChecked = enabled
        switch.setOnCheckedChangeListener { _, checked ->
            enabled = checked
            updateStatus()
            updateCountdown()
        }

        // 逐项点击监听，避免 RadioGroup 在程序设置 isChecked 时递归回调。
        presets.forEach { (id, minutes) ->
            view.find<RadioButton>(id).setOnClickListener {
                interval = minutes
                selectPreset(minutes)
                updateStatus()
                updateCountdown()
            }
        }
        view.find<RadioButton>(R.id.chip_custom).setOnClickListener { showCustomDialog() }

        view.find<Button>(R.id.btn_save).setOnClickListener { save() }
        view.find<Button>(R.id.btn_autostart).setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("开启自启动")
                .setMessage("将跳转到系统的自启动管理页，请找到本应用并允许「自启动」和「关联启动」。不同手机菜单名称略有差异。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去设置") { _, _ -> BootGuard.openAutoStart(activity) }
                .show()
        }
        view.find<Button>(R.id.btn_battery).setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("设置省电策略")
                .setMessage("请在接下来的页面允许「忽略电池优化」或将本应用省电策略设为「无限制」，避免后台被冻结。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去设置") { _, _ -> BootGuard.openBatteryOptimization(activity) }
                .show()
        }
        selectPreset(interval)
        updateStatus()
    }

    override fun onShow() {
        active = true
        updateCountdown()
        handler.post(tick)
    }

    override fun onHide() {
        active = false
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() = onHide()

    private fun selectPreset(minutes: Int) {
        val isPreset = presets.values.contains(minutes)
        presets.forEach { (id, value) -> view.find<RadioButton>(id).isChecked = value == minutes }
        val custom = view.find<RadioButton>(R.id.chip_custom)
        custom.isChecked = !isPreset
        custom.text = if (isPreset) "自定义" else "$minutes 分"
    }

    private fun showCustomDialog() {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(interval.toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(activity)
            .setTitle("自定义执行周期")
            .setMessage("请输入 ${AlarmScheduler.MIN_INTERVAL_MINUTES} ~ ${AlarmScheduler.MAX_INTERVAL_MINUTES} 之间的分钟数")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val parsed = input.text.toString().trim().toIntOrNull()
                if (parsed == null || parsed !in AlarmScheduler.MIN_INTERVAL_MINUTES..AlarmScheduler.MAX_INTERVAL_MINUTES) {
                    Toast.makeText(activity, "请输入 ${AlarmScheduler.MIN_INTERVAL_MINUTES} ~ ${AlarmScheduler.MAX_INTERVAL_MINUTES} 之间的分钟数", Toast.LENGTH_SHORT).show()
                    selectPreset(interval)
                    return@setPositiveButton
                }
                interval = parsed
                selectPreset(parsed)
                updateStatus()
                updateCountdown()
            }
            .setOnCancelListener { selectPreset(interval) }
            .show()
    }

    private fun save() {
        val prefs = AlarmScheduler.prefs(activity)
        val wasEnabled = prefs.getBoolean(AlarmScheduler.KEY_ENABLED, false)
        val oldInterval = prefs.getInt(AlarmScheduler.KEY_INTERVAL_MINUTES, AlarmScheduler.DEFAULT_INTERVAL_MINUTES)

        // 先落盘设置，再处理基准与排程，确保 Receiver / BootReceiver 读到最新值。
        prefs.edit()
            .putBoolean(AlarmScheduler.KEY_ENABLED, enabled)
            .putInt(AlarmScheduler.KEY_INTERVAL_MINUTES, interval)
            .apply()

        if (enabled) {
            val base = AlarmScheduler.lastResetAt(activity)
            val next = AlarmScheduler.nextRunMillis(activity)
            // 首次启用、周期变化、或基准已过期时，以当前时刻重建计时起点：
            // 保证倒计时从「现在」开始，而不会立刻触发一次重置。
            if (base <= 0L || next <= System.currentTimeMillis() ||
                (wasEnabled && oldInterval != interval)
            ) {
                AlarmScheduler.markReset(activity)
            }
            AlarmScheduler.schedule(activity)
            // 启动前台守护服务，防止 MIUI 划卡清除闹钟。
            KeepAliveService.start(activity)
            AppLog.append(activity, LogEntry(LogEntry.now(), "定时任务已开启", "成功"))
            Toast.makeText(activity, "已开启：${Utils.intervalLabel(interval)}", Toast.LENGTH_SHORT).show()
        } else {
            AlarmScheduler.cancel(activity)
            KeepAliveService.stop(activity)
            AppLog.append(activity, LogEntry(LogEntry.now(), "定时任务已关闭", "成功"))
            Toast.makeText(activity, "定时任务已关闭", Toast.LENGTH_SHORT).show()
        }
        updateCountdown()
    }

    private fun updateStatus() {
        view.find<TextView>(R.id.text_schedule_status).text =
            "${Utils.intervalLabel(interval)} · ${if (enabled) "运行中" else "已关闭"}"
    }

    private fun updateCountdown() {
        val countdown = view.find<TextView>(R.id.text_countdown)
        val absolute = view.find<TextView>(R.id.text_next_absolute)
        if (!enabled) {
            countdown.text = "--:--:--"
            absolute.text = "下次执行：未启用"
            return
        }
        val next = AlarmScheduler.nextRunMillis(activity)
        countdown.text = Utils.countdownText(next)
        absolute.text = "下次执行：${Utils.absoluteText(next)}"
    }
}
