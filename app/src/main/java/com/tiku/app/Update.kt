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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
 */

/** One newer build found by the version check. */
data class UpdateInfo(val build: Int, val url: String, val notes: String)

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
        data class Newer(val url: String) : ManualResult
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
                onResult(ManualResult.Newer(remote.url))
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
            // Tapping the notification opens the download page directly.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

    /** Open the download page in the browser (notification/prompt/row). */
    fun openDownloadPage(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        }
    }
}

/**
 * The in-app update prompt — a dismissible dialog in the redesign skin
 * (dark CardC card, Rose accent), hosted from TikuRoot. It only ever
 * shows on the Tik home (Screen.Home) or the K-drama facade, and only
 * for a build the user hasn't already dismissed.
 */
@Composable
fun UpdatePromptDialog() {
    val info = UpdateState.available ?: return
    val screen = AppState.screen
    if (screen !is Screen.Home && screen !is Screen.Facade) return
    val ctx = LocalContext.current
    val dismissed = remember(info.build) { UpdateCheck.dismissedBuild(ctx) }
    if (info.build <= dismissed) return
    Dialog(onDismissRequest = { UpdateCheck.dismiss(ctx, info.build) }) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(CardC).padding(22.dp),
        ) {
            Text(
                "Update available", color = Color.White,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Version ${info.build} is ready", color = Rose,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (info.notes.isNotBlank()) {
                Text(
                    info.notes, color = Sub, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Later", Modifier.weight(1f), accent = false) {
                    UpdateCheck.dismiss(ctx, info.build)
                }
                Pill("Update now", Modifier.weight(1f), accent = true) {
                    UpdateCheck.openDownloadPage(ctx, info.url)
                    UpdateCheck.dismiss(ctx, info.build)
                }
            }
        }
    }
}
