package com.signals.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        startForegroundService(Intent(this, ScanService::class.java))
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.addJavascriptInterface(Bridge(), "Android")
        w.loadUrl("file:///android_asset/index.html")
        setContentView(w)
    }

    inner class Bridge {
        @JavascriptInterface fun get(): String = getSharedPreferences("s", 0).getString("d", "{}") ?: "{}"
        @JavascriptInterface fun busy(): Boolean = getSharedPreferences("s", 0).getBoolean("b", false)
        @JavascriptInterface fun scan() { ScanService.th?.interrupt() }
        @JavascriptInterface fun setCfg(m: Int, n: Boolean, tp: Boolean, sl: Boolean) { getSharedPreferences("s", 0).edit().putInt("min", m).putBoolean("notif", n).putBoolean("ntp", tp).putBoolean("nsl", sl).apply() }
        @JavascriptInterface fun resetStats() { getSharedPreferences("s", 0).edit().remove("tr").apply() }
        @JavascriptInterface fun openUrl(u: String) { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
        @JavascriptInterface fun copy(t: String) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("s", t))
        }
    }
}
