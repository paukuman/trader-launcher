package com.example.traderlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val WIB_TZ: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
private val AMBER = Color(0xFFF59E0B)
private val YELLOW = Color(0xFFFBBF24)

private fun impactColor(i: Int): Color = when (i) {
    3 -> Red
    2 -> AMBER
    1 -> YELLOW
    else -> Muted
}

private fun clock(ms: Long): String {
    val f = SimpleDateFormat("HH:mm", Locale.ENGLISH)
    f.timeZone = WIB_TZ
    return f.format(Date(ms))
}

private fun dayKey(ms: Long): String {
    val f = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
    f.timeZone = WIB_TZ
    return f.format(Date(ms))
}

private fun dayTitle(ms: Long, now: Long): String {
    val f = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
    f.timeZone = WIB_TZ
    val t = f.format(Date(ms))
    return if (dayKey(ms) == dayKey(now)) "Today · $t" else t
}

private fun countdown(ms: Long): String {
    val m = ms / 60_000
    val d = m / 1440
    val h = (m % 1440) / 60
    val mm = m % 60
    return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${mm}m" else "${mm}m"
}

/** Currencies that matter for the pairs on the watchlist (non-forex instruments follow USD). */
private fun currenciesOf(watch: List<String>): Set<String> {
    val out = HashSet<String>()
    for (s in watch) {
        val b = Market.baseOf(s)
        if (Market.isFxBase(b) && b.length >= 6) {
            out.add(b.take(3))
            out.add(b.substring(3, 6))
        } else {
            out.add("USD")
        }
    }
    return out
}

@Composable
private fun StatusLine(at: Long, status: String, busy: Boolean, source: String) {
    val text = when {
        status.isNotEmpty() -> status
        busy && at == 0L -> "Loading…"
        at > 0L -> "Updated " + clock(at) + " WIB" + (if (source.isNotEmpty()) " · $source" else "")
        else -> ""
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            fontSize = 11.sp,
            color = if (status.isNotEmpty()) Red else Muted,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SiteButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .card(12.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(R.drawable.ic_open_in_new, tint = Accent, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NewsRow(n: NewsItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .card(12.dp)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (n.impact > 0) impactColor(n.impact) else Hairline))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(n.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOf(
                n.source,
                n.ago,
                if (n.comments > 0) n.comments.toString() + (if (n.comments == 1) " comment" else " comments") else ""
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
            if (n.summary.isNotBlank()) {
                Text(
                    n.summary,
                    fontSize = 11.5.sp,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun CalRow(e: CalEvent, now: Long, upNext: Boolean, onClick: () -> Unit) {
    val past = e.timeMs < now - 5 * 60_000
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (past) 0.65f else 1f)
            .card(10.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.width(52.dp)) {
            Text(
                if (e.label.isNotEmpty()) e.label else clock(e.timeMs),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (upNext) Accent else Color.White
            )
            if (upNext) Text("UP NEXT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(impactColor(e.impact)))
                Spacer(Modifier.width(6.dp))
                Text(e.currency, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent)
                Spacer(Modifier.width(6.dp))
                Text(e.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (e.actual.isNotBlank() || e.forecast.isNotBlank() || e.previous.isNotBlank()) {
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (e.actual.isNotBlank()) {
                        Text(
                            "A " + e.actual,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (e.better > 0) Green else if (e.better < 0) Red else Color.White
                        )
                    }
                    if (e.forecast.isNotBlank()) Text("F " + e.forecast, fontSize = 11.sp, color = Muted)
                    if (e.previous.isNotBlank()) Text("P " + e.previous, fontSize = 11.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.HeadlinesTab(
    breakingOnly: Boolean,
    onBreaking: (Boolean) -> Unit,
    onOpen: (String, String) -> Unit
) {
    val all = NewsState.news
    val shown = if (breakingOnly) all.filter { it.impact > 0 } else all
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("All", !breakingOnly) { onBreaking(false) }
        Chip("Breaking", breakingOnly) { onBreaking(true) }
    }
    StatusLine(NewsState.newsAt, NewsState.newsStatus, NewsState.newsBusy, "Forex Factory")
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(shown, key = { it.id }) { n -> NewsRow(n) { onOpen(n.url, n.title) } }
        if (shown.isEmpty() && !NewsState.newsBusy) {
            item {
                Text(
                    if (all.isEmpty()) "No headlines yet." else "No breaking headlines right now.",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 20.dp, start = 4.dp)
                )
            }
        }
        item { SiteButton("Open forexfactory.com/news") { onOpen("https://www.forexfactory.com/news", "Forex Factory News") } }
    }
}

@Composable
private fun ColumnScope.CalendarTab(
    watch: List<String>,
    now: Long,
    minImpact: Int,
    onMinImpact: (Int) -> Unit,
    mineOnly: Boolean,
    onMine: (Boolean) -> Unit,
    onOpen: (String, String) -> Unit
) {
    val all = NewsState.calendar
    val mine = remember(watch.toList()) { currenciesOf(watch) }
    val shown = all.filter { it.impact >= minImpact && (!mineOnly || it.currency in mine || it.currency == "All") }
    val next = all.firstOrNull { it.impact == 3 && it.timeMs > now && (!mineOnly || it.currency in mine) }
    val upNextId = shown.firstOrNull { it.timeMs >= now - 5 * 60_000 }?.id

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("This week", NewsState.calWeek == "this") { NewsRepo.selectWeek("this") }
        Chip("Next week", NewsState.calWeek == "next") { NewsRepo.selectWeek("next") }
        Chip("High", minImpact == 3) { onMinImpact(3) }
        Chip("Medium+", minImpact == 2) { onMinImpact(2) }
        Chip("All", minImpact == 0) { onMinImpact(0) }
        Chip("My pairs", mineOnly) { onMine(!mineOnly) }
    }
    if (next != null) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .card(12.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Red))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Next high impact", fontSize = 10.sp, color = Muted)
                Text(next.currency + " " + next.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("in " + countdown(next.timeMs - now), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
    }
    StatusLine(NewsState.calAt, NewsState.calStatus, NewsState.calBusy, NewsState.calSource)

    val groups = shown.groupBy { dayKey(it.timeMs) }
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for ((key, list) in groups) {
            item(key = "day$key") {
                Text(
                    dayTitle(list.first().timeMs, now),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Muted,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            items(list, key = { it.id }) { e -> CalRow(e, now, e.id == upNextId) { onOpen(e.url, e.title) } }
        }
        if (shown.isEmpty() && !NewsState.calBusy) {
            item {
                Text(
                    if (all.isEmpty()) "No events loaded yet." else "No events match these filters.",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 20.dp, start = 4.dp)
                )
            }
        }
        item { SiteButton("Open forexfactory.com/calendar") { onOpen("https://www.forexfactory.com/calendar", "Forex Factory Calendar") } }
        item { Text("Times in WIB. Source: Forex Factory.", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
    }
}

/** Fourth pager page: Forex Factory headlines and the economic calendar. */
@Composable
fun NewsScreen(watch: List<String>, onOpen: (String, String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var breakingOnly by remember { mutableStateOf(false) }
    var minImpact by remember { mutableIntStateOf(2) }
    var mineOnly by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        NewsRepo.refreshNews(false)
        NewsRepo.selectWeek(NewsState.calWeek)
    }
    LaunchedEffect(AppState.resumed) {
        while (AppState.resumed) {
            now = System.currentTimeMillis()
            delay(30_000)
            NewsRepo.refreshNews(false)
            NewsRepo.refreshCalendar(false)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Headlines", tab == 0) { tab = 0 }
            Spacer(Modifier.width(6.dp))
            Chip("Calendar", tab == 1) { tab = 1 }
            Spacer(Modifier.weight(1f))
            val busy = if (tab == 0) NewsState.newsBusy else NewsState.calBusy
            IconButton(
                onClick = { if (tab == 0) NewsRepo.refreshNews(true) else NewsRepo.refreshCalendar(true) },
                enabled = !busy
            ) { Ico(R.drawable.ic_refresh, tint = if (busy) Muted else Accent, size = 22.dp) }
        }
        if (tab == 0) {
            HeadlinesTab(breakingOnly, { breakingOnly = it }, onOpen)
        } else {
            CalendarTab(watch, now, minImpact, { minImpact = it }, mineOnly, { mineOnly = it }, onOpen)
        }
    }
}
