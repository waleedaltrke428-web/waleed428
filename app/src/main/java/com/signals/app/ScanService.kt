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
        val iv = mapOf("15m" to "15", "4h" to "240", "1d" to "D", "1w" to "W")[tf]
        val a = JSONObject(http("https://api.bybit.com/v5/market/kline?category=linear&symbol=$s&interval=$iv&limit=$n")).getJSONObject("result").getJSONArray("list")
        (a.length() - 1 downTo 1).map { cd(a.getJSONArray(it)) }
    }),
    Ex("OKX", {
        arr(JSONObject(http("https://www.okx.com/api/v5/public/instruments?instType=SWAP")).getJSONArray("data"))
            .filter { it.optString("settleCcy") == "USDT" && it.getString("state") == "live" }
            .map { it.getString("instId") }
    }, { s, tf, n ->
        val b = mapOf("15m" to "15m", "4h" to "4H", "1d" to "1D", "1w" to "1W")[tf]
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

class Z(var lo: Double, var hi: Double, var w: Int, var mx: Int)
class ZC(val t: Long, val z: List<Z>, val t4: Int, val t1: Int, val pad: Double)
val ZCACHE = java.util.concurrent.ConcurrentHashMap<String, ZC>()

// مناطق الدعم والمقاومة من تاريخ العملة: أسبوعي (وزن 3) + يومي (2) + 4 ساعات (1)، تُجمَّع القمم والقيعان المتقاربة بمنطقة واحدة
fun buildZones(ex: Ex, s: String): ZC? {
    val k1w = ex.k(s, "1w", 200); val k1d = ex.k(s, "1d", 300); val k4 = ex.k(s, "4h", 300)
    if (k1d.size < 60 || k4.size < 80) return null
    val tol = 0.5 * atr(k1d)
    val pts = ArrayList<Pair<Double, Int>>()
    for (t in listOf(Triple(k1w, 2, 3), Triple(k1d, 3, 2), Triple(k4, 3, 1))) {
        if (t.first.size < 10) continue
        val pv = pivots(t.first, t.second)
        for (v in pv.first + pv.second) pts.add(Pair(v, t.third))
    }
    pts.sortBy { it.first }
    val zs = ArrayList<Z>()
    for (pt in pts) {
        val z = zs.lastOrNull()
        if (z != null && pt.first - z.lo <= 2 * tol) { z.hi = pt.first; z.w += pt.second; z.mx = max(z.mx, pt.second) }
        else zs.add(Z(pt.first, pt.first, pt.second, pt.second))
    }
    return ZC(System.currentTimeMillis(), zs.filter { it.w >= 4 }, trend(k4), trend(k1d), 0.15 * atr(k1d))
}

fun evalZone(k: List<C>, zc: ZC, z: Z, long: Boolean, s: String, minScore: Int): JSONObject? {
    val p = k.last().c; val a15 = atr(k); val cl = k.map { it.c }
    val lo = z.lo - zc.pad; val hi = z.hi + zc.pad
    val near = if (long) p >= lo - 0.3 * a15 && p <= hi + 1.5 * a15 else p <= hi + 0.3 * a15 && p >= lo - 1.5 * a15
    if (!near) return null
    val last = k.last(); val rng = max(last.h - last.l, 1e-12)
    val touched = k.takeLast(3).any { if (long) it.l <= hi else it.h >= lo }
    val inZone = p >= lo && p <= hi
    val rej = touched && (if (long) (last.c > last.o && (min(last.o, last.c) - last.l) >= 0.35 * rng && last.c > lo) else (last.c < last.o && (last.h - max(last.o, last.c)) >= 0.35 * rng && last.c < hi))
    val active: Boolean
    if (rej) active = true else if (!touched && !inZone) active = false else return null
    val entry = if (active) p else if (long) hi else lo
    val sl = if (long) lo - 0.3 * a15 else hi + 0.3 * a15
    val risk = abs(entry - sl)
    if (risk < 0.6 * a15 || risk / entry > 0.05) return null
    if (abs(p - entry) > 2 * a15) return null
    if (if (long) p <= sl else p >= sl) return null
    val dirv = if (long) 1 else -1
    if (zc.t4 != dirv && z.w < 8) return null
    var sc = min(30, z.w * 3) + 5 * z.mx; val why = ArrayList<String>()
    val tfn = if (z.mx == 3) "أسبوعي" else if (z.mx == 2) "يومي" else "4 ساعات"
    why.add("${if (long) "دعم" else "مقاومة"} تاريخي $tfn ${f(lo)}–${f(hi)} (${z.w} ارتدادات)")
    if (zc.t4 == dirv) { sc += 10; why.add("اتجاه 4س متوافق") }
    if (zc.t1 == dirv) { sc += 5; why.add("اتجاه يومي متوافق") }
    if (rej) { sc += 15; why.add("شمعة رفض ${if (long) "صاعدة" else "هابطة"} من المنطقة") }
    val r = rsi(cl)
    if (if (long) r < 40 else r > 60) { sc += if (if (long) r < 30 else r > 70) 12 else 8; why.add("RSI 15د ${r.toInt()}") }
    val hh = hist(cl)
    if (if (long) hh.last() > hh[hh.size - 2] else hh.last() < hh[hh.size - 2]) { sc += 5; why.add("MACD زخم ${if (long) "صاعد" else "هابط"}") }
    val avgV = k.takeLast(30).map { it.v }.average().coerceAtLeast(1e-12)
    if (k.takeLast(3).any { it.v > 1.5 * avgV }) { sc += 6; why.add("حجم تداول مرتفع") }
    if (inZone) sc += 5
    sc = min(sc, 100)
    if (sc < minScore) return null
    val dist = (if (long) zc.z.filter { it.lo - zc.pad > entry }.map { it.lo - zc.pad - entry } else zc.z.filter { it.hi + zc.pad < entry }.map { entry - (it.hi + zc.pad) }).sorted()
    val d2 = min(dist.firstOrNull { it >= 2.2 * risk } ?: (3 * risk), 4.5 * risk)
    val d3 = min(dist.firstOrNull { it >= d2 * 1.25 } ?: (5 * risk), 8 * risk)
    val m = if (long) 1.0 else -1.0
    return JSONObject().put("sym", s).put("long", long).put("score", sc).put("why", JSONArray(why))
        .put("entry", entry).put("px", p).put("status", if (active) "active" else "wait").put("sl", sl)
        .put("tp1", entry + m * 1.5 * risk).put("tp2", entry + m * d2).put("tp3", entry + m * d3)
        .put("obl", lo).put("obh", hi).put("rsi", r).put("t", k.last().t).put("risk", risk / entry * 100)
}

fun analyze(ex: Ex, s: String, minScore: Int): JSONObject? {
    val k = ex.k(s, "15m", 150); if (k.size < 100) return null
    val key = ex.n + s; val c0 = ZCACHE[key]
    val zc: ZC = if (c0 != null && System.currentTimeMillis() - c0.t < 6 * 3600000L) c0 else (buildZones(ex, s) ?: return null).also { ZCACHE[key] = it }
    var best: JSONObject? = null
    for (z in zc.z) for (lg in listOf(true, false)) {
        val o = evalZone(k, zc, z, lg, s, minScore) ?: continue
        if (best == null || o.getInt("score") > best.getInt("score")) best = o
    }
    val p = k.last().c
    val zs = zc.z.sortedBy { abs((it.lo + it.hi) / 2 - p) }.take(8).map { JSONArray().put(it.lo - zc.pad).put(it.hi + zc.pad).put(it.w) }
    return best?.put("zs", JSONArray(zs))
}

class ScanService : Service() {
    companion object { @Volatile var th: Thread? = null; val seen = HashSet<String>() }
    private fun minScore() = prefs().getInt("min", 60)
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
                    val rem = !(prefs().getString("sbk", "") ?: "").isEmpty()
                    try { Thread.sleep(if (rem) 180000L else 900000 - (System.currentTimeMillis() % 900000) + 20000) } catch (x: InterruptedException) { }
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

    private fun track(exName: String, res: List<JSONObject>): JSONArray {
        var arr = JSONArray(prefs().getString("tr", "[]"))
        val keys = HashSet<String>()
        for (i in 0 until arr.length()) keys.add(arr.getJSONObject(i).getString("k"))
        for (x in res) {
            val k = x.getString("sym") + x.getBoolean("long") + x.getLong("t")
            if (keys.add(k)) arr.put(JSONObject().put("k", k).put("sym", x.getString("sym")).put("ex", exName).put("long", x.getBoolean("long"))
                .put("entry", x.getDouble("entry")).put("sl", x.getDouble("sl")).put("tp1", x.getDouble("tp1")).put("tp2", x.getDouble("tp2")).put("tp3", x.getDouble("tp3"))
                .put("score", x.getInt("score")).put("t", x.getLong("t")).put("chk", x.getLong("t")).put("st", 0).put("res", if (x.getString("status") == "active") "open" else "wait").put("tg", x.getString("status") == "active"))
        }
        val nt = prefs().getBoolean("ntp", true); val ns = prefs().getBoolean("nsl", true)
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val r0 = e.getString("res"); if (r0 != "open" && r0 != "wait") continue
            val exx = EXS.firstOrNull { it.n == e.getString("ex") } ?: continue
            try {
                val ks = exx.k(e.getString("sym"), "15m", 200).filter { it.t > e.getLong("chk") }
                val lg = e.getBoolean("long"); val en = e.getDouble("entry"); val sl = e.getDouble("sl")
                val tps = doubleArrayOf(e.getDouble("tp1"), e.getDouble("tp2"), e.getDouble("tp3"))
                val st0 = e.getInt("st"); var st = st0; var rs = r0; var chk = e.getLong("chk"); var tg = e.optBoolean("tg")
                for (c in ks) {
                    chk = c.t
                    if (rs == "wait") {
                        if (!(if (lg) c.l <= en else c.h >= en)) { if (if (lg) c.h >= tps[0] else c.l <= tps[0]) { rs = "miss"; break }; continue }
                        rs = "open"; tg = true
                        if (if (lg) c.l <= sl else c.h >= sl) { rs = "loss"; break }
                        continue
                    }
                    val was = st
                    if (was == 0 && (if (lg) c.l <= sl else c.h >= sl)) { rs = "loss"; break }
                    var nw = was
                    for (j in 0..2) if (if (lg) c.h >= tps[j] else c.l <= tps[j]) nw = max(nw, j + 1)
                    if (was >= 1 && nw == was && (if (lg) c.l <= en else c.h >= en)) { rs = "win"; break }
                    st = nw
                    if (st == 3) { rs = "win"; break }
                }
                val age = System.currentTimeMillis() - e.getLong("t")
                if (rs == "open" && age > 72L * 3600000L) rs = "exp"
                if (rs == "wait" && age > 24L * 3600000L) rs = "exp"
                e.put("st", st).put("res", rs).put("chk", chk).put("tg", tg)
                if (r0 == "wait" && tg && nt) note((e.getString("k") + "E").hashCode(), "✅ ${e.getString("sym")} انفعلت الصفقة", "الدخول ${f(en)} • الوقف ${f(sl)}")
                if (st > st0 && nt) note((e.getString("k") + st).hashCode(), "🎯 ${e.getString("sym")} حققت الهدف $st", "الدخول ${f(en)} • الهدف ${f(tps[st - 1])}")
                if (rs == "loss" && ns) note((e.getString("k") + "L").hashCode(), "🛑 ${e.getString("sym")} ضربت وقف الخسارة", "الوقف ${f(sl)}")
            } catch (x: Exception) { }
        }
        if (arr.length() > 150) { val na = JSONArray(); for (i in arr.length() - 150 until arr.length()) na.put(arr.get(i)); arr = na }
        prefs().edit().putString("tr", arr.toString()).apply()
        return arr
    }

    // وضع Supabase: بدل الماسح المحلي، نجيب الإشارات من القاعدة ونبعت إشعارات للجديد والأهداف والوقف
    private fun pollRemote() {
        val u = (prefs().getString("sbu", "") ?: "").trimEnd('/'); val k = prefs().getString("sbk", "") ?: ""
        if (u.isEmpty() || k.isEmpty()) return
        val c = URL("$u/rest/v1/signals?select=k,sym,is_long,entry,score,st,res&order=created_at.desc&limit=200").openConnection()
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("apikey", k); c.setRequestProperty("Authorization", "Bearer waleedatrke428@waleedatrke428")
        val arr = JSONArray(c.getInputStream().bufferedReader().use { it.readText() })
        val old = JSONObject(prefs().getString("rs", "{}") ?: "{}")
        val first = old.length() == 0
        val nw = JSONObject().put("__init", "1")
        val nsig = prefs().getBoolean("notif", true); val ntp = prefs().getBoolean("ntp", true); val nsl = prefs().getBoolean("nsl", true)
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i); val key = r.getString("k")
            val res = r.optString("res"); val st = r.optInt("st", 0)
            nw.put(key, "$res|$st")
            if (first) continue
            val prev = old.optString(key, ""); val sym = r.getString("sym")
            val side = if (r.optBoolean("is_long")) "شراء" else "بيع"
            if (prev.isEmpty()) {
                if (nsig && r.optInt("score") >= prefs().getInt("min", 60)) note(key.hashCode(), "🔔 فرصة جديدة: $sym ($side)", "الدخول ${f(r.optDouble("entry"))} • التقييم ${r.optInt("score")}")
            } else {
                val p = prev.split("|"); val pst = p[1].toIntOrNull() ?: 0
                if (st > pst && ntp) note((key + "T" + st).hashCode(), "🎯 $sym حققت الهدف $st", "بعد الهدف الأول انقل الوقف لنقطة الدخول")
                if (res == "loss" && p[0] != "loss" && nsl) note((key + "L").hashCode(), "🛑 $sym ضربت وقف الخسارة", "الدخول ${f(r.optDouble("entry"))}")
            }
        }
        prefs().edit().putString("rs", nw.toString()).apply()
    }

    private fun scan() {
        if (!(prefs().getString("sbk", "") ?: "").isEmpty()) { try { pollRemote() } catch (x: Exception) { }; return }
        var ex: Ex? = null; var syms: List<String> = emptyList()
        for (e in EXS) { try { val l = e.syms(); if (l.isNotEmpty()) { ex = e; syms = l; break } } catch (x: Exception) { } }
        val e = ex ?: run { prefs().edit().putString("d", JSONObject().put("err", "تعذّر الاتصال بالمنصات").toString()).apply(); return }
        val pool = Executors.newFixedThreadPool(6)
        val all = pool.invokeAll(syms.map { s -> Callable<JSONObject?> { try { analyze(e, s, 40) } catch (x: Exception) { null } } })
            .mapNotNull { it.get() }.sortedByDescending { it.getInt("score") }
        val ms = minScore()
        val res = all.filter { it.getInt("score") >= ms }.take(15)
        val near = all.filter { it.getInt("score") < ms }.take(40)
        pool.shutdown()
        val tr = track(e.n, res)
        val out = JSONObject().put("time", System.currentTimeMillis()).put("ex", e.n).put("total", syms.size).put("list", JSONArray(res)).put("near", JSONArray(near)).put("tr", tr)
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
