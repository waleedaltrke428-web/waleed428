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
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {
    // منع لقطة الشاشة وتسجيلها (حماية المحتوى المدفوع). شغّله true عند النشر للمستخدمين
    private val SECURE = false

    private lateinit var web: WebView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        if (SECURE) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val svc = Intent(this, ScanService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        WebView.setWebContentsDebuggingEnabled(false)
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
        @JavascriptInterface fun setSession(a: String, r: String, e: Long, d: String) { getSharedPreferences("s", 0).edit().putString("at", a).putString("rt", r).putLong("exp", e).putString("dev", d).remove("rs").apply() }
        @JavascriptInterface fun clearSession() { getSharedPreferences("s", 0).edit().remove("at").remove("rt").remove("exp").remove("rs").apply() }
        @JavascriptInterface fun hasSession(): Boolean = !(getSharedPreferences("s", 0).getString("rt", "") ?: "").isEmpty()
        @JavascriptInterface fun token(): String = Auth.token(this@MainActivity)
        @JavascriptInterface fun busy(): Boolean = getSharedPreferences("s", 0).getBoolean("b", false)
        @JavascriptInterface fun scan() { ScanService.th?.interrupt() }
        @JavascriptInterface fun setCfg(m: Int, n: Boolean, tp: Boolean, sl: Boolean) { getSharedPreferences("s", 0).edit().putInt("min", m).putBoolean("notif", n).putBoolean("ntp", tp).putBoolean("nsl", sl).apply() }
        @JavascriptInterface fun setSb(u: String, k: String) { getSharedPreferences("s", 0).edit().putString("sbu", u).putString("sbk", k).apply() }
        @JavascriptInterface fun resetStats() { getSharedPreferences("s", 0).edit().remove("rs").apply() }
        @JavascriptInterface fun openUrl(u: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        @JavascriptInterface fun copy(t: String) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("s", t))
        }
    }
}
