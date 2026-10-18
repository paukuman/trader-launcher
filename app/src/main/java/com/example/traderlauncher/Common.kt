package com.example.traderlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.math.BigDecimal
import java.math.RoundingMode

// Palette: deep green-black surfaces, emerald accent. Positive = Green, negative = Red.
val BgTop = Color(0xF2071A12)
val BgBottom = Color(0xF2030A07)
val BgOverlay = Color(0xF8061410)
val CardBg = Color(0xCC10241A)
val Surface2 = Color(0xFF12261C)
val Green = Color(0xFF22C55E)
val Red = Color(0xFFEF4444)
val Muted = Color(0xFF8FA89A)
val Accent = Color(0xFF10B981)
val OnAccent = Color(0xFF03130B)
val Hairline = Color(0x1AFFFFFF)

val TRADING_PKGS = listOf(
    "com.tradingview.tradingviewapp",
    "net.metaquotes.metatrader5",
    "net.metaquotes.metatrader4",
    "com.binance.dev",
    "com.bybit.app",
    "com.okinc.okex.gp"
)

@Composable
fun TraderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = Color(0xFF07110D),
            surface = Surface2,
            onSurface = Color.White,
            onBackground = Color.White
        )
    ) {
        // Text outside a Surface would otherwise fall back to black.
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

/** Rounded translucent card used for every list row and section. */
fun Modifier.card(radius: Dp = 14.dp): Modifier =
    this.clip(RoundedCornerShape(radius))
        .background(CardBg)
        .border(1.dp, Hairline, RoundedCornerShape(radius))

/** Small pill used for filters and suggestions. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) Accent else Color(0x1FFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) OnAccent else Color.White,
            maxLines = 1
        )
    }
}

/** Vector icon from res/drawable (ic_*.xml). Never use emoji for icons. */
@Composable
fun Ico(id: Int, modifier: Modifier = Modifier, tint: Color = Color.White, size: Dp = 24.dp) {
    Icon(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint
    )
}

fun fmtN(d: Double): String {
    if (d.isNaN() || d.isInfinite()) return "-"
    return BigDecimal(d).setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

fun String.toNum(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

data class AppEntry(val label: String, val pkg: String, val activity: String)

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
        .filter { it.pkg != ctx.packageName }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(app.pkg, app.activity))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open link", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(app.pkg, app.activity) {
        try {
            ctx.packageManager
                .getActivityIcon(ComponentName(app.pkg, app.activity))
                .toBitmap(128, 128)
                .asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}
