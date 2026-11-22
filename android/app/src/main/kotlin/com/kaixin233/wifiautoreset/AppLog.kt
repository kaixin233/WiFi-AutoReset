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

    fun toMap(): Map<String, Any> = mapOf(
        "time" to time,
        "action" to action,
        "mode" to mode,
        "result" to result,
        "detail" to detail,
    )

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

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun append(context: Context, entry: LogEntry) {
        val target = file(context)
        target.appendText(entry.toJson().toString() + "\n")
        val lines = target.readLines()
        if (lines.size > MAX_LINES) {
            target.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }

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

    fun clear(context: Context) {
        file(context).writeText("")
    }
}
