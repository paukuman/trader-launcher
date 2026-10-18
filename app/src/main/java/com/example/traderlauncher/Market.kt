package com.example.traderlauncher

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class Quote(val price: Double, val changePct: Double?, val src: String = "")

enum class Route { BINANCE, YAHOO, NONE }

/** Watchlist category shown as a filter chip. [tag] is the 2-letter badge on each row. */
enum class Cat(val label: String, val tag: String) {
    FOREX("Forex", "FX"),
    COMMODITY("Commodities", "CM"),
    CRYPTO("Crypto", "CR"),
    STOCK("Stocks", "ST"),
    INDEX("Indices", "IX")
}

/**
 * Market data layer. Watchlist entries are "symbols" in one of these forms:
 *   BTCUSDT            -> Binance spot (auto)
 *   EURUSD / XAUUSD    -> forex & metals (Yahoo, fallback open.er-api for non-metals)
 *   SPX, NDX, DXY ...  -> aliases (see ALIAS)
 *   EXCHANGE:SYMBOL    -> TradingView-style, e.g. OANDA:XAUUSD, IDX:BBCA, NASDAQ:AAPL
 * TradingView has NO official price API: we only embed its widget (ChartScreen) for charts.
 */
object Market {
    private val FIAT = setOf(
        "USD", "EUR", "GBP", "JPY", "CHF", "AUD", "NZD", "CAD", "IDR", "SGD", "MYR", "CNY",
        "HKD", "THB", "PHP", "INR", "KRW", "TWD", "SEK", "NOK", "DKK", "PLN", "ZAR", "MXN",
        "TRY", "BRL", "AED", "SAR", "XAU", "XAG", "XPT", "XPD"
    )
    private val METALS = setOf("XAU", "XAG", "XPT", "XPD")
    private val G10 = setOf("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "NZD", "CAD", "XAU", "XAG", "XPT", "XPD")

    /** alias -> (Yahoo symbol, TradingView symbol) */
    private val ALIAS: Map<String, Pair<String, String>> = mapOf(
        "SPX" to ("^GSPC" to "SP:SPX"),
        "US500" to ("^GSPC" to "SP:SPX"),
        "NDX" to ("^NDX" to "NASDAQ:NDX"),
        "NAS100" to ("^NDX" to "NASDAQ:NDX"),
        "DJI" to ("^DJI" to "DJ:DJI"),
        "US30" to ("^DJI" to "DJ:DJI"),
        "DXY" to ("DX-Y.NYB" to "TVC:DXY"),
        "USOIL" to ("CL=F" to "TVC:USOIL"),
        "WTI" to ("CL=F" to "TVC:USOIL"),
        "UKOIL" to ("BZ=F" to "TVC:UKOIL"),
        "BRENT" to ("BZ=F" to "TVC:UKOIL"),
        "NATGAS" to ("NG=F" to "TVC:NATGAS"),
        "GER40" to ("^GDAXI" to "TVC:DAX"),
        "JP225" to ("^N225" to "TVC:NI225"),
        "VIX" to ("^VIX" to "TVC:VIX")
    )
    private val YAHOO_ALT = mapOf("XAUUSD" to "GC=F", "XAGUSD" to "SI=F")
    private val HOSTS = listOf("https://data-api.binance.vision", "https://api.binance.com")
    private const val UA = "Mozilla/5.0 (Linux; Android 13) TraderLauncher/0.2"

    private val yCache = ConcurrentHashMap<String, Pair<Long, Quote>>()

    // ---------- symbol helpers ----------
    fun baseOf(sym: String): String = sym.substringAfter(':')
    private fun prefixOf(sym: String): String = sym.substringBefore(':', "")
    fun isFxBase(base: String) = base.length == 6 && base.take(3) in FIAT && base.drop(3) in FIAT
    fun isFxLike(sym: String) = isFxBase(baseOf(sym))

    // ---------- categories (watchlist filter chips) ----------
    private val COMMODITY_BASES = setOf("USOIL", "UKOIL", "NATGAS", "WTI", "BRENT")
    private val INDEX_BASES = setOf("SPX", "US500", "NDX", "NAS100", "DJI", "US30", "DXY", "VIX", "GER40", "JP225")
    private val CRYPTO_EXCHANGES = setOf(
        "BINANCE", "BYBIT", "COINBASE", "OKX", "KRAKEN", "BITSTAMP", "BITFINEX", "KUCOIN", "GATEIO", "MEXC"
    )

    fun category(sym: String): Cat {
        val pre = prefixOf(sym)
        val base = baseOf(sym)
        return when {
            base in COMMODITY_BASES -> Cat.COMMODITY
            base in INDEX_BASES -> Cat.INDEX
            isFxBase(base) ->
                if (base.take(3) in METALS || base.drop(3) in METALS) Cat.COMMODITY else Cat.FOREX
            pre.isEmpty() || pre in CRYPTO_EXCHANGES -> Cat.CRYPTO
            else -> Cat.STOCK
        }
    }

    fun yahooSymbol(sym: String): String? {
        val pre = prefixOf(sym)
        val base = baseOf(sym)
        if (pre == "BINANCE") return null
        ALIAS[base]?.let { return it.first }
        return when {
            pre == "NASDAQ" || pre == "NYSE" || pre == "AMEX" || pre == "ARCA" -> base
            pre == "IDX" -> "$base.JK"
            isFxBase(base) -> "$base=X"
            else -> null
        }
    }

    fun route(sym: String): Route {
        if (yahooSymbol(sym) != null) return Route.YAHOO
        val pre = prefixOf(sym)
        return if (pre.isEmpty() || pre == "BINANCE") Route.BINANCE else Route.NONE
    }

    fun tvSymbol(sym: String): String {
        if (':' in sym) return sym
        ALIAS[sym]?.let { return it.second }
        if (isFxBase(sym)) {
            val both = sym.take(3) in G10 && sym.drop(3) in G10
            return (if (both) "OANDA:" else "FX_IDC:") + sym
        }
        return "BINANCE:$sym"
    }

    // ---------- http ----------
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.setRequestProperty("User-Agent", UA)
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

    // ---------- providers ----------
    private fun parseTicker(o: JSONObject): Pair<String, Quote> =
        o.getString("symbol") to Quote(
            o.getString("lastPrice").toDouble(),
            o.getString("priceChangePercent").toDouble(),
            "Binance"
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
            // One invalid symbol fails the whole batch -> retry one by one
            for (s in symbols) {
                try {
                    val (k, q) = parseTicker(JSONObject(binanceGet("/api/v3/ticker/24hr?symbol=$s")))
                    out[k] = q
                } catch (e2: Exception) {
                    // ignore invalid symbol
                }
            }
        }
        return out
    }

    private fun fetchYahoo(ys: String): Quote? {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/" +
            URLEncoder.encode(ys, "UTF-8") + "?interval=1d&range=1d"
        val meta = JSONObject(get(url))
            .getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
        val price = meta.getDouble("regularMarketPrice")
        var prev = meta.optDouble("previousClose", Double.NaN)
        if (prev.isNaN()) prev = meta.optDouble("chartPreviousClose", Double.NaN)
        val chg = if (prev.isNaN() || prev == 0.0) null else (price - prev) / prev * 100.0
        return Quote(price, chg, "Yahoo")
    }

    private fun fetchYahooCached(sym: String): Quote? {
        val now = System.currentTimeMillis()
        val cached = yCache[sym]
        if (cached != null && now - cached.first < 15_000) return cached.second
        val ys = yahooSymbol(sym) ?: return null
        var q: Quote? = try { fetchYahoo(ys) } catch (e: Exception) { null }
        if (q == null) {
            val alt = YAHOO_ALT[baseOf(sym)]
            if (alt != null) q = try { fetchYahoo(alt) } catch (e: Exception) { null }
        }
        if (q != null) {
            yCache[sym] = now to q
            return q
        }
        return cached?.second // stale value is better than nothing
    }

    private fun fetchEr(syms: List<String>, out: MutableMap<String, Quote>) {
        val rates = JSONObject(get("https://open.er-api.com/v6/latest/USD")).getJSONObject("rates")
        for (s in syms) {
            val b = baseOf(s)
            val x = b.take(3)
            val y = b.drop(3)
            if (rates.has(x) && rates.has(y)) {
                out[s] = Quote(rates.getDouble(y) / rates.getDouble(x), null, "open.er-api")
            }
        }
    }

    /** When true, everything except plain Binance crypto is priced from the TradingView feed first (see TvFeed). */
    @Volatile
    var useTv: Boolean = true

    /**
     * Returns quotes keyed by the ORIGINAL watchlist symbol. Missing key = no price available.
     * Priority: Binance (plain crypto) | TradingView feed (everything else, if useTv) -> Yahoo -> open.er-api.
     */
    suspend fun fetch(symbols: List<String>): Map<String, Quote> = coroutineScope {
        val out = ConcurrentHashMap<String, Quote>()

        // 1) TradingView feed for everything that is not plain Binance crypto
        if (useTv) {
            val tvMap = symbols.filter { route(it) != Route.BINANCE }.associateWith { tvSymbol(it) }
            if (tvMap.isNotEmpty()) {
                TvFeed.ensure(tvMap.values)
                if (TvFeed.healthy()) TvFeed.await(tvMap.values.toList(), 1500)
                for ((s, tv) in tvMap) {
                    val q = TvFeed.quote(tv)
                    if (q != null) out[s] = q
                }
            }
        }

        // 2) Regular providers for whatever is still missing
        val rest = symbols.filter { !out.containsKey(it) }
        val jobs = ArrayList<Deferred<Unit>>()

        val bin = rest.filter { route(it) == Route.BINANCE }
        if (bin.isNotEmpty()) {
            jobs.add(async(Dispatchers.IO) {
                val res = fetchBinance(bin.map { baseOf(it) }.distinct())
                for (s in bin) {
                    val q = res[baseOf(s)]
                    if (q != null) out[s] = q
                }
                Unit
            })
        }
        for (s in rest.filter { route(it) == Route.YAHOO }) {
            jobs.add(async(Dispatchers.IO) {
                val q = fetchYahooCached(s)
                if (q != null) out[s] = q
                Unit
            })
        }
        jobs.awaitAll()

        // 3) Fallback for plain forex (not metals) when Yahoo failed
        val missing = rest.filter {
            route(it) == Route.YAHOO && !out.containsKey(it) && isFxBase(baseOf(it)) &&
                baseOf(it).take(3) !in METALS && baseOf(it).drop(3) !in METALS
        }
        if (missing.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try { fetchEr(missing, out) } catch (e: Exception) { }
            }
        }
        HashMap(out)
    }
}

fun fmtPrice(p: Double, fx: Boolean): String = when {
    p >= 1000 -> "%,.2f".format(p)
    fx -> if (p >= 100) "%.3f".format(p) else "%.5f".format(p)
    p >= 1 -> "%.4f".format(p)
    else -> "%.6f".format(p)
}
