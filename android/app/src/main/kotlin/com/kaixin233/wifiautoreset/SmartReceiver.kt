package com.kaixin233.wifiautoreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * 智能策略调控的闹钟接收器。
 *
 * 采样 → 连续 2 次低速 → 30 秒复核 → 等待空闲 → 执行重置，
 * 每一步都由系统闹钟驱动，持有短暂 WakeLock 保证后台执行完成。
 */
class SmartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(
                SmartScheduler.ACTION_SAMPLE,
                SmartScheduler.ACTION_VERIFY,
                SmartScheduler.ACTION_CHECK,
                SmartScheduler.ACTION_FINAL,
                SmartScheduler.ACTION_CANCEL,
            )
        ) return

        val app = context.applicationContext
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val lock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiFiAutoReset:smart")
        try {
            lock?.acquire(5 * 60_000L)
        } catch (_: Exception) {
            // 已持有或系统拒绝，忽略；下方逻辑照常执行。
        }

        Utils.asyncUi(
            {
                try {
                    when (action) {
                        SmartScheduler.ACTION_SAMPLE -> sample(app)
                        SmartScheduler.ACTION_VERIFY -> verify(app)
                        SmartScheduler.ACTION_CHECK -> checkIdle(app)
                        SmartScheduler.ACTION_FINAL -> {
                            if (SmartScheduler.prefs(app).getBoolean(SmartScheduler.KEY_PENDING_RESET, false)) {
                                doReset(app)
                            }
                        }
                        SmartScheduler.ACTION_CANCEL -> cancel(app)
                    }
                } catch (e: Exception) {
                    // 单点异常不能中断闹钟链，下个周期仍会继续采样。
                    SmartScheduler.scheduleSample(app)
                } finally {
                    try {
                        if (lock != null && lock.isHeld) lock.release()
                    } catch (_: Exception) {
                        // 超时后系统已自动释放，忽略。
                    }
                }
            },
            { /* 结果均在后台线程处理，UI 回调无需操作 */ },
        )
    }

    /** 每 2 分钟：累计流量差值 → 平均速率。 */
    private fun sample(app: Context) {
        if (!SmartScheduler.isEnabled(app)) return
        // 先排下一次，保证任何分支返回后闹钟链都不断。
        SmartScheduler.scheduleSample(app)
        if (!SmartScheduler.isWifi(app)) return

        val total = SmartScheduler.totalBytes()
        if (total < 0) return
        val now = System.currentTimeMillis()
        val prefs = SmartScheduler.prefs(app)
        val lastAt = prefs.getLong(SmartScheduler.KEY_LAST_AT, 0L)
        val lastTotal = prefs.getLong(SmartScheduler.KEY_LAST_TOTAL, -1L)
        val gap = now - lastAt

        if (lastAt <= 0L || lastTotal < 0L || total < lastTotal ||
            gap < 60_000L || gap > 10 * 60_000L
        ) {
            prefs.edit()
                .putLong(SmartScheduler.KEY_LAST_AT, now)
                .putLong(SmartScheduler.KEY_LAST_TOTAL, total)
                .apply()
            return
        }

        val bytes = total - lastTotal
        val avg = bytes / (gap / 1000.0)
        prefs.edit()
            .putLong(SmartScheduler.KEY_LAST_AT, now)
            .putLong(SmartScheduler.KEY_LAST_TOTAL, total)
            .apply()

        val valid = bytes >= SmartScheduler.VALID_WINDOW_BYTES
        val low = avg < SmartScheduler.LOW_BYTES_PER_SEC
        if (valid && low) {
            val streak = prefs.getInt(SmartScheduler.KEY_LOW_STREAK, 0) + 1
            prefs.edit().putInt(SmartScheduler.KEY_LOW_STREAK, streak).apply()
            if (streak >= 2) SmartScheduler.scheduleVerify(app)
        } else {
            prefs.edit().putInt(SmartScheduler.KEY_LOW_STREAK, 0).apply()
            if (valid && avg >= SmartScheduler.RECOVER_BYTES_PER_SEC &&
                SmartScheduler.suspectSince(app) > 0L
            ) {
                clearSuspect(app)
                Notifier.notifyLow(app, "网速已恢复", "本轮智能重置已取消")
            }
        }
    }

    /** 30 秒短窗复核：确认峰值也没有超过 500 KB/s。 */
    private fun verify(app: Context) {
        if (!SmartScheduler.isEnabled(app) || !SmartScheduler.isWifi(app)) return
        val total = SmartScheduler.totalBytes()
        if (total < 0) return
        val prefs = SmartScheduler.prefs(app)
        val lastAt = prefs.getLong(SmartScheduler.KEY_LAST_AT, 0L)
        val lastTotal = prefs.getLong(SmartScheduler.KEY_LAST_TOTAL, -1L)
        val gap = System.currentTimeMillis() - lastAt
        if (lastAt <= 0L || lastTotal < 0L || total < lastTotal || gap < 15_000L || gap > 120_000L) return

        val bytes = total - lastTotal
        val avg = bytes / (gap / 1000.0)
        if (bytes >= 1024L * 1024L && avg < SmartScheduler.LOW_BYTES_PER_SEC) {
            if (SmartScheduler.suspectSince(app) == 0L) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(SmartScheduler.KEY_SUSPECT_SINCE, now).apply()
                Notifier.notifyLow(app, "检测到云控限速", "正在等待你空闲时自动重置")
            }
            SmartScheduler.scheduleCheck(app)
        } else {
            prefs.edit().putInt(SmartScheduler.KEY_LOW_STREAK, 0).apply()
        }
    }

    /** 每 30 秒检查是否满足空闲条件，最多等 15 分钟。 */
    private fun checkIdle(app: Context) {
        if (!SmartScheduler.isEnabled(app)) return
        SmartScheduler.scheduleCheck(app)
        if (SmartScheduler.suspectSince(app) == 0L) return
        val prefs = SmartScheduler.prefs(app)
        if (prefs.getBoolean(SmartScheduler.KEY_PENDING_RESET, false)) return
        if (!SmartScheduler.canReset(app)) return

        val waited = System.currentTimeMillis() - SmartScheduler.suspectSince(app)
        if (waited > SmartScheduler.MAX_WAIT_MS) {
            clearSuspect(app)
            Notifier.notifyLow(app, "智能重置已跳过", "等待空闲超时，本轮不重置")
            return
        }

        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val screenOn = power?.isInteractive ?: true
        val total = SmartScheduler.totalBytes()
        val now = System.currentTimeMillis()
        val checkAt = prefs.getLong(SmartScheduler.KEY_CHECK_AT, 0L)
        val checkTotal = prefs.getLong(SmartScheduler.KEY_CHECK_TOTAL, -1L)
        var idleByTraffic = false
        if (total >= 0 && checkAt > 0L && checkTotal >= 0L && total >= checkTotal) {
            val gap = now - checkAt
            if (gap >= 20_000L) {
                idleByTraffic = (total - checkTotal) / (gap / 1000.0) < SmartScheduler.IDLE_BYTES_PER_SEC
            }
        }
        prefs.edit()
            .putLong(SmartScheduler.KEY_CHECK_AT, now)
            .putLong(SmartScheduler.KEY_CHECK_TOTAL, total)
            .apply()

        if (!screenOn || idleByTraffic) {
            if (screenOn) {
                // 屏幕亮着但已空闲：给 10 秒反悔窗口。
                prefs.edit().putBoolean(SmartScheduler.KEY_PENDING_RESET, true).apply()
                Notifier.smartPending(app)
                SmartScheduler.scheduleFinal(app)
            } else {
                doReset(app)
            }
        }
    }

    private fun cancel(app: Context) {
        clearSuspect(app)
        Notifier.notifyLow(app, "已取消智能重置", "本轮不会执行重置")
    }

    private fun clearSuspect(app: Context) {
        SmartScheduler.prefs(app).edit()
            .putLong(SmartScheduler.KEY_SUSPECT_SINCE, 0L)
            .putInt(SmartScheduler.KEY_LOW_STREAK, 0)
            .putBoolean(SmartScheduler.KEY_PENDING_RESET, false)
            .apply()
    }

    /** 执行真实恢复出厂，并刷新冷却与每日计数。 */
    private fun doReset(app: Context) {
        try {
            MifiClient.factoryReset()
            AppLog.append(app, LogEntry(LogEntry.now(), "智能重置", "成功"))
            SmartScheduler.noteReset(app)
            Notifier.notify(app, "智能重置已执行", "检测到云控限速，随身 WiFi 已恢复出厂，即将重启断网")
        } catch (e: Exception) {
            AppLog.append(app, LogEntry(LogEntry.now(), "智能重置", "失败"))
            SmartScheduler.prefs(app).edit()
                .putBoolean(SmartScheduler.KEY_PENDING_RESET, false)
                .apply()
            Notifier.notify(app, "智能重置失败", e.message ?: "未知异常")
            SmartScheduler.scheduleSample(app)
        }
    }
}
