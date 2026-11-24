package com.kaixin233.wifiautoreset

import android.content.Context
import android.net.TrafficStats

/**
 * 重置快照：记录「上次重置时刻」的系统累计收发字节与时间戳。
 *
 * 智能调度不再 sleep 采样——到点时只需再读一次累计计数器，与快照做差并除以
 * 经过秒数，即得到整个周期的【平均】网速。纯读内存计数器，零 sleep、零额外唤醒，
 * 足够判断「这段时间为空闲/疑似云控」还是「一直在被使用」。
 */
object ResetSnapshot {
    private const val PREFS = "reset_snapshot"
    private const val KEY_RX = "rx"
    private const val KEY_TX = "tx"
    private const val KEY_AT = "at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 每次成功执行重置后调用，记录新的基准点。 */
    fun mark(context: Context) {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx < 0 || tx < 0) {
            prefs(context).edit().clear().apply()
            return
        }
        prefs(context).edit()
            .putLong(KEY_RX, rx)
            .putLong(KEY_TX, tx)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    data class Average(
        val supported: Boolean,
        val elapsedMinutes: Long,
        val downBps: Long,
        val upBps: Long,
        val summary: String,
    )

    /**
     * 计算自上次重置以来的平均网速。无快照或计数器异常时返回 supported=false。
     */
    fun averageSinceReset(context: Context): Average {
        val p = prefs(context)
        val rx0 = p.getLong(KEY_RX, -1)
        val tx0 = p.getLong(KEY_TX, -1)
        val at0 = p.getLong(KEY_AT, -1)
        val rx1 = TrafficStats.getTotalRxBytes()
        val tx1 = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()

        if (rx0 < 0 || tx0 < 0 || at0 <= 0 || rx1 < 0 || tx1 < 0 || rx1 < rx0 || tx1 < tx0) {
            return Average(false, 0, 0, 0, "无有效基准快照，按准时模式执行")
        }
        val elapsedMs = (now - at0).coerceAtLeast(1_000L)
        val seconds = elapsedMs / 1000.0
        val downBps = ((rx1 - rx0) / seconds).toLong()
        val upBps = ((tx1 - tx0) / seconds).toLong()
        return Average(
            supported = true,
            elapsedMinutes = elapsedMs / 60_000L,
            downBps = downBps,
            upBps = upBps,
            summary = "距上次重置 ${elapsedMs / 60_000L} 分钟 · 平均下行 ${Utils.formatSpeed(downBps)} · 上行 ${Utils.formatSpeed(upBps)}",
        )
    }
}
