package com.tiku.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * ROUND 18 — IN-APP UPDATES (owner order, modelled on his Focivo app).
 *
 * versionCode is always 1, so an install learns its CI build number from
 * `assets/build_number.txt`, which the GitHub workflow stamps with
 * `${{ github.run_number }}` before every build. The latest published build
 * is a tiny public JSON on the same Supabase bucket that serves the APK:
 * `apks/version.json` = {"build": N, "url": "...", "notes": "..."}.
 *
 * When the remote build is newer, the user gets BOTH a one-per-build local
 * notification (channel `tiku_reminders`, same as the other app alerts —
 * it is app news, not chat content, so the message-alert gates do not
 * apply) and a dismissible in-app prompt; Settings ▸ App update is the
 * manual door. Everything here is additive and fully failure-silent:
 * offline, a malformed JSON, or a missing asset can only mean "no prompt",
 * never a crash or a stuck screen.
 *
 * ROUND 75 (WS7) — the update itself now happens INSIDE the app: tapping
 * Update downloads the APK directly (version.json's additive "apk" field,
 * falling back to the constant direct file URL) with in-app progress, then
 * hands the downloaded file to the system installer via FileProvider. No
 * browser and no download page are ever opened from the update flow; a
 * failed download shows an in-app error with Retry, still no browser.
 */

/** One newer build found by the version check. [apk] is the DIRECT APK
 *  file URL (ROUND 75); [url] is the legacy download-page URL, kept only
 *  so the parsed shape stays compatible — it is never opened any more. */
data class UpdateInfo(val build: Int, val url: String, val notes: String, val apk: String)

/** Observable update state — set by [UpdateCheck], read by the prompt. */
object UpdateState {
    var available by mutableStateOf<UpdateInfo?>(null)
}

object UpdateCheck {
    private const val PREFS = "tiku_update"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_LAST_NOTIFIED = "last_notified_build"
    private const val KEY_DISMISSED = "dismissed_build"

    /** At most one background fetch per 6 hours (persisted across starts). */
    private const val THROTTLE_MS = 6L * 60 * 60 * 1000

    private const val VERSION_URL =
        "https://dgjseiatlqimjsdhmhxn.supabase.co/storage/v1/object/public/apks/version.json"
    const val DEFAULT_PAGE_URL = "https://tiku-study.vercel.app"

    /** ROUND 75: the direct APK the ship pipeline publishes next to the
     *  page. Used when version.json predates the additive "apk" field —
     *  the fallback is ALWAYS this direct file, never the page. */
    const val DEFAULT_APK_URL = "https://tiku-study.vercel.app/tiku.apk"

    private const val CHANNEL_ID = "tiku_reminders"
    private const val NOTIF_ID = 990_001

    /** The CI build number baked into THIS install. 0 when the asset is
     *  missing/unparseable — installs from before this system existed,
     *  which should always be offered the update. Guarded end-to-end so
     *  the screenshot harness (Robolectric assets) can only ever see 0. */
    fun installedBuild(ctx: Context): Int = runCatching {
        ctx.assets.open("build_number.txt").bufferedReader().use { it.readText() }
            .trim().toIntOrNull() ?: 0
    }.getOrDefault(0)

    fun dismissedBuild(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DISMISSED, 0)

    /** Prompt dismissed ("Later" or after "Update now"): this build never
     *  nags again; a NEWER build re-arms the prompt and the notification. */
    fun dismiss(ctx: Context, build: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DISMISSED, build).apply()
        if (UpdateState.available?.build == build) UpdateState.available = null
    }

    /** Throttled background check, called from MainActivity.onCreate.
     *  Cheap when throttled (one prefs read) and silent on any failure. */
    fun maybeCheck(ctx: Context) {
        if (Config.PREVIEW_UI_ONLY) return
        val appCtx = ctx.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK_MS, 0L) < THROTTLE_MS) return
        // Stamp BEFORE the fetch: a failed (offline) attempt still counts,
        // so a dead network is never hammered on every cold start.
        prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()
        AppState.scope.launch { runCatching { checkInternal(appCtx) } }
    }

    private suspend fun checkInternal(ctx: Context) {
        val remote = fetchRemote() ?: return
        if (remote.build <= installedBuild(ctx)) return
        UpdateState.available = remote
        // ONE notification per remote build, persisted — app restarts and
        // re-checks never re-post it.
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_NOTIFIED, 0) < remote.build) {
            prefs.edit().putInt(KEY_LAST_NOTIFIED, remote.build).apply()
            postNotification(ctx, remote)
        }
    }

    /** Manual-check outcome for the Settings row. */
    sealed interface ManualResult {
        data class Newer(val info: UpdateInfo) : ManualResult
        data object UpToDate : ManualResult
        data object Failed : ManualResult
    }

    /** Manual check (Settings ▸ App update): ignores the throttle; the
     *  callback runs on the main thread (AppState.scope is main-bound). */
    fun checkNow(ctx: Context, onResult: (ManualResult) -> Unit) {
        val appCtx = ctx.applicationContext
        AppState.scope.launch {
            val remote = if (Config.PREVIEW_UI_ONLY) null else fetchRemote()
            if (remote == null) {
                onResult(ManualResult.Failed)
                return@launch
            }
            if (remote.build > installedBuild(appCtx)) {
                UpdateState.available = remote
                onResult(ManualResult.Newer(remote))
            } else {
                onResult(ManualResult.UpToDate)
            }
        }
    }

    /** Fetch + leniently parse version.json. Null on ANY failure. */
    private suspend fun fetchRemote(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("$VERSION_URL?t=${System.currentTimeMillis()}")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("Cache-Control", "no-cache")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val j = JSONObject(body)
                val build = j.optInt("build", 0)
                if (build <= 0) null
                else UpdateInfo(
                    build = build,
                    url = j.optString("url").ifBlank { DEFAULT_PAGE_URL },
                    notes = j.optString("notes").trim(),
                    // ROUND 75: additive direct-APK field; older published
                    // JSONs without it fall back to the constant direct
                    // file URL — never to opening the page.
                    apk = j.optString("apk").ifBlank { DEFAULT_APK_URL },
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun postNotification(ctx: Context, info: UpdateInfo) {
        runCatching {
            // No OS permission (Android 13+): stay silent — the in-app
            // prompt still covers the update.
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            // Same channel as the app's other alerts; created here if the
            // message-alert path hasn't created it yet (idempotent).
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            // ROUND 75: tapping the notification opens TIKU ITSELF (where
            // the in-app prompt offers the update) — it used to open the
            // download page in a browser, which WS7 bans outright.
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx, NOTIF_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val body = if (info.notes.isNotBlank() && info.notes.length <= 90)
                "Version ${info.build} is ready · ${info.notes}"
            else "Version ${info.build} is ready"
            val notif = Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Tiku update available")
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(NOTIF_ID, notif)
        }
    }
}

/**
 * ROUND 75 (WS7) — the in-app APK downloader + installer hand-off.
 *
 * The download runs on [AppState.scope] (the app-level scope), NOT on a
 * composition-bound scope: leaving the prompt or the Settings screen
 * neither cancels nor corrupts it, and re-entering either surface shows
 * the live [phase] again. A second tap while a download is running is a
 * no-op (single-flight). The file lands in app-specific cache storage
 * (`cacheDir/updates/tiku-update.apk`, covered by the FileProvider's
 * cache-path roots); every attempt truncates/overwrites any stale partial
 * file from a failed attempt. Before the installer is handed the file it
 * must look like a real APK: at least [MIN_APK_BYTES] and a ZIP ("PK")
 * header — anything else becomes the in-app error state, never an
 * installer launch and never a browser fallback.
 */
object UpdateDownloader {

    sealed interface Phase {
        data object Idle : Phase
        data class Downloading(val downloaded: Long, val total: Long) : Phase
        data class Failed(val message: String) : Phase
        data object NeedsPermission : Phase
        data object OpeningInstaller : Phase
    }

    var phase by mutableStateOf<Phase>(Phase.Idle)
        // internal (not private) so the screenshot harness can stage the
        // downloading / failed / needs-permission states for capture.
        internal set

    private var job: Job? = null
    private var lastApkUrl: String? = null

    /** Smallest plausible Tiku APK. Anything shorter is an error page or
     *  a truncated transfer, not an installable build. */
    private const val MIN_APK_BYTES = 1_000_000L

    fun apkFile(ctx: Context): File =
        File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "tiku-update.apk")

    /** Start (or ignore, if already running) the download of [apkUrl]. */
    fun start(ctx: Context, apkUrl: String) {
        if (job?.isActive == true) return
        val appCtx = ctx.applicationContext
        lastApkUrl = apkUrl
        phase = Phase.Downloading(0L, -1L)
        job = AppState.scope.launch {
            val ok = withContext(Dispatchers.IO) { download(appCtx, apkUrl) }
            if (ok) install(appCtx) else Unit
        }
    }

    /** Retry after a failure — re-downloads the last attempted URL from
     *  scratch (the partial file is truncated by the new attempt). */
    fun retry(ctx: Context) {
        val url = lastApkUrl ?: return
        start(ctx, url)
    }

    private fun download(ctx: Context, apkUrl: String): Boolean {
        val target = apkFile(ctx)
        return runCatching {
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Cache-Control", "no-cache")
                val total = conn.contentLengthLong
                var downloaded = 0L
                var lastShownPct = -1
                conn.inputStream.use { input ->
                    // FileOutputStream (no append) truncates any stale
                    // partial file from a previous failed attempt.
                    FileOutputStream(target).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            // Publish progress at most once per percent
                            // (or every ~256 KB when the length is
                            // unknown) so recomposition stays cheap.
                            val pct = if (total > 0) (downloaded * 100 / total).toInt() else -1
                            if (pct != lastShownPct || downloaded % (256 * 1024) < buf.size) {
                                lastShownPct = pct
                                phase = Phase.Downloading(downloaded, total)
                            }
                        }
                        out.flush()
                    }
                }
                if (total > 0 && downloaded != total) {
                    phase = Phase.Failed("Download didn't finish — check your connection and try again")
                    return false
                }
                if (!looksLikeApk(target)) {
                    phase = Phase.Failed("The downloaded file isn't a valid update — try again")
                    return false
                }
                true
            } finally {
                conn.disconnect()
            }
        }.getOrElse {
            phase = Phase.Failed("Couldn't download the update — check your connection and try again")
            false
        }
    }

    /** Plausibility gate: non-trivial size + ZIP magic ("PK\x03\x04"). */
    private fun looksLikeApk(f: File): Boolean = runCatching {
        if (!f.exists() || f.length() < MIN_APK_BYTES) return false
        f.inputStream().use { input ->
            val head = ByteArray(4)
            input.read(head) == 4 &&
                head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
        }
    }.getOrDefault(false)

    /**
     * Hand the downloaded APK to the system installer. On Android 8+ the
     * OS requires the per-app "install unknown apps" toggle: when it is
     * off, [phase] becomes [Phase.NeedsPermission] and the UI explains it
     * and routes the user to that exact toggle (an OS settings screen,
     * not a browser). Calling this again after the toggle is granted
     * launches the installer.
     */
    fun install(ctx: Context) {
        val appCtx = ctx.applicationContext
        val file = apkFile(appCtx)
        if (!looksLikeApk(file)) {
            phase = Phase.Failed("The downloaded file isn't a valid update — try again")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 &&
            !appCtx.packageManager.canRequestPackageInstalls()
        ) {
            phase = Phase.NeedsPermission
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(appCtx, "tiku.app.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appCtx.startActivity(intent)
            phase = Phase.OpeningInstaller
        }.onFailure {
            phase = Phase.Failed("Couldn't open the installer — try again")
        }
    }

    /** The one OS step WS7 allows: Android's per-app "allow installs
     *  from Tiku" toggle, opened for THIS app's package only. */
    fun openInstallPermissionSettings(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}"),
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            )
        }
    }

    /** Back to Idle (e.g. the prompt was dismissed after a failure). */
    fun reset() {
        if (job?.isActive != true) phase = Phase.Idle
    }
}

/**
 * ROUND 75: the shared in-app download UI — progress while downloading,
 * a clear error + Retry on failure, and the install-permission
 * explanation + actions when the OS toggle is missing. Renders nothing
 * in Idle. Used by the update prompt dialog and (via the Round-75
 * Settings-row fix) under Settings ▸ App update, so both surfaces show
 * the same live state from [UpdateDownloader].
 */
@Composable
fun UpdateDownloadUi() {
    val ctx = LocalContext.current
    when (val p = UpdateDownloader.phase) {
        UpdateDownloader.Phase.Idle -> Unit
        is UpdateDownloader.Phase.Downloading -> {
            Column(Modifier.padding(top = 16.dp)) {
                if (p.total > 0) {
                    val pct = (p.downloaded * 100 / p.total).toInt()
                    LinearProgressIndicator(
                        progress = { (p.downloaded.toFloat() / p.total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Rose,
                    )
                    Text(
                        "Downloading update… $pct%",
                        color = Sub, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp), fontFamily = PoppinsFamily,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Rose,
                    )
                    val mb = p.downloaded / (1024.0 * 1024.0)
                    Text(
                        "Downloading update… ${"%.1f".format(mb)} MB",
                        color = Sub, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp), fontFamily = PoppinsFamily,
                    )
                }
            }
        }
        is UpdateDownloader.Phase.Failed -> {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    p.message, color = Danger, fontSize = 13.sp, fontFamily = PoppinsFamily,
                )
                Spacer(Modifier.height(12.dp))
                Pill("Retry", Modifier.fillMaxWidth(), accent = true) {
                    UpdateDownloader.retry(ctx)
                }
            }
        }
        UpdateDownloader.Phase.NeedsPermission -> {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    "Android needs your permission before Tiku can install updates it downloads. " +
                        "Turn on “Allow from this source” for Tiku on the next screen, then come back and tap Install.",
                    color = Sub, fontSize = 13.sp, fontFamily = PoppinsFamily,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Open settings", Modifier.weight(1f), accent = false) {
                        UpdateDownloader.openInstallPermissionSettings(ctx)
                    }
                    Pill("Install", Modifier.weight(1f), accent = true) {
                        UpdateDownloader.install(ctx)
                    }
                }
            }
        }
        UpdateDownloader.Phase.OpeningInstaller -> {
            Text(
                "Opening the installer…",
                color = Sub, fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp), fontFamily = PoppinsFamily,
            )
        }
    }
}

/**
 * ROUND 76 (WS4, owner order) — the update banner pinned to the TOP of
 * the Messages screen. It exists ONLY while an update is actually
 * available ([UpdateState.available] set by the version check) and the
 * user hasn't dismissed that build — the exact same gate the prompt
 * dialog uses, so dismissing either surface hides both. Tapping
 * "Update" starts the same in-app download as everywhere else
 * ([UpdateDownloader], no browser), and the live download state
 * (progress / error + Retry / install permission) renders inside the
 * banner via [UpdateDownloadUi]. No update → this composable renders
 * nothing and the Messages layout is byte-identical to before.
 */
@Composable
fun UpdateBanner() {
    val info = UpdateState.available ?: return
    val ctx = LocalContext.current
    val dismissed = remember(info.build) { UpdateCheck.dismissedBuild(ctx) }
    if (info.build <= dismissed) return
    val phase = UpdateDownloader.phase
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .clip(RoundedCornerShape(20.dp)).background(CardC)
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Update available", color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,
                )
                Text(
                    "Version ${info.build} is ready", color = Rose,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp), fontFamily = PoppinsFamily,
                )
            }
            if (phase is UpdateDownloader.Phase.Idle) {
                Pill("Update", Modifier.padding(start = 12.dp), accent = true) {
                    UpdateDownloader.start(ctx, info.apk)
                }
            }
        }
        UpdateDownloadUi()
    }
}

/**
 * The in-app update prompt — a dismissible dialog in the redesign skin
 * (dark CardC card, Rose accent), hosted from TikuRoot. It only ever
 * shows on the Tik home (Screen.Home) or the K-drama facade, and only
 * for a build the user hasn't already dismissed.
 *
 * ROUND 75: "Update now" downloads the APK inside the app (progress
 * shows right here in this dialog) and hands it to the system installer
 * — no browser, ever. Dismissing the dialog mid-download does not cancel
 * the download (it runs on the app scope); the installer still opens
 * when it finishes.
 */
@Composable
fun UpdatePromptDialog() {
    val info = UpdateState.available ?: return
    val screen = AppState.screen
    if (screen !is Screen.Home && screen !is Screen.Facade) return
    val ctx = LocalContext.current
    val dismissed = remember(info.build) { UpdateCheck.dismissedBuild(ctx) }
    if (info.build <= dismissed) return
    val downloading = UpdateDownloader.phase is UpdateDownloader.Phase.Downloading
    Dialog(onDismissRequest = { UpdateCheck.dismiss(ctx, info.build) }) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(CardC).padding(22.dp),
        ) {
            Text(
                "Update available", color = Color.White,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
            Text(
                "Version ${info.build} is ready", color = Rose,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp), fontFamily = PoppinsFamily,)
            if (info.notes.isNotBlank()) {
                Text(
                    info.notes, color = Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp), fontFamily = PoppinsFamily,)
            }
            UpdateDownloadUi()
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Later", Modifier.weight(1f), accent = false) {
                    UpdateCheck.dismiss(ctx, info.build)
                }
                if (!downloading && UpdateDownloader.phase is UpdateDownloader.Phase.Idle) {
                    Pill("Update now", Modifier.weight(1f), accent = true) {
                        UpdateDownloader.start(ctx, info.apk)
                    }
                }
            }
        }
    }
}
