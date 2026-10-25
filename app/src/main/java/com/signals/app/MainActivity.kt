package com.signals.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        startForegroundService(Intent(this, ScanService::class.java))
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.setBackgroundColor(0xFF0B161D.toInt())
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)
        askBattery()
    }

    private fun askBattery() {
        val p = getSharedPreferences("s", 0)
        if (p.getBoolean("bat", false)) return
        p.edit().putBoolean("bat", true).apply()
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName))
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) { }
    }

    override fun onBackPressed() {
        web.evaluateJavascript("(window.goBack&&window.goBack())?1:0") { r -> if (r != "1") moveTaskToBack(true) }
    }

    inner class Bridge {
        @JavascriptInterface fun get(): String = getSharedPreferences("s", 0).getString("d", "{}") ?: "{}"
        @JavascriptInterface fun busy(): Boolean = getSharedPreferences("s", 0).getBoolean("b", false)
        @JavascriptInterface fun scan() { ScanService.th?.interrupt() }
        @JavascriptInterface fun setCfg(m: Int, n: Boolean, tp: Boolean, sl: Boolean) { getSharedPreferences("s", 0).edit().putInt("min", m).putBoolean("notif", n).putBoolean("ntp", tp).putBoolean("nsl", sl).apply() }
        @JavascriptInterface fun resetStats() { getSharedPreferences("s", 0).edit().remove("tr").apply() }
        @JavascriptInterface fun openUrl(u: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        @JavascriptInterface fun copy(t: String) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("s", t))
        }
    }
}
