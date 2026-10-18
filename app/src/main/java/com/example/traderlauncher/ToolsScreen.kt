package com.example.traderlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

private val CHECKLIST = listOf(
    "Setup sesuai trading plan",
    "Cek berita high-impact (kalender ekonomi)",
    "Stop loss ditentukan sebelum entry",
    "Risiko ≤ batas per trade",
    "Risk:Reward minimal 1:2",
    "Kondisi emosi netral (bukan revenge/FOMO)"
)

@Composable
fun ToolsScreen(store: Store) {
    val ctx = LocalContext.current
    var balance by remember { mutableStateOf(store.getStr("c_bal", "")) }
    var riskPct by remember { mutableStateOf(store.getStr("c_risk", "1")) }
    var entry by remember { mutableStateOf("") }
    var sl by remember { mutableStateOf("") }
    var tp by remember { mutableStateOf("") }
    val checks = remember { mutableStateListOf<Boolean>().apply { repeat(CHECKLIST.size) { add(false) } } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section("Kalkulator posisi") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Saldo", balance, Modifier.weight(1f)) { balance = it; store.putStr("c_bal", it) }
                NumField("Risiko %", riskPct, Modifier.weight(1f)) { riskPct = it; store.putStr("c_risk", it) }
            }
            NumField("Entry", entry) { entry = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Stop Loss", sl, Modifier.weight(1f)) { sl = it }
                NumField("Take Profit (opsional)", tp, Modifier.weight(1f)) { tp = it }
            }

            val bal = balance.toNum()
            val rp = riskPct.toNum()
            val e = entry.toNum()
            val s = sl.toNum()
            val t = tp.toNum()
            if (bal != null && rp != null && e != null && s != null && e != s) {
                val riskAmt = bal * rp / 100.0
                val dist = Math.abs(e - s)
                val units = riskAmt / dist
                Line("Nominal risiko", "%,.2f".format(riskAmt))
                Line("Jarak SL", fmtN(dist))
                Line("Ukuran posisi (unit)", "%,.4f".format(units))
                Line("≈ Lot standar forex", "%.2f".format(units / 100000.0))
                Line("Nilai posisi", "%,.2f".format(units * e))
                if (t != null) {
                    val reward = Math.abs(t - e)
                    Line("Risk : Reward", "1 : %.2f".format(reward / dist))
                    Line("Potensi profit", "%,.2f".format(units * reward))
                }
                Text(
                    "Asumsi: mata uang kuotasi = mata uang akun (mis. USDT/USD). Untuk pair JPY/IDR dll., sesuaikan nilai pip.",
                    fontSize = 11.sp,
                    color = Muted
                )
            }
        }

        Section("Checklist pra-trade") {
            CHECKLIST.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checks[i], onCheckedChange = { checks[i] = it })
                    Text(item, fontSize = 13.sp)
                }
            }
            val done = checks.count { it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done == CHECKLIST.size) "Siap eksekusi ✅" else "$done/${CHECKLIST.size} terpenuhi",
                    color = if (done == CHECKLIST.size) Green else Muted,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp
                )
                TextButton(onClick = { for (i in checks.indices) checks[i] = false }) { Text("Reset") }
            }
        }

        Section("Tautan cepat") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { openUrl(ctx, "https://www.forexfactory.com/calendar") },
                    modifier = Modifier.weight(1f)
                ) { Text("Kalender", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { openUrl(ctx, "https://www.tradingview.com/") },
                    modifier = Modifier.weight(1f)
                ) { Text("TradingView", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { openUrl(ctx, "https://coinmarketcap.com/") },
                    modifier = Modifier.weight(1f)
                ) { Text("CMC", fontSize = 12.sp) }
            }
        }
    }
}
