package com.kaixin233.wifiautoreset

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 监听窗口状态变化，把新出现的窗口所属包名记录为当前前台应用。
 * 不读取窗口内容，只使用事件自带 packageName，隐私影响最小。
 */
class ForegroundAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        // 内容变化事件常来自通知栏/桌面小部件刷新，会误判前台应用；
        // 只认窗口状态变化，并排除本应用自身。
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (pkg == packageName) return
        ForegroundAppDetector.observe(applicationContext, pkg)
    }

    override fun onInterrupt() = Unit
}
