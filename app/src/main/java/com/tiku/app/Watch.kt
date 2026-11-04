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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// ROUND 13 — WATCH, REBUILT. The build-44 design kept ONE singleton WebView
// and re-parented it between three hosts (pane, floating mini player,
// fullscreen) while HomeScreen crossfaded panes with AnimatedContent — two
// compositions overlapped during every tab change and fought over the same
// view; closing Watch and reopening it crashed. That pattern is DELETED.
//
// ROUND 14 — browsing/search stayed on m.youtube, but a /watch page itself
// was never rendered — the pane swapped in the locked youtube-nocookie
// embed (see loadYoutubeEmbed) and locked navigation to it until the user
// left via the header "+" (WatchControl.goHome) or the pane was destroyed.
// ROUND 17 SUPERSEDES that for THIS pane (owner order, 2026-11-03, with a
// Brave screenshot of the real watch page as the reference): a tapped
// video now plays on the REAL m.youtube watch page, exactly like a
// browser — full-width player, true fullscreen, scrollable title /
// actions / comments / related. No embed swap, no embed lockdown; the
// browse-phase navigation policy (YouTube hosts + the Google sign-in
// flow; everything else refused) now applies on every page. The locked
// embed survives ONLY in the facade drama player (DramaShell.kt).
//
// ROUND 14 ADDENDUM 2 §5 — the FLOATING MINI PLAYER, crash-safe by
// construction: the pane's WebView now lives in ONE root-level host
// (WatchHost) for the whole Tik session. It is NEVER re-parented,
// attached or detached on a tab switch — the host changes only the
// bounds of the single slot that holds it: EXPANDED over the Watch
// tab's content area, or a small draggable MINI window on the other
// tabs while a session is alive, so a playing video simply keeps
// playing (ROUND 19: the bounds snap in one step and a scale
// transform glides over them — an animated resize storm used to
// strand the video surface). The pane itself is composed only while
// (Watch tab || session alive); with neither, there is nothing to
// preserve and the host renders nothing. Fullscreen is the one
// exception that is NOT
// the WebView: the page-supplied custom view is rendered by
// WatchFullscreenLayer, so it is never clipped by the host's bounds.
// Lock / quick-exit still clear the session (SessionLock), which
// uncomposes the host and destroys the WebView outright, exactly as
// before.
//
// ROUND 15 (owner device test, build 53) — three root fixes:
// (1) BLACK VIDEO: the pane's WebView was built with the APPLICATION
// context and playback ran through a loadDataWithBaseURL document
// wrapping a cross-origin iframe — on the owner's phone the audio
// played but the video surface stayed black and the title never
// arrived. The WebView now gets the ACTIVITY context and the embed
// loads DIRECTLY (loadUrl of the nocookie embed URL, no wrapper).
// (2) FROZEN SURFACE: MainActivity.onPause pauses the WebView via
// WatchControl, but nothing ever called onResume — after any
// backgrounding the renderer stayed paused. Resume is now paired:
// activity resume unfreezes the view, expanding the pane also plays.
// (3) MINI OVER CHAT: WatchHost moved from HomeScreen to TikuRoot
// (MainActivity), above the Chat/Details/Moments overlays and below
// the call UI + sheets — the mini now floats over chats too and only
// disappears on lock / full stop / an active call.
//
// ROUND 17 (owner order, 2026-11-03) — THE REAL WATCH PAGE. The locked
// nocookie embed played "chota hoke" (shrunk, no page around it); the
// owner wants YouTube kept exactly as his Brave screenshot shows it.
// The /watch → embed swap and the embed-phase lockdown are REMOVED
// from this pane: /watch loads the real m.youtube watch page inside
// the browse navigation policy, the session records the /watch URL,
// Back walks the WebView's own YouTube history before the shell's
// exit behaviour, and HTML5 fullscreen runs through the existing
// WatchFullscreen layer. The embed helpers below remain ONLY because
// the facade drama player (DramaShell.kt) still builds on them.
//
// ROUND 19 (owner device test, build 62) — THE FLOATING PLAYER,
// REBUILT. His report: the video did not play inside the floating
// mini window, and leaving YouTube (Back / tab switch) stopped
// playback entirely with no floating window at all. Three causes,
// all in this file:
// (1) BACK NEVER MINIMIZED. The pane's only Back handler walked the
//     WebView's history; with no history the press fell through to
//     HomeScreen's quick-exit handler, which clears the session and
//     destroys the WebView — and WITH history it navigated away from
//     the video in place. Back on a playable page now arms MINI mode
//     (WatchSession.forceMini) — YouTube-app behaviour — and history
//     walking remains for browse/search pages only (below).
// (2) THE SHRINK WAS A RESIZE STORM. The host animated the pane
//     container's real layout size for 340 ms, re-measuring the
//     WebView at every intermediate size; the video surface ended
//     stranded (black/frozen in the mini), and nothing re-asserted
//     playback or brought the player into view — the mini showed the
//     watch page at whatever scroll offset the user had left, player
//     off-screen. The host now snaps the layout to the target size in
//     ONE step (a single relayout, which WebView playback survives)
//     and runs the visible glide as a graphicsLayer scale transform
//     over it; entering MINI re-asserts play and scrolls the page to
//     the top, so the 16:9 player is exactly what the mini shows.
// (3) SESSION DETECTION WAS /watch-ONLY. Shorts and live pages never
//     raised hasSession, so a tab switch uncomposed the host and
//     destroyed the WebView mid-playback. playablePageId() now covers
//     /watch, /shorts and /live.
//
// ROUND 20 (owner device test, build 63) — THE MINI STILL PAUSED.
// Round 19's re-assert was ONE play() call issued from the pane's
// mode effect in the same composition pass that snapped the host
// layout; the WebView's new video surface settles ASYNCHRONOUSLY
// after that relayout, and the pause the resize provokes inside
// Chromium lands after the single play() — nothing in the file ever
// pressed play again, so the mini sat paused forever (and its
// full-size gesture layer eats taps, so the owner could not unpause
// it in place either). Two more code-level holes fed the same
// symptom: (a) every JS pause + renderer freeze (MainActivity
// .onPause, WatchHost on call start) is answered only by
// resumePlayback, which deliberately resumes with autoplay = FALSE
// — and on call END nothing resumed the pane at all; (b) entering
// MINI scrolled only the WINDOW (window.scrollTo(0,0)) and never
// brought the video ELEMENT into view, so on the real watch page
// the player could sit outside the mini's 117dp viewport. The fix
// is structural, not another one-shot: a JS MINI-MODE WATCHDOG
// (setWatchMiniGuard) that makes pause effectively impossible while
// the pane is minimized — a 'pause' listener that immediately
// replays, backed by a 700 ms interval that re-arms itself on the
// live video element across SPA element swaps and nudges any paused
// element — plus timed Kotlin re-asserts across the surface-settle
// window, an element-based scroll-into-view, watchdog re-arming on
// every page (re)load, and a revive hook for the call-end path.
// EXPANDED mode disarms the watchdog: the owner's own pause rules.
//
// ROUND 21 (owner device test, build 64) — THE MINI WAS A SLIVER.
// His screenshot: a tall narrow white window stuck to one side, the
// page squished into part of it, no video properly visible — and
// tapping it did not reliably expand. Three structural defects:
// (1) GEOMETRY BY TRANSFORM: the window the owner SEES was the
//     208x117 layout POST-MULTIPLIED by the Round-19 graphicsLayer
//     scale glide. Rest state depended on a 340 ms Animatable
//     effect landing exactly on identity through every mode flip;
//     off identity, layout stayed 16:9 while the painted rect did
//     not (tall + narrow + parked mid-glide) and the page — laid
//     out for the true box — stretched inside it. FIX: the scale
//     glide is DELETED (no transform but hideMini's alpha remains),
//     and the mini size is a formula of the live constraints —
//     width 58% of the screen, clamped [190, 240].dp, height
//     width * 9/16 — resting bottom-end, 12.dp end margin, bottom
//     clearance NavBlock + 12 (stacking above the "Now watching"
//     bar where that bar shows). Painted rect ≡ layout rect.
// (2) NOTHING RE-ASSERTED THE WEBVIEW'S SIZE: the pane's AndroidView
//     had no update block. FIX: MATCH_PARENT layout params set at
//     build time and re-applied by the AndroidView update block on
//     every recomposition, mode changes included.
// (3) THE MINI FLOATED WHATEVER PAGE WAS LIVE: his sliver showed an
//     ad-landing page, not the player. FIX: WatchSession records
//     lastPlayableUrl (playable pages only); entering MINI with the
//     pane on a non-playable document restores that page, and the
//     framing JS falls back to the player container when no <video>
//     element exists yet. The Round-20 pause watchdog is untouched.
// TAP: the two sibling gesture detectors (tap vs drag racing over
// one stream) are replaced by ONE awaitEachGesture state machine —
// release within touch slop expands, past slop drags — so a tap
// expands deterministically, including after drags.
// ============================================================================

/** Plain session state for Watch — NO View references, ever. */
object WatchSession {
    const val HOME_URL = "https://m.youtube.com"

    var lastUrl by mutableStateOf(HOME_URL)

    /** ROUND 21: the last PLAYABLE page (/watch, /shorts, /live) the
     *  pane was on — unlike [lastUrl], browse/search/channel pages
     *  never overwrite it. Entering MINI restores this page when the
     *  pane has drifted onto a non-playable document (the owner's
     *  build-64 mini floated an ad-landing page instead of the
     *  video): the floating window exists to show the VIDEO. */
    var lastPlayableUrl by mutableStateOf("")
    var lastTitle by mutableStateOf("")
    var hasSession by mutableStateOf(false)

    /** ROUND 19: Back on a playable page (or any explicit minimize)
     *  arms this: WatchHost renders MINI even while the Watch tab is
     *  still the selected destination, floating the playing video
     *  over the Watch screen until the mini is tapped (expand) or
     *  closed (full stop). Plain session state — no View references. */
    var forceMini by mutableStateOf(false)

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
        if (playablePageId(url) != null) lastPlayableUrl = url
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
        lastPlayableUrl = ""
        lastTitle = ""
        hasSession = false
        forceMini = false
    }
}

/** Tiny registered controller so chrome outside the pane (the Watch header
 *  "+" button, MainActivity.onPause/onResume) can reach the ACTIVE pane's
 *  WebView. All lambdas are non-null only while a WatchPane is composed. */
object WatchControl {
    var navigate: ((String) -> Unit)? = null
    var pausePlayback: (() -> Unit)? = null
    var resumePlayback: (() -> Unit)? = null
    var stopPlayback: (() -> Unit)? = null

    /** ROUND 20: registered by the pane; invoked by WatchHost when a
     *  call ENDS. The host froze + JS-paused the pane when the call
     *  started and nothing ever revived it afterwards (the only
     *  resume hook answers with autoplay = false) — a mini left over
     *  a call came back visible but paused for good. The pane's
     *  implementation acts only while it is in MINI mode: re-arm the
     *  watchdog, unfreeze, play, re-frame the player. */
    var miniRevive: (() -> Unit)? = null

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
        // Heading home is an EXPANDED-mode intent: drop any
        // Back-minimize so the pane shows the navigation full-size.
        WatchSession.forceMini = false
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

    /** Activity resume: the symmetric half of [requestPause] (ROUND 15) —
     *  unfreeze the WebView's renderer. Deliberately does NOT force the
     *  video to play: if the JS pause landed, the player shows its own
     *  play button; EXPANDING the pane (tap on the mini / the bar) is
     *  the user's "watch now" gesture and plays via the pane itself.
     *  ROUND 20: in MINI mode the watchdog (armed on the page) does
     *  force play once the renderer unfreezes — a minimized video is
     *  never the user's deliberate pause. */
    fun requestResume() {
        resumePlayback?.invoke()
    }

    /** ROUND 20: a call just ended — bring a MINI pane back to life
     *  (see [miniRevive]). No-op in EXPANDED mode and after a stop. */
    fun requestMiniRevive() {
        miniRevive?.invoke()
    }
}

/** Fullscreen custom-view state, hoisted OUT of the pane: TikuRoot
 *  renders [WatchFullscreenLayer] right beside WatchHost (above the
 *  shell and its overlays), so a fullscreen video is never clipped by
 *  WatchHost's animated bounds. The WebView itself never moves — only
 *  this ephemeral page-supplied view gets a top-level home, the same
 *  job the pane's own box did in build 47 before the host existed. */
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

/** ROUND 19 — ANY page that plays a video: /watch?v=…, /shorts/<id>,
 *  /live/<id>. Build 62 detected only /watch, so a Short or a live
 *  stream never raised the session (WatchSession.hasSession) and
 *  leaving the tab destroyed the pane mid-playback. Every playable
 *  page must raise the session — that is what keeps WatchHost
 *  composing the pane (and the floating mini alive) across a tab
 *  switch. */
private fun playablePageId(url: String?): String? {
    watchPageVideoId(url)?.let { return it }
    if (url.isNullOrBlank()) return null
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (!host.endsWith("youtube.com")) return null
    val seg = uri.pathSegments
    if (seg.size >= 2 && (seg[0] == "shorts" || seg[0] == "live")) {
        val id = seg[1]
        if (id.length >= 6 && id.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }) return id
    }
    return null
}

/** The locked embed's URL prefix — the one main-frame target the
 *  FACADE player's WebViewClient allows while its embed is up
 *  (DramaShell.kt). The Tik Watch pane has no embed phase any more. */
internal const val YOUTUBE_EMBED_PREFIX = "https://www.youtube-nocookie.com/embed/"

/**
 * The LOCKED YouTube embed — ROUND 17: used ONLY by the facade drama
 * player (DramaShell.kt), which keeps its own locked-embed order. The
 * Tik Watch pane no longer touches these helpers: it plays the real
 * m.youtube watch page (see the file header). Kept byte-identical —
 * DramaShell.kt builds on them.
 *
 * ROUND 15: loaded DIRECTLY with loadUrl. The Round-14 route wrapped
 * the embed in a local loadDataWithBaseURL document as an iframe; on
 * the owner's phone that produced audio with a permanently black
 * video surface (and no title). A top-level embed load is the route
 * YouTube's own player is built for.
 */
internal fun youtubeEmbedUrl(videoId: String): String =
    YOUTUBE_EMBED_PREFIX + videoId +
        "?autoplay=1&playsinline=1&rel=0&modestbranding=1"

internal fun loadYoutubeEmbed(wv: WebView, videoId: String) {
    wv.loadUrl(youtubeEmbedUrl(videoId))
}

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

/** ROUND 21 — the mini window's size is a FORMULA, never a frozen
 *  constant: width = 58% of the host's width clamped to
 *  [WatchMiniMinWidth, WatchMiniMaxWidth], height = width * 9/16,
 *  computed inside WatchHost from its live constraints. The old
 *  frozen 208x117 pair is retired (the host still renders an exact
 *  16:9 box — now sized to the actual phone). */
val WatchMiniWidthFraction = 0.58f
val WatchMiniMinWidth = 190.dp
val WatchMiniMaxWidth = 240.dp
val WatchMiniCorner = 12.dp
val WatchMiniEdgeMargin = 12.dp

/** The Tik NavPill's total bottom block (72.dp pill + 14.dp column
 *  padding). ROUND 15 device fix: the EXPANDED host is BOUNDED above
 *  this band instead of filling to the screen edge — on the owner's
 *  device the WebView's surface paints over sibling Compose UI
 *  regardless of z-order, so a full-height WebView hides the NavPill
 *  completely on the Watch tab. A bounded WebView can never cover it. */
val WatchNavBlock = 86.dp

/** NowWatchingBar's bottom inset in TikuRoot: the NavPill's top edge
 *  sits 86.dp from the bottom (72.dp pill + 14.dp column padding), so
 *  the bar floats 8.dp above it. Bar height = 36.dp thumbnail + 8+8
 *  vertical padding = 52.dp, i.e. its top edge is at 146.dp. */
val NowWatchingBarBottomInset = 94.dp

/** The mini window's resting bottom clearance: bar top (146) + 8 gap —
 *  the mini and the bar coexist on chat tabs and never overlap. */
val WatchMiniBottomClearance = 154.dp

/** ROUND 17 — the pane's ONE navigation policy, on browse and watch
 *  pages alike: a main-frame load may go to YouTube itself or into the
 *  Google sign-in flow (accounts.google.com — where youtube.com/signin
 *  lands); every other main-frame target is refused — external http(s)
 *  hosts, and app-intent schemes (intent://, market://, tel:, …)
 *  alike. This is the Round-13/14 browse policy carried through
 *  unchanged; Round 17 only retired the SECOND policy (the embed-phase
 *  lockdown) that used to override it while a video played. */
private fun watchNavAllowed(url: android.net.Uri?): Boolean {
    if (url == null) return false
    val scheme = url.scheme?.lowercase() ?: return false
    if (scheme == "about") return true
    if (scheme != "http" && scheme != "https") return false
    val host = url.host?.lowercase() ?: return false
    return host == "youtu.be" ||
        host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com") ||
        host == "accounts.google.com"
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildWatchWebView(
    ctx: Context,
    startUrl: String,
    onPage: (String) -> Unit,
    onWatchPage: (String) -> Unit,
    onTitle: (String) -> Unit,
    onShowCustom: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustom: () -> Unit,
    onRendererGone: () -> Unit,
    onHistory: (Boolean) -> Unit,
    /** ROUND 20: fired with the live view whenever a document has
     *  (re)loaded or history updated — the pane re-arms the mini
     *  watchdog there, because a fresh document wipes the JS state
     *  (flag + installer) the watchdog lives in. */
    onPageReady: (WebView) -> Unit = {},
): WebView {
    val wv = WebView(ctx)
    // ROUND 21: the WebView is born MATCH_PARENT and the pane's
    // AndroidView update block re-asserts it on every mode change —
    // the view can never sit in the host box at a stale size.
    wv.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
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
        // ROUND 17 — the real watch page: a /watch navigation is NOT
        // intercepted any more; the WebView loads the actual
        // m.youtube.com/watch page (big full-width player, scrollable
        // title / actions / comments / related), exactly like the
        // owner's Brave reference. The only interception left is the
        // standing browse policy (watchNavAllowed): YouTube hosts +
        // the Google sign-in flow pass; external hosts and app-intent
        // schemes are refused. Only MAIN-FRAME requests are ever
        // judged — the page's own subframe/subresource traffic is its
        // business, never ours to block.
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val req = request ?: return false
            if (!req.isForMainFrame) return false
            return !watchNavAllowed(req.url)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            // m.youtube is an SPA: tapping a video changes the URL via
            // the History API without a full load — session tracking
            // for in-page taps happens here. Reaching a /watch page
            // (however it was reached) raises the live session and
            // records the /watch URL itself for the bar / resume.
            if (!url.isNullOrBlank()) {
                if (playablePageId(url) != null) onWatchPage(url) else onPage(url)
            }
            onHistory(view?.canGoBack() == true)
            // SPA transitions land here without a fresh document —
            // re-arming is idempotent and keeps the watchdog bound
            // to whatever video element the SPA swapped in.
            view?.let { onPageReady(it) }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (url.isNullOrBlank()) return
            // Fresh load / resume straight onto a page: same dispatch
            // as the SPA path — a /watch URL records as the session's
            // watch page, everything else as a plain page.
            if (playablePageId(url) != null) onWatchPage(url) else onPage(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            // Keep the pane's Back enablement honest after real loads
            // (SPA transitions report through doUpdateVisitedHistory).
            onHistory(view?.canGoBack() == true)
            // A fresh document wiped the watchdog's JS state — the
            // pane re-arms it for the mode it is currently in.
            view?.let { onPageReady(it) }
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
            // The watch page's document title IS the video's title
            // ("<title> - YouTube") — strip the suffix, so the "Now
            // watching" bar shows the video's name, not the site
            // chrome. The bare "YouTube" of the browse home is ignored.
            val t = title?.removeSuffix(" - YouTube")?.trim().orEmpty()
            if (t.isNotBlank() && t != "YouTube") onTitle(t)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view != null && callback != null) onShowCustom(view, callback)
        }

        override fun onHideCustomView() {
            onHideCustom()
        }
    }
    // The session only ever records http(s) URLs, but a pane must never
    // sit on a blank page: fall back to YouTube home rather than nothing.
    wv.loadUrl(if (startUrl.startsWith("http")) startUrl else WatchSession.HOME_URL)
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

/** ROUND 15 — the symmetric half of [pauseWatchWebView]. Round 14
 *  paused the WebView (MainActivity.onPause) and never resumed it, so
 *  after any backgrounding the video surface stayed frozen/black while
 *  the page lived on. [autoplay] additionally presses play on the
 *  player — used when the user EXPANDS the pane, the explicit "watch
 *  now" gesture; a plain activity resume only unfreezes the renderer
 *  and leaves a deliberately paused video paused. */
private fun resumeWatchWebView(wv: WebView, autoplay: Boolean) {
    runCatching { wv.onResume() }
    if (autoplay) {
        runCatching {
            wv.evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v&&v.paused){v.play();}})();",
                null,
            )
        }
    }
}

// ================= ROUND 20 — the MINI-mode watchdog =================
// Round 19 re-asserted play exactly ONCE on entering the mini, from
// the same composition pass that snapped the host layout. The
// WebView's video surface settles asynchronously AFTER that
// relayout, and the pause the resize provokes inside Chromium (the
// classic interrupted play() promise) therefore landed after our
// only play() — grep the file: there was no retry, no observer, no
// second call site. The mini then sat paused forever, and because
// the host's gesture layer covers the mini, the owner could not
// even tap the video to unpause it in place.
//
// The watchdog makes pause structurally impossible while the pane
// is in MINI mode and its session is alive:
// - a 'pause' listener on the video element replays ~60 ms later
//   (the delay lets a deliberate EXPANDED pause settle — impossible
//   in mini anyway — and lets an ended video stay ended: the nudge
//   checks `ended` and the mode flag at fire time);
// - a 700 ms interval re-queries the CURRENT video element every
//   tick (m.youtube's SPA swaps elements without a document reload),
//   arms the listener on any fresh element, and nudges any paused
//   one — so element swaps, missed events and rejected play()
//   promises all self-heal within a tick;
// - the mode flag lives on `window.__tikuMiniWanted`: Kotlin sets
//   it on every mode change and on every page (re)load (a fresh
//   document wipes it together with the installer). EXPANDED sets
//   it false — the owner's own pause is respected there.
// While the WebView is renderer-paused (a live call, backgrounding)
// JS timers freeze, so the watchdog is inert exactly when it must
// be; it resumes nudging the moment the renderer unfreezes.
private const val WATCH_MINI_GUARD_JS = """
(function(){
  if (window.__tikuGuardInstalled) return;
  window.__tikuGuardInstalled = true;
  function cur(){ try { return document.querySelector('video'); } catch(e) { return null; } }
  function nudge(){
    if (!window.__tikuMiniWanted) return;
    var v = cur();
    if (v && v.paused && !v.ended) {
      try { var p = v.play(); if (p && p.catch) p.catch(function(){}); } catch(e) {}
    }
  }
  function arm(v){
    if (!v || v.__tikuArmed) return;
    v.__tikuArmed = true;
    v.addEventListener('pause', function(){ setTimeout(nudge, 60); });
    v.addEventListener('loadeddata', function(){ setTimeout(nudge, 60); });
  }
  setInterval(function(){ var v = cur(); if (v) arm(v); nudge(); }, 700);
  var v0 = cur(); if (v0) arm(v0);
})();
"""

/** Arm (or disarm) the MINI watchdog on the pane's current page.
 *  Setting the flag and (re)installing are separate evals on
 *  purpose: a fresh document has neither, a live one keeps its
 *  installer and only needs the flag flipped. Fully idempotent. */
private fun setWatchMiniGuard(wv: WebView, on: Boolean) {
    runCatching { wv.evaluateJavascript("window.__tikuMiniWanted = $on;", null) }
    runCatching { wv.evaluateJavascript(WATCH_MINI_GUARD_JS, null) }
}

/** ROUND 20 — frame the PLAYER inside the mini. Round 19 scrolled
 *  only the window (`window.scrollTo(0,0)`); on the real watch page
 *  the scroll offset does not always live on the window (the SPA's
 *  own scroll containers, YouTube's docked-player state), so the
 *  player could sit below the mini's 117dp viewport — "video waha
 *  pe dikhe" failed even with the right page loaded. Scroll the
 *  video ELEMENT into view instead (scrollIntoView walks every
 *  scrollable ancestor), then correct any residual offset from the
 *  element's own bounding rect. */
private fun scrollWatchPlayerIntoView(wv: WebView) {
    runCatching {
        wv.evaluateJavascript(
            """
            (function(){
              try { window.scrollTo(0, 0); } catch(e) {}
              var v = null;
              try { v = document.querySelector('video'); } catch(e) {}
              if (!v) {
                // ROUND 21: no <video> element yet (page still
                // settling) — frame the PLAYER CONTAINER instead, so
                // the mini is aimed at the player region either way.
                try { v = document.querySelector('#movie_player, .html5-video-player, #player-container-id, #player'); } catch(e) {}
              }
              if (v) {
                try { v.scrollIntoView({ block: 'start', inline: 'nearest' }); }
                catch(e) { try { v.scrollIntoView(); } catch(e2) {} }
                try {
                  var r = v.getBoundingClientRect();
                  if (r.top > 1 || r.top < -1) window.scrollBy(0, r.top);
                } catch(e) {}
              }
            })();
            """.trimIndent(),
            null,
        )
    }
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
fun WatchPane(chromeInsets: Boolean = true, boundedAboveNav: Boolean = false) {
    // When the host bounds the pane above the NavPill band, YouTube's own
    // bottom nav already sits WatchNavBlock above the screen edge — only
    // a breath of internal inset remains. Unbounded (the harness's direct
    // call) keeps the build-47 100.dp inset.
    val bottomInset = if (!chromeInsets) 0.dp else if (boundedAboveNav) 12.dp else WatchBottomInset
    val pal = LocalPalette.current
    if (Config.PREVIEW_UI_ONLY) {
        // Screenshot/preview branch: no WebView (Robolectric has no real
        // renderer). Round-12 copy kept so the suite's text checks hold.
        Box(
            Modifier.fillMaxSize().background(pal.bg)
                .then(if (chromeInsets) Modifier.padding(bottom = bottomInset) else Modifier),
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
    // ROUND 20: the WebView callbacks below are captured by
    // remember(healGen) and must still read the pane's CURRENT mode
    // long after that composition — rememberUpdatedState is the
    // bridge (a healed WebView must not inherit a stale mode).
    val chromeInsetsState = rememberUpdatedState(chromeInsets)
    val paneEpoch = remember { WatchSession.epoch }
    var currentUrl by remember { mutableStateOf(WatchSession.lastUrl) }
    var pageTitle by remember { mutableStateOf(WatchSession.lastTitle) }
    var healGen by remember { mutableIntStateOf(0) }
    // ROUND 17 — no embed state at all: the pane renders whatever
    // YouTube page it is on. [webCanGoBack] mirrors the live WebView's
    // history (reported via the builder's onHistory) and arms the
    // pane's browser-like Back handler below.
    var webCanGoBack by remember { mutableStateOf(false) }
    // ROUND 19: true while the pane sits on a playable page (/watch,
    // /shorts, /live) — a resumed pane starts on its recorded session
    // URL, so seed from it. Drives the Back-minimizes rule below.
    var onPlayablePage by remember {
        mutableStateOf(playablePageId(WatchSession.lastUrl) != null)
    }

    val webView = remember(healGen) {
        buildWatchWebView(
            // ROUND 15: the ACTIVITY context, not applicationContext —
            // an application-context WebView is a known cause of black
            // HTML5 video surfaces and broken fullscreen on real devices.
            ctx = ctx,
            startUrl = currentUrl,
            onPage = { url ->
                currentUrl = url
                onPlayablePage = false
                WatchSession.record(url, pageTitle, paneEpoch)
            },
            onWatchPage = { url ->
                currentUrl = url
                onPlayablePage = true
                WatchSession.record(url, pageTitle, paneEpoch)
                // A video page is actually up: the session is live
                // right now, not only when the pane leaves. This is
                // what keeps WatchHost composing this pane across a
                // tab switch, so the mini window takes over with the
                // SAME WebView — playback never restarts. (This is the
                // Round-14 embed swap's old job, kept for the real
                // watch page.)
                if (paneEpoch == WatchSession.epoch) WatchSession.hasSession = true
            },
            onHistory = { webCanGoBack = it },
            onPageReady = { wv ->
                // ROUND 20: a (re)loaded document wiped the mini
                // watchdog's JS state — re-arm it for the mode the
                // pane is in NOW (read through chromeInsetsState:
                // this lambda was captured when the WebView was
                // built and must not freeze an old mode). In MINI,
                // a navigation inside the session also re-asserts
                // playback and re-frames the player.
                val mini = !chromeInsetsState.value
                setWatchMiniGuard(wv, on = mini)
                if (mini) {
                    resumeWatchWebView(wv, autoplay = true)
                    scrollWatchPlayerIntoView(wv)
                }
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
        )
    }

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
    // ROUND 19 — Back, three tiers, all gated to EXPANDED mode
    // (chromeInsets) so the floating mini never steals Back from the
    // tab / chat underneath it:
    // (1) MINIMIZE — on a playable page, Back shrinks the playing
    //     video into the floating mini player (YouTube-app behaviour;
    //     the owner's Round-14 design). Build 62 had NO such path:
    //     Back either walked the WebView history out from under the
    //     video (the video died in place) or, with no history left,
    //     fell through to HomeScreen's quick-exit lock — session
    //     cleared, WebView destroyed, no floating window. The press
    //     now only arms WatchSession.forceMini; WatchHost renders
    //     MINI around this same live pane.
    // (2) HISTORY — on browse / search / channel pages the Round-17
    //     browser-like walk is unchanged.
    // (3) FULLSCREEN exit — declared last so it keeps priority over
    //     both (the dispatcher's most recent enabled handler wins).
    // With none enabled (browse home, no history), the press falls
    // through to the shell's quick-exit lock, as before.
    BackHandler(enabled = chromeInsets && onPlayablePage && WatchFullscreen.view == null) {
        // A playable page IS a live session — raise it explicitly so
        // the host can never uncompose the pane out from under the
        // mini this press is creating.
        WatchSession.hasSession = true
        WatchSession.forceMini = true
    }
    BackHandler(
        enabled = chromeInsets && webCanGoBack && !onPlayablePage &&
            WatchFullscreen.view == null,
    ) {
        runCatching { webView.goBack() }
    }
    BackHandler(enabled = WatchFullscreen.view != null) { exitFullscreen() }

    // ROUND 15: expanding the pane (Watch tab, or a tap on the mini /
    // the "Now watching" bar) is the user's "watch now" gesture —
    // unfreeze the renderer AND play. ROUND 19: entering the MINI is
    // the symmetric "keep watching" gesture — the floating window
    // exists so the video KEEPS PLAYING in it. Re-assert play (a
    // surface stalled by the bounds change revives on the play call)
    // and scroll the page to the top: the pane is laid out at the
    // mini's own size, so the top of the watch page is the 16:9
    // player itself — the mini shows the VIDEO, not whatever section
    // (comments, related) the user had scrolled to.
    // ROUND 20: that single re-assert was not enough on device (see
    // the watchdog block above): the pause provoked by the relayout
    // lands AFTER any one play() issued here. Entering MINI now
    // (a) arms the JS watchdog, which keeps the video playing for
    //     the whole minimized session on its own;
    // (b) re-asserts across the surface-settle window (~2 s of
    //     timed retries), covering the watchdog's own install race;
    // (c) frames the player by scrolling the video ELEMENT into
    //     view, not just the window.
    // Entering EXPANDED disarms the watchdog first — the owner's
    // own pause is law there — then plays as before. The effect is
    // keyed on the mode, so flipping to EXPANDED cancels any
    // in-flight mini re-assert loop.
    LaunchedEffect(chromeInsets, webView) {
        if (chromeInsets) {
            setWatchMiniGuard(webView, on = false)
            resumeWatchWebView(webView, autoplay = true)
        } else {
            // ROUND 21: the mini shows THE VIDEO — if the pane has
            // drifted onto a non-playable document (his build-64 mini
            // floated an ad-landing page), restore the last playable
            // page before framing/watchdog work begins. The restore
            // load re-fires onPageReady, which re-arms + re-frames.
            val recordedPlayable = WatchSession.lastPlayableUrl
            val liveUrl = runCatching { webView.url }.getOrNull() ?: currentUrl
            if (!onPlayablePage && recordedPlayable.isNotBlank() && recordedPlayable != liveUrl) {
                runCatching { webView.loadUrl(recordedPlayable) }
            }
            setWatchMiniGuard(webView, on = true)
            resumeWatchWebView(webView, autoplay = true)
            scrollWatchPlayerIntoView(webView)
            for (stepMs in listOf(300L, 600L, 1000L)) {
                delay(stepMs)
                setWatchMiniGuard(webView, on = true)
                resumeWatchWebView(webView, autoplay = true)
                scrollWatchPlayerIntoView(webView)
            }
        }
    }

    // A healed WebView starts with an empty history; its first page
    // commit re-reports the real value through onHistory.
    LaunchedEffect(webView) { webCanGoBack = false }

    DisposableEffect(webView) {
        WatchControl.navigate = { url ->
            // The header home button (WatchControl.goHome) routes here:
            // load the requested page (YouTube browse home) in the
            // live pane — an ordinary in-place navigation now.
            runCatching { webView.loadUrl(url) }
        }
        WatchControl.pausePlayback = { pauseWatchWebView(webView) }
        WatchControl.resumePlayback = { resumeWatchWebView(webView, autoplay = false) }
        WatchControl.miniRevive = {
            // ROUND 20: WatchHost froze + JS-paused this pane when a
            // call started; the call has now ended. In MINI mode the
            // video must come back PLAYING (watchdog re-armed,
            // renderer unfrozen, play pressed, player re-framed) —
            // before this hook the mini returned visible but paused
            // for good. In EXPANDED mode the owner's pause rules.
            if (!chromeInsetsState.value) {
                setWatchMiniGuard(webView, on = true)
                resumeWatchWebView(webView, autoplay = true)
                scrollWatchPlayerIntoView(webView)
            }
        }
        WatchControl.stopPlayback = {
            // FULL stop (mini ✕ / bar ✕ via WatchControl.stop): kill the
            // page + playback NOW; WatchSession.clear() right after makes
            // the host uncompose this pane, whose dispose destroys the
            // WebView — recordOnLeave there is epoch-guarded and ignored.
            runCatching { webView.stopLoading() }
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.onPause() }
        }
        onDispose {
            if (WatchControl.navigate != null) WatchControl.navigate = null
            if (WatchControl.pausePlayback != null) WatchControl.pausePlayback = null
            if (WatchControl.resumePlayback != null) WatchControl.resumePlayback = null
            if (WatchControl.stopPlayback != null) WatchControl.stopPlayback = null
            if (WatchControl.miniRevive != null) WatchControl.miniRevive = null
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
            .then(if (chromeInsets) Modifier.padding(bottom = bottomInset) else Modifier),
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // ROUND 21: re-asserted on EVERY recomposition (mode
                // changes included) — the WebView's layout params are
                // pinned to the pane box by construction, so a mode
                // snap can never leave the view at the other mode's
                // size inside the right box.
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            },
        )
    }
}

/**
 * The fullscreen surface for the Watch WebView's custom view, rendered
 * by TikuRoot beside WatchHost — the video gets the whole Tik area
 * instead of WatchHost's current bounds (which may be the 208x117 mini
 * window when fullscreen is requested from it).
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
 * ROUND 14 Addendum 2 §5 — the floating-player host. [WatchPane] is
 * called from exactly ONE call site inside it, so the WebView is
 * created once and destroyed only when the Tik session ends (lock /
 * quick-exit / full stop). Mode changes never move the WebView
 * between parents (the Round 11/13 crash lesson); since Round 19
 * they also never animate its layout — the host snaps the slot to
 * the target size and glides a scale transform over it.
 *
 * ROUND 15: the host is composed by TikuRoot (MainActivity) in the
 * home branch's Box — ABOVE HomeScreen and the Chat / ChatDetails /
 * Moments overlays, BELOW the call UI, the story viewer, the sheets
 * and the toast — so the mini floats over chats too, as the owner
 * demanded. It still dies with the Tik session: locking flips
 * TikuRoot's branch, uncomposing this whole subtree.
 *
 * Modes (driven by AppState.tikDestination + WatchSession.hasSession
 * + WatchSession.forceMini):
 * - EXPANDED (destination == 2, no forceMini): fills the Watch content
 *   area — full width, top at [WatchChromeHeaderHeight] (exactly under
 *   WatchScreen's chrome), bottom bounded above the NavPill band by
 *   [WatchNavBlock] (the pane's own 12.dp inset finishes the job).
 * - MINI (destination != 2 with a session alive, OR forceMini armed by
 *   Back on the Watch tab itself): the same live pane at 208x117
 *   (16:9), radius 12, border + shadow. Resting spot: bottom-right,
 *   12.dp from the right edge, [WatchMiniBottomClearance] from the
 *   bottom — i.e. directly above the "Now watching" bar, which stays
 *   visible underneath it. DRAGGABLE anywhere (clamped to the screen),
 *   springs to the nearest horizontal edge on release; a tap returns
 *   to the Watch tab EXPANDED; the ✕ chip fully stops playback.
 *   ROUND 19: a mode change snaps the pane's layout to the target
 *   size in one step and glides a graphicsLayer scale over it — the
 *   WebView is never dragged through an animated resize again.
 * - CALL (a call is ringing / outgoing / connected): playback pauses
 *   and the MINI window fades out (alpha 0, bounds kept) — neither an
 *   uncompose nor a relayout, so the pane and its session survive
 *   the call untouched.
 * - Not composed at all when there is no session and the Watch tab is
 *   not selected: nothing exists to preserve, and a later visit builds
 *   the pane fresh on the recorded session URL, exactly like build 47.
 */
@Composable
fun WatchHost() {
    val dest = AppState.tikDestination
    val hasSession = WatchSession.hasSession
    val forceMini = WatchSession.forceMini
    if (Config.PREVIEW_UI_ONLY) {
        // Screenshot harness: Robolectric must never see a real WebView.
        // Only the EXPANDED placeholder renders (shot24 composes
        // WatchPane() directly and gets the identical surface).
        if (dest == 2) {
            Box(Modifier.fillMaxSize().padding(top = WatchChromeHeaderHeight)) { WatchPane() }
        }
        return
    }
    // ROUND 19: EXPANDED needs the Watch tab AND no Back-minimize —
    // forceMini keeps the host in MINI on the Watch tab itself. The
    // early return also honours forceMini so arming it can never
    // uncompose the pane it is about to float.
    val expanded = dest == 2 && !forceMini
    if (!expanded && !hasSession && !forceMini) return
    // ROUND 15 — a live call owns the screen and the audio: pause the
    // video and hide the MINI window (ROUND 19: alpha 0 at mini
    // bounds, pane composed — a zero-size relayout is exactly the
    // kind of surface shock this round removes).
    val callState by CallController.state.collectAsState()
    val callActive = callState !is CallUiState.Idle
    LaunchedEffect(callActive) {
        if (callActive) {
            WatchControl.requestPause()
        } else {
            // ROUND 20: the call ended — revive a MINI pane the call
            // froze (the pane hook no-ops in EXPANDED mode). Before
            // this, nothing ever resumed the pane after a call: the
            // mini came back visible but paused for good.
            WatchControl.requestMiniRevive()
        }
    }
    val hideMini = !expanded && callActive
    // The host floats ABOVE the chat/details/moments overlays now, so
    // the mini's drag/tap/✕ layer stays live over them; only the call
    // UI and the story viewer (siblings composed above this host in
    // TikuRoot) take the screen — and hideMini — away from it.
    val covered = AppState.screen is Screen.Call || AppState.screen is Screen.Story

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        val spec = tween<Dp>(340, easing = RdEase)
        val rAnim by animateDpAsState(if (expanded) 0.dp else WatchMiniCorner, spec, label = "watchR")
        // ROUND 19 — the pane's LAYOUT size is the mode's target size,
        // applied in ONE step on a mode change. Build 62 animated the
        // layout size itself (wAnim/hAnim), re-measuring the WebView
        // at dozens of intermediate sizes over 340 ms; the video
        // surface could not track the moving target and ended up
        // stranded — the mini rendered black/frozen while the page
        // lived. A single relayout (a browser window being resized
        // once) is what WebView playback reliably survives. (Round 19
        // painted the shrink as a graphicsLayer scale over this
        // layout — ROUND 21 removed that transform entirely; see the
        // size block below. The position Animatables still glide the
        // origin with the same 340 ms / RdEase timing.)
        // ROUND 21 — the mini's size is computed from the LIVE
        // constraints: width = 58% of the host width clamped to
        // [190.dp, 240.dp], height = width * 9/16 — an exact 16:9
        // box on any phone. The layout still snaps to the mode's
        // target in ONE step (the Round-19 layout decision stands).
        // What Round 21 REMOVES is the graphicsLayer SCALE glide
        // that painted over it: the transform's terminal state
        // depended on a 340 ms Animatable effect reaching exactly
        // 1f through every mode flip, and on the owner's phone the
        // window came to rest OFF identity — layout a correct 16:9
        // box, painted rect stretched by a leftover scale (the
        // tall narrow sliver of his build-64 screenshot, page
        // squished inside it). With no scale anywhere in the tree,
        // the painted rect IS the layout rect by construction and
        // that state cannot exist. The position glide below moves
        // only the origin; it cannot distort size.
        val miniW = (maxW * WatchMiniWidthFraction)
            .coerceIn(WatchMiniMinWidth, WatchMiniMaxWidth)
        val miniH = miniW * 9f / 16f
        val layoutW = if (expanded) maxW else miniW
        val layoutH = if (expanded) {
            (maxH - WatchChromeHeaderHeight - WatchNavBlock).coerceAtLeast(0.dp)
        } else miniH
        // Position Animatables run in PX floats (drag deltas arrive
        // in px). Same targets and the same 340ms / RdEase glide as
        // the design — origin only (Round 21: no size transform).
        val density = LocalDensity.current
        val posSpec = tween<Float>(340, easing = RdEase)
        val headerPx = with(density) { WatchChromeHeaderHeight.toPx() }
        val ax = remember { Animatable(0f) }
        val ay = remember { Animatable(headerPx) }
        var hasCustomPos by remember { mutableStateOf(false) }
        // Resting bottom clearance: the floor is the NavPill band
        // (WatchNavBlock) + the 12.dp edge margin — but where the
        // "Now watching" bar will show (it is composed ABOVE this
        // host in TikuRoot), the mini keeps the shipped stack and
        // rests above the bar instead of sliding under it. The bar
        // gate mirrors NowWatchingBar's own `show`.
        val barShowsHere = hasSession && dest != 2 &&
            AppState.screen is Screen.Home && ChatSession.unlocked
        val miniBottomClearance = if (barShowsHere) {
            WatchMiniBottomClearance
        } else {
            WatchNavBlock + WatchMiniEdgeMargin
        }
        val defMiniX = with(density) { (maxW - miniW - WatchMiniEdgeMargin).toPx() }
        val defMiniY = with(density) { (maxH - miniBottomClearance - miniH).toPx() }
        val miniHalfPx = with(density) { (miniW / 2).toPx() }
        val midPx = with(density) { (maxW / 2).toPx() }
        val marginPx = with(density) { WatchMiniEdgeMargin.toPx() }
        val maxDragXPx = with(density) { (maxW - miniW).toPx() }
        val maxDragYPx = with(density) { (maxH - miniH).toPx() }
        // Position driver: mode switches glide the window between the
        // expanded origin and the mini resting spot. Once the user has
        // dragged the mini, its position is theirs — mode switches and
        // resizes leave ax/ay alone (render-time clamps keep it on
        // screen). The gesture handler stops these animatables before
        // taking over, so the two never fight.
        LaunchedEffect(expanded, hasCustomPos, defMiniX, defMiniY) {
            if (expanded) {
                launch { ax.animateTo(0f, posSpec) }
                launch { ay.animateTo(headerPx, posSpec) }
            } else if (!hasCustomPos) {
                launch { ax.animateTo(defMiniX, posSpec) }
                launch { ay.animateTo(defMiniY, posSpec) }
            }
        }
        val curX = with(density) { ax.value.coerceIn(0f, (maxW - layoutW).toPx().coerceAtLeast(0f)).toDp() }
        val curY = with(density) { ay.value.coerceIn(0f, (maxH - layoutH).toPx().coerceAtLeast(0f)).toDp() }
        Box(
            Modifier
                .offset(x = curX, y = curY)
                .size(layoutW, layoutH)
                .graphicsLayer {
                    // ROUND 21: no scale transform exists any more
                    // (see the size block above) — the painted rect is
                    // the layout rect by construction. hideMini (a
                    // live call owns the screen) fades the mini out
                    // here instead of collapsing its bounds.
                    alpha = if (hideMini) 0f else 1f
                }
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
            WatchPane(chromeInsets = expanded, boundedAboveNav = expanded)
            if (!expanded && !covered && !hideMini) {
                // Drag / tap layer ABOVE the WebView (which would
                // otherwise eat every touch as page interaction).
                val gestureScope = rememberCoroutineScope()
                Box(
                    Modifier.fillMaxSize()
                        // ROUND 21 — ONE gesture state machine, not two
                        // competing detectors. Rounds 19/20 stacked
                        // detectTapGestures and detectDragGestures as
                        // sibling pointerInput blocks on this Box: both
                        // await the same stream in parallel, and whether
                        // the TAP (expand!) fired depended on which
                        // detector consumed what — the owner's "tap the
                        // small window and it must open" was a race.
                        // Now a single awaitEachGesture loop decides:
                        // release within touch slop = TAP (expand);
                        // movement past slop = DRAG (the position
                        // Animatables stop, the window follows, release
                        // springs to the nearest edge). A tap after any
                        // number of drags takes the exact same path.
                        // Animatable calls go through gestureScope.launch
                        // (the loop's own scope is @RestrictsSuspension).
                        .pointerInput(maxW, maxH, miniW, miniH) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var dragged = false
                                var released = false
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!dragged &&
                                        (change.position - down.position).getDistance() >
                                        viewConfiguration.touchSlop
                                    ) {
                                        dragged = true
                                        hasCustomPos = true
                                        gestureScope.launch { ax.stop() }
                                        gestureScope.launch { ay.stop() }
                                    }
                                    if (dragged && change.pressed) {
                                        val delta = change.positionChange()
                                        if (delta != Offset.Zero) {
                                            gestureScope.launch {
                                                ax.snapTo((ax.value + delta.x).coerceIn(0f, maxDragXPx))
                                            }
                                            gestureScope.launch {
                                                ay.snapTo((ay.value + delta.y).coerceIn(0f, maxDragYPx))
                                            }
                                        }
                                        change.consume()
                                    }
                                    released = !change.pressed
                                } while (!released)
                                // A cancelled stream (the layer left
                                // composition mid-touch) decides
                                // NOTHING — no tap, no spring.
                                if (!released) return@awaitEachGesture
                                if (!dragged) {
                                    // TAP: back to the Watch tab, expanded —
                                    // and drop any Back-minimize, so a mini
                                    // armed on the Watch tab itself expands
                                    // in place.
                                    WatchSession.forceMini = false
                                    AppState.goTik(2)
                                } else {
                                    // Release after a drag: spring to the
                                    // nearest horizontal edge.
                                    val targetX = if (ax.value + miniHalfPx < midPx) {
                                        marginPx
                                    } else {
                                        defMiniX
                                    }
                                    gestureScope.launch {
                                        ax.animateTo(
                                            targetX,
                                            spring(dampingRatio = 0.78f, stiffness = 420f),
                                        )
                                    }
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
                    // live pane — no fresh pane, no reload. A lingering
                    // Back-minimize is dropped for the same reason.
                    WatchSession.forceMini = false
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
