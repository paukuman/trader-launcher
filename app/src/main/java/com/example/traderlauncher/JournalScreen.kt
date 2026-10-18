package com.example.traderlauncher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun exportCsv(ctx: Context, trades: List<Trade>) {
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val sb = StringBuilder("date,pair,side,entry,sl,tp,rr,pnl,setup,note\n")
    trades.sortedBy { it.time }.forEach { t ->
        sb.append(
            listOf(
                df.format(Date(t.time)),
                t.pair,
                if (t.isLong) "LONG" else "SHORT",
                t.entry?.let { fmtN(it) } ?: "",
                t.sl?.let { fmtN(it) } ?: "",
                t.tp?.let { fmtN(it) } ?: "",
                t.rr?.let { "%.2f".format(Locale.US, it) } ?: "",
                t.pnl?.let { fmtN(it) } ?: "",
                q(t.setup),
                q(t.note)
            ).joinToString(",")
        ).append("\n")
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Trading journal")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    ctx.startActivity(Intent.createChooser(send, "Export journal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun StatBox(label: String, value: String, color: Color = Color.White, modifier: Modifier = Modifier) {
    Column(
        modifier
            .card(12.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

@Composable
fun Seg(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Color(0x33FFFFFF),
            contentColor = if (selected) OnAccent else Color.White
        )
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
fun NumField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun TradeDialog(
    initial: Trade?,
    onDismiss: () -> Unit,
    onSave: (Trade) -> Unit,
    onDelete: (() -> Unit)?
) {
    val p0 = initial?.pnl
    var pair by remember { mutableStateOf(initial?.pair ?: "") }
    var isLong by remember { mutableStateOf(initial?.isLong ?: true) }
    var entry by remember { mutableStateOf(initial?.entry?.let { fmtN(it) } ?: "") }
    var sl by remember { mutableStateOf(initial?.sl?.let { fmtN(it) } ?: "") }
    var tp by remember { mutableStateOf(initial?.tp?.let { fmtN(it) } ?: "") }
    var result by remember { mutableIntStateOf(if (p0 == null) 0 else if (p0 >= 0) 1 else 2) }
    var amount by remember { mutableStateOf(if (p0 == null) "" else fmtN(Math.abs(p0))) }
    var setup by remember { mutableStateOf(initial?.setup ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(if (initial == null) "New trade" else "Edit trade") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pair,
                    onValueChange = { pair = it.uppercase() },
                    label = { Text("Pair") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Seg("LONG", isLong, Modifier.weight(1f)) { isLong = true }
                    Seg("SHORT", !isLong, Modifier.weight(1f)) { isLong = false }
                }
                NumField("Entry", entry) { entry = it }
                NumField("Stop loss", sl) { sl = it }
                NumField("Take profit", tp) { tp = it }
                Text("Result", fontSize = 12.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Seg("Open", result == 0, Modifier.weight(1f)) { result = 0 }
                    Seg("Win", result == 1, Modifier.weight(1f)) { result = 1 }
                    Seg("Loss", result == 2, Modifier.weight(1f)) { result = 2 }
                }
                if (result != 0) {
                    NumField("Amount (positive, e.g. 25)", amount) { amount = it }
                }
                OutlinedTextField(
                    value = setup,
                    onValueChange = { setup = it },
                    label = { Text("Setup / strategy") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes / emotions / review") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Ico(R.drawable.ic_delete, tint = Red, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete this trade", color = Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pair.isNotBlank(),
                onClick = {
                    val amt = amount.toNum() ?: 0.0
                    val pnl: Double? = when (result) {
                        0 -> null
                        1 -> amt
                        else -> -amt
                    }
                    val now = System.currentTimeMillis()
                    onSave(
                        Trade(
                            id = initial?.id ?: now,
                            time = initial?.time ?: now,
                            pair = pair.trim(),
                            isLong = isLong,
                            entry = entry.toNum(),
                            sl = sl.toNum(),
                            tp = tp.toNum(),
                            pnl = pnl,
                            setup = setup.trim(),
                            note = note.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TradeItem(t: Trade, onClick: () -> Unit) {
    val df = remember { SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH) }
    val p = t.pnl
    val stripe = if (p == null) Accent else if (p >= 0) Green else Red
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .card(12.dp)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(stripe))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.pair, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (t.isLong) "  LONG" else "  SHORT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (t.isLong) Green else Red
                        )
                    }
                    Text(df.format(Date(t.time)), fontSize = 10.5.sp, color = Muted)
                }
                Text(
                    if (p == null) "OPEN" else "%+,.2f".format(p),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = stripe
                )
            }
            val parts = mutableListOf<String>()
            t.entry?.let { parts.add("Entry " + fmtN(it)) }
            t.sl?.let { parts.add("SL " + fmtN(it)) }
            t.tp?.let { parts.add("TP " + fmtN(it)) }
            t.rr?.let { parts.add("RR 1:%.1f".format(it)) }
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (t.setup.isNotBlank()) {
                Text("Setup: " + t.setup, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
            }
            if (t.note.isNotBlank()) {
                Text(t.note, fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Cumulative PnL of closed trades, oldest to newest. */
@Composable
private fun EquityCurve(points: List<Double>, modifier: Modifier = Modifier) {
    val col = if ((points.lastOrNull() ?: 0.0) >= 0) Green else Red
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val lo = minOf(points.minOf { it }, 0.0)
        val hi = maxOf(points.maxOf { it }, 0.0)
        val span = if (hi - lo == 0.0) 1.0 else hi - lo
        val w = size.width
        val h = size.height
        fun px(i: Int): Float = w * i / (points.size - 1)
        fun py(v: Double): Float = (h - ((v - lo) / span * h)).toFloat()
        drawLine(Color(0x33FFFFFF), Offset(0f, py(0.0)), Offset(w, py(0.0)), strokeWidth = 1f)
        val path = Path()
        points.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v))
        }
        drawPath(path, col, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun JournalScreen(
    trades: List<Trade>,
    onSave: (Trade) -> Unit,
    onDelete: (Long) -> Unit
) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Trade?>(null) }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 open, 2 wins, 3 losses

    val pnls = trades.mapNotNull { it.pnl }
    val wins = pnls.filter { it > 0 }
    val losses = pnls.filter { it < 0 }
    val net = pnls.sum()
    val winRate = if (pnls.isEmpty()) "–" else "%.0f%%".format(wins.size * 100.0 / pnls.size)
    val pf = when {
        losses.isNotEmpty() -> "%.2f".format(wins.sum() / -losses.sum())
        wins.isNotEmpty() -> "∞"
        else -> "–"
    }
    // Not wrapped in remember(): `trades` is one mutable list instance, so a key would go stale.
    var acc = 0.0
    val curve = trades.filter { it.pnl != null }.sortedBy { it.time }.map { acc += it.pnl ?: 0.0; acc }
    val shown = when (filter) {
        1 -> trades.filter { it.pnl == null }
        2 -> trades.filter { (it.pnl ?: 0.0) > 0 }
        3 -> trades.filter { (it.pnl ?: 0.0) < 0 }
        else -> trades
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Journal", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { exportCsv(ctx, trades) }, enabled = trades.isNotEmpty()) {
                Ico(R.drawable.ic_share, tint = if (trades.isNotEmpty()) Color.White else Muted, size = 20.dp)
            }
            Button(
                onClick = { creating = true },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Ico(R.drawable.ic_add, tint = OnAccent, size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("Trade", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatBox("Trades", trades.size.toString(), modifier = Modifier.weight(1f))
            StatBox("Win rate", winRate, modifier = Modifier.weight(1f))
            StatBox(
                "Net PnL",
                "%+,.2f".format(net),
                color = if (net >= 0) Green else Red,
                modifier = Modifier.weight(1.4f)
            )
            StatBox("Profit factor", pf, modifier = Modifier.weight(1.1f))
        }
        if (curve.size >= 2) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).card(12.dp).padding(10.dp)
            ) {
                Text("Equity curve", fontSize = 10.sp, color = Muted)
                EquityCurve(curve, Modifier.fillMaxWidth().height(56.dp).padding(top = 6.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Open", "Wins", "Losses").forEachIndexed { i, label ->
                Chip(label, filter == i) { filter = i }
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(top = 8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(shown, key = { it.id }) { t -> TradeItem(t) { editing = t } }
            if (shown.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (trades.isEmpty()) "No trades logged yet" else "No trades match this filter",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (trades.isEmpty()) {
                            Text(
                                "Tap Trade to log your first one.",
                                fontSize = 12.sp,
                                color = Muted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TradeDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { onSave(it); creating = false },
            onDelete = null
        )
    }
    editing?.let { t ->
        TradeDialog(
            initial = t,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(t.id); editing = null }
        )
    }
}
