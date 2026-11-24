package com.kaixin233.wifiautoreset

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * 智能调度检测器（零开销版）：不再 sleep 采样，而是基于「自上次重置以来的平均网速」
 * 判断这一周期设备是空闲还是繁忙。判断所需数据来自 ResetSnapshot（纯读系统计数器）。
 *
 * - 平均速率 >= 500 KB/s：这段周期一直在被使用 → 繁忙，延缓重置，避免打断。
 * - 平均速率 < 500 KB/s：接近空闲；若长期低速则说明疑似被云控 → 适合重置。
 * - 用户已知设备约 90 分钟后会被云控：以「整个周期平均」判断足够，无需精确到秒。
 *
 * 屏幕状态仅写入日志辅助解释，不单独决定是否延缓，避免屏幕常亮导致永不重置。
 */
object IdleDetector {
    /** 500 KB/s：低于该平均速率视为空闲/疑似云控，高于则视为繁忙。 */
    const val BUSY_THRESHOLD_BPS = 500L * 1024

    data class State(
        val supported: Boolean,
        val screenOn: Boolean,
        val busy: Boolean,
        val summary: String,
    )

    fun detect(context: Context, average: ResetSnapshot.Average): State {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val screenOn = pm?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) it.isInteractive
            else @Suppress("DEPRECATION") it.isScreenOn
        } ?: true

        if (!average.supported) {
            return State(false, screenOn, false, average.summary)
        }

        val busy = average.downBps >= BUSY_THRESHOLD_BPS || average.upBps >= BUSY_THRESHOLD_BPS
        val screenText = if (screenOn) "屏幕亮" else "屏幕灭"
        val summary = "$screenText · ${average.summary}"
        return State(true, screenOn, busy, summary)
    }
}
