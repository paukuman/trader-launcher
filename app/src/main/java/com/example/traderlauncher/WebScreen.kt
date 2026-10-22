package com.example.traderlauncher

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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

/**
 * Full-screen in-app browser for opening a news story or calendar event. A real WebView usually passes
 * Cloudflare checks that block plain HTTP clients, so it is also the fallback when scraping fails.
 */
@Composable
fun WebScreen(url: String, title: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var loading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var reload by remember(url) { mutableStateOf(0) }
    val holder = remember(url, reload) { arrayOfNulls<WebView>(1) }

    DisposableEffect(url, reload) {
        onDispose {
            holder[0]?.destroy()
            holder[0] = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgOverlay)
            .pointerInput(Unit) {} // swallow touches
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Ico(R.drawable.ic_close) }
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                reload++
                loading = true
                error = null
            }) { Ico(R.drawable.ic_refresh, size = 22.dp) }
            IconButton(onClick = { openUrl(ctx, url) }) { Ico(R.drawable.ic_open_in_new) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.runtime.key(url, reload) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            holder[0] = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.userAgentString = settings.userAgentString.replace("; wv", "")
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            setBackgroundColor(android.graphics.Color.parseColor("#061410"))
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView, u: String?) {
                                    loading = false
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    err: WebResourceError
                                ) {
                                    if (request.isForMainFrame) {
                                        error = err.description.toString()
                                        loading = false
                                    }
                                }
                            }
                            loadUrl(url)
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
                    Text("Page failed to load", fontWeight = FontWeight.SemiBold)
                    Text(e, fontSize = 12.sp, color = Muted)
                    Text("Use refresh, or open it in your browser.", fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}
