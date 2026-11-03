package com.tiku.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpOrCanceled
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ============================================================================
// ROUND 13 — WATCH, REBUILT. The build-44 design kept ONE singleton WebView
// and re-parented it between three hosts (pane, floating mini player,
// fullscreen) while HomeScreen crossfaded panes with AnimatedContent — two
// compositions overlapped during every tab change and fought over the same
// view; closing Watch and reopening it crashed. That pattern is DELETED.
//
// ROUND 14 — browsing/search stay on m.youtube, but a /watch page itself is
// never rendered — the pane swaps in the locked youtube-nocookie embed
// (see youtubeEmbedHtml) and locks navigation to it until the user leaves
// via the header "+" (WatchControl.goHome) or the pane is destroyed.
//
// ROUND 14 ADDENDUM 2 §5 — the FLOATING MINI PLAYER, crash-safe by
// construction: the pane's WebView now lives in ONE root-level host
// (WatchHost, composed by HomeScreen above the panes, below the NavPill)
// for the whole Tik session. It is NEVER re-parented, attached or detached
// on a tab switch — the host only animates the bounds of the single slot
// that holds it: EXPANDED over the Watch tab's content area, or a small
// draggable MINI window on the other tabs while a session is alive, so a
// playing video simply keeps playing. The pane itself is composed only
// while (Watch tab || session alive); with neither, there is nothing to
// preserve and the host renders nothing. Fullscreen is the one exception
// that is NOT the WebView: the page-supplied custom view is rendered by
// WatchFullscreenLayer at the HomeScreen root (above nav + sheets), so
// it is never clipped by the host's bounds. Lock / quick-exit still clear
// the session (SessionLock), which uncomposes the host and destroys the
// WebView outright, exactly as before.
// ============================================================================

/** Plain session state for Watch — NO View references, ever. */
object WatchSession {
    const val HOME_URL = "https://m.youtube.com"

    var lastUrl by mutableStateOf(HOME_URL)
    var lastTitle by mutableStateOf("")
    var hasSession by mutableStateOf(false)

    /** Bumped by [clear]; a pane captures the epoch at creation and its
     *  dispose-time save is ignored after a clear (lock / quick-exit), so a
     *  torn-down pane can never resurrect a cleared session. */
    var epoch: Int = 0
        private set

    /** Live update from the composed pane (does not raise the bar). */
    fun record(url: String, title: String, fromEpoch: Int) {
        if (fromEpoch != epoch) return
        if (!url.startsWith("http")) return
        lastUrl = url
        if (title.isNotBlank()) lastTitle = title
    }

    /** The pane is going away with a page loaded: the bar may now show. */
    fun recordOnLeave(url: String, title: String, fromEpoch: Int) {
        if (fromEpoch != epoch) return
        if (!url.startsWith("http")) return
        lastUrl = url
        if (title.isNotBlank()) lastTitle = title
        hasSession = true
    }

    /** Lock / quick-exit / bar close: forget everything. */
    fun clear() {
        epoch++
        lastUrl = HOME_URL
        lastTitle = ""
        hasSession = false
    }
}

/** Tiny registered controller so chrome outside the pane (the Watch header
 *  "+" button, MainActivity.onPause) can reach the ACTIVE pane's WebView.
 *  Both lambdas are non-null only while a WatchPane is composed. */
object WatchControl {
    var navigate: ((String) -> Unit)? = null
    var pausePlayback: (() -> Unit)? = null
    var stopPlayback: (() -> Unit)? = null

    /** FULL stop — the mini window's ✕ and the "Now watching" bar's ✕:
     *  halt the live pane first (about:blank + pause), then clear the
     *  session, which uncomposes WatchHost and destroys the WebView.
     *  This is the owner's rule: closing from the chat side stops the
     *  video completely, it does not just hide the window. */
    fun stop() {
        stopPlayback?.invoke()
        WatchSession.clear()
    }

    /** Header "+": if the pane is alive, navigate it; otherwise seed the
     *  session and switch to the Watch tab (the pane resumes at HOME). */
    fun goHome() {
        val nav = navigate
        if (nav != null) {
            nav(WatchSession.HOME_URL)
        } else {
            WatchSession.lastUrl = WatchSession.HOME_URL
            WatchSession.hasSession = true
            AppState.homeTab = HomeTab.WATCH
            if (AppState.screen !is Screen.Chat && AppState.screen !is Screen.ChatDetails) {
                AppState.go(Screen.Home)
            }
        }
    }

    /** Activity pause: pause the active pane's video, if any. When no pane
     *  is composed nothing can be playing — its WebView is destroyed. */
    fun requestPause() {
        pausePlayback?.invoke()
    }
}

/** Fullscreen custom-view state, hoisted OUT of the pane: HomeScreen's
 *  root renders [WatchFullscreenLayer] above the whole shell (nav,
 *  sheets), so a fullscreen video is never clipped by WatchHost's
 *  animated bounds. The WebView itself never moves — only this ephemeral
 *  page-supplied view gets a top-level home, the same job the pane's own
 *  box did in build 47 before the host existed. */
object WatchFullscreen {
    var view by mutableStateOf<View?>(null)
    var callback by mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
}

/** YouTube thumbnail id from a watch/shorts/embed URL, or null. */
fun youtubeThumbId(url: String): String? {
    Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(url)?.let { return it.groupValues[1] }
    return Regex("/(?:shorts|embed)/([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
}

/** Video id of a YouTube /watch page URL, or null for anything else
 *  (browse/search/channel pages keep loading normally). */
private fun watchPageVideoId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (!host.endsWith("youtube.com")) return null
    if (uri.path != "/watch") return null
    val v = uri.getQueryParameter("v") ?: return null
    return v.takeIf { it.length >= 6 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
}

/**
 * ROUND 14 — the LOCKED YouTube embed shared by both players (this pane
 * and the facade drama player in DramaShell.kt): a local HTML document
 * whose only content is a youtube-nocookie iframe — no watch page, no
 * "Open App" pill, no comments, no related-video rail. Combined with
 * each player's WebViewClient lockdown, playback stays in this document.
 */
internal fun youtubeEmbedHtml(videoId: String): String =
    "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, " +
        "maximum-scale=1, user-scalable=no\">" +
        "<title>YouTube</title>" +
        "<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}" +
        "iframe{position:absolute;left:0;top:0;width:100%;height:100%;border:0}</style>" +
        "</head><body>" +
        "<iframe src=\"https://www.youtube-nocookie.com/embed/" + videoId +
        "?playsinline=1&rel=0&modestbranding=1&autoplay=1\" allowfullscreen " +
        "allow=\"autoplay; encrypted-media; picture-in-picture\"></iframe>" +
        "</body></html>"

internal fun loadYoutubeEmbed(wv: WebView, videoId: String) {
    wv.loadDataWithBaseURL(
        "https://www.youtube-nocookie.com",
        youtubeEmbedHtml(videoId),
        "text/html", "UTF-8", null,
    )
}

/** Mutable holder so the pane's WebView-building lambdas can reach the
 *  CURRENT WebView instance (rebuilt on a renderer-gone heal). */
private class WebViewRef { var wv: WebView? = null }

/** Bottom inset the Watch pane keeps so YouTube's own bottom nav stays
 *  clear of Tik's floating glass nav (nav pill 60dp + 14dp page margin +
 *  breathing room). */
val WatchBottomInset = 100.dp

// ---- ROUND 14 Addendum 2 §5 — WatchHost layout contract -----------------
// WatchScreen (Home.kt) renders ONLY the chrome (Title + home button);
// WatchHost renders the pane. These constants are the single source of
// truth both sides share so the host's EXPANDED rect lands EXACTLY on
// the area under WatchScreen's header:
//   header height = top padding (24) + button height (46) = 70.
val WatchChromeTopPadding = 24.dp
val WatchChromeButtonSize = 46.dp
val WatchChromeHeaderHeight = 70.dp

/** Mini window: 208x117 is exact 16:9. */
val WatchMiniWidth = 208.dp
val WatchMiniHeight = 117.dp
val WatchMiniCorner = 12.dp
val WatchMiniEdgeMargin = 12.dp

/** NowWatchingBar's bottom inset in TikuRoot: the NavPill's top edge
 *  sits 86.dp from the bottom (72.dp pill + 14.dp column padding), so
 *  the bar floats 8.dp above it. Bar height = 36.dp thumbnail + 8+8
 *  vertical padding = 52.dp, i.e. its top edge is at 146.dp. */
val NowWatchingBarBottomInset = 94.dp

/** The mini window's resting bottom clearance: bar top (146) + 8 gap —
 *  the mini and the bar coexist on chat tabs and never overlap. */
val WatchMiniBottomClearance = 154.dp

@SuppressLint("SetJavaScriptEnabled")
private fun buildWatchWebView(
    ctx: Context,
    startUrl: String,
    onPage: (String) -> Unit,
    onTitle: (String) -> Unit,
    onShowCustom: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustom: () -> Unit,
    onRendererGone: () -> Unit,
    onWatchVideo: (String) -> Unit,
    embedLocked: () -> Boolean,
): WebView {
    val wv = WebView(ctx)
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        cacheMode = WebSettings.LOAD_DEFAULT
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // Keep YouTube's own UA: spoofing a desktop UA breaks mobile playback.
        userAgentString = WebSettings.getDefaultUserAgent(ctx)
    }
    wv.setBackgroundColor(android.graphics.Color.BLACK)
    wv.webViewClient = object : WebViewClient() {
        // ROUND 14 — clean playback: browsing/search stay on m.youtube,
        // but a /watch page is never rendered — the pane swaps in the
        // locked nocookie embed for that video (see onWatchVideo), and
        // while the embed is up, no main-frame navigation leaves it.
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (embedLocked()) return true
            val id = watchPageVideoId(request?.url?.toString())
            if (id != null) {
                onWatchVideo(id)
                return true
            }
            return false
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            // m.youtube is an SPA: tapping a video changes the URL via the
            // History API without shouldOverrideUrlLoading ever firing —
            // the embed swap has to happen here for in-page taps.
            if (!embedLocked()) {
                watchPageVideoId(url)?.let { onWatchVideo(it) }
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (url.isNullOrBlank()) return
            // The embed document itself is not a session page: it must
            // never overwrite the canonical watch URL recorded for the
            // "Now watching" bar / resume.
            if (embedLocked()) return
            val id = watchPageVideoId(url)
            if (id != null) {
                // Fresh load / resume straight onto a watch page: swap
                // before it can render.
                onWatchVideo(id)
                return
            }
            onPage(url)
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // The renderer died (happens after aggressive backgrounding).
            // Returning true takes responsibility: the pane swaps in a
            // fresh WebView on the same URL (pane-local heal, spec B).
            onRendererGone()
            return true
        }
    }
    wv.webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, title: String?) {
            // While the embed is up, keep the real video's title (the
            // local embed document has nothing better to report).
            if (embedLocked()) return
            if (!title.isNullOrBlank()) onTitle(title)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view != null && callback != null) onShowCustom(view, callback)
        }

        override fun onHideCustomView() {
            onHideCustom()
        }
    }
    if (startUrl.startsWith("http")) wv.loadUrl(startUrl)
    return wv
}

private fun destroyWatchWebView(wv: WebView) {
    runCatching { wv.stopLoading() }
    runCatching { wv.loadUrl("about:blank") }
    (wv.parent as? ViewGroup)?.removeView(wv)
    runCatching { wv.destroy() }
}

private fun pauseWatchWebView(wv: WebView) {
    runCatching {
        wv.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v&&!v.paused){v.pause();}})();",
            null,
        )
    }
    runCatching { wv.onPause() }
}

/**
 * The Watch pane. Owns its WebView end to end: created here, destroyed here.
 * Placement is NOT decided here — [WatchHost] composes this pane in its
 * single root-level slot and only animates that slot's bounds (expanded
 * on the Watch tab, mini window elsewhere); the pane is never re-parented.
 * Fullscreen's custom view is hoisted to [WatchFullscreen] and rendered
 * by [WatchFullscreenLayer] at the HomeScreen root.
 *
 * [chromeInsets] keeps the build-47 bottom inset (YouTube's own bottom
 * nav clear of the NavPill) in EXPANDED mode; the mini window has no
 * chrome to clear, so the host passes false there. The screenshot suite
 * calls `WatchPane()` with the default, unchanged.
 */
@Composable
fun WatchPane(chromeInsets: Boolean = true) {
    val pal = LocalPalette.current
    if (Config.PREVIEW_UI_ONLY) {
        // Screenshot/preview branch: no WebView (Robolectric has no real
        // renderer). Round-12 copy kept so the suite's text checks hold.
        Box(
            Modifier.fillMaxSize().background(pal.bg)
                .then(if (chromeInsets) Modifier.padding(bottom = WatchBottomInset) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TikIcon("play", Modifier.size(44.dp), tint = pal.sub)
                Text(
                    "YouTube opens here on your phone",
                    fontFamily = InterTight, fontSize = 14.sp, color = pal.sub,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        return
    }

    val ctx = LocalContext.current
    val paneEpoch = remember { WatchSession.epoch }
    var currentUrl by remember { mutableStateOf(WatchSession.lastUrl) }
    var pageTitle by remember { mutableStateOf(WatchSession.lastTitle) }
    var healGen by remember { mutableIntStateOf(0) }
    // ROUND 14 — locked-embed state: [embedId] is the video currently
    // shown as a clean embed (null = free browsing on m.youtube);
    // [liveRef]/[embedRef] track which WebView instance is alive / hosts
    // the embed, so a renderer-gone heal re-loads the embed on the
    // fresh view instead of resurrecting the raw watch page.
    var embedId by remember { mutableStateOf<String?>(null) }
    val liveRef = remember { WebViewRef() }
    val embedRef = remember { WebViewRef() }

    val webView = remember(healGen) {
        buildWatchWebView(
            ctx = ctx.applicationContext,
            startUrl = currentUrl,
            onPage = { url ->
                currentUrl = url
                WatchSession.record(url, pageTitle, paneEpoch)
            },
            onTitle = { title ->
                pageTitle = title
                WatchSession.record(currentUrl, title, paneEpoch)
            },
            onShowCustom = { view, callback ->
                WatchFullscreen.view = view
                WatchFullscreen.callback = callback
            },
            onHideCustom = {
                val cb = WatchFullscreen.callback
                WatchFullscreen.view = null
                WatchFullscreen.callback = null
                runCatching { cb?.onCustomViewHidden() }
            },
            onRendererGone = { healGen++ },
            onWatchVideo = { id ->
                val target = liveRef.wv
                if (target != null && (embedId != id || embedRef.wv !== target)) {
                    embedId = id
                    // The session keeps the canonical watch URL: the bar
                    // thumbnail parses it, and a fresh pane resuming here
                    // lands in onPageStarted and swaps to the embed again.
                    val canonical = "https://www.youtube.com/watch?v=$id"
                    currentUrl = canonical
                    WatchSession.record(canonical, pageTitle, paneEpoch)
                    // A video is actually PLAYING: the session is live
                    // right now, not only when the pane leaves. This is
                    // what keeps WatchHost composing this pane across a
                    // tab switch, so the mini window takes over with the
                    // SAME WebView — playback never restarts.
                    if (paneEpoch == WatchSession.epoch) WatchSession.hasSession = true
                    embedRef.wv = target
                    runCatching { loadYoutubeEmbed(target, id) }
                }
            },
            embedLocked = { embedId != null },
        )
    }
    liveRef.wv = webView

    fun exitFullscreen() {
        // Completing the CustomViewCallback makes the chrome client run its
        // onHideCustomView, which clears the state; the guards below cover
        // the already-cleared case.
        if (WatchFullscreen.view != null) {
            val cb = WatchFullscreen.callback
            WatchFullscreen.view = null
            WatchFullscreen.callback = null
            runCatching { cb?.onCustomViewHidden() }
        }
    }
    BackHandler(enabled = WatchFullscreen.view != null) { exitFullscreen() }

    DisposableEffect(webView) {
        WatchControl.navigate = { url ->
            // The way back from the embed to browsing: the header "+"
            // (WatchControl.goHome) routes here — drop the lock first,
            // then load the requested page (YouTube home) normally.
            embedId = null
            embedRef.wv = null
            runCatching { webView.loadUrl(url) }
        }
        WatchControl.pausePlayback = { pauseWatchWebView(webView) }
        WatchControl.stopPlayback = {
            // FULL stop (mini ✕ / bar ✕ via WatchControl.stop): kill the
            // page + playback NOW; WatchSession.clear() right after makes
            // the host uncompose this pane, whose dispose destroys the
            // WebView — recordOnLeave there is epoch-guarded and ignored.
            embedId = null
            embedRef.wv = null
            runCatching { webView.stopLoading() }
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.onPause() }
        }
        onDispose {
            if (WatchControl.navigate != null) WatchControl.navigate = null
            if (WatchControl.pausePlayback != null) WatchControl.pausePlayback = null
            if (WatchControl.stopPlayback != null) WatchControl.stopPlayback = null
            // Save BEFORE destroy; epoch-guarded so a lock-time clear wins.
            WatchSession.recordOnLeave(currentUrl, pageTitle, paneEpoch)
            if (WatchFullscreen.view != null) {
                val cb = WatchFullscreen.callback
                WatchFullscreen.view = null
                WatchFullscreen.callback = null
                runCatching { cb?.onCustomViewHidden() }
            }
            destroyWatchWebView(webView)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .then(if (chromeInsets) Modifier.padding(bottom = WatchBottomInset) else Modifier),
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The fullscreen surface for the Watch WebView's custom view, rendered
 * by HomeScreen's root ABOVE the NavPill and the note sheet — the video
 * gets the whole Tik area instead of WatchHost's current bounds (which
 * may be the 208x117 mini window when fullscreen is requested from it).
 */
@Composable
fun WatchFullscreenLayer() {
    val v = WatchFullscreen.view ?: return
    key(v) {
        AndroidView(
            factory = { v },
            modifier = Modifier.fillMaxSize().background(Color.Black),
        )
    }
}

/**
 * ROUND 14 Addendum 2 §5 — the floating-player host. Composed by
 * HomeScreen's root Box in ONE fixed sibling slot (panes < WatchHost <
 * NavPill) for the whole Tik session; [WatchPane] is called from exactly
 * ONE call site inside it, so the WebView is created once and destroyed
 * only when the Tik session ends (lock / quick-exit / full stop). Mode
 * changes only animate this host's bounds — the WebView is never moved
 * between parents, which is the Round 11/13 crash lesson.
 *
 * Modes (driven by AppState.tikDestination + WatchSession.hasSession):
 * - EXPANDED (destination == 2): fills the Watch content area — full
 *   width, top at [WatchChromeHeaderHeight] (exactly under WatchScreen's
 *   chrome), bottom at the screen edge (the pane's own 100.dp inset
 *   keeps YouTube's bottom nav clear of the NavPill, as in build 47).
 * - MINI (destination != 2, session alive): the same live pane shrunk to
 *   208x117 (16:9), radius 12, border + shadow. Resting spot: bottom-
 *   right, 12.dp from the right edge, [WatchMiniBottomClearance] from
 *   the bottom — i.e. directly above the "Now watching" bar, which
 *   stays visible underneath it. DRAGGABLE anywhere (clamped to the
 *   screen), springs to the nearest horizontal edge on release; a tap
 *   returns to the Watch tab; the ✕ chip fully stops playback.
 * - Not composed at all when there is no session and the Watch tab is
 *   not selected: nothing exists to preserve, and a later visit builds
 *   the pane fresh on the recorded session URL, exactly like build 47.
 *   When HomeScreen sits under a Chat/Call/Story overlay (screen !=
 *   Home) the host keeps its last rect but drops the touch overlay —
 *   the overlay sibling above it owns the screen; the video keeps
 *   playing and the mini reappears when the user comes back.
 */
@Composable
fun WatchHost() {
    val dest = AppState.tikDestination
    val hasSession = WatchSession.hasSession
    if (Config.PREVIEW_UI_ONLY) {
        // Screenshot harness: Robolectric must never see a real WebView.
        // Only the EXPANDED placeholder renders (shot24 composes
        // WatchPane() directly and gets the identical surface).
        if (dest == 2) {
            Box(Modifier.fillMaxSize().padding(top = WatchChromeHeaderHeight)) { WatchPane() }
        }
        return
    }
    val expanded = dest == 2
    if (!expanded && !hasSession) return
    val covered = AppState.screen !is Screen.Home

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        val spec = tween<Dp>(340, easing = RdEase)
        val wAnim by animateDpAsState(if (expanded) maxW else WatchMiniWidth, spec, label = "watchW")
        val hAnim by animateDpAsState(
            if (expanded) maxH - WatchChromeHeaderHeight else WatchMiniHeight, spec, label = "watchH",
        )
        val rAnim by animateDpAsState(if (expanded) 0.dp else WatchMiniCorner, spec, label = "watchR")
        val ax = remember { Animatable(0.dp) }
        val ay = remember { Animatable(WatchChromeHeaderHeight) }
        var hasCustomPos by remember { mutableStateOf(false) }
        val defMiniX = maxW - WatchMiniWidth - WatchMiniEdgeMargin
        val defMiniY = maxH - WatchMiniBottomClearance - WatchMiniHeight
        // Position driver: mode switches glide the window between the
        // expanded origin and the mini resting spot. Once the user has
        // dragged the mini, its position is theirs — mode switches and
        // resizes leave ax/ay alone (render-time clamps keep it on
        // screen). The gesture handler stops these animatables before
        // taking over, so the two never fight.
        LaunchedEffect(expanded, hasCustomPos, defMiniX, defMiniY) {
            if (expanded) {
                launch { ax.animateTo(0.dp, spec) }
                launch { ay.animateTo(WatchChromeHeaderHeight, spec) }
            } else if (!hasCustomPos) {
                launch { ax.animateTo(defMiniX, spec) }
                launch { ay.animateTo(defMiniY, spec) }
            }
        }
        val curX = ax.value.coerceIn(0.dp, (maxW - wAnim).coerceAtLeast(0.dp))
        val curY = ay.value.coerceIn(0.dp, (maxH - hAnim).coerceAtLeast(0.dp))
        Box(
            Modifier
                .offset(x = curX, y = curY)
                .size(wAnim, hAnim)
                .shadow(if (expanded) 0.dp else 14.dp, RoundedCornerShape(rAnim))
                .clip(RoundedCornerShape(rAnim))
                .then(
                    if (expanded) Modifier
                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(rAnim)),
                )
                .background(Color.Black),
        ) {
            // THE one call site: identical in every mode, so a mode
            // change is a pure recomposition (new chromeInsets flag) —
            // the pane's remember/DisposableEffect state, and with it
            // the WebView, survive untouched.
            WatchPane(chromeInsets = expanded)
            if (!expanded && !covered) {
                // Drag / tap layer ABOVE the WebView (which would
                // otherwise eat every touch as page interaction).
                Box(
                    Modifier.fillMaxSize().pointerInput(maxW, maxH) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            ax.stop()
                            ay.stop()
                            var movedPx = 0f
                            var up = false
                            while (!up) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                    ?: ev.changes.firstOrNull() ?: break
                                if (ch.changedToUpOrCanceled()) {
                                    up = true
                                } else {
                                    val d = ch.positionChange()
                                    if (d != Offset.Zero) {
                                        movedPx += d.getDistance()
                                        if (movedPx > viewConfiguration.touchSlop) {
                                            hasCustomPos = true
                                            ax.snapTo(
                                                (ax.value + d.x.toDp())
                                                    .coerceIn(0.dp, maxW - WatchMiniWidth),
                                            )
                                            ay.snapTo(
                                                (ay.value + d.y.toDp())
                                                    .coerceIn(0.dp, maxH - WatchMiniHeight),
                                            )
                                        }
                                        ch.consume()
                                    }
                                }
                            }
                            if (movedPx <= viewConfiguration.touchSlop) {
                                // Tap (not a drag): back to the Watch tab, expanded.
                                AppState.goTik(2)
                            } else {
                                // Release: spring to the nearest horizontal edge.
                                val targetX = if (ax.value + WatchMiniWidth / 2 < maxW / 2) {
                                    WatchMiniEdgeMargin
                                } else {
                                    maxW - WatchMiniWidth - WatchMiniEdgeMargin
                                }
                                ax.animateTo(targetX, spring(dampingRatio = 0.78f, stiffness = 420f))
                            }
                        }
                    },
                )
                // Mini ✕ — FULL stop (owner's rule), same as the bar's ✕.
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.62f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .press { WatchControl.stop() },
                    contentAlignment = Alignment.Center,
                ) { TikIcon("x", Modifier.size(13.dp), tint = Color.White) }
            }
        }
    }
}

/**
 * "Now watching" — the slim glass bar. Shows on other Tik tabs while a
 * Watch session exists, directly BELOW the floating mini window
 * (WatchHost rests 8.dp above it); play jumps back to the Watch tab
 * (host expands around the same live pane), × STOPS the video
 * completely (WatchControl.stop — pane halt + session clear), per the
 * owner's rule. Rendered by MainActivity above the glass nav.
 */
@Composable
fun NowWatchingBar(modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val show = WatchSession.hasSession &&
        AppState.homeTab != HomeTab.WATCH &&
        AppState.screen is Screen.Home &&
        ChatSession.unlocked
    if (!show) return
    val thumbId = youtubeThumbId(WatchSession.lastUrl)
    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(22.dp), useLargeGlass = true, blur = true)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (thumbId != null) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/$thumbId/hqdefault.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(pal.s2),
                contentAlignment = Alignment.Center,
            ) { TikIcon("play", Modifier.size(18.dp), tint = pal.tx) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Now watching",
                fontFamily = InterTight, fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp, color = pal.sub,
            )
            Text(
                WatchSession.lastTitle.ifBlank { "YouTube" },
                fontFamily = InterTight, fontWeight = FontWeight.Medium,
                fontSize = 13.sp, color = pal.tx,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(pal.ac)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // goTik(2) flips the host to EXPANDED around the same
                    // live pane — no fresh pane, no reload.
                    AppState.goTik(2)
                    AppState.go(Screen.Home)
                },
            contentAlignment = Alignment.Center,
        ) { TikIcon("playFill", Modifier.size(17.dp), tint = Color.White) }
        Box(
            Modifier.size(36.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // Owner's rule: closing from the chat side STOPS the
                    // video completely — halt the pane, then clear.
                    WatchControl.stop()
                },
            contentAlignment = Alignment.Center,
        ) { TikIcon("x", Modifier.size(17.dp), tint = pal.sub) }
    }
}
