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
    private var smart = true
    private var interval = AlarmScheduler.DEFAULT_INTERVAL_MINUTES
    private var active = false

    private val presets = mapOf(
        R.id.chip_15 to 15,
        R.id.chip_30 to 30,
        R.id.chip_60 to 60,
        R.id.chip_120 to 120,
        R.id.chip_360 to 360,
        R.id.chip_720 to 720,
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
        smart = prefs.getBoolean(AlarmScheduler.KEY_SMART, true)
        interval = prefs.getInt(AlarmScheduler.KEY_INTERVAL_MINUTES, AlarmScheduler.DEFAULT_INTERVAL_MINUTES)
            .coerceIn(AlarmScheduler.MIN_INTERVAL_MINUTES, AlarmScheduler.MAX_INTERVAL_MINUTES)

        val switch = view.find<Switch>(R.id.switch_enabled)
        switch.isChecked = enabled
        switch.setOnCheckedChangeListener { _, checked ->
            enabled = checked
            updateStatus()
            updateCountdown()
        }

        val switchSmart = view.find<Switch>(R.id.switch_smart)
        switchSmart.isChecked = smart
        switchSmart.setOnCheckedChangeListener { _, checked ->
            smart = checked
            updateStatus()
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
        // 先落盘全部设置，再调度，确保 AlarmReceiver / BootReceiver 读到最新值。
        AlarmScheduler.prefs(activity).edit()
            .putBoolean(AlarmScheduler.KEY_ENABLED, enabled)
            .putBoolean(AlarmScheduler.KEY_SMART, smart)
            .putInt(AlarmScheduler.KEY_INTERVAL_MINUTES, interval)
            .apply()
        if (enabled) {
            AlarmScheduler.schedule(activity)
            AppLog.append(
                activity,
                LogEntry(
                    LogEntry.now(), "定时任务", "手动", "开启",
                    "每 $interval 分钟执行恢复出厂${if (smart) "（智能调度：繁忙延缓）" else ""}",
                ),
            )
        } else {
            AlarmScheduler.cancel(activity)
            AppLog.append(activity, LogEntry(LogEntry.now(), "定时任务", "手动", "关闭", "定时任务已关闭"))
        }
        Toast.makeText(
            activity,
            if (enabled) "已开启：${Utils.intervalLabel(interval)}${if (smart) " · 智能" else ""}" else "定时任务已关闭",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun updateStatus() {
        val smartText = if (smart) "智能调度（忙时延缓）" else "准时执行"
        view.find<TextView>(R.id.text_schedule_status).text =
            "${Utils.intervalLabel(interval)} · ${if (enabled) "运行中" else "已关闭"} · $smartText"
    }

    private fun updateCountdown() {
        view.find<TextView>(R.id.text_countdown).text =
            if (enabled) Utils.countdownText(interval) else "--:--:--"
        view.find<TextView>(R.id.text_next_absolute).text =
            if (enabled) "下次执行：${Utils.nextRunAbsolute(interval)}" else "下次执行：未启用"
    }
}
