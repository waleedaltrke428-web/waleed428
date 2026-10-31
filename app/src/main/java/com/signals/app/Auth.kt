package com.signals.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// إدارة جلسة المستخدم (JWT): هون بس بيتم التجديد، عشان ما يصير تعارض بين التطبيق والخدمة الخلفية
object Auth {
    private val lock = Any()

    fun token(c: Context): String = synchronized(lock) {
        val p = c.getSharedPreferences("s", 0)
        val at = p.getString("at", "") ?: ""; val rt = p.getString("rt", "") ?: ""
        val exp = p.getLong("exp", 0L); val now = System.currentTimeMillis() / 1000
        if (at.isEmpty() || rt.isEmpty()) return@synchronized ""
        if (exp - now > 120) return@synchronized at
        val u = (p.getString("sbu", "") ?: "").trimEnd('/'); val k = p.getString("sbk", "") ?: ""
        if (u.isEmpty() || k.isEmpty()) return@synchronized at
        try {
            val con = URL("$u/auth/v1/token?grant_type=refresh_token").openConnection() as HttpURLConnection
            con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 20000
            con.setRequestProperty("apikey", k); con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(JSONObject().put("refresh_token", rt).toString().toByteArray()) }
            if (con.responseCode != 200) return@synchronized at
            val j = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            val na = j.getString("access_token")
            p.edit().putString("at", na).putString("rt", j.getString("refresh_token")).putLong("exp", now + j.optLong("expires_in", 3600)).apply()
            na
        } catch (e: Exception) { at }
    }
}
