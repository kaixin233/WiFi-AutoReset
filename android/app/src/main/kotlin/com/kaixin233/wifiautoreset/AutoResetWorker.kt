package com.kaixin233.wifiautoreset

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** 按自定义周期执行恢复出厂设置，执行后自动排下一次。 */
class AutoResetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val resultText: String
        val detail: String
        try {
            val body = MifiClient.factoryReset()
            resultText = if (body.contains("success", ignoreCase = true)) "成功" else "已发送"
            detail = body.take(300)
        } catch (e: Exception) {
            AppLog.append(
                applicationContext,
                LogEntry(LogEntry.now(), "恢复出厂设置", "定时", "失败", e.message ?: "未知异常"),
            )
            scheduleNext(applicationContext)
            return Result.success()
        }
        AppLog.append(
            applicationContext,
            LogEntry(LogEntry.now(), "恢复出厂设置", "定时", resultText, detail),
        )
        scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        const val PREFS = "wifi_auto_reset"
        const val KEY_ENABLED = "enabled"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val DEFAULT_INTERVAL_MINUTES = 60
        const val MIN_INTERVAL_MINUTES = 5
        const val MAX_INTERVAL_MINUTES = 1440

        // 保留原有 work 名称，确保覆盖安装后 REPLACE 能接管旧版本遗留的定时任务。
        private const val WORK_NAME = "hourly_factory_reset"
        // 与界面倒计时保持一致：每个周期起点后 5 秒执行。
        private const val OFFSET_MS = 5_000L

        fun getIntervalMinutes(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
                .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        }

        /** 下一个周期起点 + 5 秒，按 Unix 纪元对齐，60 分钟时即为整点后 5 秒。 */
        fun nextRunMillis(context: Context): Long {
            val slotMs = getIntervalMinutes(context) * 60_000L
            val now = System.currentTimeMillis()
            return ((now / slotMs) + 1) * slotMs + OFFSET_MS
        }

        fun scheduleNext(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ENABLED, false)) return
            val delay = (nextRunMillis(context) - System.currentTimeMillis())
                .coerceAtLeast(1_000L)
            val request = OneTimeWorkRequestBuilder<AutoResetWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
