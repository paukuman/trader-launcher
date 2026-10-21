package com.example.traderlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Create a price alert. [symbol] is prefilled when opened from a pair, otherwise the user types it. */
@Composable
fun AlertEditDialog(
    symbol: String?,
    priceOf: (String) -> Double?,
    onSave: (PriceAlert) -> Unit,
    onDismiss: () -> Unit
) {
    var sym by remember { mutableStateOf(symbol ?: "") }
    var level by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf<Boolean?>(null) } // null = follow the current price
    var note by remember { mutableStateOf("") }

    val s = normSymbol(sym)
    val cur = if (s.isEmpty()) null else priceOf(s)
    val lvl = level.toNum()
    val above = dir ?: (lvl != null && cur != null && lvl >= cur)
    val fx = Market.isFxBase(Market.baseOf(s))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("New price alert") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (symbol == null) {
                    OutlinedTextField(
                        value = sym,
                        onValueChange = { sym = it.uppercase() },
                        label = { Text("Symbol (e.g. BTCUSDT, EURUSD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    if (cur != null) "Current price: " + fmtPrice(cur, fx) else "Current price unknown",
                    fontSize = 12.sp,
                    color = Muted
                )
                NumField("Alert level", level) { level = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Seg("Above", above, Modifier.weight(1f)) { dir = true }
                    Seg("Below", !above, Modifier.weight(1f)) { dir = false }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Custom message (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "You are notified once when the price reaches the level. Sounds are set in Alerts.",
                    fontSize = 11.sp,
                    color = Muted
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = s.isNotEmpty() && lvl != null,
                onClick = {
                    onSave(PriceAlert(System.currentTimeMillis(), s, lvl ?: 0.0, above, note.trim()))
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(subtitle, fontSize = 11.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SoundRow(label: String, value: String, onPick: () -> Unit, onTest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            Text(value, fontSize = 11.sp, color = Muted, maxLines = 1)
        }
        TextButton(onClick = onTest) { Text("Test", fontSize = 12.sp) }
        OutlinedButton(onClick = onPick) { Text("Change", fontSize = 12.sp) }
    }
}

/** Full-screen overlay: alert list, SL/TP monitoring, notification sounds and background settings. */
@Composable
fun AlertsScreen(
    store: Store,
    onAddAlert: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val alerts by AlertRepo.alerts.collectAsState()
    val df = remember { SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH) }

    var tick by remember { mutableIntStateOf(0) } // bump to re-read sound names
    var vib by remember { mutableStateOf(store.getStr("vib", "1") == "1") }
    var loud by remember { mutableStateOf(store.getStr("loud", "0") == "1") }
    var sltp by remember { mutableStateOf(store.getStr("sltp_on", "1") == "1") }
    var mon by remember { mutableStateOf(store.getStr("mon_on", "1") == "1") }
    var wake by remember { mutableStateOf(store.getStr("mon_wake", "0") == "1") }
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    var pickKind by remember { mutableStateOf("price") }

    LaunchedEffect(AppState.resumed) {
        notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        batteryOk = isIgnoringBattery(ctx)
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            @Suppress("DEPRECATION")
            val uri: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            store.putStr("snd_$pickKind", uri?.toString() ?: "")
            tick++
        }
    }

    fun pickSound(kind: String) {
        pickKind = kind
        val cur = store.getStr("snd_$kind", "")
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                if (kind == "price") "Price alert sound" else "SL / TP alert sound"
            )
            if (cur.isNotEmpty()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur))
        }
        picker.launch(i)
    }

    val priceSound = remember(tick) { Notifier.soundTitle(ctx, store, "price") }
    val riskSound = remember(tick) { Notifier.soundTitle(ctx, store, "risk") }
    val openWatch = AlertEngine.openTradeCount(ctx)

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
            Text("Alerts", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Ico(R.drawable.ic_close) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!notifOn) {
                Section("Notifications are off", R.drawable.ic_bell) {
                    Text(
                        "Alerts cannot notify you until notifications are allowed for this app.",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openAppSettings(ctx)
                        }
                    }) { Text("Allow notifications") }
                }
            }

            Section("Price alerts", R.drawable.ic_bell) {
                if (alerts.isEmpty()) {
                    Text("No alerts yet. Long-press a pair on Markets, or add one here.", fontSize = 12.sp, color = Muted)
                }
                alerts.sortedWith(compareByDescending<PriceAlert> { it.active }.thenByDescending { it.id }).forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().card(10.dp).padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fx = Market.isFxBase(Market.baseOf(a.symbol))
                        Column(Modifier.weight(1f)) {
                            Text(a.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                (if (a.above) "Above " else "Below ") + fmtPrice(a.level, fx) +
                                    (if (a.note.isBlank()) "" else " · " + a.note),
                                fontSize = 11.sp,
                                color = Muted
                            )
                            if (!a.active) {
                                Text(
                                    "Triggered " + df.format(Date(a.firedAt)) + " at " + fmtPrice(a.firedPrice, fx),
                                    fontSize = 11.sp,
                                    color = Green
                                )
                            }
                        }
                        if (!a.active) {
                            TextButton(onClick = {
                                AlertRepo.rearm(a.id)
                                MonitorService.sync(ctx)
                            }) { Text("Re-arm", fontSize = 12.sp) }
                        }
                        IconButton(onClick = {
                            AlertRepo.remove(a.id)
                            MonitorService.sync(ctx)
                        }) { Ico(R.drawable.ic_delete, tint = Red, size = 20.dp) }
                    }
                }
                OutlinedButton(onClick = onAddAlert) {
                    Ico(R.drawable.ic_add, tint = Accent, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Add alert")
                }
            }

            Section("Stop loss / take profit", R.drawable.ic_check) {
                ToggleRow(
                    "Alert on SL / TP",
                    "Open journal trades with an SL or TP notify you when price touches a level.",
                    sltp
                ) {
                    sltp = it
                    store.putStr("sltp_on", if (it) "1" else "0")
                    AlertEngine.invalidateTrades()
                    MonitorService.sync(ctx)
                }
                Text(
                    if (sltp) "Watching $openWatch open trade" + (if (openWatch == 1) "" else "s") + "."
                    else "Turned off.",
                    fontSize = 11.sp,
                    color = Muted
                )
            }

            Section("Sounds", R.drawable.ic_apps) {
                SoundRow("Price alerts", priceSound, { pickSound("price") }, { Notifier.test(ctx, "price") })
                SoundRow("SL / TP alerts", riskSound, { pickSound("risk") }, { Notifier.test(ctx, "risk") })
                ToggleRow("Vibrate", "Vibration pattern with every alert.", vib) {
                    vib = it
                    store.putStr("vib", if (it) "1" else "0")
                }
                ToggleRow(
                    "Loud mode",
                    "Play at alarm volume so alerts are heard even when the ringer is silent.",
                    loud
                ) {
                    loud = it
                    store.putStr("loud", if (it) "1" else "0")
                }
            }

            Section("Background", R.drawable.ic_refresh) {
                ToggleRow(
                    "Watch while the app is closed",
                    "Runs a small foreground service with a status notification. Off = alerts only fire while the app is open.",
                    mon
                ) {
                    mon = it
                    store.putStr("mon_on", if (it) "1" else "0")
                    MonitorService.sync(ctx)
                }
                ToggleRow(
                    "Reliable mode",
                    "Keeps the CPU awake so checks continue with the screen off. Uses more battery.",
                    wake
                ) {
                    wake = it
                    store.putStr("mon_wake", if (it) "1" else "0")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Battery optimisation", fontSize = 13.sp)
                        Text(
                            if (batteryOk) "Unrestricted: Android will not pause the app."
                            else "Android may pause the app when the phone idles. Allow unrestricted for dependable alerts.",
                            fontSize = 11.sp,
                            color = Muted
                        )
                    }
                    if (!batteryOk) {
                        OutlinedButton(onClick = { requestBatteryExemption(ctx) }) { Text("Allow", fontSize = 12.sp) }
                    } else {
                        Ico(R.drawable.ic_check, tint = Green, size = 20.dp)
                    }
                }
            }
        }
    }
}

private fun isIgnoringBattery(ctx: Context): Boolean =
    try {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) {
        false
    }

private fun requestBatteryExemption(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            // no settings screen available on this device
        }
    }
}

private fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // ignore
    }
}
