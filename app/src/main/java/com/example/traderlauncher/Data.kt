package com.example.traderlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Trade(
    val id: Long,
    val time: Long,
    val pair: String,
    val isLong: Boolean,
    val entry: Double?,
    val sl: Double?,
    val tp: Double?,
    val pnl: Double?,
    val setup: String,
    val note: String
) {
    /** Planned risk:reward, null when the data is incomplete. */
    val rr: Double?
        get() {
            if (entry == null || sl == null || tp == null) return null
            val risk = Math.abs(entry - sl)
            return if (risk == 0.0) null else Math.abs(tp - entry) / risk
        }
}

private fun JSONObject.numOrNull(k: String): Double? =
    if (has(k) && !isNull(k)) getDouble(k) else null

fun Trade.toJson(): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("time", time)
    o.put("pair", pair)
    o.put("long", isLong)
    o.put("entry", entry ?: JSONObject.NULL)
    o.put("sl", sl ?: JSONObject.NULL)
    o.put("tp", tp ?: JSONObject.NULL)
    o.put("pnl", pnl ?: JSONObject.NULL)
    o.put("setup", setup)
    o.put("note", note)
    return o
}

fun tradeFromJson(o: JSONObject) = Trade(
    id = o.getLong("id"),
    time = o.getLong("time"),
    pair = o.getString("pair"),
    isLong = o.getBoolean("long"),
    entry = o.numOrNull("entry"),
    sl = o.numOrNull("sl"),
    tp = o.numOrNull("tp"),
    pnl = o.numOrNull("pnl"),
    setup = o.optString("setup", ""),
    note = o.optString("note", "")
)

class Store(context: Context) {
    private val p = context.getSharedPreferences("trader", Context.MODE_PRIVATE)

    var watchlist: List<String>
        get() = p.getString("watch", null)
            ?.split(",")?.filter { it.isNotBlank() }
            ?: listOf("XAUUSD", "EURUSD", "USDJPY", "BTCUSDT", "ETHUSDT", "SOLUSDT", "SPX", "NASDAQ:AAPL")
        set(v) {
            p.edit().putString("watch", v.joinToString(",")).apply()
        }

    fun getPinned(): List<String>? =
        p.getString("pinned", null)?.split(",")?.filter { it.isNotBlank() }

    fun setPinned(v: List<String>) {
        p.edit().putString("pinned", v.joinToString(",")).apply()
    }

    fun loadTrades(): List<Trade> {
        val s = p.getString("journal", "[]") ?: "[]"
        val arr = JSONArray(s)
        return (0 until arr.length()).map { tradeFromJson(arr.getJSONObject(it)) }
    }

    fun saveTrades(list: List<Trade>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p.edit().putString("journal", arr.toString()).apply()
    }

    fun getStr(k: String, def: String): String = p.getString(k, def) ?: def
    fun putStr(k: String, v: String) {
        p.edit().putString(k, v).apply()
    }
}
