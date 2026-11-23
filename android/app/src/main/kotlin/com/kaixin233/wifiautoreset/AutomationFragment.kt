package com.kaixin233.wifiautoreset

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.button.MaterialButton

class AutomationFragment : Fragment(R.layout.fragment_automation) {

    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var interval = AutoResetWorker.DEFAULT_INTERVAL_MINUTES

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
            if (!isAdded) return
            updateCountdown()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(
            AutoResetWorker.PREFS, Context.MODE_PRIVATE,
        )
        enabled = prefs.getBoolean(AutoResetWorker.KEY_ENABLED, false)
        interval = prefs.getInt(
            AutoResetWorker.KEY_INTERVAL_MINUTES,
            AutoResetWorker.DEFAULT_INTERVAL_MINUTES,
        ).coerceIn(AutoResetWorker.MIN_INTERVAL_MINUTES, AutoResetWorker.MAX_INTERVAL_MINUTES)

        view.find<SwitchCompat>(R.id.switch_enabled).isChecked = enabled
        selectPreset(interval)

        view.find<SwitchCompat>(R.id.switch_enabled).setOnCheckedChangeListener { _, checked ->
            enabled = checked
            updateStatus()
            updateCountdown()
        }
        presets.forEach { (id, minutes) ->
            view.find<Chip>(id).setOnClickListener {
                interval = minutes
                selectPreset(minutes)
                updateStatus()
                updateCountdown()
            }
        }
        view.find<Chip>(R.id.chip_custom).setOnClickListener { showCustomDialog() }
        view.find<MaterialButton>(R.id.btn_save).setOnClickListener { save() }

        updateStatus()
        handler.post(tick)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(tick)
        super.onDestroyView()
    }

    private fun selectPreset(minutes: Int) {
        val v = view ?: return
        val isPreset = presets.values.contains(minutes)
        presets.forEach { (id, value) -> v.find<Chip>(id).isChecked = value == minutes }
        val custom = v.find<Chip>(R.id.chip_custom)
        custom.isChecked = !isPreset
        // 切回预设周期时必须还原文案，否则自定义分钟数会残留。
        custom.text = if (isPreset) "自定义" else "$minutes 分"
    }

    private fun showCustomDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(interval.toString())
            setSelection(text.length)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("自定义执行周期")
            .setMessage("请输入 ${AutoResetWorker.MIN_INTERVAL_MINUTES} ~ ${AutoResetWorker.MAX_INTERVAL_MINUTES} 之间的分钟数")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val parsed = input.text.toString().trim().toIntOrNull()
                if (parsed == null || parsed !in AutoResetWorker.MIN_INTERVAL_MINUTES..AutoResetWorker.MAX_INTERVAL_MINUTES) {
                    Toast.makeText(
                        requireContext(),
                        "请输入 ${AutoResetWorker.MIN_INTERVAL_MINUTES} ~ ${AutoResetWorker.MAX_INTERVAL_MINUTES} 之间的分钟数",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@setPositiveButton
                }
                interval = parsed
                selectPreset(parsed)
                updateStatus()
                updateCountdown()
            }
            .show()
    }

    private fun save() {
        val prefs = requireContext().getSharedPreferences(
            AutoResetWorker.PREFS, Context.MODE_PRIVATE,
        )
        prefs.edit()
            .putBoolean(AutoResetWorker.KEY_ENABLED, enabled)
            .putInt(AutoResetWorker.KEY_INTERVAL_MINUTES, interval)
            .apply()
        if (enabled) {
            AutoResetWorker.scheduleNext(requireContext())
            AppLog.append(
                requireContext(),
                LogEntry(LogEntry.now(), "定时任务", "手动", "开启", "每 $interval 分钟执行恢复出厂"),
            )
        } else {
            AutoResetWorker.cancel(requireContext())
            AppLog.append(
                requireContext(),
                LogEntry(LogEntry.now(), "定时任务", "手动", "关闭", "定时任务已关闭"),
            )
        }
        Toast.makeText(
            requireContext(),
            if (enabled) "已开启：${Utils.intervalLabel(interval)}" else "定时任务已关闭",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun updateStatus() {
        val v = view ?: return
        v.find<TextView>(R.id.text_schedule_status).text =
            "${Utils.intervalLabel(interval)} · ${if (enabled) "运行中" else "已关闭"} · 周期起点后 5 秒执行"
    }

    private fun updateCountdown() {
        val v = view ?: return
        v.find<TextView>(R.id.text_countdown).text =
            if (enabled) Utils.countdownText(interval) else "--:--:--"
    }
}
