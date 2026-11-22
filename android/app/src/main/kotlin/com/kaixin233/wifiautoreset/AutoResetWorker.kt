package com.kaixin233.wifiautoreset

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 整点执行恢复出厂设置，执行后自动排下一次。 */
class AutoResetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dryRun = prefs.getBoolean(KEY_DRY_RUN, true)
        val resultText: String
        val detail: String
        try {
            if (dryRun) {
                resultText = "演练"
                detail = "未发送请求（dryRun=true）"
            } else {
                val body = MifiClient.factoryReset()
                resultText = if (body.contains("success", ignoreCase = true)) "成功" else "已发送"
                detail = body.take(300)
            }
            AppLog.append(
                applicationContext,
                LogEntry(LogEntry.now(), "恢复出厂设置", "定时", resultText, detail),
            )
        } catch (e: Exception) {
            AppLog.append(
                applicationContext,
                LogEntry(LogEntry.now(), "恢复出厂设置", "定时", "失败", e.message ?: "未知异常"),
            )
        }
        scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        const val PREFS = "wifi_auto_reset"
        const val KEY_ENABLED = "enabled"
        const val KEY_DRY_RUN = "dry_run"
        private const val WORK_NAME = "hourly_factory_reset"

        fun scheduleNext(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ENABLED, false)) return
            val next = Calendar.getInstance().apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.HOUR_OF_DAY, 1)
            }
            val delay = next.timeInMillis - System.currentTimeMillis()
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
