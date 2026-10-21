package com.example.traderlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full backup (JSON) and journal import (JSON or the CSV that the Journal share button produces).
 * Device specific settings such as notification sound URIs are deliberately not included.
 */
object Backup {
    private val KEYS = listOf(
        "c_bal", "c_risk", "src_tv", "cat", "vib", "loud", "sltp_on", "mon_on", "mon_wake",
        "chart_engine", "dl_hide", "upd_auto", "pinned"
    )

    /** What a file contains, ready to be applied after the user confirms. */
    class Parsed(
        val kind: String, // "json" = replaces data, "csv" = adds trades
        val trades: List<Trade>,
        val alerts: List<PriceAlert>?,
        val watch: List<String>?,
        val settings: Map<String, String>?
    )

    fun fileName(): String =
        "TraderLauncher-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

    fun export(ctx: Context, store: Store): String {
        val o = JSONObject()
        o.put("app", "TraderLauncher")
        o.put("format", 1)
        o.put("appVersion", Updater.installedVersion(ctx))
        o.put("exportedAt", System.currentTimeMillis())
        val tr = JSONArray()
        store.loadTrades().forEach { tr.put(it.toJson()) }
        o.put("trades", tr)
        val al = JSONArray()
        AlertRepo.alerts.value.forEach { al.put(it.toJson()) }
        o.put("alerts", al)
        o.put("watchlist", JSONArray(store.watchlist))
        val st = JSONObject()
        KEYS.forEach { k -> store.getRaw(k)?.let { st.put(k, it) } }
        o.put("settings", st)
        return o.toString(2)
    }

    /** Returns null when the text is neither a backup nor a journal CSV. */
    fun parse(text: String): Parsed? {
        val t = text.trim().removePrefix("\uFEFF")
        if (t.startsWith("{")) return parseJson(t)
        if (t.startsWith("date,pair,side")) return parseCsv(t)
        return null
    }

    private fun parseJson(t: String): Parsed? {
        return try {
            val o = JSONObject(t)
            if (o.optString("app") != "TraderLauncher") return null
            val ta = o.optJSONArray("trades") ?: JSONArray()
            val trades = (0 until ta.length()).map { tradeFromJson(ta.getJSONObject(it)) }
            val aa = o.optJSONArray("alerts")
            val alerts = aa?.let { a -> (0 until a.length()).map { alertFromJson(a.getJSONObject(it)) } }
            val wa = o.optJSONArray("watchlist")
            val watch = wa?.let { a -> (0 until a.length()).map { a.getString(it) } }
            val so = o.optJSONObject("settings")
            val settings = so?.let { s ->
                s.keys().asSequence().filter { it in KEYS }.associateWith { s.getString(it) }
            }
            Parsed("json", trades, alerts, watch, settings)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseCsv(t: String): Parsed? {
        return try {
            val rows = csvRows(t)
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val now = System.currentTimeMillis()
            val trades = ArrayList<Trade>()
            for ((i, r) in rows.drop(1).withIndex()) {
                if (r.size < 8 || r[1].isBlank()) continue
                val time = try { df.parse(r[0])?.time ?: now } catch (e: Exception) { now }
                trades.add(
                    Trade(
                        id = time + i,
                        time = time,
                        pair = r[1].trim(),
                        isLong = !r[2].trim().equals("SHORT", ignoreCase = true),
                        entry = r[3].toNum(),
                        sl = r[4].toNum(),
                        tp = r[5].toNum(),
                        pnl = r[7].toNum(),
                        setup = r.getOrElse(8) { "" },
                        note = r.getOrElse(9) { "" }
                    )
                )
            }
            Parsed("csv", trades, null, null, null)
        } catch (e: Exception) {
            null
        }
    }

    /** Small RFC 4180 reader: quoted fields may contain commas, quotes ("") and line breaks. */
    private fun csvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    sb.append(c)
                }
            } else if (c == '"') {
                quoted = true
            } else if (c == ',') {
                row.add(sb.toString())
                sb.setLength(0)
            } else if (c == '\n') {
                row.add(sb.toString())
                sb.setLength(0)
                rows.add(row)
                row = ArrayList()
            } else if (c != '\r') {
                sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows
    }

    /** JSON replaces journal / alerts / watchlist / settings; CSV only adds trades that are not there yet. */
    fun apply(ctx: Context, store: Store, p: Parsed) {
        if (p.kind == "csv") {
            val cur = store.loadTrades()
            val seen = cur.map { Triple(it.time, it.pair, it.isLong) }.toHashSet()
            val add = p.trades.filter { Triple(it.time, it.pair, it.isLong) !in seen }.sortedByDescending { it.time }
            store.saveTrades(add + cur)
        } else {
            store.saveTrades(p.trades)
            p.alerts?.let { AlertRepo.replaceAlerts(it) }
            p.watch?.let { if (it.isNotEmpty()) store.watchlist = it }
            p.settings?.forEach { (k, v) -> store.putStr(k, v) }
        }
        AlertEngine.invalidateTrades()
        MonitorService.sync(ctx)
    }
}
