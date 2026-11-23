package com.kaixin233.wifiautoreset

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class NativeBridge(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val methodChannel = MethodChannel(messenger, CHANNEL)
    private val eventChannel = EventChannel(messenger, SPEED_CHANNEL)
    private val detector = ForegroundAppDetector(context)
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speedSink: EventChannel.EventSink? = null
    private var monitor: SpeedMonitor? = null

    companion object {
        const val CHANNEL = "com.kaixin233.wifiautoreset/native"
        const val SPEED_CHANNEL = "com.kaixin233.wifiautoreset/speed"

        fun attach(context: Context, messenger: BinaryMessenger): NativeBridge {
            val bridge = NativeBridge(context, messenger)
            bridge.methodChannel.setMethodCallHandler(bridge)
            bridge.eventChannel.setStreamHandler(bridge)
            return bridge
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "hasAccessibilityAccess" -> result.success(detector.hasPermission())

            "requestAccessibilityAccess" -> {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                result.success(null)
            }

            "foregroundApp" -> {
                val pkg = detector.currentPackage()
                result.success(
                    mapOf(
                        "package" to (pkg ?: ""),
                        "label" to detector.labelOf(pkg),
                        "time" to detector.lastTime().ifBlank { LogEntry.now() },
                    ),
                )
            }

            "deviceInfo" -> async(result) { MifiClient.deviceInfo() }
            "statusInfo" -> async(result) { MifiClient.statusInfo() }
            "gatewayReachable" -> async(result) { MifiClient.reachable() }

            "factoryReset" -> {
                async(result) {
                    performAction("恢复出厂设置") { MifiClient.factoryReset() }
                }
            }

            "reboot" -> {
                async(result) { performAction("重启设备") { MifiClient.reboot() } }
            }

            "resetTraffic" -> {
                async(result) { performAction("重置流量统计") { MifiClient.resetTraffic() } }
            }

            "configureSchedule" -> {
                val enabled = call.argument<Boolean>("enabled") ?: false
                val interval = (call.argument<Int>("intervalMinutes") ?: AutoResetWorker.DEFAULT_INTERVAL_MINUTES)
                    .coerceIn(AutoResetWorker.MIN_INTERVAL_MINUTES, AutoResetWorker.MAX_INTERVAL_MINUTES)
                val prefs = context.getSharedPreferences(AutoResetWorker.PREFS, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(AutoResetWorker.KEY_ENABLED, enabled)
                    .putInt(AutoResetWorker.KEY_INTERVAL_MINUTES, interval)
                    .apply()
                if (enabled) AutoResetWorker.scheduleNext(context) else AutoResetWorker.cancel(context)
                AppLog.append(
                    context,
                    LogEntry(
                        LogEntry.now(),
                        "定时任务",
                        if (enabled) "开启" else "关闭",
                        "成功",
                        "每 ${interval} 分钟执行恢复出厂",
                    ),
                )
                result.success(
                    mapOf("enabled" to enabled, "intervalMinutes" to interval),
                )
            }

            "getSchedule" -> {
                val prefs = context.getSharedPreferences(AutoResetWorker.PREFS, Context.MODE_PRIVATE)
                result.success(
                    mapOf(
                        "enabled" to prefs.getBoolean(AutoResetWorker.KEY_ENABLED, false),
                        "intervalMinutes" to prefs.getInt(
                            AutoResetWorker.KEY_INTERVAL_MINUTES,
                            AutoResetWorker.DEFAULT_INTERVAL_MINUTES,
                        ),
                    ),
                )
            }

            "getLogs" -> async(result) { AppLog.read(context).map { it.toMap() } }
            "clearLogs" -> {
                AppLog.clear(context)
                result.success(null)
            }

            "getLogsEnabled" -> result.success(mapOf("enabled" to AppLog.isEnabled(context)))
            "setLogsEnabled" -> {
                val enabled = call.argument<Boolean>("enabled") ?: true
                if (!enabled && AppLog.isEnabled(context)) {
                    AppLog.append(
                        context,
                        LogEntry(LogEntry.now(), "日志设置", "手动", "已关闭", "后续操作不再写入执行日志"),
                    )
                }
                AppLog.setEnabled(context, enabled)
                if (enabled) {
                    AppLog.append(
                        context,
                        LogEntry(LogEntry.now(), "日志设置", "手动", "已开启", "后续操作将写入执行日志"),
                    )
                }
                result.success(mapOf("enabled" to enabled))
            }

            else -> result.notImplemented()
        }
    }

    private fun async(result: MethodChannel.Result, block: () -> Any?) {
        executor.execute {
            try {
                val value = block()
                mainHandler.post { result.success(value) }
            } catch (e: Exception) {
                mainHandler.post { result.error("NATIVE_ERROR", e.message ?: "未知错误", null) }
            }
        }
    }

    private fun performAction(name: String, call: () -> String): Map<String, Any> {
        return try {
            val body = call()
            AppLog.append(context, LogEntry(LogEntry.now(), name, "手动", "已发送", body.take(300)))
            mapOf("ok" to true, "detail" to body.take(300))
        } catch (e: Exception) {
            val message = e.message ?: "未知异常"
            AppLog.append(context, LogEntry(LogEntry.now(), name, "手动", "失败", message))
            mapOf("ok" to false, "detail" to message)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        speedSink = events
        monitor = SpeedMonitor { rx, tx ->
            mainHandler.post {
                speedSink?.success(
                    mapOf("rx" to rx, "tx" to tx, "time" to LogEntry.now()),
                )
            }
        }
        monitor?.start()
    }

    override fun onCancel(arguments: Any?) {
        monitor?.stop()
        monitor = null
        speedSink = null
    }
}
