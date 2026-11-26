package com.kaixin233.wifiautoreset

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView

/** 日志页面控制器：卡片式展示执行记录，按结果着色，便于快速浏览。 */
class LogsPage(
    private val activity: Activity,
    override val view: View,
) : PageController {

    private inner class LogAdapter : BaseAdapter() {
        private var items: List<LogEntry> = emptyList()

        fun submit(data: List<LogEntry>) {
            items = data
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(activity)
                .inflate(R.layout.item_log, parent, false)
            val item = getItem(position)

            row.find<TextView>(R.id.log_time).text = item.time
            row.find<TextView>(R.id.log_action).text = item.action

            val badge = row.find<TextView>(R.id.log_result)
            badge.text = item.result
            val color = when {
                item.result.contains("失败") -> R.color.danger
                item.result.contains("成功") || item.result == "已开启" -> R.color.success
                else -> R.color.nav_unchecked
            }
            badge.setTextColor(activity.resources.getColor(color, activity.theme))
            return row
        }
    }

    private val adapter = LogAdapter()

    init {
        view.find<ListView>(R.id.list_logs).adapter = adapter

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
        adapter.submit(logs)
        view.find<TextView>(R.id.text_empty).visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        view.find<TextView>(R.id.text_logs_hint).visibility =
            if (AppLog.isEnabled(activity)) View.GONE else View.VISIBLE
    }
}
