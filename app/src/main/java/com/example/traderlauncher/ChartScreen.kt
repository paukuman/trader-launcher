package com.example.traderlauncher

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Official TradingView "Advanced Chart" embed widget rendered in a WebView. */
private fun chartHtml(tv: String): String = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;height:100%;background:#0B0F1A}</style></head><body>
<div class="tradingview-widget-container" style="height:100%;width:100%">
<div class="tradingview-widget-container__widget" style="height:100%;width:100%"></div>
<script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js" async>
{"autosize":true,"symbol":"$tv","interval":"15","timezone":"Asia/Jakarta","theme":"dark","style":"1","locale":"en","allow_symbol_change":true,"calendar":false,"support_host":"https://www.tradingview.com"}
</script></div></body></html>
""".trimIndent()

@Composable
fun ChartScreen(sym: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val tv = Market.tvSymbol(sym)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F1A))
            .pointerInput(Unit) {} // swallow touches so nothing underneath reacts
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Ico(R.drawable.ic_close) }
            Text(tv, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                openUrl(ctx, "https://www.tradingview.com/chart/?symbol=" + Uri.encode(tv))
            }) { Ico(R.drawable.ic_open_in_new) }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.parseColor("#0B0F1A"))
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(
                        "https://www.tradingview.com/", chartHtml(tv), "text/html", "utf-8", null
                    )
                }
            }
        )
    }
}
