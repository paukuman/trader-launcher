package com.example.traderlauncher

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

// Pure Kotlin (no Android classes) so the parsers can be compiled and tested on a plain JVM.

data class NewsItem(
    val id: Long,
    val title: String,
    val url: String,
    val source: String,
    val ago: String,
    val comments: Int,
    val impact: Int, // 0 none, 1 low, 2 medium, 3 high (Forex Factory "breaking news" rating)
    val summary: String
)

data class CalEvent(
    val id: String,
    val timeMs: Long,
    val label: String, // "" for a normal time, else "Tentative" / "All day" / "Day 3"
    val currency: String,
    val impact: Int, // 0 holiday / non-economic, 1 low, 2 medium, 3 high
    val title: String,
    val actual: String,
    val forecast: String,
    val previous: String,
    val better: Int, // 1 actual better than forecast, -1 worse, 0 unknown or in line
    val url: String
)

object NewsParser {
    private const val FF = "https://www.forexfactory.com"
    private val OPTS = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    private val ANCHOR = Regex("<a\\b([^>]*)>(.*?)</a>", OPTS)
    private val HREF = Regex("href\\s*=\\s*([\"'])(.*?)\\1", OPTS)
    private val NEWS_PATH = Regex("^(?:https?://(?:www\\.)?forexfactory\\.com)?/news/(\\d+)-([A-Za-z0-9-]*)(/hit)?(#post)?$")
    private val IMPACT_ICON = Regex("impact/ff/(low|medium|high)\\.svg", RegexOption.IGNORE_CASE)
    private val TAGS = Regex("<[^>]*>", OPTS)
    private val SPACES = Regex("\\s+")
    private val AGO = Regex(
        "^\\|?\\s*((?:\\d+\\s*(?:days?|d|hrs?|h|mins?|m|secs?|s)\\s*)+ago|[A-Z][a-z]{2,8}\\s+\\d{1,2}(?:,\\s*\\d{4})?)",
        RegexOption.IGNORE_CASE
    )
    private val COMMENTS = Regex("^\\|?\\s*(\\d+)\\s+comments?", RegexOption.IGNORE_CASE)

    private class Item(val id: Long, val slug: String) {
        var title = ""
        var source = ""
        var comments = 0
        var titleEnd = 0
        var hitStart = -1
        var hitEnd = -1
    }

    fun decode(s: String): String {
        var out = s
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&rsquo;", "\u2019").replace("&lsquo;", "\u2018").replace("&ndash;", "\u2013")
            .replace("&mdash;", "\u2014").replace("&hellip;", "\u2026")
        out = Regex("&#(\\d+);").replace(out) { m ->
            val n = m.groupValues[1].toIntOrNull()
            if (n != null && n in 1..0x10FFFF) String(Character.toChars(n)) else m.value
        }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m ->
            val n = m.groupValues[1].toIntOrNull(16)
            if (n != null && n in 1..0x10FFFF) String(Character.toChars(n)) else m.value
        }
        return out
    }

    fun clean(html: String): String =
        decode(TAGS.replace(html, " ")).replace(SPACES, " ").trim()

    /** True for a Cloudflare challenge / block page instead of the real site. */
    fun looksBlocked(html: String): Boolean {
        val h = html.take(4000)
        return h.contains("Just a moment", true) || h.contains("challenge-platform", true) ||
            h.contains("cf-browser-verification", true) || h.contains("Attention Required", true)
    }

    /** Forex Factory /news page: every story links to /news/<id>-<slug>; "/hit" is the source link, "#post" the comments. */
    fun parseNews(html: String): List<NewsItem> {
        val items = LinkedHashMap<Long, Item>()
        for (m in ANCHOR.findAll(html)) {
            val href = HREF.find(m.groupValues[1])?.groupValues?.get(2)?.trim() ?: continue
            val pm = NEWS_PATH.find(href) ?: continue
            val id = pm.groupValues[1].toLongOrNull() ?: continue
            val it = items.getOrPut(id) { Item(id, pm.groupValues[2]) }
            val text = clean(m.groupValues[2])
            when {
                pm.groupValues[3].isNotEmpty() -> {
                    if (it.hitStart < 0) {
                        it.hitStart = m.range.first
                        it.hitEnd = m.range.last + 1
                        if (text.startsWith("From", true)) it.source = text.substring(4).trim()
                    }
                }
                pm.groupValues[4].isNotEmpty() -> {
                    val n = Regex("\\d+").find(text)?.value?.toIntOrNull()
                    if (n != null) it.comments = n
                }
                else -> if (it.title.isEmpty() && text.isNotBlank()) {
                    it.title = text
                    it.titleEnd = m.range.last + 1
                }
            }
        }

        val out = ArrayList<NewsItem>()
        for (it in items.values) {
            if (it.title.isEmpty()) continue
            var impact = 0
            var ago = ""
            var comments = it.comments
            var summary = ""
            if (it.hitStart >= 0) {
                // The impact icon sits between the title link and the "From ..." link of the same story.
                val from = maxOf(it.titleEnd, it.hitStart - 800)
                val window = html.substring(minOf(from, it.hitStart), it.hitStart)
                val icon = IMPACT_ICON.findAll(window).lastOrNull()?.groupValues?.get(1)?.lowercase()
                impact = when (icon) { "high" -> 3; "medium" -> 2; "low" -> 1; else -> 0 }

                var seg = html.substring(it.hitEnd, minOf(html.length, it.hitEnd + 2500))
                val next = seg.indexOf("document/news.svg")
                if (next >= 0) seg = seg.substring(0, next)
                var txt = clean(seg)
                AGO.find(txt)?.let { a ->
                    ago = a.groupValues[1].trim()
                    txt = txt.substring(a.range.last + 1).trim()
                }
                COMMENTS.find(txt)?.let { c ->
                    comments = c.groupValues[1].toIntOrNull() ?: comments
                    txt = txt.substring(c.range.last + 1).trim()
                }
                summary = txt.removePrefix("|").trim().take(260)
            }
            out.add(
                NewsItem(it.id, it.title, "$FF/news/${it.id}-${it.slug}", it.source, ago, comments, impact, summary)
            )
        }
        return out.sortedByDescending { it.id }.take(80)
    }

    // ------------------------------------------------------------------ calendar

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "").trim()

    private fun impactOf(s: String): Int {
        val t = s.lowercase()
        return when {
            t.contains("high") || t.contains("red") -> 3
            t.contains("med") || t.contains("orange") || t.contains("-ora") -> 2
            t.contains("low") || t.contains("yellow") || t.contains("-yel") -> 1
            else -> 0
        }
    }

    /** The JSON array that starts at the '[' at [start], respecting strings and escapes. */
    private fun balancedArray(s: String, start: Int): String? {
        var depth = 0
        var inStr = false
        var quote = '"'
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') i++ else if (c == quote) inStr = false
            } else if (c == '"' || c == '\'') {
                inStr = true
                quote = c
            } else if (c == '[') {
                depth++
            } else if (c == ']') {
                depth--
                if (depth == 0) return s.substring(start, i + 1)
            }
            i++
        }
        return null
    }

    /**
     * The calendar page embeds its full data (including released "actual" values) as a JS object with a
     * "days" array of {events: [...]}. Returns an empty list when that layout is not found.
     */
    fun parseCalendarPage(html: String): List<CalEvent> {
        val base = maxOf(html.indexOf("calendarComponentStates"), 0)
        val m = Regex("\"?days\"?\\s*:\\s*\\[").find(html, base) ?: return emptyList()
        val raw = balancedArray(html, m.range.last) ?: return emptyList()
        val days = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<CalEvent>()
        for (d in 0 until days.length()) {
            val events = days.optJSONObject(d)?.optJSONArray("events") ?: continue
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                val name = e.str("name").ifEmpty { e.str("title") }
                val dateline = e.optLong("dateline", 0L)
                if (name.isEmpty() || dateline <= 0L) continue
                val timeLabel = e.str("timeLabel")
                val solo = e.str("soloUrl")
                out.add(
                    CalEvent(
                        id = e.str("id").ifEmpty { "$dateline-$name" },
                        timeMs = dateline * 1000L,
                        label = if (timeLabel.any { it.isDigit() } && timeLabel.contains(":")) "" else timeLabel,
                        currency = e.str("currency").ifEmpty { e.str("country") },
                        impact = impactOf(e.str("impactName") + " " + e.str("impactClass")),
                        title = decode(name),
                        actual = decode(e.str("actual")),
                        forecast = decode(e.str("forecast")),
                        previous = decode(e.str("previous")),
                        better = e.optInt("actualBetterWorse", 0).coerceIn(-1, 1),
                        url = if (solo.startsWith("/")) FF + solo else "$FF/calendar"
                    )
                )
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Official weekly export (nfs.faireconomy.media): forecast and previous only. */
    fun parseCalendarFeed(json: String): List<CalEvent> {
        val arr = JSONArray(json)
        val out = ArrayList<CalEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.str("title")
            val date = o.str("date")
            if (title.isEmpty() || date.isEmpty()) continue
            val ms = try { OffsetDateTime.parse(date).toInstant().toEpochMilli() } catch (e: Exception) { continue }
            val impactText = o.str("impact")
            out.add(
                CalEvent(
                    id = "$ms-${o.str("country")}-$title",
                    timeMs = ms,
                    label = if (impactText.equals("Holiday", true)) "All day" else "",
                    currency = o.str("country"),
                    impact = if (impactText.equals("Holiday", true)) 0 else impactOf(impactText),
                    title = title,
                    actual = o.str("actual"),
                    forecast = o.str("forecast"),
                    previous = o.str("previous"),
                    better = 0,
                    url = "$FF/calendar"
                )
            )
        }
        return out.sortedBy { it.timeMs }
    }
}
