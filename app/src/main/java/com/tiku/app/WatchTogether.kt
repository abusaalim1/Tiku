package com.tiku.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

// ============================================================================
// ROUND 87 — WATCH TOGETHER (owner order, 2026-11-25).
//
// A 1:1 chat session for watching together in real time, INSIDE Tiku:
// YouTube plays in sync (host drives, guest follows); any other URL
// opens in a general in-app WebView browser (playback sync is YouTube
// only — a random page's video cannot be synced).
//
// This is NOT the R75 public Watch tab (removed in build 138): there
// is no tab, no 3-tab nav, no discovery surface. A session lives only
// inside one 1:1 chat, started from an invite card message, and the
// player/browser sits ON TOP of the same chat — the existing thread +
// composer stay below it (see Chat.kt: the pane is a child of the
// chat's own header column; the list's top padding grows by
// WATCH_PANE_HEIGHT_DP while it is open).
//
// Session state lives in ONE Firestore doc per invite, following the
// /babeg gamestate pattern:
//   chats/{chatId}/watchparty/{sessionId}  (sessionId == invite message id)
//   {url, videoId, kind, hostUid, state, positionSeconds, updatedAt,
//    joined{uid:bool}, hostLeft, inviteMsgId}
// Plaintext metadata only (video id / state / position) — consistent
// with the Nikah step metadata; chat content itself stays E2EE. The
// URL is shared by design: both partners see the same video.
//
// Rules the code below keeps:
//  - ONE active session per chat: starting a new one ends the old one
//    (engine, in the invite send) and only the latest invite card can
//    be active (the card reads "Session ended" for superseded ones).
//  - ONE session listener per participant, attached while the chat is
//    open and removed on leaving it (Chat.kt DisposableEffect).
//  - Host writes on state change only + a 15 s heartbeat WHILE PLAYING
//    (tick-driven: the player's 1 s position poll is the only clock —
//    no timer loop lives in composition, so the harness can idle).
//  - Guest seeks when drift > 2 s; late join jumps to the host's
//    corrected position once.
//  - Host leaving pauses the session (state line "Host left — session
//    paused"); the guest keeps watching locally. No background
//    playback: closing the pane destroys the WebView, so audio stops.
//  - The WebViews never leave Tik: no external intents, no Custom
//    Tabs, no "open in Chrome" escape — non-http(s) schemes are
//    swallowed, target=_blank loads in-view (multiple windows off).
//  - Old builds degrade to plain text: the invite is a kind="text"
//    message whose encrypted body is a readable fallback line
//    ("Watch together: <url>") and whose wp* fields are additive.
// ============================================================================

/** The watch pane's height — Chat.kt grows the message list's top
 *  padding by exactly this while the pane is open. */
internal const val WATCH_PANE_HEIGHT_DP = 264

/** The chat's own dark tokens (the same values as Chat.kt's
 *  RdCard / RdRose / RdSub / RdDanger) — reused, not reinvented:
 *  the pane sits on the chat surface, so it wears the chat's colours.
 *  No new colours, no new fonts anywhere in this round. */
private val WatchCard = Color(0xFF1B1B1F)
private val WatchRose = Color(0xFFFF5C8A)
private val WatchSub = Color(0xFF9A99A6)
private val WatchDanger = Color(0xFFFF453A)

// ================= PURE SYNC CORE (no Android APIs — unit-tested) =================

/** One Watch Together session, decoded from its Firestore doc. */
data class WatchSession(
    val id: String = "",
    val url: String = "",
    val videoId: String = "",
    val kind: String = "web", // "youtube" | "web"
    val hostUid: String = "",
    /** "waiting" | "playing" | "paused" | "ended". */
    val state: String = "waiting",
    val positionSec: Double = 0.0,
    /** Server-timestamp ms of the last host write (0 = not stamped yet). */
    val updatedAtMs: Long = 0L,
    val joined: Map<String, Boolean> = emptyMap(),
    val hostLeft: Boolean = false,
    val inviteMsgId: String = "",
) {
    val active: Boolean get() = state != "ended"
    val isYouTube: Boolean get() = kind == "youtube"
}

/** The pure sync math + URL parsing. Every rule here is pinned by
 *  WatchTogetherR87Test — the pane calls these, it never re-derives
 *  them inline. */
object WatchSync {
    /** The ONLY periodic write: one host heartbeat per 15 s while
     *  playing. Justification (the read-quota war): one listener per
     *  participant + one 15 s write is tiny next to the 120 reads/hr
     *  presence heartbeat, and 15 s bounds a guest's drift to ~15 s
     *  of playback even if every state-change write were lost. */
    const val HEARTBEAT_MS = 15_000L
    /** Guest seeks when its position is further than this from the
     *  host's corrected position. 2 s absorbs network + render jitter
     *  without visible jumps; anything larger is a real desync. */
    const val DRIFT_THRESHOLD_SEC = 2.0
    /** Guest shows "Waiting for host…" when the host's last write is
     *  older than this while the session claims to be playing — three
     *  missed heartbeats, so a single slow write never flashes it. */
    const val HOST_STALE_GRACE_MS = 45_000L
    const val MAX_URL_LEN = 2048

    enum class Kind { YOUTUBE, WEB }
    data class Parsed(val kind: Kind, val videoId: String?, val url: String)

    /** A YouTube video id is exactly 11 base64url chars. */
    fun isVideoId(s: String): Boolean =
        s.length == 11 && s.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    /** Extract a YouTube video id from a watch / shorts / embed /
     *  live / v / youtu.be URL, or null. Pure string work (no Uri —
     *  this object must stay unit-testable on the JVM). */
    fun extractYouTubeId(rawUrl: String): String? {
        val url = rawUrl.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        val noScheme = url.substringAfter("://", url)
        val authority = noScheme.substringBefore("/").substringBefore("?").substringBefore("#")
            .substringAfterLast("@").substringBefore(":").lowercase()
        val host = authority.removePrefix("www.")
        val afterAuthority = noScheme.substringAfter(authority, "")
        val path = afterAuthority.substringBefore("?").substringBefore("#")
        val query = afterAuthority.substringAfter("?", "").substringBefore("#")
        fun queryParam(name: String): String? =
            query.split("&").firstNotNullOfOrNull { part ->
                val kv = part.split("=", limit = 2)
                if (kv.size == 2 && kv[0] == name) kv[1].takeIf { it.isNotEmpty() } else null
            }
        fun firstPathSegmentAfter(prefix: String): String? {
            if (!path.startsWith(prefix)) return null
            return path.removePrefix(prefix).substringBefore("/").takeIf { it.isNotEmpty() }
        }
        val isYouTubeHost = host == "youtube.com" || host.endsWith(".youtube.com")
        if (isYouTubeHost) {
            queryParam("v")?.takeIf { isVideoId(it) }?.let { return it }
            firstPathSegmentAfter("/shorts/")?.takeIf { isVideoId(it) }?.let { return it }
            firstPathSegmentAfter("/embed/")?.takeIf { isVideoId(it) }?.let { return it }
            firstPathSegmentAfter("/live/")?.takeIf { isVideoId(it) }?.let { return it }
            firstPathSegmentAfter("/v/")?.takeIf { isVideoId(it) }?.let { return it }
        }
        if (host == "youtu.be") {
            return path.trimStart('/').substringBefore("/").takeIf { isVideoId(it) }
        }
        return null
    }

    /** Parse a pasted link: YouTube (with video id) or any http(s)
     *  web URL. A bare domain gets https://. Returns null for
     *  anything that is not a usable link (blank, too long, no host,
     *  host without a dot, non-http(s) scheme). */
    fun parseUrl(raw: String): Parsed? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > MAX_URL_LEN || t.any { it.isWhitespace() }) return null
        val lowered = t.lowercase()
        if (lowered.startsWith("javascript:") || lowered.startsWith("data:") ||
            lowered.startsWith("intent:") || lowered.startsWith("file:") ||
            lowered.startsWith("content:")
        ) return null
        val withScheme =
            if (lowered.startsWith("http://") || lowered.startsWith("https://")) t
            else "https://$t"
        val noScheme = withScheme.substringAfter("://")
        val authority = noScheme.substringBefore("/").substringBefore("?").substringBefore("#")
            .substringAfterLast("@").substringBefore(":").lowercase()
        if (authority.isEmpty() || !authority.contains('.') ||
            authority.startsWith(".") || authority.startsWith("-") ||
            authority.endsWith(".") ||
            authority.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }
        ) return null
        val videoId = extractYouTubeId(withScheme)
        return if (videoId != null) Parsed(Kind.YOUTUBE, videoId, withScheme)
        else Parsed(Kind.WEB, null, withScheme)
    }

    /** The host's position as the guest should see it RIGHT NOW: while
     *  playing, the stored position plus the wall-clock time since the
     *  host's last write (the server timestamp is the clock both
     *  phones share). Paused / waiting / ended: the stored position.
     *  An unstamped write (updatedAt 0 — the local echo of the host's
     *  own set, before the server stamp lands) corrects to nothing. */
    fun correctedPosition(
        state: String, positionSec: Double, updatedAtMs: Long, nowMs: Long,
    ): Double {
        if (state != "playing" || updatedAtMs <= 0L) return positionSec.coerceAtLeast(0.0)
        val elapsed = ((nowMs - updatedAtMs).coerceAtLeast(0L)) / 1000.0
        return (positionSec + elapsed).coerceAtLeast(0.0)
    }

    /** Seek when the gap is strictly over the threshold — a gap of
     *  exactly 2 s is jitter, not desync. */
    fun shouldSeek(localPosSec: Double, hostPosSec: Double): Boolean =
        abs(localPosSec - hostPosSec) > DRIFT_THRESHOLD_SEC

    /** The heartbeat gate (used by the host's 1 s position ticks):
     *  due only while playing and only once the cadence has elapsed
     *  since the last write. */
    fun heartbeatDue(lastWriteMs: Long, nowMs: Long, playing: Boolean): Boolean =
        playing && nowMs - lastWriteMs >= HEARTBEAT_MS

    /** Host-side seek detection on a position tick: during normal
     *  playback the player sits at lastSentPos + elapsed time, so a
     *  gap over the threshold against THAT expectation — not against
     *  the raw last-sent position — is a real seek (a raw comparison
     *  would "detect" a seek every ~3 s of ordinary playback). */
    fun seekJumpDetected(
        lastSentPos: Double, lastSentMs: Long, curPos: Double, nowMs: Long,
    ): Boolean {
        if (lastSentMs <= 0L) return false
        val expected = lastSentPos + ((nowMs - lastSentMs).coerceAtLeast(0L)) / 1000.0
        return abs(curPos - expected) > DRIFT_THRESHOLD_SEC
    }

    /** The guest's "Waiting for host…" condition: the session claims
     *  to be playing but the host has not written for over the grace. */
    fun hostStale(session: WatchSession, nowMs: Long): Boolean =
        session.state == "playing" && session.updatedAtMs > 0L &&
            nowMs - session.updatedAtMs > HOST_STALE_GRACE_MS

    /** Host leaving: the session parks PAUSED at the host's corrected
     *  position with the hostLeft flag — the guest sees "Host left —
     *  session paused" and keeps the frozen frame; the host can rejoin
     *  and press play. An ended session is never resurrected. */
    fun hostLeftState(session: WatchSession, nowMs: Long): WatchSession {
        if (!session.active) return session
        return session.copy(
            state = "paused",
            positionSec = correctedPosition(
                session.state, session.positionSec, session.updatedAtMs, nowMs,
            ),
            updatedAtMs = nowMs,
            hostLeft = true,
        )
    }

    /** A participant leaving only clears their joined flag — the
     *  session itself is untouched (a guest leaving never pauses
     *  the host's video). */
    fun guestLeft(session: WatchSession, uid: String): WatchSession =
        session.copy(joined = session.joined + (uid to false))

    /** Ending a session: state "ended", everything else kept so the
     *  card can still name the video it was. */
    fun endSession(session: WatchSession): WatchSession =
        session.copy(state = "ended")

    /** Starting a new session supersedes the old one (single-active
     *  rule): the old session, if still active, comes back ended.
     *  Null when there is nothing to supersede. */
    fun superseded(old: WatchSession?): WatchSession? =
        old?.takeIf { it.active }?.let { endSession(it) }
}

// ================= SESSION STORE (one listener per chat) =================

/** Decode one watchparty doc snapshot (null = no session yet). */
internal fun watchSessionFromDoc(id: String, snap: DocumentSnapshot?): WatchSession? {
    if (snap == null || !snap.exists()) return null
    return WatchSession(
        id = id,
        url = snap.getString("url") ?: "",
        videoId = snap.getString("videoId") ?: "",
        kind = snap.getString("kind")?.takeIf { it == "youtube" || it == "web" } ?: "web",
        hostUid = snap.getString("hostUid") ?: "",
        state = snap.getString("state")
            ?.takeIf { it == "waiting" || it == "playing" || it == "paused" || it == "ended" }
            ?: "waiting",
        positionSec = (snap.getDouble("positionSeconds") ?: 0.0).coerceAtLeast(0.0),
        updatedAtMs = snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
        joined = (snap.get("joined") as? Map<*, *>)?.entries
            ?.associate { it.key.toString() to (it.value == true) } ?: emptyMap(),
        hostLeft = snap.getBoolean("hostLeft") == true,
        inviteMsgId = snap.getString("inviteMsgId") ?: "",
    )
}

/** The per-chat session flow, modelled on NikahStore: one snapshot
 *  listener per chat, attached while the chat is open (Chat.kt's
 *  DisposableEffect) and removed on leaving it. The listener follows
 *  the LATEST invite message's doc id — a new invite re-keys it, so
 *  there is never more than one live listener per participant. */
object WatchPartyStore {
    private val flows = mutableMapOf<String, MutableStateFlow<WatchSession?>>()
    private val regs = mutableMapOf<String, ListenerRegistration>()
    private val watched = mutableMapOf<String, String>()
    /** Chats whose session was staged via [setPreview] (the test /
     *  preview seam — production never calls it, so this set is
     *  always empty there and [watch] behaves exactly as written). */
    private val previewStaged = mutableSetOf<String>()

    fun flow(chatId: String): StateFlow<WatchSession?> =
        flows.getOrPut(chatId) { MutableStateFlow(null) }

    fun current(chatId: String): WatchSession? = flows[chatId]?.value

    /** Attach the session listener for [chatId]'s [sessionId]
     *  (idempotent per session; re-keying removes the old one).
     *  A staged preview session is never overwritten by a listener:
     *  the harness stages the session AND the doc (dummy backend)
     *  would answer "no doc", which must not wipe the staged state
     *  mid-shot. */
    fun watch(chatId: String, sessionId: String) {
        if (watched[chatId] == sessionId && regs.containsKey(chatId)) return
        if (chatId in previewStaged) {
            watched[chatId] = sessionId
            return
        }
        unwatch(chatId)
        watched[chatId] = sessionId
        val reg = runCatching {
            FirebaseEngine.listenWatchParty(chatId, sessionId) { s ->
                flows.getOrPut(chatId) { MutableStateFlow(null) }.value = s
            }
        }.getOrNull() ?: return
        regs[chatId] = reg
    }

    fun unwatch(chatId: String) {
        regs.remove(chatId)?.remove()
        watched.remove(chatId)
    }

    /** Test / preview seam: set the session directly (PreviewRepo
     *  stages it on send; the harness stages it for shots). Passing
     *  null clears the staged session (and the staged mark). */
    fun setPreview(chatId: String, session: WatchSession?) {
        if (session != null) previewStaged.add(chatId) else previewStaged.remove(chatId)
        flows.getOrPut(chatId) { MutableStateFlow(null) }.value = session
    }
}

/** The card / banner title for an invite message: YouTube videos
 *  have no title metadata on the message, so they read as "YouTube
 *  video"; web pages read as their domain. */
internal fun watchDisplayTitle(m: TikuMessage): String =
    if (m.wpKind == "youtube") "YouTube video"
    else watchDomain(m.wpUrl).ifBlank { "Web page" }

internal fun watchDomain(rawUrl: String): String {
    val u = rawUrl.trim()
    val noScheme = u.substringAfter("://", u)
    val host = noScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        .substringAfterLast("@")
    return host.removePrefix("www.")
}

// ================= WEBVIEWS (never leave Tik) =================

/** The native half of the YouTube bridge, exposed to the player
 *  page as "TikuWatchNative". Its methods run on the WebView's
 *  private JS thread; every callback hands off to the main thread
 *  through the pane's postOnMain, and every field it touches is
 *  immutable or @Volatile. */
internal class WatchPlayerBridge(
    private val onReadyFn: (Double) -> Unit,
    private val onPlayerStateFn: (Int, Double) -> Unit,
    private val onTickFn: (Double) -> Unit,
    private val onErrorFn: (Int) -> Unit,
) {
    @JavascriptInterface fun onReady(durationSec: Double) = onReadyFn(durationSec)
    @JavascriptInterface fun onPlayerState(state: Int, positionSec: Double) =
        onPlayerStateFn(state, positionSec)
    @JavascriptInterface fun onTick(positionSec: Double) = onTickFn(positionSec)
    @JavascriptInterface fun onError(code: Int) = onErrorFn(code)
}

internal fun destroyWatchWebView(wv: WebView) {
    runCatching { wv.stopLoading() }
    runCatching { wv.loadUrl("about:blank") }
    (wv.parent as? ViewGroup)?.removeView(wv)
    runCatching { wv.destroy() }
}

/** The YouTube player view: the local HTML asset (assets/watch/
 *  youtube_player.html, video id substituted) loaded with the
 *  youtube.com base URL, driving the IFrame API. The view refuses
 *  EVERY navigation — the player is a fixed embed, never a browser —
 *  and DOM storage is on because the IFrame API needs it. Playback
 *  needs no user gesture: the guest's player must start when the
 *  host presses play, with no tap on the guest's phone. */
@SuppressLint("SetJavaScriptEnabled")
internal fun buildWatchYouTubeWebView(
    ctx: Context,
    videoId: String,
    html: String,
    bridge: WatchPlayerBridge,
    onLoadError: (() -> Unit)? = null,
): WebView {
    val wv = WebView(ctx)
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
    }
    wv.addJavascriptInterface(bridge, "TikuWatchNative")
    wv.webViewClient = object : WebViewClient() {
        // The player page never navigates: refuse everything (a tap
        // on the YouTube logo etc. stays put — there is no outside
        // browser to escape to).
        override fun shouldOverrideUrlLoading(
            view: WebView?, request: WebResourceRequest?,
        ): Boolean = true

        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?,
            error: android.webkit.WebResourceError?,
        ) {
            if (request?.isForMainFrame == true) onLoadError?.invoke()
        }
    }
    wv.loadDataWithBaseURL(
        "https://www.youtube.com/",
        html.replace("__VIDEO_ID__", videoId),
        "text/html", "utf-8", null,
    )
    return wv
}

/** The general in-app browser: any http(s) URL, navigated ONLY
 *  inside this WebView. http(s) links load in-view (returning false
 *  lets the view handle them); every other scheme (intent:, tel:,
 *  mailto:, market:, …) is swallowed — it can never fire an external
 *  intent or a deep link out of Tik. target=_blank loads in-view too:
 *  multiple windows are off, so a new-window link opens in this same
 *  view. Mixed content uses COMPATIBILITY (it is a browser — pages
 *  must render); the fixed YouTube embed above keeps NEVER_ALLOW. */
@SuppressLint("SetJavaScriptEnabled")
internal fun buildWatchBrowserWebView(
    ctx: Context,
    url: String,
    onPage: (title: String, url: String, canGoBack: Boolean) -> Unit,
    onLoadError: (() -> Unit)? = null,
): WebView {
    val wv = WebView(ctx)
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
    }
    wv.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?, request: WebResourceRequest?,
        ): Boolean {
            val scheme = request?.url?.scheme?.lowercase()
            // http(s): false = the WebView loads it in-view itself.
            // Anything else: true = swallowed, no intent ever fires.
            return scheme != "http" && scheme != "https"
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onPage(view?.title ?: "", url ?: "", view?.canGoBack() == true)
        }

        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?,
            error: android.webkit.WebResourceError?,
        ) {
            if (request?.isForMainFrame == true) onLoadError?.invoke()
        }
    }
    wv.loadUrl(url)
    return wv
}

/** The WebView host for one open watch pane (real mode only in
 *  production; under the harness the shadow WebView is inert and the
 *  Compose chrome + placeholder carry the shot). Created once per
 *  (message, attempt), destroyed with the pane — closing the pane,
 *  leaving the chat, or locking the session tears the view down, so
 *  audio stops the moment the screen closes. */
@Composable
private fun WatchWebViewHost(
    msg: TikuMessage,
    ytHtml: String?,
    bridge: WatchPlayerBridge,
    wvHolder: MutableState<WebView?>,
    webAttempt: Int,
    onPage: (title: String, url: String, canGoBack: Boolean) -> Unit,
    onLoadError: () -> Unit,
) {
    val ctx = LocalContext.current
    val webView = remember(msg.id, webAttempt) {
        (if (msg.wpKind == "youtube") {
            buildWatchYouTubeWebView(
                ctx, msg.wpVideoId, ytHtml ?: "", bridge, onLoadError,
            )
        } else {
            buildWatchBrowserWebView(ctx, msg.wpUrl, onPage, onLoadError)
        }).also { wvHolder.value = it }
    }
    DisposableEffect(msg.id, webAttempt) {
        onDispose { destroyWatchWebView(webView) }
    }
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
}

// ================= THE WATCH PANE (player/browser on top of the chat) =================

/** The watch pane: video/browser on top, the SAME chat thread +
 *  composer below it (it is a child of the chat's own header column
 *  in Chat.kt — not a second chat, not a full-screen takeover).
 *  Back goes back in browser history first, then closes the pane.
 *
 *  Sync wiring (guest): every session snapshot re-applies the host
 *  state (seek on drift > 2 s, play/pause to match); the 1 s player
 *  ticks do the same continuously. Sync wiring (host): state changes
 *  publish at once; the ticks publish a heartbeat at most every
 *  15 s and publish immediately on a detected seek. There is NO
 *  timer loop in composition: the player's own 1 s poll is the only
 *  clock, plus one single-shot delay that flips the "Waiting for
 *  host…" line — the harness can always idle.
 */
@Composable
fun WatchTogetherPane(
    chatId: String,
    msg: TikuMessage,
    session: WatchSession?,
    role: String,
    myUid: String,
    peerName: String,
    peerUid: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHost = role == "host"
    val isYouTube = msg.wpKind == "youtube"
    val ctx = LocalContext.current
    val sessionNow by rememberUpdatedState(session)
    val wvHolder = remember(msg.id) { mutableStateOf<WebView?>(null) }
    var playerReady by remember(msg.id) { mutableStateOf(false) }
    var playerPlaying by remember(msg.id) { mutableStateOf(false) }
    var playerPos by remember(msg.id) { mutableStateOf(0.0) }
    var playerDur by remember(msg.id) { mutableStateOf(0.0) }
    var loadFailed by remember(msg.id) { mutableStateOf(false) }
    var webAttempt by remember(msg.id) { mutableIntStateOf(0) }
    var pageTitle by remember(msg.id) { mutableStateOf("") }
    var pageUrl by remember(msg.id) { mutableStateOf(msg.wpUrl) }
    var canGoBack by remember(msg.id) { mutableStateOf(false) }
    var nowMs by remember(msg.id) { mutableLongStateOf(System.currentTimeMillis()) }
    val playerPosNow by rememberUpdatedState(playerPos)
    val playerPlayingNow by rememberUpdatedState(playerPlaying)
    /** JS-thread holders: the bridge writes here, the main thread reads. */
    val lastPos = remember(msg.id) { object { @Volatile var v: Double = 0.0 } }
    val lastSent = remember(msg.id) {
        object { @Volatile var pos: Double = 0.0; @Volatile var atMs: Long = 0L }
    }
    val caughtUp = remember(msg.id) { mutableStateOf(false) }
    val endedByMe = remember(msg.id) { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    fun postOnMain(fn: () -> Unit) {
        mainHandler.post { runCatching { fn() } }
    }

    // The player page: the local asset, re-read on retry. A missing
    // asset is a load failure with a retry, never a blank pane.
    val ytHtml = remember(msg.id, webAttempt) {
        if (!isYouTube) null
        else runCatching {
            ctx.assets.open("watch/youtube_player.html").bufferedReader().readText()
        }.getOrNull()
    }
    LaunchedEffect(msg.id, webAttempt) {
        if (isYouTube && ytHtml == null) loadFailed = true
    }

    /** Host-only publish: state change or heartbeat. Updates the
     *  last-sent watermark the seek detector + heartbeat gate read. */
    fun hostPublish(state: String, pos: Double) {
        if (!isHost) return
        lastSent.pos = pos
        lastSent.atMs = System.currentTimeMillis()
        FirebaseEngine.writeWatchParty(chatId, msg.id, state, pos, hostLeft = false)
    }

    /** Guest-only: bring this phone's player to the host's state. */
    fun syncGuestToSession(s: WatchSession) {
        val wv = wvHolder.value ?: return
        if (!s.isYouTube) return
        val target = WatchSync.correctedPosition(
            s.state, s.positionSec, s.updatedAtMs, System.currentTimeMillis(),
        )
        if (WatchSync.shouldSeek(playerPosNow, target)) {
            wv.evaluateJavascript("window.__tikuSeek($target)", null)
        }
        when (s.state) {
            "playing" -> if (!playerPlayingNow) {
                wv.evaluateJavascript("window.__tikuPlay()", null)
            }
            "paused", "waiting" -> if (playerPlayingNow) {
                wv.evaluateJavascript("window.__tikuPause()", null)
            }
        }
    }

    val bridge = remember(msg.id, webAttempt) {
        WatchPlayerBridge(
            onReadyFn = { dur ->
                postOnMain {
                    playerReady = true
                    playerDur = dur
                    val s = sessionNow ?: return@postOnMain
                    if (!s.isYouTube) return@postOnMain
                    val target = WatchSync.correctedPosition(
                        s.state, s.positionSec, s.updatedAtMs, System.currentTimeMillis(),
                    )
                    if (isHost) {
                        // Re-opened mid-session: resume where the host left it.
                        if (s.state == "playing") {
                            wvHolder.value?.evaluateJavascript(
                                "window.__tikuSeek($target); window.__tikuPlay();", null,
                            )
                        }
                    } else if (!caughtUp.value) {
                        // Late join: jump to the host's position once.
                        wvHolder.value?.evaluateJavascript("window.__tikuSeek($target)", null)
                        if (s.state == "playing") {
                            wvHolder.value?.evaluateJavascript("window.__tikuPlay()", null)
                        }
                        caughtUp.value = true
                    } else {
                        syncGuestToSession(s)
                    }
                }
            },
            onPlayerStateFn = { state, pos ->
                postOnMain {
                    playerPos = pos
                    lastPos.v = pos
                    playerPlaying = state == 1
                    if (!isHost) return@postOnMain
                    val mapped = when (state) {
                        1 -> "playing"
                        // 0 = the video finished: park it paused at the end.
                        2, 0 -> "paused"
                        // -1 unstarted, 3 buffering, 5 cued: no publish.
                        else -> return@postOnMain
                    }
                    val s = sessionNow
                    if (s == null || s.state != mapped) hostPublish(mapped, pos)
                }
            },
            onTickFn = { pos ->
                postOnMain {
                    playerPos = pos
                    lastPos.v = pos
                    val s = sessionNow ?: return@postOnMain
                    if (!s.isYouTube) return@postOnMain
                    if (isHost) {
                        if (!playerPlayingNow) return@postOnMain
                        val now = System.currentTimeMillis()
                        // A real seek publishes at once; otherwise the
                        // heartbeat gate allows at most one write / 15 s.
                        if (WatchSync.seekJumpDetected(lastSent.pos, lastSent.atMs, pos, now)) {
                            hostPublish("playing", pos)
                        } else if (WatchSync.heartbeatDue(lastSent.atMs, now, true)) {
                            hostPublish("playing", pos)
                        }
                    } else {
                        if (s.state != "playing") return@postOnMain
                        val target = WatchSync.correctedPosition(
                            s.state, s.positionSec, s.updatedAtMs, System.currentTimeMillis(),
                        )
                        if (WatchSync.shouldSeek(pos, target)) {
                            wvHolder.value?.evaluateJavascript("window.__tikuSeek($target)", null)
                        }
                    }
                }
            },
            onErrorFn = {
                postOnMain { loadFailed = true }
            },
        )
    }

    // Guest: every session snapshot re-applies the host state (a
    // snapshot that lands before the player is ready is a no-op —
    // onReady does the catch-up instead).
    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        if (isHost || !playerReady) return@LaunchedEffect
        syncGuestToSession(s)
    }
    LaunchedEffect(playerReady) {
        if (!playerReady) return@LaunchedEffect
        val s = sessionNow ?: return@LaunchedEffect
        if (!isHost) syncGuestToSession(s)
    }
    // "Waiting for host…" needs a clock, but only one single-shot
    // delay per snapshot: once the grace has elapsed the line is
    // stable, so nothing keeps ticking.
    LaunchedEffect(msg.id, session?.state, session?.updatedAtMs) {
        if (isHost) return@LaunchedEffect
        val s = sessionNow ?: return@LaunchedEffect
        if (s.state != "playing") return@LaunchedEffect
        val remaining = WatchSync.HOST_STALE_GRACE_MS -
            (System.currentTimeMillis() - s.updatedAtMs)
        if (remaining <= 0L) {
            nowMs = System.currentTimeMillis()
            return@LaunchedEffect
        }
        delay(remaining)
        nowMs = System.currentTimeMillis()
    }

    // Leaving the pane: host parks the session paused ("Host left —
    // session paused" for the guest); a guest only clears their
    // joined flag. The WebView itself dies with its host (below),
    // so no audio keeps playing after the screen closes. The End
    // button sets endedByMe first, so closing after an explicit End
    // never overwrites "ended" with "paused".
    DisposableEffect(msg.id) {
        onDispose {
            val s = WatchPartyStore.current(chatId)
            if (isHost) {
                if (!endedByMe.value && s != null && s.active) {
                    FirebaseEngine.hostLeaveWatchParty(chatId, msg.id, lastPos.v)
                }
            } else {
                if (myUid.isNotEmpty()) {
                    FirebaseEngine.markWatchLeft(chatId, msg.id, myUid)
                }
            }
        }
    }

    BackHandler {
        val wv = wvHolder.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    val bothJoined = (session?.joined?.values?.count { it } ?: 0) >= 2
    val peerJoined = session?.joined?.get(peerUid) == true
    val stateLine = when {
        session == null -> "Starting session…"
        session.state == "ended" -> "Session ended"
        session.hostLeft -> "Host left — session paused"
        !isYouTube -> "In-app browser · both of you see this page"
        session.state == "playing" && !isHost &&
            WatchSync.hostStale(session, nowMs) -> "Waiting for host…"
        session.state == "playing" -> "Playing together"
        session.state == "paused" -> "Paused"
        session.state == "waiting" ->
            if (isHost) "Press play to start" else "Waiting for host to start"
        else -> ""
    }
    val joinLine = when {
        bothJoined -> "Both joined"
        peerJoined -> "$peerName joined"
        else -> "Waiting for $peerName to join"
    }
    val timeText = if (playerDur > 0) {
        "${VideoTimerPolicy.formatClock(playerPos.toInt())} / " +
            VideoTimerPolicy.formatClock(playerDur.toInt())
    } else if (playerPos > 0) {
        VideoTimerPolicy.formatClock(playerPos.toInt())
    } else ""

    Column(
        modifier.fillMaxWidth().height(WATCH_PANE_HEIGHT_DP.dp).background(Color.Black),
    ) {
        // ---- header: title + live state + End (host) + close ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(WatchRose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow, null, Modifier.size(18.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    watchDisplayTitle(msg),
                    color = Color.White, fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontFamily = InterTight,
                )
                Text(
                    stateLine,
                    color = WatchSub, fontSize = 11.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontFamily = PoppinsFamily,
                )
            }
            if (isHost && session?.active == true) {
                Text(
                    "End",
                    color = WatchRose, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            endedByMe.value = true
                            FirebaseEngine.endWatchParty(chatId, msg.id)
                            onClose()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
        // ---- surface: the WebView over a placeholder ----
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // Placeholder behind the view: what the harness sees (its
            // WebView is a shadow), and what a slow load shows until
            // the real page covers it.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmojiGlyph("▶️", 30.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (isYouTube) "Video plays here" else "Page loads here",
                    color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
                    fontFamily = PoppinsFamily,
                )
                if (!isYouTube) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        watchDomain(msg.wpUrl),
                        color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontFamily = PoppinsFamily,
                    )
                }
            }
            if (loadFailed) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (isYouTube) "This video can't play here."
                        else "This page couldn't load.",
                        color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, fontFamily = InterTight,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Check your connection and try again.",
                        color = WatchSub, fontSize = 12.5.sp, fontFamily = PoppinsFamily,
                    )
                    Spacer(Modifier.size(14.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(WatchRose)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                loadFailed = false
                                webAttempt++
                            }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Try again", color = Color.White, fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            } else {
                key(webAttempt) {
                    WatchWebViewHost(
                        msg = msg, ytHtml = ytHtml, bridge = bridge,
                        wvHolder = wvHolder, webAttempt = webAttempt,
                        onPage = { title, url, back ->
                            pageTitle = title
                            pageUrl = url
                            canGoBack = back
                        },
                        onLoadError = { loadFailed = true },
                    )
                }
            }
        }
        // ---- controls: play/pause + time (YouTube) / back + URL (web) ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isYouTube) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(
                            if (isHost) WatchRose
                            else WatchRose.copy(alpha = 0.45f),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val wv = wvHolder.value ?: return@clickable
                            if (!playerReady) return@clickable
                            if (!isHost) {
                                AppState.toast("Only the host can play or pause")
                                return@clickable
                            }
                            if (playerPlaying) {
                                wv.evaluateJavascript("window.__tikuPause()", null)
                            } else {
                                wv.evaluateJavascript("window.__tikuPlay()", null)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playerPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, Modifier.size(20.dp), tint = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (timeText.isNotEmpty()) {
                    Text(
                        timeText, color = Color.White, fontSize = 12.5.sp,
                        fontFamily = PoppinsFamily,
                    )
                    Spacer(Modifier.width(10.dp))
                }
            } else {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(
                            if (canGoBack) Color.White.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = 0.05f),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val wv = wvHolder.value ?: return@clickable
                            if (wv.canGoBack()) wv.goBack()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ArrowBack, null, Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = if (canGoBack) 1f else 0.4f),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    (pageTitle.ifBlank { null } ?: pageUrl).ifBlank { msg.wpUrl },
                    color = Color.White, fontSize = 12.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), fontFamily = PoppinsFamily,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                joinLine, color = WatchSub, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = PoppinsFamily,
            )
        }
    }
}

// ================= THE URL ENTRY SHEET =================

/** The "Watch together" entry sheet: paste a link, start the
 *  session. States the honest scope line in-UI (YouTube syncs;
 *  other pages open in the in-app browser and their videos can't
 *  be synced). Invalid input is a plain inline error, never a
 *  crash and never a send. */
@Composable
fun WatchUrlSheet(
    initial: String,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp)).background(WatchCard)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .padding(20.dp),
        ) {
            Text(
                "Watch together",
                color = Color.White, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = InterTight,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "Paste a YouTube link or any web page. It opens inside Tik " +
                    "for both of you — never in an outside browser.",
                color = WatchSub, fontSize = 13.sp, fontFamily = PoppinsFamily,
            )
            Spacer(Modifier.size(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "Paste a link…", color = WatchSub, fontSize = 14.sp,
                        fontFamily = PoppinsFamily,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = TextStyle(
                        fontFamily = PoppinsFamily, color = Color.White, fontSize = 14.sp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (error != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    error!!, color = WatchDanger, fontSize = 12.5.sp,
                    fontFamily = PoppinsFamily,
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                "YouTube videos stay in sync. Other pages open in the " +
                    "in-app browser — their videos can't be synced.",
                color = WatchSub, fontSize = 12.sp, fontFamily = PoppinsFamily,
            )
            Spacer(Modifier.size(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Cancel",
                    color = WatchSub, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(WatchRose)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val parsed = WatchSync.parseUrl(text)
                            if (parsed == null) {
                                error = "Enter a YouTube link or a web address"
                            } else {
                                onStart(parsed.url)
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Start watching", color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                    )
                }
            }
        }
    }
}
