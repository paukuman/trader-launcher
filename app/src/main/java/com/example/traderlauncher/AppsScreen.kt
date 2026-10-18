@file:OptIn(ExperimentalFoundationApi::class)

package com.example.traderlauncher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Third pager page: search, pinned apps and the full app grid. */
@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    pinned: List<AppEntry>,
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

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        BasicTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let { onLaunch(it) } }),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ico(R.drawable.ic_search, tint = Muted, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Search apps", color = Muted, fontSize = 14.sp)
                        inner()
                    }
                }
            }
        )

        if (pinned.isNotEmpty() && q.isEmpty()) {
            Text(
                "Pinned",
                fontSize = 11.sp,
                color = Muted,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
            )
            Row(
                Modifier.fillMaxWidth().card(16.dp).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                pinned.forEach { app ->
                    Box(
                        Modifier
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { sel = app })
                            .padding(4.dp)
                    ) { AppIcon(app, 46.dp) }
                }
            }
        }

        Text(
            if (q.isEmpty()) "All apps · ${apps.size}" else "Results · ${filtered.size}",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
        )
        if (apps.isEmpty()) {
            Text("Loading apps…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(filtered, key = { it.pkg + "/" + it.activity }) { app ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { sel = app })
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(app, 44.dp)
                    Text(
                        app.label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    sel?.let { app ->
        val isPinned = pinned.any { it.pkg == app.pkg }
        AlertDialog(
            onDismissRequest = { sel = null },
            containerColor = Surface2,
            title = { Text(app.label, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(onClick = { onTogglePin(app.pkg); sel = null }) {
                        Text(if (isPinned) "Unpin" else "Pin to top")
                    }
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        sel = null
                    }) { Text("App info") }
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        sel = null
                    }) { Text("Uninstall") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { sel = null }) { Text("Close") } }
        )
    }
}
