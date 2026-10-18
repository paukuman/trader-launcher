package com.example.traderlauncher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
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
        putExtra(Intent.EXTRA_SUBJECT, "Jurnal trading")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    ctx.startActivity(Intent.createChooser(send, "Ekspor jurnal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun StatBox(label: String, value: String, color: Color = Color.White, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

@Composable
fun Seg(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Color(0x33FFFFFF),
            contentColor = Color.White
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
        title = { Text(if (initial == null) "Trade baru" else "Edit trade") },
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
                NumField("Stop Loss", sl) { sl = it }
                NumField("Take Profit", tp) { tp = it }
                Text("Hasil", fontSize = 12.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Seg("Open", result == 0, Modifier.weight(1f)) { result = 0 }
                    Seg("Win", result == 1, Modifier.weight(1f)) { result = 1 }
                    Seg("Loss", result == 2, Modifier.weight(1f)) { result = 2 }
                }
                if (result != 0) {
                    NumField("Nominal (positif, mis. 25)", amount) { amount = it }
                }
                OutlinedTextField(
                    value = setup,
                    onValueChange = { setup = it },
                    label = { Text("Setup / strategi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan / emosi / evaluasi") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Hapus trade ini", color = Red) }
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
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun TradeItem(t: Trade, onClick: () -> Unit) {
    val df = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.pair, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (t.isLong) "  LONG" else "  SHORT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (t.isLong) Green else Red
                    )
                }
                Text(df.format(Date(t.time)), fontSize = 11.sp, color = Muted)
            }
            val p = t.pnl
            Text(
                if (p == null) "OPEN" else "%+,.2f".format(p),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (p == null) Accent else if (p >= 0) Green else Red
            )
        }
        val parts = mutableListOf<String>()
        t.entry?.let { parts.add("Entry " + fmtN(it)) }
        t.sl?.let { parts.add("SL " + fmtN(it)) }
        t.tp?.let { parts.add("TP " + fmtN(it)) }
        t.rr?.let { parts.add("RR 1:%.1f".format(it)) }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" • "), fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        if (t.setup.isNotBlank()) {
            Text("Setup: " + t.setup, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (t.note.isNotBlank()) {
            Text(t.note, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Jurnal", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { exportCsv(ctx, trades) }, enabled = trades.isNotEmpty()) { Text("Ekspor") }
            Button(onClick = { creating = true }) { Text("+ Trade") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatBox("Trade", trades.size.toString(), modifier = Modifier.weight(1f))
            StatBox("Win rate", winRate, modifier = Modifier.weight(1f))
            StatBox(
                "Net PnL",
                "%+,.2f".format(net),
                color = if (net >= 0) Green else Red,
                modifier = Modifier.weight(1.3f)
            )
            StatBox("PF", pf, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            Modifier.weight(1f).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(trades, key = { it.id }) { t -> TradeItem(t) { editing = t } }
            if (trades.isEmpty()) {
                item { Text("Belum ada catatan. Tekan + Trade untuk mulai.", color = Muted) }
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
