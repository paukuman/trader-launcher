@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.traderlauncher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppState {
    var resumed by mutableStateOf(false)
    var homeSignal by mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val store = Store(applicationContext)
        Market.useTv = store.getStr("src_tv", "1") == "1"
        setContent { TraderTheme { Root(store) } }
    }

    override fun onResume() {
        super.onResume()
        AppState.resumed = true
    }

    override fun onPause() {
        super.onPause()
        AppState.resumed = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppState.homeSignal++ // Home button pressed while launcher is open
    }
}

private val TABS = listOf(
    "Markets" to R.drawable.ic_chart,
    "Journal" to R.drawable.ic_journal,
    "Apps" to R.drawable.ic_apps
)

/** Slim top switcher: tap a tab or swipe the pages. The calculator icon opens Tools. */
@Composable
fun TopBar(state: PagerState, onTab: (Int) -> Unit, onTools: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .padding(3.dp)
        ) {
            TABS.forEachIndexed { i, tab ->
                val sel = state.currentPage == i
                Row(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (sel) Accent else Color.Transparent)
                        .clickable { onTab(i) }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ico(tab.second, tint = if (sel) OnAccent else Muted, size = 16.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        tab.first,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sel) OnAccent else Muted
                    )
                }
            }
        }
        IconButton(onClick = onTools) { Ico(R.drawable.ic_calculator, tint = Accent, size = 22.dp) }
    }
}

@Composable
fun Root(store: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 }) // 0 Markets, 1 Journal, 2 Apps

    var toolsOpen by remember { mutableStateOf(false) }
    var chartSym by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf(Cat.values().firstOrNull { it.name == store.getStr("cat", "") }) }

    val watch = remember { mutableStateListOf<String>().apply { addAll(store.watchlist) } }
    val quotes = remember { mutableStateMapOf<String, Quote>() }
    val trades = remember { mutableStateListOf<Trade>().apply { addAll(store.loadTrades()) } }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var pinned by remember { mutableStateOf(store.getPinned()) }

    val resumed = AppState.resumed
    val signal = AppState.homeSignal

    LaunchedEffect(signal) {
        toolsOpen = false
        chartSym = null
        pagerState.animateScrollToPage(0)
    }

    fun handleBack() {
        if (chartSym != null) {
            chartSym = null
        } else if (toolsOpen) {
            toolsOpen = false
        } else {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }
    BackHandler { handleBack() } // always consumed: a launcher must never close

    LaunchedEffect(resumed) {
        if (resumed) apps = withContext(Dispatchers.IO) { loadApps(ctx) } else TvFeed.close()
    }

    // Auto refresh quotes every 5 s while the launcher is visible
    val watchKey = watch.joinToString(",")
    LaunchedEffect(resumed, watchKey) {
        if (resumed) {
            while (true) {
                val syms = watch.toList()
                if (syms.isNotEmpty()) quotes.putAll(Market.fetch(syms))
                delay(5000)
            }
        }
    }

    val pinnedPkgs = pinned ?: TRADING_PKGS
    val pinnedApps = remember(apps, pinned) {
        pinnedPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.take(5)
    }

    fun togglePin(pkg: String) {
        val cur = pinnedApps.map { it.pkg }.toMutableList()
        if (pkg in cur) {
            cur.remove(pkg)
        } else {
            cur.add(pkg)
            while (cur.size > 5) cur.removeAt(0)
        }
        store.setPinned(cur)
        pinned = cur
    }

    fun addPair(raw: String) {
        val s = raw.uppercase().trim().replace("/", "").replace("-", "").replace(" ", "")
        if (s.isEmpty() || s in watch) return
        scope.launch {
            val r = Market.fetch(listOf(s))
            val hasPrice = r.containsKey(s)
            if (hasPrice || ':' in s) {
                watch.add(s)
                store.watchlist = watch.toList()
                quotes.putAll(r)
                if (!hasPrice) {
                    Toast.makeText(ctx, "No price available, added as chart only", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(ctx, "Symbol $s not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveTrade(t: Trade) {
        val i = trades.indexOfFirst { it.id == t.id }
        if (i >= 0) trades[i] = t else trades.add(0, t)
        store.saveTrades(trades.toList())
    }

    fun deleteTrade(id: Long) {
        trades.removeAll { it.id == id }
        store.saveTrades(trades.toList())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopBar(
                state = pagerState,
                onTab = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                onTools = { toolsOpen = true }
            )
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> HomeScreen(
                        watch = watch,
                        quotes = quotes,
                        cat = cat,
                        onCat = { c ->
                            cat = c
                            store.putStr("cat", c?.name ?: "")
                        },
                        onAdd = { addPair(it) },
                        onRemove = { s ->
                            watch.remove(s)
                            store.watchlist = watch.toList()
                        },
                        onOpenChart = { chartSym = it }
                    )
                    1 -> JournalScreen(
                        trades = trades,
                        onSave = { saveTrade(it) },
                        onDelete = { deleteTrade(it) }
                    )
                    else -> AppsScreen(
                        apps = apps,
                        pinned = pinnedApps,
                        onLaunch = { launchApp(ctx, it) },
                        onTogglePin = { togglePin(it) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = toolsOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ToolsScreen(store = store, onClose = { toolsOpen = false })
        }

        chartSym?.let { s -> ChartScreen(sym = s, onClose = { chartSym = null }) }
    }
}
