package com.kaixin233.wifiautoreset

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val time: String,
    val action: String,
    val mode: String,
    val result: String,
    val detail: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("time", time)
        put("action", action)
        put("mode", mode)
        put("result", result)
        put("detail", detail)
    }

    companion object {
        fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        fun fromJson(o: JSONObject) = LogEntry(
            o.optString("time"),
            o.optString("action"),
            o.optString("mode"),
            o.optString("result"),
            o.optString("detail"),
        )
    }
}

object AppLog {
    private const val FILE = "execution_log.jsonl"
    private const val MAX_LINES = 500
    private const val PREFS = "wifi_auto_reset"
    private const val KEY_ENABLED = "log_enabled"

    /** 应用级上下文，用于后台线程/Receiver 无 Activity 时写日志。 */
    @Volatile
    var appContext: Context? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 无 Context 场景（如原生客户端内部）写日志；未初始化时静默跳过。 */
    fun appendGlobal(entry: LogEntry) {
        appContext?.let { append(it, entry) }
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** 日志总开关，默认开启。关闭后所有 append 都直接跳过。 */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 后台定时任务与 UI 线程可能同时写，必须串行化，避免行交错/文件损坏。 */
    @Synchronized
    fun append(context: Context, entry: LogEntry) {
        if (!isEnabled(context)) return
        val target = file(context)
        target.appendText(entry.toJson().toString() + "\n")
        val lines = target.readLines()
        if (lines.size > MAX_LINES) {
            target.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }

    @Synchronized
    fun read(context: Context): List<LogEntry> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        return target.readLines().reversed().mapNotNull { line ->
            try {
                LogEntry.fromJson(JSONObject(line))
            } catch (e: Exception) {
                null
            }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).writeText("")
    }
}
