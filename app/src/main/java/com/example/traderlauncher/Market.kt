package com.example.traderlauncher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Quote(val price: Double, val changePct: Double?)

object Market {
    private val FIAT = setOf(
        "USD", "EUR", "GBP", "JPY", "CHF", "AUD", "NZD", "CAD",
        "IDR", "SGD", "MYR", "CNY", "HKD", "XAU", "XAG"
    )

    // Endpoint data-publik Binance (market data saja, tanpa API key)
    private val HOSTS = listOf("https://data-api.binance.vision", "https://api.binance.com")

    fun isForex(s: String) = s.length == 6 && s.take(3) in FIAT && s.drop(3) in FIAT

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun binanceGet(path: String): String {
        var last: Exception? = null
        for (h in HOSTS) {
            try {
                return get(h + path)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no host")
    }

    private fun parseTicker(o: JSONObject): Pair<String, Quote> =
        o.getString("symbol") to Quote(
            o.getString("lastPrice").toDouble(),
            o.getString("priceChangePercent").toDouble()
        )

    private fun fetchBinance(symbols: List<String>): Map<String, Quote> {
        val out = HashMap<String, Quote>()
        try {
            val param = URLEncoder.encode(
                symbols.joinToString(",", "[", "]") { "\"$it\"" }, "UTF-8"
            )
            val arr = JSONArray(binanceGet("/api/v3/ticker/24hr?symbols=$param"))
            for (i in 0 until arr.length()) {
                val (s, q) = parseTicker(arr.getJSONObject(i))
                out[s] = q
            }
        } catch (e: Exception) {
            // Satu simbol salah bikin batch gagal -> coba satu per satu
            for (s in symbols) {
                try {
                    val (k, q) = parseTicker(JSONObject(binanceGet("/api/v3/ticker/24hr?symbol=$s")))
                    out[k] = q
                } catch (e2: Exception) {
                    // abaikan simbol yang tidak valid
                }
            }
        }
        return out
    }

    private fun fetchFx(pairs: List<String>): Map<String, Quote> {
        val rates = JSONObject(get("https://open.er-api.com/v6/latest/USD")).getJSONObject("rates")
        val out = HashMap<String, Quote>()
        for (p in pairs) {
            val b = p.take(3)
            val q = p.drop(3)
            if (rates.has(b) && rates.has(q)) {
                out[p] = Quote(rates.getDouble(q) / rates.getDouble(b), null)
            }
        }
        return out
    }

    fun fetch(symbols: List<String>): Map<String, Quote> {
        val out = HashMap<String, Quote>()
        val crypto = symbols.filter { !isForex(it) }
        val fx = symbols.filter { isForex(it) }
        if (crypto.isNotEmpty()) {
            try { out.putAll(fetchBinance(crypto)) } catch (e: Exception) { }
        }
        if (fx.isNotEmpty()) {
            try { out.putAll(fetchFx(fx)) } catch (e: Exception) { }
        }
        return out
    }
}

fun fmtPrice(p: Double, fx: Boolean): String = when {
    p >= 1000 -> "%,.2f".format(p)
    fx -> if (p >= 100) "%.3f".format(p) else "%.5f".format(p)
    p >= 1 -> "%.4f".format(p)
    else -> "%.6f".format(p)
}
