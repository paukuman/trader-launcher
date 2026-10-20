package com.example.traderlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Same normalisation the watchlist uses, so alert / journal symbols match price keys. */
fun normSymbol(raw: String): String =
    raw.uppercase().trim().replace("/", "").replace("-", "").replace(" ", "")

/**
 * Process-wide alert state. The UI and [MonitorService] share it, so the service can keep
 * working after the Activity is gone. Every mutation is persisted through [Store].
 */
object AlertRepo {
    private val lock = Any()
    private var store: Store? = null
    private val _alerts = MutableStateFlow<List<PriceAlert>>(emptyList())
    private val _hits = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val alerts: StateFlow<List<PriceAlert>> = _alerts

    /** tradeId -> 1 (stop loss hit) or 2 (take profit hit). */
    val tradeHits: StateFlow<Map<Long, Int>> = _hits

    fun init(ctx: Context) {
        synchronized(lock) {
            if (store != null) return
            val s = Store(ctx.applicationContext)
            store = s
            _alerts.value = try { s.loadAlerts() } catch (e: Exception) { emptyList() }
            _hits.value = try { s.loadHits() } catch (e: Exception) { emptyMap() }
        }
    }

    fun storeOf(ctx: Context): Store {
        init(ctx)
        return store!!
    }

    fun add(a: PriceAlert) {
        synchronized(lock) {
            val l = _alerts.value + a
            _alerts.value = l
            store?.saveAlerts(l)
        }
    }

    fun remove(id: Long) {
        synchronized(lock) {
            val l = _alerts.value.filter { it.id != id }
            _alerts.value = l
            store?.saveAlerts(l)
        }
    }

    fun rearm(id: Long) {
        synchronized(lock) {
            val l = _alerts.value.map {
                if (it.id == id) it.copy(active = true, firedAt = 0L, firedPrice = 0.0) else it
            }
            _alerts.value = l
            store?.saveAlerts(l)
        }
    }

    /** True only for the call that actually flips the alert from active to fired (no double notifications). */
    fun fire(id: Long, price: Double): Boolean = synchronized(lock) {
        val cur = _alerts.value.firstOrNull { it.id == id }
        if (cur == null || !cur.active) {
            false
        } else {
            val l = _alerts.value.map {
                if (it.id == id) it.copy(active = false, firedAt = System.currentTimeMillis(), firedPrice = price) else it
            }
            _alerts.value = l
            store?.saveAlerts(l)
            true
        }
    }

    /** True only the first time a trade's SL / TP is marked as hit. */
    fun markHit(tradeId: Long, code: Int): Boolean = synchronized(lock) {
        if (_hits.value.containsKey(tradeId)) {
            false
        } else {
            val m = _hits.value + (tradeId to code)
            _hits.value = m
            store?.saveHits(m)
            true
        }
    }

    fun clearHit(tradeId: Long) {
        synchronized(lock) {
            if (_hits.value.containsKey(tradeId)) {
                val m = _hits.value - tradeId
                _hits.value = m
                store?.saveHits(m)
            }
        }
    }

    /** Should the background service be running right now? */
    fun needsMonitoring(ctx: Context): Boolean {
        val s = storeOf(ctx)
        if (s.getStr("mon_on", "1") != "1") return false
        if (_alerts.value.any { it.active }) return true
        return AlertEngine.symbols(ctx).isNotEmpty()
    }
}

/** Decides when an alert fires. Feed it prices whenever you have them; it is safe to call from several places. */
object AlertEngine {
    private var tradesCache: List<Trade> = emptyList()
    private var tradesAt = 0L

    fun invalidateTrades() {
        tradesAt = 0L
    }

    private fun sltpOn(ctx: Context) = AlertRepo.storeOf(ctx).getStr("sltp_on", "1") == "1"

    /** Open trades that have at least one of SL / TP and were not alerted yet. */
    private fun openTrades(ctx: Context): List<Trade> {
        val now = SystemClock.elapsedRealtime()
        if (now - tradesAt > 1500L) {
            tradesCache = try {
                AlertRepo.storeOf(ctx).loadTrades().filter { it.pnl == null && (it.sl != null || it.tp != null) }
            } catch (e: Exception) {
                emptyList()
            }
            tradesAt = now
        }
        return tradesCache.filter { !AlertRepo.tradeHits.value.containsKey(it.id) }
    }

    fun openTradeCount(ctx: Context): Int = if (sltpOn(ctx)) openTrades(ctx).size else 0

    /** Symbols that need a live price: active alerts plus open trades with SL or TP. */
    fun symbols(ctx: Context): List<String> {
        val out = LinkedHashSet<String>()
        for (a in AlertRepo.alerts.value) if (a.active) out.add(a.symbol)
        if (sltpOn(ctx)) for (t in openTrades(ctx)) out.add(normSymbol(t.pair))
        return out.toList()
    }

    /** 0 = nothing, 1 = stop loss touched, 2 = take profit touched. Wrong-side levels are ignored. */
    private fun hitCode(t: Trade, p: Double): Int {
        val sl = t.sl
        val tp = t.tp
        val e = t.entry
        if (sl != null && (e == null || (if (t.isLong) sl < e else sl > e))) {
            if (if (t.isLong) p <= sl else p >= sl) return 1
        }
        if (tp != null && (e == null || (if (t.isLong) tp > e else tp < e))) {
            if (if (t.isLong) p >= tp else p <= tp) return 2
        }
        return 0
    }

    fun evaluate(ctx: Context, quotes: Map<String, Quote>) {
        if (quotes.isEmpty()) return
        for (a in AlertRepo.alerts.value) {
            if (!a.active) continue
            val p = quotes[a.symbol]?.price ?: continue
            val hit = if (a.above) p >= a.level else p <= a.level
            if (hit && AlertRepo.fire(a.id, p)) Notifier.priceAlert(ctx, a, p)
        }
        if (sltpOn(ctx)) {
            for (t in openTrades(ctx)) {
                val p = quotes[normSymbol(t.pair)]?.price ?: continue
                val code = hitCode(t, p)
                if (code != 0 && AlertRepo.markHit(t.id, code)) Notifier.tradeAlert(ctx, t, code, p)
            }
        }
    }
}

object Notifier {
    private const val MONITOR_CHANNEL = "monitor"

    fun soundUri(store: Store, kind: String): Uri {
        val s = store.getStr("snd_$kind", "")
        return if (s.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(s)
    }

    fun soundTitle(ctx: Context, store: Store, kind: String): String {
        val s = store.getStr("snd_$kind", "")
        if (s.isEmpty()) return "Default notification sound"
        return try {
            RingtoneManager.getRingtone(ctx, Uri.parse(s))?.getTitle(ctx) ?: "Custom sound"
        } catch (e: Exception) {
            "Custom sound"
        }
    }

    /**
     * Android fixes a channel's sound once it exists, so the channel id carries a signature of the settings:
     * changing the sound, vibration or loud mode creates a fresh channel and removes the old one.
     */
    private fun channel(ctx: Context, kind: String): String {
        val store = AlertRepo.storeOf(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val sound = soundUri(store, kind)
        val loud = store.getStr("loud", "0") == "1"
        val vib = store.getStr("vib", "1") == "1"
        val sig = (sound.toString() + "|" + loud + "|" + vib).hashCode().toUInt().toString(16)
        val id = "al_${kind}_$sig"
        if (nm.getNotificationChannel(id) == null) {
            nm.notificationChannels
                .filter { it.id.startsWith("al_${kind}_") }
                .forEach { nm.deleteNotificationChannel(it.id) }
            val ch = NotificationChannel(
                id,
                if (kind == "price") "Price alerts" else "SL / TP alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(if (loud) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            ch.enableVibration(vib)
            if (vib) ch.vibrationPattern = longArrayOf(0, 300, 150, 300)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        return id
    }

    private fun openApp(ctx: Context, page: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("page", page)
        return PendingIntent.getActivity(ctx, page, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, kind: String, title: String, text: String, page: Int) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val loud = AlertRepo.storeOf(ctx).getStr("loud", "0") == "1"
        val b = NotificationCompat.Builder(ctx, channel(ctx, kind))
            .setSmallIcon(R.drawable.ic_chart)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, page))
        if (loud) b.setCategory(NotificationCompat.CATEGORY_ALARM)
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (e: SecurityException) {
            // permission revoked between the check and the call
        }
    }

    private fun price(sym: String, p: Double) = fmtPrice(p, Market.isFxBase(Market.baseOf(sym)))

    fun priceAlert(ctx: Context, a: PriceAlert, p: Double) {
        val dir = if (a.above) "above" else "below"
        val note = if (a.note.isBlank()) "" else " · " + a.note
        post(
            ctx,
            (a.id % 1_000_000_000L).toInt(),
            "price",
            a.symbol + " " + dir + " " + price(a.symbol, a.level),
            "Price now " + price(a.symbol, p) + note,
            0
        )
    }

    fun tradeAlert(ctx: Context, t: Trade, code: Int, p: Double) {
        val sym = normSymbol(t.pair)
        val side = if (t.isLong) "LONG" else "SHORT"
        val title = (if (code == 1) "Stop loss hit" else "Take profit hit") + " · " + t.pair
        val level = if (code == 1) t.sl else t.tp
        val text = side + " · price " + price(sym, p) + " touched " +
            (if (code == 1) "SL " else "TP ") + (level?.let { fmtN(it) } ?: "") + ". Tap to review the trade."
        post(ctx, (t.id % 1_000_000_000L).toInt() + 1, "risk", title, text, 1)
    }

    fun test(ctx: Context, kind: String) {
        post(
            ctx,
            if (kind == "price") 9001 else 9002,
            kind,
            if (kind == "price") "Test price alert" else "Test SL / TP alert",
            "This is how your alert will sound and look.",
            0
        )
    }

    fun monitorNotification(ctx: Context, alerts: Int, trades: Int): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(MONITOR_CHANNEL) == null) {
            val ch = NotificationChannel(MONITOR_CHANNEL, "Background monitoring", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val parts = mutableListOf<String>()
        if (alerts > 0) parts.add("$alerts price alert" + (if (alerts == 1) "" else "s"))
        if (trades > 0) parts.add("$trades open trade" + (if (trades == 1) "" else "s") + " (SL/TP)")
        return NotificationCompat.Builder(ctx, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_chart)
            .setContentTitle("Watching the market")
            .setContentText(if (parts.isEmpty()) "Starting…" else parts.joinToString(" · "))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(ctx, 0))
            .build()
    }
}

/**
 * Foreground service that keeps the price feed alive while alerts or SL/TP targets exist.
 * It stops itself when there is nothing left to watch.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ArrayList<Job>()
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlertRepo.init(this)
        running = true
        Market.useTv = AlertRepo.storeOf(this).getStr("src_tv", "1") == "1"
        goForeground(Notifier.monitorNotification(this, 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (jobs.isEmpty()) {
            jobs.add(scope.launch { pollLoop() })
            // TradingView ticks arrive over the websocket; evaluate them as they come.
            jobs.add(scope.launch {
                TvFeed.updates.collect {
                    pushEvaluate()
                    delay(300)
                }
            })
        }
        return START_STICKY
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FG_ID, n)
        }
    }

    private fun pushEvaluate() {
        if (!Market.useTv) return
        val q = HashMap<String, Quote>()
        for (s in AlertEngine.symbols(this)) {
            TvFeed.quote(Market.tvSymbol(s))?.let { q[s] = it }
        }
        AlertEngine.evaluate(this, q)
    }

    private suspend fun pollLoop() {
        var shown = ""
        while (scope.isActive) {
            if (!AlertRepo.needsMonitoring(this)) {
                stopSelf()
                return
            }
            updateWakeLock()
            val syms = AlertEngine.symbols(this)
            if (syms.isNotEmpty()) {
                // Subscribe everything to the websocket (including plain crypto) so ticks are pushed.
                if (Market.useTv) TvFeed.ensure(syms.map { Market.tvSymbol(it) })
                try {
                    AlertEngine.evaluate(this, Market.fetch(syms))
                } catch (e: Exception) {
                    // network hiccup: try again on the next round
                }
            }
            val alerts = AlertRepo.alerts.value.count { it.active }
            val trades = AlertEngine.openTradeCount(this)
            val label = "$alerts/$trades"
            if (label != shown) {
                shown = label
                getSystemService(NotificationManager::class.java)
                    .notify(FG_ID, Notifier.monitorNotification(this, alerts, trades))
            }
            delay(4000)
        }
    }

    /** Optional "reliable mode": keeps the CPU awake so polling still runs with the screen off. */
    @SuppressLint("WakelockTimeout")
    private fun updateWakeLock() {
        val want = AlertRepo.storeOf(this).getStr("mon_wake", "0") == "1"
        if (want) {
            val w = wake ?: (getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TraderLauncher:monitor")).also { wake = it }
            w.acquire(10 * 60 * 1000L) // re-acquired every round, so it cannot leak if the service dies
        } else {
            wake?.let { if (it.isHeld) it.release() }
            wake = null
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        if (!AppState.resumed) TvFeed.close()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false
        const val FG_ID = 7001

        /** Start or stop the service so it matches what needs watching. Safe to call any time. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            try {
                if (AlertRepo.needsMonitoring(app)) {
                    ContextCompat.startForegroundService(app, Intent(app, MonitorService::class.java))
                } else if (running) {
                    app.stopService(Intent(app, MonitorService::class.java))
                }
            } catch (e: Exception) {
                // Background start not allowed right now; it is retried the next time the app opens.
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            MonitorService.sync(ctx)
        }
    }
}
