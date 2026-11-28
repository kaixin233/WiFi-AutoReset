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
 * 仪表盘内的自动恢复区块：以固定周期为主，智能策略作为可选的辅助检测手段。
 *
 * - 固定周期：独立开关 + 周期，按最后一次重置时间链式排程。
 * - 智能辅助：独立开关，在固定周期之外检测云控限速，空闲时提前重置；不影响固定周期节奏。
 * 两者可同时开启，也可单独使用；任一开启都会拉起前台守护服务保住闹钟。
 */
class AutomationController(
    private val activity: Activity,
    private val view: View,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var smartAssist = false
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
        enabled = AlarmScheduler.isEnabled(activity)
        smartAssist = SmartScheduler.isEnabled(activity)
        interval = prefs.getInt(AlarmScheduler.KEY_INTERVAL_MINUTES, AlarmScheduler.DEFAULT_INTERVAL_MINUTES)
            .coerceIn(AlarmScheduler.MIN_INTERVAL_MINUTES, AlarmScheduler.MAX_INTERVAL_MINUTES)

        view.find<Switch>(R.id.switch_enabled).apply {
            isChecked = enabled
            setOnCheckedChangeListener { _, checked ->
                enabled = checked
                updateStatus()
                updateCountdown()
            }
        }
        view.find<Switch>(R.id.switch_smart).apply {
            isChecked = smartAssist
            setOnCheckedChangeListener { _, checked ->
                smartAssist = checked
                updateStatus()
                updateCountdown()
            }
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
        updateCountdown()
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

    /** 一次保存两项独立配置：固定周期为主，智能辅助可选。 */
    private fun save() {
        val prefs = AlarmScheduler.prefs(activity)
        val wasFixed = AlarmScheduler.isEnabled(activity)
        val wasSmart = SmartScheduler.isEnabled(activity)
        val oldInterval = prefs.getInt(
            AlarmScheduler.KEY_INTERVAL_MINUTES,
            AlarmScheduler.DEFAULT_INTERVAL_MINUTES,
        )

        prefs.edit()
            .putBoolean(AlarmScheduler.KEY_ENABLED, enabled)
            .putBoolean(SmartScheduler.KEY_SMART_ENABLED, smartAssist)
            .putInt(AlarmScheduler.KEY_INTERVAL_MINUTES, interval)
            .apply()

        // 固定周期：基准过期或周期变更时从当前时刻重新起算，然后排程。
        if (enabled) {
            val base = AlarmScheduler.lastResetAt(activity)
            val next = AlarmScheduler.nextRunMillis(activity)
            if (base <= 0L || next <= System.currentTimeMillis() ||
                (wasFixed && oldInterval != interval)
            ) {
                AlarmScheduler.markReset(activity)
            }
            AlarmScheduler.schedule(activity)
        } else {
            AlarmScheduler.cancel(activity)
        }

        // 智能辅助：独立启停，开启时重新采样，关闭时清空判定状态。
        if (smartAssist) {
            if (!wasSmart) SmartScheduler.resetState(activity)
            SmartScheduler.scheduleSample(activity, 5_000L)
        } else {
            SmartScheduler.cancelAll(activity)
            SmartScheduler.resetState(activity)
        }

        // 守护服务：任一启用即保活，全部关闭才停止。
        if (enabled || smartAssist) {
            KeepAliveService.start(activity)
        } else {
            KeepAliveService.stop(activity)
        }

        val parts = mutableListOf<String>()
        parts += if (enabled) "周期 ${Utils.intervalLabel(interval)}" else "周期关闭"
        parts += if (smartAssist) "智能辅助开" else "智能辅助关"
        AppLog.append(activity, LogEntry(LogEntry.now(), "自动任务已保存", parts.joinToString("，")))
        Toast.makeText(activity, parts.joinToString(" · "), Toast.LENGTH_SHORT).show()

        updateStatus()
        updateCountdown()
    }

    private fun updateStatus() {
        val status = view.find<TextView>(R.id.text_schedule_status)
        status.text = if (enabled) "${Utils.intervalLabel(interval)} · 运行中" else "固定周期 · 已关闭"
    }

    private fun updateCountdown() {
        // 固定周期倒计时始终按主任务显示；智能辅助状态单独一行展示。
        view.find<TextView>(R.id.text_smart_status).text = SmartScheduler.statusText(activity)
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
