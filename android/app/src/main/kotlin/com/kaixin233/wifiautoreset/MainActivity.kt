package com.kaixin233.wifiautoreset

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/** 纯原生单页主界面：标题栏 + 仪表盘，所有功能集中在一个可滚动页面。 */
class MainActivity : Activity() {

    private var page: DashboardPage? = null
    private var headerBasePaddingTop: Int? = null
    private var containerBasePaddingBottom: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupEdgeToEdge()

        val holder = findViewById<ViewGroup>(R.id.container)
        val pageView = layoutInflater.inflate(R.layout.page_dashboard, holder, false)
        page = DashboardPage(this, pageView).also { holder.addView(it.view) }

        requestNotificationPermissionIfNeeded()
        // 若定时任务已启用但守护服务未运行（如被系统清理后重新进入），补拉一次。
        if (AlarmScheduler.isEnabled(this)) KeepAliveService.start(this)
    }

    override fun onResume() {
        super.onResume()
        page?.onShow()
        page?.onResume()
    }

    override fun onPause() {
        page?.onHide()
        super.onPause()
    }

    /** Android 13+ 首次进入时申请通知权限，用于定时任务执行结果提示。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
    }

    private fun setupEdgeToEdge() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val lightStatus = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            val lightNav = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = if (night) 0 else lightStatus or lightNav
            window.insetsController?.setSystemBarsAppearance(appearance, lightStatus or lightNav)

            window.decorView.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val header = findViewById<View>(R.id.toolbar_title)
                val container = findViewById<View>(R.id.container)
                val headerTop = headerBasePaddingTop ?: header.paddingTop.also { headerBasePaddingTop = it }
                header.setPadding(header.paddingStart, headerTop + bars.top, header.paddingEnd, header.paddingBottom)
                val bottom = containerBasePaddingBottom
                    ?: container.paddingBottom.also { containerBasePaddingBottom = it }
                container.setPadding(container.paddingStart, container.paddingTop, container.paddingEnd, bottom + bars.bottom)
                v.onApplyWindowInsets(insets)
            }
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            flags = if (night) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    override fun onDestroy() {
        page?.onDestroy()
        page = null
        super.onDestroy()
    }

    companion object {
        private const val REQ_NOTIFICATION = 2001
    }
}
