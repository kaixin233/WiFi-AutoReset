package com.kaixin233.wifiautoreset

import android.view.View

/** 纯原生页面控制器契约：持有根 View 并响应显示/隐藏/恢复/销毁。 */
interface PageController {
    val view: View
    fun onShow() {}
    fun onHide() {}
    fun onResume() {}
    fun onDestroy() {}
}
