package com.kaixin233.wifiautoreset

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * 后台保活引导：跳转到厂商/系统的后台设置页，让用户手动放开限制。
 *
 * 国内 ROM（尤其 MIUI/HyperOS）会激进清理后台，仅靠前台服务仍可能被「省电策略」
 * 或「睡眠待机」冻结。这里提供两个入口：
 * 1. 自启动 / 关联启动管理（各 ROM 路径不同，逐个尝试已知 activity）。
 * 2. 电池优化豁免（原生 ACTION，MIUI 上会落到「省电策略」）。
 *
 * 全部用隐式/半显式 Intent，找不到时给出 Toast 提示并回退到应用详情页，
 * 不依赖任何第三方库。
 */
object BootGuard {

    /** 跳转自启动管理页：按 MIUI → 其他国产 ROM → 应用详情的优先级尝试。 */
    fun openAutoStart(context: Context) {
        val candidates = listOf(
            // MIUI / HyperOS
            Intent("miui.intent.action.APP_AUTOSTART_MANAGE"),
            componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManageActivity"),
            // 华为 / 荣耀
            componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            // OPPO / 一加
            componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // vivo
            componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        )
        if (!tryStartAny(context, candidates)) {
            openAppDetails(context)
            toast(context, "未找到自启动设置，已打开应用详情，请手动开启自启动")
        }
    }

    /** 跳转电池优化豁免：原生入口，MIUI 上会落到「省电策略」。 */
    fun openBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (tryStartAny(context, listOf(intent))) {
                toast(context, "请选择「允许」/「不优化」，避免后台被冻结")
                return
            }
            val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (tryStartAny(context, listOf(listIntent))) return
        }
        openAppDetails(context)
        toast(context, "请在本页将省电策略设为「无限制」")
    }

    /** 应用详情页兜底：用户可在此找到自启动、省电、通知等全部权限。 */
    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "无法打开系统设置")
        }
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun tryStartAny(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                }
            } catch (_: Exception) {
                // 该 ROM 无此 activity，尝试下一个。
            }
        }
        return false
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
