package com.kaixin233.wifiautoreset

import android.net.TrafficStats
import android.os.Handler
import android.os.Looper

/** 每秒采样一次总收发字节差，换算成 B/s。 */
class SpeedMonitor(private val onSample: (Long, Long) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val now = System.currentTimeMillis()
            if (lastAt > 0 && rx >= 0 && tx >= 0 && lastRx >= 0 && lastTx >= 0) {
                val seconds = (now - lastAt) / 1000.0
                if (seconds > 0) {
                    onSample(
                        ((rx - lastRx) / seconds).toLong().coerceAtLeast(0),
                        ((tx - lastTx) / seconds).toLong().coerceAtLeast(0),
                    )
                }
            }
            lastRx = rx
            lastTx = tx
            lastAt = now
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (running) return
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastAt = System.currentTimeMillis()
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}
