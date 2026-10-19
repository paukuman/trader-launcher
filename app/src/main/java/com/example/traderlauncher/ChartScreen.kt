package com.example.traderlauncher

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private const val CHART_BG = "#061410"

/** Engine 0: official TradingView "Advanced Chart" embed script, wrapped in a tiny HTML page. */
private fun widgetHtml(tv: String): String = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;height:100%;background:$CHART_BG}</style></head><body>
<div class="tradingview-widget-container" style="height:100%;width:100%">
<div class="tradingview-widget-container__widget" style="height:100%;width:100%"></div>
<script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js" async>
{"autosize":true,"symbol":"$tv","interval":"15","timezone":"Asia/Jakarta","theme":"dark","style":"1","locale":"en","allow_symbol_change":true,"calendar":false,"support_host":"https://www.tradingview.com"}
</script></div></body></html>
""".trimIndent()

/** Engine 1: the widget's frame page loaded directly (no script injection). */
private fun directUrl(tv: String): String =
    "https://s.tradingview.com/widgetembed/?symbol=" + Uri.encode(tv) +
        "&interval=15&theme=dark&style=1&timezone=" + Uri.encode("Asia/Jakarta") +
        "&locale=en&toolbarbg=061410&hidesidetoolbar=1&symboledit=1&saveimage=0&withdateranges=1"

@Composable
fun ChartScreen(sym: String, store: Store, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val tv = Market.tvSymbol(sym)

    var engine by remember { mutableIntStateOf(if (store.getStr("chart_engine", "0") == "1") 1 else 0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var jsError by remember { mutableStateOf<String?>(null) }
    val holder = remember(engine, reloadKey) { arrayOfNulls<WebView>(1) }

    DisposableEffect(engine, reloadKey) {
        onDispose {
            holder[0]?.destroy()
            holder[0] = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF061410))
            .pointerInput(Unit) {} // swallow touches so nothing underneath reacts
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Ico(R.drawable.ic_close) }
            Text(tv, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Chip(if (engine == 0) "Widget" else "Direct", false) {
                engine = 1 - engine
                store.putStr("chart_engine", engine.toString())
                loading = true
                error = null
                jsError = null
            }
            IconButton(onClick = {
                reloadKey++
                loading = true
                error = null
                jsError = null
            }) { Ico(R.drawable.ic_refresh, size = 22.dp) }
            IconButton(onClick = {
                openUrl(ctx, "https://www.tradingview.com/chart/?symbol=" + Uri.encode(tv))
            }) { Ico(R.drawable.ic_open_in_new) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(engine, reloadKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            holder[0] = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // Present as plain Chrome; some embeds treat the "; wv" WebView marker differently.
                            settings.userAgentString = settings.userAgentString.replace("; wv", "")
                            // The chart lives in an iframe on another domain: it needs third-party cookies/storage.
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            setBackgroundColor(android.graphics.Color.parseColor(CHART_BG))
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                        jsError = m.message().take(140)
                                    }
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    err: WebResourceError
                                ) {
                                    if (request.isForMainFrame) {
                                        error = err.description.toString() + " (" + err.errorCode + ")"
                                        loading = false
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    resp: WebResourceResponse
                                ) {
                                    if (request.isForMainFrame) {
                                        error = "HTTP " + resp.statusCode
                                        loading = false
                                    }
                                }
                            }
                            if (engine == 0) {
                                loadDataWithBaseURL(
                                    "https://www.tradingview.com/", widgetHtml(tv), "text/html", "utf-8", null
                                )
                            } else {
                                loadUrl(directUrl(tv))
                            }
                        }
                    }
                )
            }

            if (loading) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            error?.let { e ->
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Chart failed to load", fontWeight = FontWeight.SemiBold)
                    Text(e, fontSize = 12.sp, color = Muted)
                    Text("Try the refresh button, switch Widget / Direct, or open it in the browser.", fontSize = 12.sp, color = Muted)
                }
            }
            jsError?.let { m ->
                Text(
                    "console: $m",
                    fontSize = 9.sp,
                    color = Muted,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                )
            }
        }
    }
}
