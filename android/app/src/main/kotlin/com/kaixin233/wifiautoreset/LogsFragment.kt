package com.kaixin233.wifiautoreset

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class LogsFragment : Fragment(R.layout.fragment_logs) {

    private lateinit var adapter: ArrayAdapter<String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        view.find<ListView>(R.id.list_logs).adapter = adapter

        val switchLogs = view.find<SwitchCompat>(R.id.switch_logs)
        switchLogs.isChecked = AppLog.isEnabled(requireContext())
        switchLogs.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                AppLog.setEnabled(requireContext(), true)
                AppLog.append(
                    requireContext(),
                    LogEntry(LogEntry.now(), "日志设置", "手动", "已开启", "后续操作将写入执行日志"),
                )
            } else {
                AppLog.append(
                    requireContext(),
                    LogEntry(LogEntry.now(), "日志设置", "手动", "已关闭", "后续操作不再写入执行日志"),
                )
                AppLog.setEnabled(requireContext(), false)
            }
            refresh()
        }

        view.find<Button>(R.id.btn_clear).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("清空日志")
                .setMessage("确定要清空全部执行记录吗？此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空") { _, _ ->
                    AppLog.clear(requireContext())
                    refresh()
                }
                .show()
        }

        refresh()
    }

    private fun refresh() {
        val v = view ?: return
        val logs = AppLog.read(requireContext())
        adapter.clear()
        adapter.addAll(logs.map { entry ->
            buildString {
                appendLine("${entry.time}  ${entry.action} · ${entry.result}（${entry.mode}）")
                append(entry.detail)
            }
        })
        adapter.notifyDataSetChanged()
        v.find<TextView>(R.id.text_empty).visibility =
            if (logs.isEmpty()) View.VISIBLE else View.GONE
        v.find<TextView>(R.id.text_logs_hint).visibility =
            if (AppLog.isEnabled(requireContext())) View.GONE else View.VISIBLE
    }
}
