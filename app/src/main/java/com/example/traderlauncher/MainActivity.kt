@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.traderlauncher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
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

    /** Set by notification taps: page to show (0 Markets, 1 Journal), -1 = none. */
    var targetPage by mutableIntStateOf(-1)
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
        AlertRepo.init(applicationContext)
        MonitorService.sync(applicationContext)
        intent?.getIntExtra("page", -1)?.let { if (it >= 0) AppState.targetPage = it }
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
        val page = intent.getIntExtra("page", -1)
        if (page >= 0) AppState.targetPage = page // notification tap
        else AppState.homeSignal++ // Home button pressed while launcher is open
    }
}

private val TABS = listOf(
    "Markets" to R.drawable.ic_chart,
    "Journal" to R.drawable.ic_journal,
    "Apps" to R.drawable.ic_apps
)

/** Slim top switcher: tap a tab or swipe the pages. The calculator icon opens Tools. */
@Composable
fun TopBar(state: PagerState, hasAlerts: Boolean, onTab: (Int) -> Unit, onAlerts: () -> Unit, onTools: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
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
        IconButton(onClick = onAlerts, modifier = Modifier.size(40.dp)) {
            Box {
                Ico(R.drawable.ic_bell, tint = Accent, size = 22.dp)
                if (hasAlerts) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFBBF24))
                    )
                }
            }
        }
        IconButton(onClick = onTools, modifier = Modifier.size(40.dp)) {
            Ico(R.drawable.ic_calculator, tint = Accent, size = 22.dp)
        }
    }
}

/** One-time hint shown while this app is not the default home app. */
@Composable
fun DefaultBanner(onSet: () -> Unit, onHide: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .card(12.dp)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Make this your home screen",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "Set default",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OnAccent,
            modifier = Modifier
                .clip(CircleShape)
                .background(Accent)
                .clickable(onClick = onSet)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        IconButton(onClick = onHide) { Ico(R.drawable.ic_close, tint = Muted, size = 18.dp) }
    }
}

@Composable
fun Root(store: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 }) // 0 Markets, 1 Journal, 2 Apps

    var toolsOpen by remember { mutableStateOf(false) }
    var alertsOpen by remember { mutableStateOf(false) }
    var alertFor by remember { mutableStateOf<String?>(null) } // "" = ask for the symbol
    var chartSym by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf(Cat.values().firstOrNull { it.name == store.getStr("cat", "") }) }

    val watch = remember { mutableStateListOf<String>().apply { addAll(store.watchlist) } }
    val quotes = remember { mutableStateMapOf<String, Quote>() }
    val trades = remember { mutableStateListOf<Trade>().apply { addAll(store.loadTrades()) } }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var pinned by remember { mutableStateOf(store.getPinned()) }
    var isDefault by remember { mutableStateOf(isDefaultLauncher(ctx)) }
    val alerts by AlertRepo.alerts.collectAsState()
    val hits by AlertRepo.tradeHits.collectAsState()
    var bannerHidden by remember { mutableStateOf(store.getStr("dl_hide", "0") == "1") }
    var roleAsked by remember { mutableStateOf(false) }

    val resumed = AppState.resumed
    val signal = AppState.homeSignal

    LaunchedEffect(signal) {
        toolsOpen = false
        alertsOpen = false
        chartSym = null
        pagerState.animateScrollToPage(0)
    }

    val targetPage = AppState.targetPage
    LaunchedEffect(targetPage) {
        if (targetPage >= 0) {
            toolsOpen = false
            alertsOpen = false
            chartSym = null
            pagerState.animateScrollToPage(targetPage)
            AppState.targetPage = -1
        }
    }

    fun handleBack() {
        if (chartSym != null) {
            chartSym = null
        } else if (alertsOpen) {
            alertsOpen = false
        } else if (toolsOpen) {
            toolsOpen = false
        } else {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }
    BackHandler { handleBack() } // always consumed: a launcher must never close

    LaunchedEffect(resumed) {
        if (resumed) {
            isDefault = isDefaultLauncher(ctx)
            val fresh = withContext(Dispatchers.IO) { loadApps(ctx) }
            if (fresh != apps) apps = fresh // keep the same list when nothing changed: no needless grid churn
        } else if (!MonitorService.running) {
            TvFeed.close()
        }
    }

    // Poll Binance / Yahoo / fallback sources every 3 s while the launcher is visible.
    val watchKey = watch.joinToString(",")
    LaunchedEffect(resumed, watchKey) {
        if (resumed) {
            while (true) {
                // Watchlist plus anything an alert or an open trade needs, so alerts also work for pairs not listed.
                val syms = (watch.toList() + AlertEngine.symbols(ctx)).distinct()
                if (syms.isNotEmpty()) {
                    val res = Market.fetch(syms)
                    AlertEngine.evaluate(ctx, res)
                    for ((k, v) in res) {
                        val cur = quotes[k]
                        // A live TradingView tick is newer than any polled value fetched a moment ago.
                        if (cur != null && cur.src == "TradingView" && v.src != "TradingView" &&
                            Market.useTv && TvFeed.healthy()) continue
                        if (cur != v) quotes[k] = v
                    }
                }
                delay(3000)
            }
        }
    }

    // TradingView ticks are pushed over the websocket: show them immediately instead of waiting for the next
    // poll. The StateFlow conflates, and the delay caps UI updates at about 4 per second.
    LaunchedEffect(resumed, watchKey) {
        if (resumed) {
            TvFeed.updates.collect {
                if (Market.useTv) {
                    for (s in (watch.toList() + AlertEngine.symbols(ctx)).distinct()) {
                        if (Market.route(s) == Route.BINANCE) continue
                        val q = TvFeed.quote(Market.tvSymbol(s)) ?: continue
                        if (quotes[s] != q) quotes[s] = q
                    }
                    AlertEngine.evaluate(ctx, quotes.toMap())
                }
                delay(250)
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

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun addAlert(a: PriceAlert) {
        AlertRepo.add(a)
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        MonitorService.sync(ctx)
        Toast.makeText(ctx, "Alert set for " + a.symbol, Toast.LENGTH_SHORT).show()
    }

    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultLauncher(ctx)
    }

    fun requestDefault() {
        val rm = if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java) else null
        if (!roleAsked && rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleAsked = true
            homeRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            // Older Android, or the role dialog was already shown (Android stops asking after a decline):
            // send the user to the system "Default home app" screen.
            try {
                ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Open Settings > Apps > Default apps > Home app", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveTrade(t: Trade) {
        val i = trades.indexOfFirst { it.id == t.id }
        if (i >= 0) trades[i] = t else trades.add(0, t)
        store.saveTrades(trades.toList())
        AlertRepo.clearHit(t.id) // edited or closed: watch its SL / TP again
        AlertEngine.invalidateTrades()
        MonitorService.sync(ctx)
    }

    fun deleteTrade(id: Long) {
        trades.removeAll { it.id == id }
        store.saveTrades(trades.toList())
        AlertRepo.clearHit(id)
        AlertEngine.invalidateTrades()
        MonitorService.sync(ctx)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopBar(
                state = pagerState,
                hasAlerts = alerts.any { it.active },
                onTab = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                onAlerts = { alertsOpen = true },
                onTools = { toolsOpen = true }
            )
            if (!isDefault && !bannerHidden) {
                DefaultBanner(
                    onSet = { requestDefault() },
                    onHide = {
                        bannerHidden = true
                        store.putStr("dl_hide", "1")
                    }
                )
            }
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
                        alerts = alerts,
                        onAdd = { addPair(it) },
                        onRemove = { s ->
                            watch.remove(s)
                            store.watchlist = watch.toList()
                        },
                        onAlert = { alertFor = it },
                        onOpenChart = { chartSym = it }
                    )
                    1 -> JournalScreen(
                        trades = trades,
                        hits = hits,
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
            ToolsScreen(
                store = store,
                isDefault = isDefault,
                onSetDefault = { requestDefault() },
                onClose = { toolsOpen = false }
            )
        }

        AnimatedVisibility(
            visible = alertsOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            AlertsScreen(
                store = store,
                onAddAlert = { alertFor = "" },
                onClose = { alertsOpen = false }
            )
        }

        chartSym?.let { s -> ChartScreen(sym = s, store = store, onClose = { chartSym = null }) }

        alertFor?.let { s ->
            AlertEditDialog(
                symbol = if (s.isEmpty()) null else s,
                priceOf = { sym -> quotes[sym]?.price },
                onSave = { a ->
                    addAlert(a)
                    alertFor = null
                },
                onDismiss = { alertFor = null }
            )
        }
    }
}
