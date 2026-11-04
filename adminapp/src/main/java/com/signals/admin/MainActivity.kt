package com.signals.admin

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

// تطبيق لوحة التحكم للأدمن: بيعرض صفحة اللوحة المضمّنة بالتطبيق (نفس كود موقع الويب)
class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        WebView.setWebContentsDebuggingEnabled(false)
        web.setBackgroundColor(0xFF0B1020.toInt())
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)
    }

    override fun onBackPressed() { moveTaskToBack(true) }
}
