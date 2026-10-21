package com.example.traderlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Section(title: String, icon: Int, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .card(14.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(icon, tint = Accent, size = 18.dp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
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
    "Setup matches my trading plan",
    "High-impact news checked (economic calendar)",
    "Stop loss set before entry",
    "Risk within my per-trade limit",
    "Risk:Reward at least 1:2",
    "Emotionally neutral (no revenge / FOMO)"
)

/** Full-screen overlay opened from the toolbar: position calculator, pre-trade checklist, settings, links. */
@Composable
fun ToolsScreen(store: Store, isDefault: Boolean, onSetDefault: () -> Unit, onRestored: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var balance by remember { mutableStateOf(store.getStr("c_bal", "")) }
    var riskPct by remember { mutableStateOf(store.getStr("c_risk", "1")) }
    var entry by remember { mutableStateOf("") }
    var sl by remember { mutableStateOf("") }
    var tp by remember { mutableStateOf("") }
    var useTv by remember { mutableStateOf(Market.useTv) }
    var tvStatus by remember { mutableStateOf(TvFeed.status()) }
    LaunchedEffect(AppState.resumed) {
        while (AppState.resumed) {
            tvStatus = TvFeed.status()
            delay(2000)
        }
    }
    val checks = remember { mutableStateListOf<Boolean>().apply { repeat(CHECKLIST.size) { add(false) } } }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgOverlay)
            .pointerInput(Unit) {} // swallow touches
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tools", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Ico(R.drawable.ic_close) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Section("Home screen", R.drawable.ic_apps) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isDefault) "Default launcher" else "Not the default launcher", fontSize = 13.sp)
                        Text(
                            if (isDefault) "The Home button opens this app."
                            else "Set it as default so the Home button opens this app.",
                            fontSize = 11.sp,
                            color = Muted
                        )
                    }
                    if (!isDefault) {
                        OutlinedButton(onClick = onSetDefault) { Text("Set default", fontSize = 12.sp) }
                    } else {
                        Ico(R.drawable.ic_check, tint = Green, size = 20.dp)
                    }
                }
            }

            UpdateSection()

            BackupSection(store, onRestored)

            Section("Position size calculator", R.drawable.ic_calculator) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Balance", balance, Modifier.weight(1f)) { balance = it; store.putStr("c_bal", it) }
                    NumField("Risk %", riskPct, Modifier.weight(1f)) { riskPct = it; store.putStr("c_risk", it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("0.5", "1", "2").forEach { v ->
                        Chip("$v%", riskPct == v) { riskPct = v; store.putStr("c_risk", v) }
                    }
                }
                NumField("Entry", entry) { entry = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Stop loss", sl, Modifier.weight(1f)) { sl = it }
                    NumField("Take profit (optional)", tp, Modifier.weight(1f)) { tp = it }
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
                    Line("Risk amount", "%,.2f".format(riskAmt))
                    Line("Stop distance", fmtN(dist))
                    Line("Position size (units)", "%,.4f".format(units))
                    Line("≈ Standard forex lots", "%.2f".format(units / 100000.0))
                    Line("Position value", "%,.2f".format(units * e))
                    if (t != null) {
                        val reward = Math.abs(t - e)
                        Line("Risk : Reward", "1 : %.2f".format(reward / dist))
                        Line("Potential profit", "%,.2f".format(units * reward))
                    }
                    Text(
                        "Assumes the quote currency equals your account currency (e.g. USDT/USD). For JPY or IDR pairs, adjust the pip value.",
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
            }

            Section("Pre-trade checklist", R.drawable.ic_check) {
                CHECKLIST.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checks[i], onCheckedChange = { checks[i] = it })
                        Text(item, fontSize = 13.sp)
                    }
                }
                val done = checks.count { it }
                val all = done == CHECKLIST.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (all) {
                            Ico(R.drawable.ic_check, tint = Green, size = 18.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (all) "Ready to execute" else "$done/${CHECKLIST.size} done",
                            color = if (all) Green else Muted,
                            fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = { for (i in checks.indices) checks[i] = false }) { Text("Reset") }
                }
            }

            Section("Price source", R.drawable.ic_chart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TradingView (unofficial)", fontSize = 13.sp)
                        Text(
                            "Forex, metals, indices and stocks via the TradingView websocket. Crypto stays on Binance. Yahoo is the fallback.",
                            fontSize = 11.sp,
                            color = Muted
                        )
                    }
                    Switch(
                        checked = useTv,
                        onCheckedChange = {
                            useTv = it
                            Market.useTv = it
                            store.putStr("src_tv", if (it) "1" else "0")
                            if (!it) TvFeed.close()
                        }
                    )
                }
                Text("Status: " + (if (useTv) tvStatus else "off"), fontSize = 11.sp, color = Muted)
            }

            Section("Quick links", R.drawable.ic_open_in_new) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { openUrl(ctx, "https://www.forexfactory.com/calendar") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Calendar", fontSize = 12.sp) }
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
}
