package com.example.traderlauncher

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * UNOFFICIAL TradingView quote feed (reverse-engineered websocket; same protocol the npm package
 * @mathieuc/tradingview speaks). Personal use only. It can break whenever TradingView changes it,
 * so Market.fetch always falls back to Yahoo/Binance when this returns nothing.
 *
 * Wire format (text frames):
 *   frame   = "~m~" + payload.length + "~m~" + payload      (several frames may be concatenated)
 *   payload = JSON {"m": method, "p": [params]}   or heartbeat "~h~N" (must be echoed back as a frame)
 * Client sends : set_auth_token ["unauthorized_user_token"], quote_create_session [sid],
 *                quote_set_fields [sid, "lp","ch","chp",...], quote_add_symbols [sid, "EXCH:SYMBOL", ...]
 * Server sends : qsd [sid, {"n":"EXCH:SYMBOL","s":"ok","v":{"lp":2345.6,"ch":1.2,"chp":0.05}}]
 *                (later updates only contain the changed fields), quote_completed, protocol_error, ...
 */
object TvFeed {
    private const val URL = "wss://data.tradingview.com/socket.io/websocket"
    private val ORIGINS = listOf(
        "https://s.tradingview.com",
        "https://data.tradingview.com",
        "https://www.tradingview.com"
    )
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private class Tick {
        @Volatile var lp: Double = Double.NaN
        @Volatile var chp: Double = Double.NaN
        @Volatile var ts: Long = 0L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived connection
        .build()

    private val lock = Any()
    private var ws: WebSocket? = null
    @Volatile private var open = false
    @Volatile private var fails = 0
    @Volatile var lastError: String = ""
    private var originIdx = 0
    private var lastAttempt = 0L

    private val wanted = LinkedHashSet<String>() // guarded by lock
    private val added = HashSet<String>()        // guarded by lock: already sent on the current connection
    private val ticks = ConcurrentHashMap<String, Tick>()
    private val bad = ConcurrentHashMap.newKeySet<String>() // symbols rejected by the server
    private val session = "qs_" + (1..12).map { ('a'..'z').random() }.joinToString("")

    // ---------- public api ----------

    /** Subscribe to [symbols] (TradingView-style, e.g. "OANDA:XAUUSD") and connect if needed. */
    fun ensure(symbols: Collection<String>) {
        synchronized(lock) {
            wanted.addAll(symbols)
            val w = ws
            if (w == null) {
                val now = System.currentTimeMillis()
                val backoff = if (fails >= 3) 60_000L else 0L
                if (now - lastAttempt >= backoff) {
                    lastAttempt = now
                    connect()
                }
            } else if (open) {
                flushAdds(w)
            }
        }
    }

    fun healthy(): Boolean = fails < 2

    /** Last known quote, or null (never received / connection dead and data older than 30 s). */
    fun quote(tv: String): Quote? {
        val t = ticks[tv] ?: return null
        val lp = t.lp
        if (lp.isNaN()) return null
        if (!open && System.currentTimeMillis() - t.ts > 30_000) return null
        val c = t.chp
        return Quote(lp, if (c.isNaN()) null else c, "TradingView")
    }

    /** Waits (up to [maxMs]) until every symbol has a first tick or was rejected. */
    suspend fun await(symbols: List<String>, maxMs: Long) {
        var waited = 0L
        while (waited < maxMs && symbols.any { quote(it) == null && it !in bad }) {
            delay(100)
            waited += 100
        }
    }

    fun status(): String = when {
        open -> "terhubung (${ticks.size} simbol)"
        lastError.isNotEmpty() -> "gagal: " + lastError.take(80)
        else -> "belum tersambung"
    }

    fun close() {
        synchronized(lock) {
            ws?.close(1000, "bye")
            ws = null
            open = false
            added.clear()
            bad.clear()
        }
    }

    // ---------- internals ----------

    private fun connect() { // lock must be held
        val req = Request.Builder()
            .url(URL)
            .header("Origin", ORIGINS[originIdx % ORIGINS.size])
            .header("User-Agent", UA)
            .build()
        ws = client.newWebSocket(req, listener)
    }

    private fun frame(s: String) = "~m~${s.length}~m~$s"

    private fun send(w: WebSocket, method: String, vararg params: Any) {
        val arr = JSONArray()
        for (p in params) arr.put(p)
        val msg = JSONObject().put("m", method).put("p", arr).toString()
        w.send(frame(msg))
    }

    private fun flushAdds(w: WebSocket) { // lock must be held
        val todo = wanted.filter { it !in added && it !in bad }
        if (todo.isEmpty()) return
        send(w, "quote_add_symbols", session, *todo.toTypedArray())
        added.addAll(todo)
    }

    private fun drop(w: WebSocket) {
        synchronized(lock) {
            if (ws === w) {
                ws = null
                open = false
                added.clear()
            }
        }
    }

    private fun handle(w: WebSocket, raw: String) {
        var i = 0
        while (i < raw.length && raw.startsWith("~m~", i)) {
            val j = raw.indexOf("~m~", i + 3)
            if (j < 0) return
            val len = raw.substring(i + 3, j).toIntOrNull() ?: return
            val start = j + 3
            val end = start + len
            if (end > raw.length) return
            payload(w, raw.substring(start, end))
            i = end
        }
    }

    private fun payload(w: WebSocket, p: String) {
        if (p.startsWith("~h~")) {
            w.send(frame(p)) // heartbeat echo
            return
        }
        val o = try { JSONObject(p) } catch (e: Exception) { return }
        when (o.optString("m")) {
            "qsd" -> {
                val d = o.getJSONArray("p").optJSONObject(1) ?: return
                val n = d.optString("n")
                if (d.optString("s") == "error") {
                    bad.add(n)
                    return
                }
                val v = d.optJSONObject("v") ?: return
                val t = ticks.getOrPut(n) { Tick() }
                if (v.has("lp") && !v.isNull("lp")) t.lp = v.getDouble("lp")
                if (v.has("chp") && !v.isNull("chp")) t.chp = v.getDouble("chp")
                t.ts = System.currentTimeMillis()
            }
            "critical_error", "protocol_error" -> lastError = p.take(200)
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                open = true
                fails = 0
                lastError = ""
                added.clear()
                send(webSocket, "set_auth_token", "unauthorized_user_token")
                send(webSocket, "quote_create_session", session)
                send(webSocket, "quote_set_fields", session, "lp", "ch", "chp", "lp_time")
                flushAdds(webSocket)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handle(webSocket, text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            drop(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            lastError = (t.message ?: t.javaClass.simpleName) + (response?.let { " (HTTP ${it.code})" } ?: "")
            fails++
            if (!open) originIdx++ // connect failed -> try the next Origin header
            drop(webSocket)
        }
    }
}
