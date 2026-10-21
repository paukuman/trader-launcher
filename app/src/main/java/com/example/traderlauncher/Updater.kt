package com.example.traderlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val notes: String, val url: String, val size: Long)

/** Observable by the UI (banner and Tools section). */
object UpdateState {
    var info by mutableStateOf<UpdateInfo?>(null)
    var status by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f) // -1 = idle, 0..1 while downloading
    var busy by mutableStateOf(false)
}

/**
 * Looks at the latest GitHub Release, downloads its APK and hands it to the system installer.
 * Android only accepts the update when it is signed with the same key as the installed app.
 */
object Updater {
    const val REPO = "paukuman/trader-launcher"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun installedVersion(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").takeWhile { it.isDigit() || it == '.' }
            .split('.').map { it.toIntOrNull() ?: 0 }

    /** True when [a] is a higher version than [b] (semantic, numeric per part). */
    fun isNewer(a: String, b: String): Boolean {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /** Silent check at most every 6 hours, only when the user left auto-check on. */
    fun autoCheck(ctx: Context) {
        val store = AlertRepo.storeOf(ctx)
        if (store.getStr("upd_auto", "1") != "1") return
        val last = store.getStr("upd_last", "0").toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        check(ctx, false)
    }

    fun check(ctx: Context, manual: Boolean) {
        if (UpdateState.busy) return
        val app = ctx.applicationContext
        UpdateState.busy = true
        if (manual) UpdateState.status = "Checking…"
        scope.launch {
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$REPO/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "TraderLauncher")
                    .build()
                http.newCall(req).execute().use { r ->
                    if (r.code == 404) {
                        if (manual) UpdateState.status = "No public release found (is the repo private?)"
                    } else if (!r.isSuccessful) {
                        if (manual) UpdateState.status = "GitHub answered HTTP " + r.code
                    } else {
                        AlertRepo.storeOf(app).putStr("upd_last", System.currentTimeMillis().toString())
                        val o = JSONObject(r.body?.string() ?: "{}")
                        val tag = o.optString("tag_name")
                        val assets = o.optJSONArray("assets")
                        var url = ""
                        var size = 0L
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val a = assets.getJSONObject(i)
                                if (a.optString("name").endsWith(".apk")) {
                                    url = a.optString("browser_download_url")
                                    size = a.optLong("size", 0L)
                                    break
                                }
                            }
                        }
                        val mine = installedVersion(app)
                        if (tag.isNotEmpty() && url.isNotEmpty() && isNewer(tag, mine)) {
                            UpdateState.info = UpdateInfo(tag.removePrefix("v"), o.optString("body", ""), url, size)
                            UpdateState.status = "Update " + tag.removePrefix("v") + " available"
                        } else {
                            UpdateState.info = null
                            UpdateState.status = if (manual) "You are up to date ($mine)" else ""
                        }
                    }
                }
            } catch (e: Exception) {
                if (manual) UpdateState.status = "Could not check: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                UpdateState.busy = false
            }
        }
    }

    fun downloadAndInstall(ctx: Context) {
        val info = UpdateState.info ?: return
        if (UpdateState.busy) return
        val app = ctx.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            UpdateState.status = "Allow installs from this app, then tap Install again"
            try {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // no such settings screen on this device
            }
            return
        }
        UpdateState.busy = true
        UpdateState.progress = 0f
        UpdateState.status = "Downloading…"
        scope.launch {
            try {
                val dir = File(app.cacheDir, "updates")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "TraderLauncher-" + info.version + ".apk")
                val req = Request.Builder().url(info.url).header("User-Agent", "TraderLauncher").build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP " + r.code)
                    val body = r.body ?: throw IOException("empty download")
                    val total = if (body.contentLength() > 0) body.contentLength() else info.size
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) UpdateState.progress = (done.toFloat() / total).coerceIn(0f, 1f)
                            }
                        }
                    }
                }
                if (info.size > 0 && file.length() != info.size) throw IOException("size mismatch, try again")
                UpdateState.status = "Downloaded, opening the installer…"
                withContext(Dispatchers.Main) { install(app, file) }
            } catch (e: Exception) {
                UpdateState.status = "Download failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                UpdateState.busy = false
                UpdateState.progress = -1f
            }
        }
    }

    private fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(i)
    }
}
