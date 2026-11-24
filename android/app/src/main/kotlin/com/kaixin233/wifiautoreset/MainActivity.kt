package com.kaixin233.wifiautoreset

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

/** 纯原生主界面：手写标题栏 + 内容容器 + 手写底栏，页面用 View 控制器切换。 */
class MainActivity : Activity() {

    private var currentTab = -1
    private val pages = mutableMapOf<Int, PageController>()
    private var headerBasePaddingTop: Int? = null
    private var navBasePaddingBottom: Int? = null

    private data class Tab(val root: Int, val icon: Int, val label: Int, val title: Int)

    private val tabs = listOf(
        Tab(R.id.tab_dashboard, R.id.iv_dashboard, R.id.tv_dashboard, R.string.title_dashboard),
        Tab(R.id.tab_automation, R.id.iv_automation, R.id.tv_automation, R.string.title_automation),
        Tab(R.id.tab_logs, R.id.iv_logs, R.id.tv_logs, R.string.title_logs),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        setContentView(R.layout.activity_main)
        setupEdgeToEdge()

        tabs.forEach { tab ->
            findViewById<View>(tab.root).setOnClickListener { switchTo(tab) }
        }

        val savedTab = savedInstanceState?.getInt(KEY_TAB, R.id.tab_dashboard) ?: R.id.tab_dashboard
        val initial = tabs.firstOrNull { it.root == savedTab } ?: tabs[0]
        switchTo(initial, force = true)

        requestNotificationPermissionIfNeeded()
    }

    /** Android 13+ 首次进入时申请通知权限，用于定时任务执行结果提示。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        // 系统对已选「不再询问」的请求不会再次弹窗，直接申请即可。
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
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
            // WindowInsetsController：API 30+ 原生常量，浅色图标仅在浅色主题使用。
            val lightStatus = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            val lightNav = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = if (night) 0 else lightStatus or lightNav
            window.insetsController?.setSystemBarsAppearance(appearance, lightStatus or lightNav)

            val decor = window.decorView
            decor.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val header = findViewById<View>(R.id.toolbar_title)
                val nav = findViewById<View>(R.id.bottom_nav)
                // 用初始 padding 作为基线，避免 insets 多次分发时累加；
                // toolbar 高度为 wrap_content+minHeight，top padding 会撑高而不是压缩文字。
                val headerTop = headerBasePaddingTop ?: header.paddingTop.also { headerBasePaddingTop = it }
                header.setPadding(header.paddingStart, headerTop + bars.top, header.paddingEnd, header.paddingBottom)
                val navBottom = navBasePaddingBottom ?: nav.paddingBottom.also { navBasePaddingBottom = it }
                nav.setPadding(nav.paddingStart, nav.paddingTop, nav.paddingEnd, navBottom + bars.bottom)
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

    private fun switchTo(tab: Tab, force: Boolean = false) {
        if (tab.root == currentTab && !force) return
        pages[currentTab]?.onHide()
        val holder = findViewById<View>(R.id.container) as android.view.ViewGroup
        holder.removeAllViews()

        currentTab = tab.root
        findViewById<TextView>(R.id.toolbar_title).setText(tab.title)
        updateTabStyles(tab)

        val page = pages.getOrPut(tab.root) { createPage(tab) }
        holder.addView(page.view)
        page.onShow()
    }

    private fun createPage(tab: Tab): PageController {
        val layout = when (tab.root) {
            R.id.tab_automation -> R.layout.page_automation
            R.id.tab_logs -> R.layout.page_logs
            else -> R.layout.page_dashboard
        }
        val pageView = layoutInflater.inflate(layout, null)
        return when (tab.root) {
            R.id.tab_automation -> AutomationPage(this, pageView)
            R.id.tab_logs -> LogsPage(this, pageView)
            else -> DashboardPage(this, pageView)
        }
    }

    private fun updateTabStyles(selected: Tab) {
        val primary = resources.getColor(R.color.primary, theme)
        val muted = resources.getColor(R.color.nav_unchecked, theme)
        tabs.forEach { tab ->
            val on = tab.root == selected.root
            findViewById<ImageView>(tab.icon).setColorFilter(if (on) primary else muted, PorterDuff.Mode.SRC_IN)
            findViewById<TextView>(tab.label).setTextColor(if (on) primary else muted)
        }
    }

    override fun onDestroy() {
        pages.values.forEach { it.onDestroy() }
        pages.clear()
        super.onDestroy()
    }

    companion object {
        private const val KEY_TAB = "current_tab"
        private const val REQ_NOTIFICATION = 2001
    }
}
