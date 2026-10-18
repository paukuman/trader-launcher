package com.example.traderlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        AppState.homeSignal++
    }
}

@Composable
fun Root(store: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    val watch = remember { mutableStateListOf<String>().apply { addAll(store.watchlist) } }
    val quotes = remember { mutableStateMapOf<String, Quote>() }
    val trades = remember { mutableStateListOf<Trade>().apply { addAll(store.loadTrades()) } }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var pinned by remember { mutableStateOf(store.getPinned()) }

    val resumed = AppState.resumed
    val signal = AppState.homeSignal

    LaunchedEffect(signal) { tab = 0 }
    BackHandler { tab = 0 }

    LaunchedEffect(resumed) {
        if (resumed) apps = withContext(Dispatchers.IO) { loadApps(ctx) }
    }

    // Auto-refresh harga tiap 5 detik selama launcher terlihat
    val watchKey = watch.joinToString(",")
    LaunchedEffect(resumed, watchKey) {
        if (resumed) {
            while (true) {
                val syms = watch.toList()
                if (syms.isNotEmpty()) {
                    quotes.putAll(withContext(Dispatchers.IO) { Market.fetch(syms) })
                }
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
        val s = raw.uppercase().replace("/", "").replace("-", "").replace(" ", "")
        if (s.isEmpty() || s in watch) return
        scope.launch {
            val r = withContext(Dispatchers.IO) { Market.fetch(listOf(s)) }
            if (r.containsKey(s)) {
                watch.add(s)
                store.watchlist = watch.toList()
                quotes.putAll(r)
            } else {
                Toast.makeText(ctx, "Pair $s tidak ditemukan", Toast.LENGTH_SHORT).show()
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

    Box(Modifier.fillMaxSize().background(Color(0xCC0B0F1A))) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            bottomBar = {
                NavigationBar(containerColor = Color(0xF20B0F1A), contentColor = Color.White) {
                    val items = listOf("📈" to "Pantau", "📓" to "Jurnal", "🧮" to "Alat", "🔲" to "Apps")
                    items.forEachIndexed { i, item ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(item.first) },
                            label = { Text(item.second) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(
                        watch = watch,
                        quotes = quotes,
                        dock = dock,
                        onAdd = { addPair(it) },
                        onRemove = { s ->
                            watch.remove(s)
                            store.watchlist = watch.toList()
                        },
                        onOpenApp = { launchApp(ctx, it) }
                    )
                    1 -> JournalScreen(
                        trades = trades,
                        onSave = { saveTrade(it) },
                        onDelete = { deleteTrade(it) }
                    )
                    2 -> ToolsScreen(store)
                    else -> AppsScreen(
                        apps = apps,
                        pinnedPkgs = dock.map { it.pkg },
                        onLaunch = { launchApp(ctx, it) },
                        onTogglePin = { togglePin(it) }
                    )
                }
            }
        }
    }
}
