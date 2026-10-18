@file:OptIn(ExperimentalFoundationApi::class)

package com.example.traderlauncher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    pinnedPkgs: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (String) -> Unit
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<AppEntry?>(null) }
    val filtered = remember(apps, q) {
        val s = q.trim().lowercase()
        if (s.isEmpty()) apps else apps.filter { it.label.lowercase().contains(s) }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text("Cari aplikasi…") },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let { onLaunch(it) } })
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(filtered, key = { it.pkg + "/" + it.activity }) { app ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { sel = app })
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(app, 48.dp)
                    Text(
                        app.label,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    sel?.let { app ->
        val isPinned = app.pkg in pinnedPkgs
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton(onClick = { onTogglePin(app.pkg); sel = null }) {
                        Text(if (isPinned) "Lepas dari dock" else "Pin ke dock")
                    }
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        sel = null
                    }) { Text("Info aplikasi") }
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        sel = null
                    }) { Text("Copot pemasangan") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { sel = null }) { Text("Tutup") } }
        )
    }
}
