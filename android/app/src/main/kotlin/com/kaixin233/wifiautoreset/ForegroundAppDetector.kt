package com.kaixin233.wifiautoreset

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** 依赖「使用情况访问」权限读取前台应用包名。 */
class ForegroundAppDetector(private val context: Context) {
    private val usage by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun currentPackage(): String? {
        if (!hasPermission()) return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 10_000, now)
        val event = UsageEvents.Event()
        var found: String? = null
        var latest = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp > latest) {
                latest = event.timeStamp
                found = event.packageName
            }
        }
        return found
    }

    fun labelOf(packageName: String?): String {
        if (packageName == null) return "未知"
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
