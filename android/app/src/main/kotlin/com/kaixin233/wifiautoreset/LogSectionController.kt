package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * 仪表盘内可折叠的执行日志区块：默认收起，展开后显示最近 8 条记录。
 */
class LogSectionController(
    private val activity: Activity,
    private val view: View,
) {
    private val logList = view.find<LinearLayout>(R.id.log_list)
    private var expanded = false

    init {
        expanded = AlarmScheduler.prefs(activity).getBoolean(KEY_EXPANDED, false)
        applyExpanded()

        view.find<View>(R.id.header_logs).setOnClickListener {
            expanded = !expanded
            AlarmScheduler.prefs(activity).edit().putBoolean(KEY_EXPANDED, expanded).apply()
            applyExpanded()
            if (expanded) refresh()
        }

        val switchLogs = view.find<Switch>(R.id.switch_logs)
        switchLogs.isChecked = AppLog.isEnabled(activity)
        switchLogs.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                AppLog.setEnabled(activity, true)
                AppLog.append(activity, LogEntry(LogEntry.now(), "日志已开启", "成功"))
            } else {
                AppLog.append(activity, LogEntry(LogEntry.now(), "日志已关闭", "成功"))
                AppLog.setEnabled(activity, false)
            }
            refresh()
        }

        view.find<View>(R.id.btn_clear).setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("清空日志")
                .setMessage("确定要清空全部执行记录吗？此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空") { _, _ ->
                    AppLog.clear(activity)
                    refresh()
                }
                .show()
        }
        refresh()
    }

    fun onShow() = refresh()

    fun onResume() = refresh()

    private fun applyExpanded() {
        view.find<View>(R.id.logs_content).visibility =
            if (expanded) View.VISIBLE else View.GONE
        view.find<TextView>(R.id.text_logs_toggle).text = if (expanded) "收起" else "展开"
    }

    private fun refresh() {
        val logs = AppLog.read(activity).take(MAX_VISIBLE)
        logList.removeAllViews()
        logs.forEach { entry ->
            val row = LayoutInflater.from(activity).inflate(R.layout.item_log, logList, false)
            row.find<TextView>(R.id.log_time).text = entry.time
            row.find<TextView>(R.id.log_action).text = entry.action
            val badge = row.find<TextView>(R.id.log_result)
            badge.text = entry.result
            val color = when {
                entry.result.contains("失败") -> R.color.danger
                entry.result.contains("成功") || entry.result == "已开启" -> R.color.success
                else -> R.color.nav_unchecked
            }
            badge.setTextColor(activity.resources.getColor(color, activity.theme))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 8 }
            logList.addView(row, lp)
        }
        view.find<View>(R.id.text_empty).visibility =
            if (logs.isEmpty() && expanded) View.VISIBLE else View.GONE
        view.find<View>(R.id.text_logs_hint).visibility =
            if (AppLog.isEnabled(activity)) View.GONE else View.VISIBLE
    }

    companion object {
        private const val KEY_EXPANDED = "logs_expanded"
        private const val MAX_VISIBLE = 8
    }
}
