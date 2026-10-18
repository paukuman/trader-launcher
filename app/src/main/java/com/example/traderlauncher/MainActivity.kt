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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

@Composable
fun PageDots(state: PagerState) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(state.pageCount) { i ->
            val sel = state.currentPage == i
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (sel) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (sel) Color.White else Color(0x66FFFFFF))
            )
        }
    }
}

@Composable
fun Root(store: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 }) // 0 Pantau, 1 Jurnal, 2 Alat

    var drawerOpen by remember { mutableStateOf(false) }
    var chartSym by remember { mutableStateOf<String?>(null) }

    val watch = remember { mutableStateListOf<String>().apply { addAll(store.watchlist) } }
    val quotes = remember { mutableStateMapOf<String, Quote>() }
    val trades = remember { mutableStateListOf<Trade>().apply { addAll(store.loadTrades()) } }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var pinned by remember { mutableStateOf(store.getPinned()) }

    val resumed = AppState.resumed
    val signal = AppState.homeSignal

    LaunchedEffect(signal) {
        drawerOpen = false
        chartSym = null
        pagerState.animateScrollToPage(0)
    }

    fun handleBack() {
        if (chartSym != null) {
            chartSym = null
        } else if (drawerOpen) {
            drawerOpen = false
        } else {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }
    BackHandler { handleBack() } // always consumed: a launcher must never close

    LaunchedEffect(resumed) {
        if (resumed) apps = withContext(Dispatchers.IO) { loadApps(ctx) }
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

    val dockPkgs = pinned ?: TRADING_PKGS
    val dock = remember(apps, pinned) {
        dockPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.take(5)
    }

    fun togglePin(pkg: String) {
        val cur = dock.map { it.pkg }.toMutableList()
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
                    Toast.makeText(ctx, "Harga tidak tersedia, ditambahkan sebagai chart saja", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(ctx, "Simbol $s tidak ditemukan", Toast.LENGTH_SHORT).show()
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

    // Swipe up on the dock area opens the app drawer
    val swipeUp = Modifier.pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total < -60f) drawerOpen = true },
            onVerticalDrag = { _, d -> total += d }
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xCC0B0F1A))) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> HomeScreen(
                        watch = watch,
                        quotes = quotes,
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
                    else -> ToolsScreen(store)
                }
            }
            Column(swipeUp) {
                PageDots(pagerState)
                Dock(dock, onOpenApp = { launchApp(ctx, it) }, onOpenDrawer = { drawerOpen = true })
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            AppsScreen(
                apps = apps,
                pinnedPkgs = dock.map { it.pkg },
                onLaunch = { launchApp(ctx, it); drawerOpen = false },
                onTogglePin = { togglePin(it) },
                onClose = { drawerOpen = false }
            )
        }

        chartSym?.let { s -> ChartScreen(sym = s, onClose = { chartSym = null }) }
    }
}
