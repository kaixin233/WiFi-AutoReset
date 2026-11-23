package com.kaixin233.wifiautoreset

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var currentTab: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // targetSdk 36 强制 edge-to-edge：必须显式处理状态栏/手势栏 inset，
        // 否则顶栏会被状态栏压住、底栏与手势条及屏幕圆角重叠。
        WindowCompat.enableEdgeToEdge(window)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = bars.top)
            nav.updatePadding(bottom = bars.bottom)
            insets
        }

        nav.setOnItemSelectedListener { item ->
            if (item.itemId != currentTab) switchTo(item.itemId)
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_dashboard
        } else {
            currentTab = nav.selectedItemId
            toolbar.title = nav.menu.findItem(currentTab).title
        }
    }

    private fun switchTo(itemId: Int) {
        currentTab = itemId
        val (fragment, titleRes) = when (itemId) {
            R.id.nav_automation -> AutomationFragment() to R.string.title_automation
            R.id.nav_logs -> LogsFragment() to R.string.title_logs
            R.id.nav_web -> WebFragment() to R.string.title_web
            else -> DashboardFragment() to R.string.title_dashboard
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commitAllowingStateLoss()
        findViewById<MaterialToolbar>(R.id.toolbar).title = getString(titleRes)
    }
}
