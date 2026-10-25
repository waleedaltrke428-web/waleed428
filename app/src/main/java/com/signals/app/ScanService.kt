package com.signals.app

import android.app.*
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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class C(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double, val v: Double)
class Ex(val n: String, val syms: () -> List<String>, val k: (String, String, Int) -> List<C>)

fun http(u: String): String {
    val c = URL(u).openConnection()
    c.connectTimeout = 15000; c.readTimeout = 20000
    c.setRequestProperty("User-Agent", "Mozilla/5.0")
    return c.getInputStream().bufferedReader().use { it.readText() }
}
fun arr(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
fun cd(x: JSONArray) = C(x.getString(0).toLong(), x.getString(1).toDouble(), x.getString(2).toDouble(), x.getString(3).toDouble(), x.getString(4).toDouble(), x.getString(5).toDouble())

val EXS = listOf(
    Ex("Binance", {
        arr(JSONObject(http("https://fapi.binance.com/fapi/v1/exchangeInfo")).getJSONArray("symbols"))
            .filter { it.getString("contractType") == "PERPETUAL" && it.getString("quoteAsset") == "USDT" && it.getString("status") == "TRADING" }
            .map { it.getString("symbol") }
    }, { s, tf, n ->
        val a = JSONArray(http("https://fapi.binance.com/fapi/v1/klines?symbol=$s&interval=$tf&limit=$n"))
        (0 until a.length() - 1).map { cd(a.getJSONArray(it)) }
    }),
    Ex("Bybit", {
        arr(JSONObject(http("https://api.bybit.com/v5/market/instruments-info?category=linear&limit=1000")).getJSONObject("result").getJSONArray("list"))
            .filter { it.getString("quoteCoin") == "USDT" && it.getString("contractType") == "LinearPerpetual" && it.getString("status") == "Trading" }
            .map { it.getString("symbol") }
    }, { s, tf, n ->
        val iv = mapOf("15m" to "15", "4h" to "240", "1d" to "D")[tf]
        val a = JSONObject(http("https://api.bybit.com/v5/market/kline?category=linear&symbol=$s&interval=$iv&limit=$n")).getJSONObject("result").getJSONArray("list")
        (a.length() - 1 downTo 1).map { cd(a.getJSONArray(it)) }
    }),
    Ex("OKX", {
        arr(JSONObject(http("https://www.okx.com/api/v5/public/instruments?instType=SWAP")).getJSONArray("data"))
            .filter { it.optString("settleCcy") == "USDT" && it.getString("state") == "live" }
            .map { it.getString("instId") }
    }, { s, tf, n ->
        val b = mapOf("15m" to "15m", "4h" to "4H", "1d" to "1D")[tf]
        val a = JSONObject(http("https://www.okx.com/api/v5/market/candles?instId=$s&bar=$b&limit=$n")).getJSONArray("data")
        (a.length() - 1 downTo 0).map { a.getJSONArray(it) }.filter { it.getString(8) == "1" }.map { cd(it) }
    })
)

fun ema(c: List<Double>, p: Int): Double { val k = 2.0 / (p + 1); var e = c[0]; for (i in 1 until c.size) e = c[i] * k + e * (1 - k); return e }
fun rsi(c: List<Double>, p: Int = 14): Double {
    var g = 0.0; var l = 0.0
    for (i in 1..p) { val d = c[i] - c[i - 1]; if (d > 0) g += d else l -= d }
    g /= p; l /= p
    for (i in p + 1 until c.size) { val d = c[i] - c[i - 1]; g = (g * (p - 1) + max(d, 0.0)) / p; l = (l * (p - 1) + max(-d, 0.0)) / p }
    return if (l == 0.0) 100.0 else 100 - 100 / (1 + g / l)
}
fun atr(k: List<C>, p: Int = 14): Double {
    var a = 0.0
    for (i in k.size - p until k.size) a += max(k[i].h - k[i].l, max(abs(k[i].h - k[i - 1].c), abs(k[i].l - k[i - 1].c)))
    return a / p
}
fun trend(k: List<C>): Int {
    val c = k.map { it.c }; val p = c.last(); val a = ema(c, 20); val b = ema(c, 50)
    return if (p > b && a > b) 1 else if (p < b && a < b) -1 else 0
}
fun pivots(k: List<C>, w: Int = 3): Pair<List<Double>, List<Double>> {
    val hs = ArrayList<Double>(); val ls = ArrayList<Double>()
    for (i in w until k.size - w) {
        var h = true; var l = true
        for (j in 1..w) {
            if (k[i].h <= k[i - j].h || k[i].h <= k[i + j].h) h = false
            if (k[i].l >= k[i - j].l || k[i].l >= k[i + j].l) l = false
        }
        if (h) hs.add(k[i].h)
        if (l) ls.add(k[i].l)
    }
    return Pair(hs, ls)
}
fun f(x: Double): String {
    val a = abs(x)
    return if (a >= 100) String.format(Locale.US, "%.2f", x) else if (a >= 1) String.format(Locale.US, "%.4f", x)
    else if (a >= 0.01) String.format(Locale.US, "%.5f", x) else String.format(Locale.US, "%.4g", x)
}

fun emaS(c: List<Double>, p: Int): List<Double> { val k = 2.0 / (p + 1); var e = c[0]; return c.map { e = it * k + e * (1 - k); e } }
fun hist(c: List<Double>): List<Double> { val m = emaS(c, 12).zip(emaS(c, 26)) { a, b -> a - b }; return m.zip(emaS(m, 9)) { a, b -> a - b } }
fun pctB(c: List<Double>): Double {
    val w = c.takeLast(20); val m = w.average(); val sd = sqrt(w.map { (it - m) * (it - m) }.average())
    return if (sd == 0.0) 0.5 else (c.last() - (m - 2 * sd)) / (4 * sd)
}
fun adx(k: List<C>, p: Int = 14): Double {
    var tr = 0.0; var pd = 0.0; var md = 0.0; val dx = ArrayList<Double>()
    for (i in 1 until k.size) {
        val up = k[i].h - k[i - 1].h; val dn = k[i - 1].l - k[i].l
        val t = max(k[i].h - k[i].l, max(abs(k[i].h - k[i - 1].c), abs(k[i].l - k[i - 1].c)))
        val pm = if (up > dn && up > 0) up else 0.0; val mm = if (dn > up && dn > 0) dn else 0.0
        if (i <= p) { tr += t; pd += pm; md += mm } else { tr = tr - tr / p + t; pd = pd - pd / p + pm; md = md - md / p + mm }
        if (i >= p && tr > 0) { val pi = 100 * pd / tr; val mi = 100 * md / tr; dx.add(if (pi + mi == 0.0) 0.0 else 100 * abs(pi - mi) / (pi + mi)) }
    }
    if (dx.size < p) return 0.0
    var av = dx.take(p).average()
    for (i in p until dx.size) av = (av * (p - 1) + dx[i]) / p
    return av
}

fun analyze(ex: Ex, s: String, minScore: Int): JSONObject? {
    val k4 = ex.k(s, "4h", 200); if (k4.size < 80) return null
    val t4 = trend(k4); if (t4 == 0) return null
    val k1 = ex.k(s, "1d", 200); if (k1.size < 60 || trend(k1) != t4) return null
    val k = ex.k(s, "15m", 120); if (k.size < 60) return null
    val long = t4 == 1
    val last = k.last(); val prev = k[k.size - 2]; val p = last.c; val cl = k.map { it.c }
    val r = rsi(cl); val a15 = atr(k); val a4 = atr(k4); val ad = adx(k4)
    val hh = hist(cl); val h0 = hh[hh.size - 1]; val h1 = hh[hh.size - 2]
    val rv = last.v / k.takeLast(21).dropLast(1).map { it.v }.average().coerceAtLeast(1e-12)
    val pb = pctB(cl)
    val (hs, lo4) = pivots(k4)
    val sup = lo4.filter { it < p }.maxOrNull() ?: 0.0
    val res = hs.filter { it > p }.minOrNull()
    val w = k4.takeLast(100); var hi = 0; var lo = 0
    w.forEachIndexed { i, x -> if (x.h > w[hi].h) hi = i; if (x.l < w[lo].l) lo = i }
    val top = w[hi].h; val bot = w[lo].l; val rg = top - bot; val tol = 0.3 * a4
    val nf = listOf(0.382, 0.5, 0.618, 0.786).firstOrNull { abs(p - (if (long) top - it * rg else bot + it * rg)) <= tol }
    var sc = 2; val why = ArrayList<String>()
    why.add("اتجاه ${if (long) "صاعد" else "هابط"} على 4س واليومي")
    if (ad > 22) { sc++; why.add("ADX ${ad.toInt()} اتجاه قوي") }
    if (rv > 1.5) { sc++; why.add("حجم تداول ×${String.format(Locale.US, "%.1f", rv)}") }
    if (long) {
        if (r < 40) { sc++; why.add("RSI 15د ${r.toInt()} تشبع بيعي") }
        if (r < 30) sc++
        if (sup > 0 && p - sup <= tol) { sc++; why.add("عند دعم 4س ${f(sup)}") }
        if (nf != null && lo < hi) { sc++; why.add("تصحيح فيبو $nf") }
        if (h0 > h1) { sc++; why.add("MACD زخم صاعد") }
        if (pb < 0.2) { sc++; why.add("قرب الحد السفلي للبولنجر") }
        if (last.c > last.o && last.c > prev.h * 0.999) { sc++; why.add("شمعة تأكيد صاعدة") } else sc = min(sc, 5)
    } else {
        if (r > 60) { sc++; why.add("RSI 15د ${r.toInt()} تشبع شرائي") }
        if (r > 70) sc++
        if (res != null && res - p <= tol) { sc++; why.add("عند مقاومة 4س ${f(res)}") }
        if (nf != null && hi < lo) { sc++; why.add("ارتداد فيبو $nf") }
        if (h0 < h1) { sc++; why.add("MACD زخم هابط") }
        if (pb > 0.8) { sc++; why.add("قرب الحد العلوي للبولنجر") }
        if (last.c < last.o && last.c < prev.l * 1.001) { sc++; why.add("شمعة تأكيد هابطة") } else sc = min(sc, 5)
    }
    sc = min(sc, 10)
    if (sc < minScore) return null
    val rec = k.takeLast(20)
    var sl = if (long) rec.minOf { it.l } - 0.2 * a15 else rec.maxOf { it.h } + 0.2 * a15
    var risk = abs(p - sl)
    if (risk < 0.8 * a15) { risk = 0.8 * a15; sl = if (long) p - risk else p + risk }
    val m = if (long) 1.0 else -1.0
    return JSONObject().put("sym", s).put("long", long).put("score", sc).put("why", JSONArray(why))
        .put("entry", p).put("sl", sl).put("tp1", p + m * 1.5 * risk).put("tp2", p + m * 3 * risk).put("tp3", p + m * 5 * risk)
        .put("rsi", r).put("adx", ad).put("rv", rv).put("t", last.t).put("risk", risk / p * 100)
}

class ScanService : Service() {
    companion object { @Volatile var th: Thread? = null; val seen = HashSet<String>() }
    private fun minScore() = prefs().getInt("min", 7)
    private fun prefs() = getSharedPreferences("s", 0)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, fl: Int, id: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("svc", "التشغيل", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("sig", "الفرص", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, "svc").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("يفحص السوق كل 15 دقيقة").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        if (th == null) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sig:scan").acquire()
            th = Thread {
                while (true) {
                    prefs().edit().putBoolean("b", true).apply()
                    try { scan() } catch (x: Exception) { }
                    prefs().edit().putBoolean("b", false).apply()
                    try { Thread.sleep(900000 - (System.currentTimeMillis() % 900000) + 20000) } catch (x: InterruptedException) { }
                }
            }
            th!!.start()
        }
        return START_STICKY
    }

    private fun scan() {
        var ex: Ex? = null; var syms: List<String> = emptyList()
        for (e in EXS) { try { val l = e.syms(); if (l.isNotEmpty()) { ex = e; syms = l; break } } catch (x: Exception) { } }
        val e = ex ?: run { prefs().edit().putString("d", JSONObject().put("err", "تعذّر الاتصال بالمنصات").toString()).apply(); return }
        val pool = Executors.newFixedThreadPool(6)
        val all = pool.invokeAll(syms.map { s -> Callable<JSONObject?> { try { analyze(e, s, 4) } catch (x: Exception) { null } } })
            .mapNotNull { it.get() }.sortedByDescending { it.getInt("score") }
        val ms = minScore()
        val res = all.filter { it.getInt("score") >= ms }.take(15)
        val near = all.filter { it.getInt("score") < ms }.take(40)
        pool.shutdown()
        val out = JSONObject().put("time", System.currentTimeMillis()).put("ex", e.n).put("total", syms.size).put("list", JSONArray(res)).put("near", JSONArray(near))
        prefs().edit().putString("d", out.toString()).apply()
        if (seen.size > 3000) seen.clear()
        val fresh = res.filter { seen.add(it.getString("sym") + it.getBoolean("long") + it.getLong("t")) }
        if (fresh.isNotEmpty() && prefs().getBoolean("notif", true)) {
            val names = fresh.take(5).joinToString("  ") { (if (it.getBoolean("long")) "🟢 " else "🔴 ") + it.getString("sym") }
            val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(2,
                Notification.Builder(this, "sig").setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("فرص جديدة (${fresh.size})").setContentText(names)
                    .setContentIntent(pi).setAutoCancel(true).build())
        }
    }
}
