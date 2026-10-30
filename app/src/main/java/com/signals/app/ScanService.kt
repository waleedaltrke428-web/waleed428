package com.signals.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.Locale

fun fmtP(x: Double): String {
    val a = Math.abs(x)
    return if (a >= 100) String.format(Locale.US, "%.2f", x) else if (a >= 1) String.format(Locale.US, "%.4f", x)
    else if (a >= 0.01) String.format(Locale.US, "%.5f", x) else String.format(Locale.US, "%.4g", x)
}

// خدمة خلفية: بتتابع الإشارات من الخادم (Supabase) وبتبعت إشعارات. ما في أي فحص محلي.
class ScanService : Service() {
    companion object { @Volatile var th: Thread? = null }
    private fun prefs() = getSharedPreferences("s", 0)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, fl: Int, id: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("svc", "التشغيل", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("sig", "الفرص", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, "svc").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("بيتابع الإشارات من الخادم").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        if (th == null) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sig:poll").acquire()
            th = Thread {
                while (true) {
                    prefs().edit().putBoolean("b", true).apply()
                    try { poll() } catch (x: Exception) { }
                    prefs().edit().putBoolean("b", false).apply()
                    try { Thread.sleep(180000L) } catch (x: InterruptedException) { }
                }
            }
            th!!.start()
        }
        return START_STICKY
    }

    private fun note(id: Int, title: String, text: String) {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id,
            Notification.Builder(this, "sig").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build())
    }

    private fun poll() {
        val u = (prefs().getString("sbu", "") ?: "").trimEnd('/'); val k = prefs().getString("sbk", "") ?: ""
        if (u.isEmpty() || k.isEmpty()) return
        val c = URL("$u/rest/v1/signals?select=k,sym,is_long,entry,sl,score,st,res,strategy&order=created_at.desc&limit=200").openConnection()
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("apikey", k); c.setRequestProperty("Authorization", "Bearer w@14010090580")
        val arr = JSONArray(c.getInputStream().bufferedReader().use { it.readText() })
        val old = JSONObject(prefs().getString("rs", "{}") ?: "{}")
        val first = old.length() == 0
        val nw = JSONObject().put("__init", "1")
        val nsig = prefs().getBoolean("notif", true); val ntp = prefs().getBoolean("ntp", true); val nsl = prefs().getBoolean("nsl", true)
        val newOnes = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i); val key = r.getString("k")
            val res = r.optString("res"); val st = r.optInt("st", 0)
            nw.put(key, "$res|$st")
            if (first) continue
            val prev = old.optString(key, ""); val sym = r.getString("sym")
            if (prev.isEmpty()) {
                newOnes.add(r)
            } else {
                val p = prev.split("|"); val pst = p[1].toIntOrNull() ?: 0
                if (st > pst && ntp) note((key + "T" + st).hashCode(), "🎯 $sym حققت الهدف $st", "انقل الوقف لنقطة الدخول بعد الهدف الأول")
                if (res == "loss" && p[0] != "loss" && nsl) note((key + "L").hashCode(), "🛑 $sym ضربت وقف الخسارة", "الدخول ${fmtP(r.optDouble("entry"))}")
            }
        }
        val min = prefs().getInt("min", 60)
        val fresh = newOnes.filter { it.optInt("score") >= min }.sortedByDescending { it.optInt("score") }
        if (nsig) {
            fresh.take(5).forEach { r ->
                note(r.getString("k").hashCode(), "🔔 فرصة ${if (r.optBoolean("is_long")) "شراء" else "بيع"}: ${r.getString("sym")}",
                    "الدخول ${fmtP(r.optDouble("entry"))} • الوقف ${fmtP(r.optDouble("sl"))} • ${if (r.optString("strategy") == "pro") "المطوّرة" else "الرسمية"}")
            }
            if (fresh.size > 5) note(7, "🔔 ${fresh.size - 5} فرص إضافية", "افتح التطبيق لعرضها")
        }
        prefs().edit().putString("rs", nw.toString()).apply()
    }
}
