package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView

/** 日志页面控制器。 */
class LogsPage(
    private val activity: Activity,
    override val view: View,
) : PageController {

    private lateinit var adapter: ArrayAdapter<String>

    init {
        adapter = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1)
        view.find<ListView>(R.id.list_logs).adapter = adapter

        val switchLogs = view.find<Switch>(R.id.switch_logs)
        switchLogs.isChecked = AppLog.isEnabled(activity)
        switchLogs.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                AppLog.setEnabled(activity, true)
                AppLog.append(activity, LogEntry(LogEntry.now(), "日志设置", "手动", "已开启", "后续操作将写入执行日志"))
            } else {
                AppLog.append(activity, LogEntry(LogEntry.now(), "日志设置", "手动", "已关闭", "后续操作不再写入执行日志"))
                AppLog.setEnabled(activity, false)
            }
            refresh()
        }

        view.find<Button>(R.id.btn_clear).setOnClickListener {
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

    override fun onShow() = refresh()

    private fun refresh() {
        val logs = AppLog.read(activity)
        adapter.clear()
        adapter.addAll(logs.map { entry ->
            buildString {
                appendLine("${entry.time}  ${entry.action} · ${entry.result}（${entry.mode}）")
                append(entry.detail)
            }
        })
        adapter.notifyDataSetChanged()
        view.find<TextView>(R.id.text_empty).visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        view.find<TextView>(R.id.text_logs_hint).visibility =
            if (AppLog.isEnabled(activity)) View.GONE else View.VISIBLE
    }
}
