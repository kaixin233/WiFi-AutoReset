package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * 仪表盘内的定时任务区块：周期、开关、倒计时与后台保活入口。
 * 逻辑沿用 AlarmManager 调度；UI 已合并进仪表盘，不再有独立页面。
 */
class AutomationController(
    private val activity: Activity,
    private val view: View,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var interval = AlarmScheduler.DEFAULT_INTERVAL_MINUTES
    private var active = false
    private var pendingAutoStart = false

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

        view.find<Button>(R.id.btn_autostart).setOnClickListener { confirmAutoStart() }
        view.find<Button>(R.id.btn_battery).setOnClickListener { confirmBattery() }

        selectPreset(interval)
        updateStatus()
        refreshKeepAlive()
    }

    fun onShow() {
        active = true
        updateCountdown()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun onHide() {
        active = false
        handler.removeCallbacks(tick)
    }

    fun onResume() {
        updateCountdown()
        refreshKeepAlive()
        if (pendingAutoStart) {
            pendingAutoStart = false
            AlertDialog.Builder(activity)
                .setTitle("自启动设置完成？")
                .setMessage("确认后将从界面移除该入口。")
                .setNegativeButton("还没完成", null)
                .setPositiveButton("已完成") { _, _ -> markAutoStartDone() }
                .show()
        }
    }

    fun onDestroy() = onHide()

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
                    Toast.makeText(
                        activity,
                        "请输入 ${AlarmScheduler.MIN_INTERVAL_MINUTES} ~ ${AlarmScheduler.MAX_INTERVAL_MINUTES} 之间的分钟数",
                        Toast.LENGTH_SHORT,
                    ).show()
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

        prefs.edit()
            .putBoolean(AlarmScheduler.KEY_ENABLED, enabled)
            .putInt(AlarmScheduler.KEY_INTERVAL_MINUTES, interval)
            .apply()

        if (enabled) {
            val base = AlarmScheduler.lastResetAt(activity)
            val next = AlarmScheduler.nextRunMillis(activity)
            if (base <= 0L || next <= System.currentTimeMillis() ||
                (wasEnabled && oldInterval != interval)
            ) {
                AlarmScheduler.markReset(activity)
            }
            AlarmScheduler.schedule(activity)
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

    private fun confirmAutoStart() {
        AlertDialog.Builder(activity)
            .setTitle("开启自启动")
            .setMessage("跳转后请允许本应用自启动与关联启动，返回后确认即可隐藏此入口。")
            .setNegativeButton("取消", null)
            .setNeutralButton("我已设置好") { _, _ -> markAutoStartDone() }
            .setPositiveButton("去设置") { _, _ ->
                pendingAutoStart = true
                BootGuard.openAutoStart(activity)
            }
            .show()
    }

    private fun confirmBattery() {
        if (isBatteryDone()) {
            refreshKeepAlive()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("设置省电策略")
            .setMessage("跳转后请选择「不优化」/「无限制」，返回后可确认已完成。")
            .setNegativeButton("取消", null)
            .setNeutralButton("我已设置好") { _, _ -> markBatteryDone() }
            .setPositiveButton("去设置") { _, _ -> BootGuard.openBatteryOptimization(activity) }
            .show()
    }

    private fun markAutoStartDone() {
        AlarmScheduler.prefs(activity).edit().putBoolean(KEY_AUTO_START_DONE, true).apply()
        refreshKeepAlive()
    }

    private fun markBatteryDone() {
        AlarmScheduler.prefs(activity).edit().putBoolean(KEY_BATTERY_DONE, true).apply()
        refreshKeepAlive()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val power = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(activity.packageName)
    }

    private fun isBatteryDone(): Boolean =
        isIgnoringBatteryOptimizations() ||
            AlarmScheduler.prefs(activity).getBoolean(KEY_BATTERY_DONE, false)

    /** 已完成的入口直接隐藏；两项都完成后整张卡片隐藏。 */
    private fun refreshKeepAlive() {
        if (isIgnoringBatteryOptimizations() &&
            !AlarmScheduler.prefs(activity).getBoolean(KEY_BATTERY_DONE, false)
        ) {
            markBatteryDone()
            return
        }
        val autoDone = AlarmScheduler.prefs(activity).getBoolean(KEY_AUTO_START_DONE, false)
        val batteryDone = isBatteryDone()
        view.find<View>(R.id.card_keepalive).visibility =
            if (autoDone && batteryDone) View.GONE else View.VISIBLE
        view.find<View>(R.id.btn_autostart).visibility =
            if (autoDone) View.GONE else View.VISIBLE
        view.find<View>(R.id.btn_battery).visibility =
            if (batteryDone) View.GONE else View.VISIBLE
    }

    companion object {
        private const val KEY_AUTO_START_DONE = "auto_start_done"
        private const val KEY_BATTERY_DONE = "battery_done"
    }
}
