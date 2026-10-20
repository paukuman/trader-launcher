@file:OptIn(ExperimentalFoundationApi::class)

package com.example.traderlauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WIB: ZoneId = ZoneId.of("Asia/Jakarta")

private data class Sess(val name: String, val zone: String, val open: Int, val close: Int)

private val SESSIONS = listOf(
    Sess("Sydney", "Australia/Sydney", 7, 16),
    Sess("Tokyo", "Asia/Tokyo", 9, 18),
    Sess("London", "Europe/London", 8, 17),
    Sess("New York", "America/New_York", 8, 17)
)

/** Pair(isOpen, "open-close" in WIB). DST handled through real time zones. */
private fun sessionState(s: Sess, now: ZonedDateTime): Pair<Boolean, String> {
    val local = now.withZoneSameInstant(ZoneId.of(s.zone))
    val weekday = local.dayOfWeek.value in 1..5
    val isOpen = weekday && local.hour >= s.open && local.hour < s.close
    val o = local.withHour(s.open).withMinute(0).withZoneSameInstant(WIB)
    val c = local.withHour(s.close).withMinute(0).withZoneSameInstant(WIB)
    return isOpen to "%02d–%02d".format(o.hour, c.hour)
}

/** Compact clock (WIB) with one row of market-session pills. */
@Composable
fun ClockHeader() {
    var now by remember { mutableStateOf(ZonedDateTime.now(WIB)) }
    LaunchedEffect(AppState.resumed) {
        while (AppState.resumed) {
            now = ZonedDateTime.now(WIB)
            delay(1000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 40.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                now.format(DateTimeFormatter.ofPattern(":ss")),
                fontSize = 18.sp,
                color = Muted,
                modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("WIB · UTC+7", fontSize = 11.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SESSIONS.forEach { s ->
                val st = sessionState(s, now)
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (st.first) Color(0x3322C55E) else Color(0x14FFFFFF))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (st.first) Green else Muted)
                    )
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text(s.name, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(st.second, fontSize = 9.sp, color = Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(selected: Cat?, onSelect: (Cat?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Chip("All", selected == null) { onSelect(null) }
        Cat.values().forEach { c -> Chip(c.label, selected == c) { onSelect(c) } }
    }
}

@Composable
fun PairRow(sym: String, q: Quote?, bell: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val base = Market.baseOf(sym)
    val fx = Market.isFxBase(base)
    val cat = Market.category(sym)
    val pre = sym.substringBefore(':', "")
    val src = when (q?.src) {
        "TradingView" -> "TradingView"
        "Binance" -> "Binance"
        "Yahoo" -> "Yahoo"
        "open.er-api" -> "Daily rate"
        else -> if (Market.route(sym) == Route.NONE) "Chart only" else "Loading…"
    }
    val c = q?.changePct
    Row(
        Modifier
            .fillMaxWidth()
            .card(12.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(0x2210B981)),
            contentAlignment = Alignment.Center
        ) {
            Text(cat.tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (fx) base.take(3) + "/" + base.drop(3) else base,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                if (bell) {
                    Spacer(Modifier.width(5.dp))
                    Ico(R.drawable.ic_bell, tint = Accent, size = 12.dp)
                }
            }
            Text(
                (if (pre.isNotEmpty()) "$pre · " else "") + src,
                fontSize = 10.5.sp,
                color = Muted,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (q == null) "…" else fmtPrice(q.price, fx),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                if (c == null) "—" else "%+.2f%%".format(c),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (c == null) Muted else if (c >= 0) Green else Red,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (c == null) Color.Transparent
                        else if (c >= 0) Color(0x2622C55E) else Color(0x26EF4444)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

private val SUGGEST: Map<Cat, List<String>> = mapOf(
    Cat.FOREX to listOf("EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCAD", "USDIDR"),
    Cat.COMMODITY to listOf("XAUUSD", "XAGUSD", "USOIL", "UKOIL", "NATGAS"),
    Cat.CRYPTO to listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "DOGEUSDT"),
    Cat.STOCK to listOf("NASDAQ:AAPL", "NASDAQ:NVDA", "NASDAQ:TSLA", "NYSE:JPM", "IDX:BBCA", "IDX:BBRI"),
    Cat.INDEX to listOf("SPX", "NDX", "DJI", "DXY", "GER40", "VIX")
)

@Composable
private fun AddDialog(
    watch: List<String>,
    startCat: Cat,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(startCat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Add instrument") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Cat.values().forEach { c -> Chip(c.label, cat == c) { cat = c } }
                }
                Text(
                    "Tap to add",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                (SUGGEST[cat] ?: emptyList()).chunked(3).forEach { rowItems ->
                    Row(
                        Modifier.padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { s ->
                            Chip(Market.baseOf(s), s in watch) { if (s !in watch) onAdd(s) }
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.uppercase() },
                    singleLine = true,
                    label = { Text("Custom symbol") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "Examples: BTCUSDT, EURUSD, XAUUSD, SPX, IDX:BBCA, NASDAQ:AAPL, or any TradingView symbol like OANDA:XAUUSD.",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onAdd(text); text = "" }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun HomeScreen(
    watch: List<String>,
    quotes: Map<String, Quote>,
    cat: Cat?,
    onCat: (Cat?) -> Unit,
    alerts: List<PriceAlert>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAlert: (String) -> Unit,
    onOpenChart: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<String?>(null) }
    val shown = if (cat == null) watch else watch.filter { Market.category(it) == cat }

    Column(Modifier.fillMaxSize()) {
        ClockHeader()
        Spacer(Modifier.height(8.dp))
        CategoryRow(cat, onCat)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                (cat?.label ?: "All markets") + " · " + shown.size,
                fontSize = 12.sp,
                color = Muted,
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color(0x3310B981))
                    .combinedClickable(onClick = { showAdd = true })
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ico(R.drawable.ic_add, tint = Accent, size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(shown, key = { it }) { sym ->
                PairRow(
                    sym = sym,
                    q = quotes[sym],
                    bell = alerts.any { it.active && it.symbol == normSymbol(sym) },
                    onClick = { onOpenChart(sym) },
                    onLong = { toRemove = sym }
                )
            }
            if (shown.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nothing here yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap Add to track your first instrument.",
                            fontSize = 12.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(
            watch = watch,
            startCat = cat ?: Cat.FOREX,
            onAdd = onAdd,
            onDismiss = { showAdd = false }
        )
    }

    toRemove?.let { s ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            containerColor = Surface2,
            title = { Text(s) },
            text = {
                TextButton(onClick = { toRemove = null; onAlert(s) }) {
                    Ico(R.drawable.ic_bell, tint = Accent, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Set price alert")
                }
            },
            confirmButton = {
                TextButton(onClick = { onRemove(s); toRemove = null }) {
                    Ico(R.drawable.ic_delete, tint = Red, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove", color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text("Cancel") }
            }
        )
    }
}
