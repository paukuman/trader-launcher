package com.example.traderlauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** What the News page shows. Written from background threads, read by Compose. */
object NewsState {
    var news by mutableStateOf<List<NewsItem>>(emptyList())
    var newsAt by mutableLongStateOf(0L)
    var newsBusy by mutableStateOf(false)
    var newsStatus by mutableStateOf("") // empty = fine, otherwise the last error

    var calWeek by mutableStateOf("this") // "this" or "next"
    var calendar by mutableStateOf<List<CalEvent>>(emptyList())
    var calAt by mutableLongStateOf(0L)
    var calBusy by mutableStateOf(false)
    var calStatus by mutableStateOf("")
    var calSource by mutableStateOf("")
}

/**
 * Fetches Forex Factory headlines and the economic calendar. The calendar tries the page first (it contains the
 * released "actual" values) and falls back to the official weekly JSON export (forecast and previous only).
 * Results are cached; automatic refreshes are spaced out so the site is not hammered.
 */
object NewsRepo {
    private const val FF = "https://www.forexfactory.com"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val FRESH_MS = 5L * 60 * 1000 // headlines
    private const val FRESH_CAL_MS = 10L * 60 * 1000
    private const val MIN_MANUAL_GAP_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private class CalCache(val at: Long, val events: List<CalEvent>, val source: String)

    private val calCache = HashMap<String, CalCache>()

    private fun get(url: String): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        http.newCall(req).execute().use { r ->
            val bytes = r.body?.bytes() ?: ByteArray(0)
            // The site declares ISO-8859-1 but sends UTF-8; only fall back when UTF-8 decoding fails.
            val utf8 = String(bytes, Charsets.UTF_8)
            return r.code to (if (utf8.contains('\uFFFD')) String(bytes, Charsets.ISO_8859_1) else utf8)
        }
    }

    private fun why(code: Int, body: String): String = when {
        code == 403 || code == 503 || NewsParser.looksBlocked(body) ->
            "Forex Factory blocked the request (Cloudflare). Use the browser button to read it on the site."
        code == 429 -> "Too many requests, try again in a few minutes."
        code != 200 -> "Forex Factory answered HTTP $code."
        else -> "Could not read the page. Its layout may have changed."
    }

    fun refreshNews(force: Boolean) {
        if (NewsState.newsBusy) return
        val age = System.currentTimeMillis() - NewsState.newsAt
        if (NewsState.news.isNotEmpty() && age < (if (force) MIN_MANUAL_GAP_MS else FRESH_MS)) return
        NewsState.newsBusy = true
        scope.launch {
            try {
                val (code, body) = get("$FF/news")
                val items = if (code == 200) NewsParser.parseNews(body) else emptyList()
                if (items.isNotEmpty()) {
                    NewsState.news = items
                    NewsState.newsAt = System.currentTimeMillis()
                    NewsState.newsStatus = ""
                } else {
                    NewsState.newsStatus = why(code, body)
                }
            } catch (e: Exception) {
                NewsState.newsStatus = "Network error: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                NewsState.newsBusy = false
            }
        }
    }

    private fun publish(week: String, c: CalCache) {
        if (NewsState.calWeek != week) return
        NewsState.calendar = c.events
        NewsState.calAt = c.at
        NewsState.calSource = c.source
        NewsState.calStatus = ""
    }

    /** Switch between this week and next week; shows cached data immediately, then refreshes if it is stale. */
    fun selectWeek(week: String) {
        NewsState.calWeek = week
        val c = calCache[week]
        if (c != null) publish(week, c) else NewsState.calendar = emptyList()
        refreshCalendar(false)
    }

    fun refreshCalendar(force: Boolean) {
        if (NewsState.calBusy) return
        val week = NewsState.calWeek
        val cached = calCache[week]
        val age = if (cached == null) Long.MAX_VALUE else System.currentTimeMillis() - cached.at
        if (cached != null && age < (if (force) MIN_MANUAL_GAP_MS else FRESH_CAL_MS)) {
            publish(week, cached)
            return
        }
        NewsState.calBusy = true
        scope.launch {
            var events: List<CalEvent> = emptyList()
            var source = ""
            var error = ""
            try {
                val (code, body) = get("$FF/calendar?week=$week")
                if (code == 200) events = NewsParser.parseCalendarPage(body)
                if (events.isNotEmpty()) source = "Forex Factory calendar" else error = why(code, body)
            } catch (e: Exception) {
                error = "Network error: " + (e.message ?: e.javaClass.simpleName)
            }
            if (events.isEmpty()) {
                try {
                    val file = if (week == "next") "ff_calendar_nextweek.json" else "ff_calendar_thisweek.json"
                    val (code, body) = get("https://nfs.faireconomy.media/$file")
                    if (code == 200) {
                        events = NewsParser.parseCalendarFeed(body)
                        if (events.isNotEmpty()) {
                            source = "Forex Factory weekly feed (forecast and previous only)"
                            error = ""
                        }
                    } else if (code == 429) {
                        error = "The weekly feed is rate limited, try again in a few minutes."
                    }
                } catch (e: Exception) {
                    if (error.isEmpty()) error = "Network error: " + (e.message ?: e.javaClass.simpleName)
                }
            }
            if (events.isNotEmpty()) {
                val c = CalCache(System.currentTimeMillis(), events.distinctBy { it.id }, source)
                calCache[week] = c
                publish(week, c)
            } else if (NewsState.calWeek == week) {
                NewsState.calStatus = error.ifEmpty { "No events found." }
            }
            NewsState.calBusy = false
        }
    }
}
