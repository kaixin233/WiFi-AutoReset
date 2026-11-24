package com.kaixin233.wifiautoreset

import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.PowerManager

/**
 * 智能调度检测器：判断「手机 + 随身 WiFi」当前是否繁忙。
 *
 * 判定依据（来自实测经验）：
 * - 实时速率 >= 500 KB/s：设备正在被大流量使用（或上行密集），此时重置会打断使用 → 繁忙，延缓。
 * - 实时速率 < 500 KB/s：设备接近空闲；若长期低速则说明可能被云控限速 → 适合执行重置。
 * - 屏幕状态作为辅助信号写入日志，便于解释决策；不单独决定是否延缓，避免屏幕常亮导致永不重置。
 *
 * 采样在后台线程完成（含 2 秒间隔），不阻塞主线程与广播 onReceive。
 */
object IdleDetector {
    /** 500 KB/s：低于该速率视为空闲/疑似云控，高于则视为繁忙。 */
    const val BUSY_THRESHOLD_BPS = 500L * 1024
    private const val SAMPLE_MS = 2_000L

    data class State(
        val supported: Boolean,
        val screenOn: Boolean,
        val downBps: Long,
        val upBps: Long,
        val busy: Boolean,
        val summary: String,
    )

    fun detect(context: Context): State {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val screenOn = pm?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) it.isInteractive
            else @Suppress("DEPRECATION") it.isScreenOn
        } ?: true

        val rx0 = TrafficStats.getTotalRxBytes()
        val tx0 = TrafficStats.getTotalTxBytes()
        Thread.sleep(SAMPLE_MS)
        val rx1 = TrafficStats.getTotalRxBytes()
        val tx1 = TrafficStats.getTotalTxBytes()

        // 计数器不支持（返回负值）时降级：仅凭屏幕状态无法可靠判定，返回 supported=false，
        // 由调用方按「非智能」直接执行，保证功能可用。
        if (rx0 < 0 || tx0 < 0 || rx1 < rx0 || tx1 < tx0) {
            return State(
                supported = false,
                screenOn = screenOn,
                downBps = 0,
                upBps = 0,
                busy = false,
                summary = "流量计数器不可用，跳过智能判断",
            )
        }

        val seconds = SAMPLE_MS / 1000.0
        val downBps = ((rx1 - rx0) / seconds).toLong()
        val upBps = ((tx1 - tx0) / seconds).toLong()
        val busy = downBps >= BUSY_THRESHOLD_BPS || upBps >= BUSY_THRESHOLD_BPS
        val screenText = if (screenOn) "屏幕亮" else "屏幕灭"
        val cloudHint = if (!busy && downBps in 1 until (50L * 1024)) "，疑似被云控限速" else ""
        val summary = buildString {
            append(screenText)
            append(" · 下行 ")
            append(Utils.formatSpeed(downBps))
            append(" · 上行 ")
            append(Utils.formatSpeed(upBps))
            append(cloudHint)
        }
        return State(
            supported = true,
            screenOn = screenOn,
            downBps = downBps,
            upBps = upBps,
            busy = busy,
            summary = summary,
        )
    }
}
