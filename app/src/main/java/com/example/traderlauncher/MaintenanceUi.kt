package com.example.traderlauncher

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Banner shown while a newer GitHub release exists and the user did not skip that version. */
@Composable
fun UpdateBanner(info: UpdateInfo, onInstall: () -> Unit, onSkip: () -> Unit) {
    val busy = UpdateState.busy
    val progress = UpdateState.progress
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .card(12.dp)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Update " + info.version + " available",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (busy && progress >= 0f) (progress * 100).toInt().toString() + "%" else "Install",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OnAccent,
            modifier = Modifier
                .clip(CircleShape)
                .background(Accent)
                .clickable(enabled = !busy, onClick = onInstall)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        IconButton(onClick = onSkip) { Ico(R.drawable.ic_close, tint = Muted, size = 18.dp) }
    }
}

@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val info = UpdateState.info
    val status = UpdateState.status
    val busy = UpdateState.busy
    val progress = UpdateState.progress
    var auto by remember { mutableStateOf(AlertRepo.storeOf(ctx).getStr("upd_auto", "1") == "1") }

    Section("App update", R.drawable.ic_refresh) {
        Text("Installed version " + Updater.installedVersion(ctx), fontSize = 13.sp)
        if (status.isNotEmpty()) Text(status, fontSize = 11.sp, color = Muted)
        if (busy && progress >= 0f) Text("Downloading " + (progress * 100).toInt() + "%", fontSize = 11.sp, color = Accent)
        if (info != null && info.notes.isNotBlank()) {
            Text(info.notes.trim().take(400), fontSize = 11.sp, color = Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Updater.check(ctx, true) }, enabled = !busy) {
                Text("Check now", fontSize = 12.sp)
            }
            if (info != null) {
                Button(onClick = { Updater.downloadAndInstall(ctx) }, enabled = !busy) {
                    Text("Install " + info.version, fontSize = 12.sp)
                }
            }
        }
        ToggleRow("Check automatically", "Looks for a new GitHub release at most every 6 hours.", auto) {
            auto = it
            AlertRepo.storeOf(ctx).putStr("upd_auto", if (it) "1" else "0")
        }
    }
}

@Composable
fun BackupSection(store: Store, onRestored: () -> Unit) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Backup.Parsed?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    fun offer(text: String) {
        val p = Backup.parse(text)
        if (p == null) {
            Toast.makeText(ctx, "Not a Trader Launcher backup or journal CSV", Toast.LENGTH_LONG).show()
        } else {
            pending = p
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(Backup.export(ctx, store).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not save: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                offer(text)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not read the file", Toast.LENGTH_LONG).show()
            }
        }
    }

    Section("Backup & restore", R.drawable.ic_share) {
        Text(
            "Saves the journal, alerts, watchlist and settings to a file you keep (Drive, Files, Telegram). " +
                "Restore also reads the CSV that the Journal share button produced in older versions.",
            fontSize = 11.sp,
            color = Muted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save.launch(Backup.fileName()) }) { Text("Save backup", fontSize = 12.sp) }
            OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Text("From file", fontSize = 12.sp) }
            OutlinedButton(onClick = { pasteOpen = true }) { Text("Paste", fontSize = 12.sp) }
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            containerColor = Surface2,
            title = { Text("Paste backup or CSV") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 8,
                    label = { Text("Text") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = pasteText.isNotBlank(),
                    onClick = {
                        offer(pasteText)
                        pasteOpen = false
                        pasteText = ""
                    }
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Cancel") } }
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Surface2,
            title = { Text(if (p.kind == "csv") "Import journal CSV" else "Restore backup") },
            text = {
                Text(
                    if (p.kind == "csv") {
                        p.trades.size.toString() + " trades found. They are added to your journal; trades already there are skipped."
                    } else {
                        "This replaces your current data with the backup: " + p.trades.size + " trades, " +
                            (p.alerts?.size ?: 0) + " alerts, " + (p.watch?.size ?: 0) + " watchlist pairs and the saved settings."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Backup.apply(ctx, store, p)
                    onRestored()
                    pending = null
                    Toast.makeText(ctx, "Done", Toast.LENGTH_SHORT).show()
                }) { Text(if (p.kind == "csv") "Import" else "Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}
