package com.kaixin233.wifiautoreset

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通过无障碍服务监听窗口切换事件来识别前台应用。
 *
 * 相比 UsageStatsManager，无障碍服务不依赖「使用情况访问」权限，
 * 侧载的未知应用也可在系统设置中手动开启，且能实时拿到 packageName。
 */
class ForegroundAppDetector(private val context: Context) {
    companion object {
        private const val PREFS = "foreground_app"
        private const val KEY_PACKAGE = "package"
        private const val KEY_AT = "at"

        @Volatile
        private var cachedPackage: String? = null

        @Volatile
        private var cachedAt: String = ""

        /** 由无障碍服务在收到窗口事件时调用。相同包名不重复写入。 */
        fun observe(context: Context, packageName: String) {
            if (packageName.isBlank() || packageName == cachedPackage) return
            cachedPackage = packageName
            cachedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PACKAGE, packageName)
                .putString(KEY_AT, cachedAt)
                .apply()
        }
    }

    private val component by lazy {
        ComponentName(context, ForegroundAccessibilityService::class.java).flattenToString()
    }

    /** 系统「无障碍」设置中是否已启用本应用的服务。 */
    fun hasPermission(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    fun currentPackage(): String? {
        if (!hasPermission()) return null
        val stored = cachedPackage ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACKAGE, null)
        return stored?.takeIf { it.isNotBlank() }
    }

    fun lastTime(): String {
        if (cachedAt.isNotBlank()) return cachedAt
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_AT, "") ?: ""
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
