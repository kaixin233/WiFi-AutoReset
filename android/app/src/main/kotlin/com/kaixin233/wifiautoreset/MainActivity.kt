package com.kaixin233.wifiautoreset

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            switchTo(item.itemId)
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_dashboard
        } else {
            findViewById<MaterialToolbar>(R.id.toolbar).title =
                nav.menu.findItem(nav.selectedItemId).title
        }
    }

    private fun switchTo(itemId: Int) {
        val fragment: Fragment
        val titleRes: Int
        when (itemId) {
            R.id.nav_automation -> {
                fragment = AutomationFragment()
                titleRes = R.string.title_automation
            }
            R.id.nav_logs -> {
                fragment = LogsFragment()
                titleRes = R.string.title_logs
            }
            R.id.nav_web -> {
                fragment = WebFragment()
                titleRes = R.string.title_web
            }
            else -> {
                fragment = DashboardFragment()
                titleRes = R.string.title_dashboard
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commitAllowingStateLoss()
        findViewById<MaterialToolbar>(R.id.toolbar).title = getString(titleRes)
    }
}
