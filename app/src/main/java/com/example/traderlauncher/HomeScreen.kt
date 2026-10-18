@file:OptIn(ExperimentalFoundationApi::class)

package com.example.traderlauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** Pair(isOpen, "jamBuka–jamTutup" dalam WIB). DST ikut dihitung lewat zona waktu. */
private fun sessionState(s: Sess, now: ZonedDateTime): Pair<Boolean, String> {
    val local = now.withZoneSameInstant(ZoneId.of(s.zone))
    val weekday = local.dayOfWeek.value in 1..5
    val isOpen = weekday && local.hour >= s.open && local.hour < s.close
    val o = local.withHour(s.open).withMinute(0).withZoneSameInstant(WIB)
    val c = local.withHour(s.close).withMinute(0).withZoneSameInstant(WIB)
    return isOpen to "%02d–%02d".format(o.hour, c.hour)
}

@Composable
fun ClockHeader() {
    var now by remember { mutableStateOf(ZonedDateTime.now(WIB)) }
    LaunchedEffect(AppState.resumed) {
        while (AppState.resumed) {
            now = ZonedDateTime.now(WIB)
            delay(1000)
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            fontSize = 44.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale("id", "ID"))) + " • WIB",
            fontSize = 13.sp,
            color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SESSIONS.forEach { s ->
                val st = sessionState(s, now)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (st.first) Color(0x4022C55E) else Color(0x22FFFFFF))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.name, fontSize = 11.sp, maxLines = 1)
                    Text(
                        if (st.first) "BUKA" else "TUTUP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (st.first) Green else Muted
                    )
                    Text(st.second, fontSize = 9.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
fun PairRow(sym: String, q: Quote?, onClick: () -> Unit, onLong: () -> Unit) {
    val fx = Market.isForex(sym)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (fx) sym.take(3) + "/" + sym.drop(3) else sym,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(if (fx) "Forex • kurs harian" else "Binance • live", fontSize = 11.sp, color = Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (q == null) "…" else fmtPrice(q.price, fx),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            val c = q?.changePct
            Text(
                if (c == null) "—" else "%+.2f%%".format(c),
                fontSize = 12.sp,
                color = if (c == null) Muted else if (c >= 0) Green else Red
            )
        }
    }
}

@Composable
fun Dock(dock: List<AppEntry>, onOpenApp: (AppEntry) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dock.isEmpty()) {
            Text(
                "Dock kosong. Tekan lama aplikasi di tab Apps untuk pin.",
                fontSize = 12.sp,
                color = Muted,
                textAlign = TextAlign.Center
            )
        } else {
            dock.forEach { app ->
                Column(
                    Modifier.clickable { onOpenApp(app) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(app, 50.dp)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    watch: List<String>,
    quotes: Map<String, Quote>,
    dock: List<AppEntry>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenApp: (AppEntry) -> Unit
) {
    val ctx = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ClockHeader()
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pantauan", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAdd = true }) { Text("+ Pair") }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(watch, key = { it }) { sym ->
                PairRow(
                    sym = sym,
                    q = quotes[sym],
                    onClick = { openChart(ctx, sym) },
                    onLong = { toRemove = sym }
                )
            }
            if (watch.isEmpty()) {
                item { Text("Belum ada pair. Tekan + Pair.", color = Muted) }
            }
        }
        Dock(dock, onOpenApp)
    }

    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Tambah pair") },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.uppercase() },
                        singleLine = true,
                        label = { Text("Mis. BTCUSDT, EURUSD") }
                    )
                    Text(
                        "Crypto: data Binance (live). Forex: kurs harian indikatif. Emas: pakai PAXGUSDT.",
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onAdd(text); showAdd = false }) { Text("Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Batal") }
            }
        )
    }

    toRemove?.let { s ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(s) },
            text = { Text("Hapus dari pantauan?") },
            confirmButton = {
                TextButton(onClick = { onRemove(s); toRemove = null }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text("Batal") }
            }
        )
    }
}
