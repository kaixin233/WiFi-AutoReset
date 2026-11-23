package com.kaixin233.wifiautoreset

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment

class WebFragment : Fragment(R.layout.fragment_web) {

    private var webView: WebView? = null
    private var progress: ProgressBar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress = view.find(R.id.progress)
        val web = view.find<WebView>(R.id.webview)
        webView = web
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progress?.visibility = View.GONE
                view?.postDelayed({ login() }, 600)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                progress?.visibility = View.GONE
            }
        }
        web.loadUrl("http://192.168.1.1")

        view.find<Button>(R.id.btn_religin).setOnClickListener { login() }
        view.find<Button>(R.id.btn_web_factory).setOnClickListener {
            val ctx = requireContext()
            AlertDialog.Builder(ctx)
                .setTitle("恢复出厂设置")
                .setMessage("此操作会清空随身 WiFi 全部配置并重启，手机将断网。确认继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认") { _, _ ->
                    Utils.asyncUi(
                        {
                            val body = MifiClient.factoryReset()
                            AppLog.append(
                                ctx,
                                LogEntry(LogEntry.now(), "恢复出厂设置", "手动", "已发送", body.take(300)),
                            )
                        },
                        { error ->
                            Toast.makeText(
                                ctx,
                                if (error == null) "已执行" else "失败：${error.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
                .show()
        }
    }

    private fun login() {
        webView?.evaluateJavascript(
            """
            (function(){
              try {
                var u = document.getElementById('tbarouter_username');
                var p = document.getElementById('tbarouter_password');
                if (u && p) {
                  u.value = 'admin';
                  p.value = 'admin';
                  if (typeof Login === 'function') Login();
                }
              } catch (e) {}
            })();
            """.trimIndent(),
            null,
        )
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        progress = null
        super.onDestroyView()
    }
}
