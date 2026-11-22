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
            "hasUsageAccess" -> result.success(detector.hasPermission())

            "requestUsageAccess" -> {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
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
                        "time" to LogEntry.now(),
                    ),
                )
            }

            "deviceInfo" -> async(result) { MifiClient.deviceInfo() }
            "gatewayReachable" -> async(result) { MifiClient.reachable() }

            "factoryReset" -> {
                val dryRun = call.argument<Boolean>("dryRun") ?: true
                async(result) {
                    performAction("恢复出厂设置", dryRun) { MifiClient.factoryReset() }
                }
            }

            "reboot" -> {
                val dryRun = call.argument<Boolean>("dryRun") ?: true
                async(result) { performAction("重启设备", dryRun) { MifiClient.reboot() } }
            }

            "resetTraffic" -> {
                val dryRun = call.argument<Boolean>("dryRun") ?: true
                async(result) { performAction("重置流量统计", dryRun) { MifiClient.resetTraffic() } }
            }

            "configureSchedule" -> {
                val enabled = call.argument<Boolean>("enabled") ?: false
                val dryRun = call.argument<Boolean>("dryRun") ?: true
                val prefs = context.getSharedPreferences(AutoResetWorker.PREFS, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(AutoResetWorker.KEY_ENABLED, enabled)
                    .putBoolean(AutoResetWorker.KEY_DRY_RUN, dryRun)
                    .apply()
                if (enabled) AutoResetWorker.scheduleNext(context) else AutoResetWorker.cancel(context)
                AppLog.append(
                    context,
                    LogEntry(LogEntry.now(), "定时任务", if (enabled) "开启" else "关闭", "成功", "dryRun=$dryRun"),
                )
                result.success(mapOf("enabled" to enabled, "dryRun" to dryRun))
            }

            "getSchedule" -> {
                val prefs = context.getSharedPreferences(AutoResetWorker.PREFS, Context.MODE_PRIVATE)
                result.success(
                    mapOf(
                        "enabled" to prefs.getBoolean(AutoResetWorker.KEY_ENABLED, false),
                        "dryRun" to prefs.getBoolean(AutoResetWorker.KEY_DRY_RUN, true),
                    ),
                )
            }

            "getLogs" -> async(result) { AppLog.read(context).map { it.toMap() } }
            "clearLogs" -> {
                AppLog.clear(context)
                result.success(null)
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

    private fun performAction(name: String, dryRun: Boolean, call: () -> String): Map<String, Any> {
        if (dryRun) {
            AppLog.append(context, LogEntry(LogEntry.now(), name, "手动", "演练", "未发送请求"))
            return mapOf("ok" to true, "dryRun" to true, "detail" to "演练模式：未发送请求")
        }
        return try {
            val body = call()
            AppLog.append(context, LogEntry(LogEntry.now(), name, "手动", "已发送", body.take(300)))
            mapOf("ok" to true, "dryRun" to false, "detail" to body.take(300))
        } catch (e: Exception) {
            val message = e.message ?: "未知异常"
            AppLog.append(context, LogEntry(LogEntry.now(), name, "手动", "失败", message))
            mapOf("ok" to false, "dryRun" to false, "detail" to message)
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
