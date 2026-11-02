package com.tiku.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ROUND 10 — WATCH: YouTube inside the vault. ROUND 11 — lifecycle
 * hardening + design alignment (owner's Tikuu_11 UI).
 *
 * Lives ONLY inside ChatRoot (post-gate): a "Watch" tab in HomeScreen hosts
 * [WatchPane]; nothing about it exists in the study facade, in
 * notifications, or outside FLAG_SECURE's coverage (it renders in the same
 * activity window). The content is the LIVE YouTube page — there is no
 * dummy feed, no cards, no segments anywhere in this pane. The WebView is
 * owned by [WatchHolder] — a plain singleton, NOT by any composition — so
 * it survives tab switches and can be re-parented between the full pane
 * and the floating mini window without losing the page.
 *
 * ROUND 11 crash fixes (the "Watch goes blank and never comes back" bug):
 *  - The Build-40 holder had NO renderer-death handling: when the WebView
 *    renderer process died (routine for heavy video pages under memory
 *    pressure), obtain() kept handing back the corpse and the tab sat
 *    blank forever. Now onRenderProcessGone swaps in a fresh WebView on
 *    the last URL — the tab heals itself.
 *  - Re-parenting is guarded: obtain() always detaches before returning,
 *    the pane↔mini hand-off checks the view is actually un-parented
 *    before floating the mini, and every holder call checks
 *    [WatchHolder.webViewAlive] before touching the view.
 *  - Tab switches only pause/resume the WebView — destroy happens ONLY on
 *    lock teardown and the mini window's close button.
 *
 * LOCK SAFETY (hard rule): [WatchHolder.destroy] is called from
 * ChatSession.lock() — the single lock choke point — and from the owner's
 * relaxed quick-exit, so no video and NO AUDIO can survive leaving Tik.
 * MainActivity.onPause merely pauses playback.
 */

/** Bottom clearance for Watch content. ROUND 12: the floating glass bar
 *  is gone — the design's flat .nav is ~57dp — and Watch now reuses the
 *  design .body bottom padding (84) that every Home pane uses, so
 *  YouTube's own bottom navigation stays fully visible and tappable
 *  above Tik's bar (the Round-11 fix, re-measured for the new nav). */
private val WatchBottomInset = 84.dp

/** The drawn Watch glyph: a rounded rectangle with a play triangle — the
 *  app's own mark in its own style. Deliberately NOT the official YouTube
 *  logo and not an emoji. */
@Composable
fun WatchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.30f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.06f, size.height * 0.14f),
            size = Size(size.width * 0.88f, size.height * 0.72f),
            cornerRadius = CornerRadius(r, r),
        )
        val cx = size.width * 0.52f
        val cy = size.height * 0.50f
        val s = size.minDimension * 0.17f
        val tri = Path().apply {
            moveTo(cx - s * 0.7f, cy - s)
            lineTo(cx - s * 0.7f, cy + s)
            lineTo(cx + s * 0.95f, cy)
            close()
        }
        drawPath(tri, Color.White)
    }
}

/**
 * The owner design's `.btn` + `I('plus',22)`, measured from Tikuu_11 —
 * not eyeballed: a bare 34dp box (6dp padding around a 22dp glyph), no
 * background, no border. The glyph is the design's own SVG path
 * (M12 5v14M5 12h14 on a 24 grid) stroked at 1.6 with round caps, in the
 * title ink. The pressed state mirrors the CSS `.btn:active`
 * (scale .88, opacity .7).
 */
@Composable
fun DesignPlusButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.size(34.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.88f else 1f
                scaleY = if (pressed) 0.88f else 1f
                alpha = if (pressed) 0.7f else 1f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val u = size.width / 24f
            val sw = 1.6f * u
            drawLine(
                Tik.Tx,
                Offset(12 * u, 5 * u), Offset(12 * u, 19 * u),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
            drawLine(
                Tik.Tx,
                Offset(5 * u, 12 * u), Offset(19 * u, 12 * u),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
        }
    }
}

object WatchHolder {
    const val HOME_URL = "https://m.youtube.com"

    /** The single WebView instance (null = no Watch session). Observable
     *  state: the pane and the mini window key their AndroidView on it, so
     *  a renderer-gone instance swap rebuilds the host around the fresh
     *  view automatically. */
    var webView: WebView? by mutableStateOf(null)
        private set

    /** True only while [webView] is a live, usable instance. Cleared the
     *  moment the view is destroyed or its renderer dies; EVERY holder
     *  operation checks it before touching the view. */
    var webViewAlive: Boolean = false
        private set

    /** True while the mini window should be floating over ChatRoot. */
    var miniActive by mutableStateOf(false)

    /** Fullscreen video view handed over by the WebChromeClient, if any. */
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var appCtx: Context? = null

    /** Last http(s) URL the page navigated to — the self-heal target after
     *  a renderer death. Reset to YouTube home on destroy, so a locked
     *  session leaves no browsing trail behind. */
    private var lastUrl: String = HOME_URL

    /** Re-parent guard: true while a pane↔mini hand-off is in flight, so
     *  the two hosts can never fight over the WebView mid-transition. */
    private var handoffInFlight: Boolean = false

    fun currentUrl(): String = if (webViewAlive) webView?.url ?: "" else ""

    private fun detach(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(ctx: Context, url: String): WebView {
        val app = ctx.applicationContext
        appCtx = app
        val wv = WebView(app)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                // http(s) navigates INSIDE the view; intent://, market://
                // and every other external scheme are swallowed — Watch
                // never hands the user (or the trail) to another app.
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                // Track the last real page so a renderer-gone heal can
                // return to it (wv.url is unreliable once the renderer
                // is dead).
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    lastUrl = url
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // The renderer process died. Returning true promises WE
                // handle it: swap the corpse for a fresh WebView on the
                // last URL — the tab must never sit blank.
                handleRendererGone(view)
                return true
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
            }
            override fun onHideCustomView() {
                fullscreenView = null
                fullscreenCallback = null
            }
        }
        webViewAlive = true
        webView = wv
        wv.loadUrl(url)
        return wv
    }

    /**
     * Renderer-death self-heal: drop the dead instance (detach + destroy
     * is the platform-recommended disposal for a gone renderer) and create
     * a fresh one on the last URL. The pane/mini observe [webView], so
     * they re-key and show the new view on their own. Mini state is left
     * untouched — if the mini was up, it re-forms around the fresh view.
     */
    private fun handleRendererGone(dead: WebView) {
        if (webView !== dead) return
        webViewAlive = false
        webView = null
        exitFullscreen()
        runCatching { detach(dead) }
        runCatching { dead.destroy() }
        val ctx = appCtx ?: return
        create(ctx, lastUrl)
    }

    /**
     * The live WebView, creating it on first use. Always detached from any
     * previous parent before being returned, so a host can never receive a
     * view that is still attached elsewhere (the double-attach crash).
     */
    fun obtain(ctx: Context): WebView {
        val live = webView
        if (live != null && webViewAlive) {
            detach(live)
            return live
        }
        return create(ctx, lastUrl)
    }

    /** Header plus button (owner design): back to YouTube home. A real
     *  navigation on the live page — creating the session first if the
     *  Watch tab was never opened. */
    fun goHome(ctx: Context) {
        miniActive = false
        val wv = obtain(ctx)
        if (webViewAlive) runCatching { wv.loadUrl(HOME_URL) }
    }

    /** Pause playback (JS + WebView lifecycle). Used when the app merely
     *  backgrounds without locking — the session itself survives. */
    fun pause() {
        if (!webViewAlive) return
        val wv = webView ?: return
        runCatching {
            wv.evaluateJavascript(
                "document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}})",
                null,
            )
        }
        runCatching { wv.onPause() }
    }

    /** Resume the WebView's lifecycle when the Watch tab comes back. The
     *  page keeps its own play state — this only un-freezes rendering. */
    fun resume() {
        if (!webViewAlive) return
        runCatching { webView?.onResume() }
    }

    /** Mini-window pause/play toggle, straight into the page's video. */
    fun setPlaying(play: Boolean) {
        if (!webViewAlive) return
        val wv = webView ?: return
        val js = if (play)
            "document.querySelectorAll('video').forEach(function(v){try{v.play()}catch(e){}})"
        else
            "document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}})"
        runCatching { wv.evaluateJavascript(js, null) }
        runCatching { if (play) wv.onResume() else wv.onPause() }
    }

    fun exitFullscreen() {
        runCatching { fullscreenCallback?.onCustomViewHidden() }
        fullscreenView = null
        fullscreenCallback = null
    }

    /**
     * FULL TEARDOWN — the lock path and the mini window's ✕. Best-effort
     * JS pause first, then the page is blanked, detached and the WebView
     * destroyed: after this, no frame and no audio of the video can exist
     * anywhere. Also exits any fullscreen view, clears the mini state and
     * forgets the last URL (no trail survives a lock).
     */
    fun destroy() {
        exitFullscreen()
        miniActive = false
        handoffInFlight = false
        lastUrl = HOME_URL
        val wv = webView
        webView = null
        webViewAlive = false
        if (wv == null) return
        // JS-pause on the INSTANCE first (blanking + destroy below are what
        // actually guarantee silence; this just stops playback cleanly).
        runCatching {
            wv.evaluateJavascript(
                "document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}})",
                null,
            )
        }
        runCatching { wv.stopLoading() }
        runCatching { wv.loadUrl("about:blank") }
        detach(wv)
        runCatching { wv.destroy() }
    }

    /** The pane was re-entered: any scheduled hand-off is moot. */
    fun onPaneEntered() {
        handoffInFlight = false
    }

    /** The pane is being left (tab switch — NEVER a destroy): a video page
     *  floats the mini window once the tab transition has settled and the
     *  WebView is verifiably un-parented; any other page simply pauses. */
    fun onPaneLeft(scope: kotlinx.coroutines.CoroutineScope) {
        if (!webViewAlive || webView == null) return
        if (!currentUrl().contains("/watch") && !lastUrl.contains("/watch")) {
            pause()
            return
        }
        if (handoffInFlight) return
        handoffInFlight = true
        val wv = webView
        scope.launch {
            delay(400)
            handoffInFlight = false
            if (webViewAlive && webView === wv && wv?.parent == null &&
                AppState.homeTab != HomeTab.WATCH && ChatSession.unlocked
            ) {
                miniActive = true
            }
        }
    }
}

/** The Watch tab itself: the holder's WebView at full size, inset above
 *  the floating tab bar so YouTube's own navigation stays visible.
 *  Leaving the tab with a video open floats the mini window (see
 *  [WatchMiniOverlay]); tab switches never destroy the WebView. */
@Composable
fun WatchPane() {
    if (Config.PREVIEW_UI_ONLY) {
        // PREVIEW (Roborazzi) only: no WebView, no network, no fake feed —
        // just the pane's honest placeholder line. Never in the real app.
        Box(
            Modifier.fillMaxSize().background(Tik.Bg).padding(bottom = WatchBottomInset),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "YouTube opens here on your phone",
                fontSize = 14.sp, color = Tik.Sub, fontFamily = Manrope,
            )
        }
        return
    }
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val wv = WatchHolder.webView
    LaunchedEffect(Unit) {
        WatchHolder.miniActive = false
        WatchHolder.onPaneEntered()
        WatchHolder.obtain(ctx)
        WatchHolder.resume()
    }
    DisposableEffect(Unit) {
        onDispose { WatchHolder.onPaneLeft(scope) }
    }
    Box(Modifier.fillMaxSize().background(Tik.Bg)) {
        // Keyed on the instance: a renderer-gone swap rebuilds this host
        // around the fresh WebView instead of leaving the dead one on screen.
        key(wv) {
            if (wv != null) {
                AndroidView(
                    factory = { WatchHolder.obtain(ctx) },
                    modifier = Modifier.fillMaxSize().padding(bottom = WatchBottomInset),
                )
            }
        }
        // fullscreen video (WebChromeClient hand-over) covers the pane
        WatchHolder.fullscreenView?.let { fv ->
            BackHandler { WatchHolder.exitFullscreen() }
            AndroidView(
                factory = { fv },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

/**
 * The floating mini player — the SAME WebView re-parented into a small
 * draggable window over ChatRoot (drag pattern copied from
 * FloatingCallWindow). Rendered from MainActivity's home layer, above the
 * tab content, and its bounds always stay above the floating tab bar.
 * Tap (not on a button) expands back to the Watch tab; the ✕ closes Watch
 * entirely (pause + blank + destroy via the holder).
 */
@Composable
fun WatchMiniOverlay() {
    val tab = AppState.homeTab
    val screen = AppState.screen
    val wv = WatchHolder.webView
    val show = WatchHolder.miniActive && wv != null && WatchHolder.webViewAlive && tab != HomeTab.WATCH &&
        ChatSession.unlocked && (screen is Screen.Home || screen is Screen.Chat || screen is Screen.ChatDetails)
    if (!show) return
    val settings by SettingsStore.state.collectAsState()
    var paused by remember { mutableStateOf(false) }
    val winW = 184.dp
    val winH = 104.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // The window's bottom edge may never slide under the tab bar.
        val maxOffY = (maxHeight - winH - WatchBottomInset).coerceAtLeast(8.dp)
        var offX by remember { mutableStateOf(maxWidth - winW - 14.dp) }
        var offY by remember { mutableStateOf(maxOffY) }
        LaunchedEffect(maxWidth, maxHeight) {
            offX = offX.coerceIn(8.dp, (maxWidth - winW - 8.dp).coerceAtLeast(8.dp))
            offY = offY.coerceIn(8.dp, maxOffY)
        }
        Box(
            Modifier.offset(x = offX, y = offY).size(winW, winH)
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, Tik.Ln, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offX = (offX + with(density) { drag.x.toDp() })
                            .coerceIn(8.dp, (maxWidth - winW - 8.dp).coerceAtLeast(8.dp))
                        offY = (offY + with(density) { drag.y.toDp() })
                            .coerceIn(8.dp, maxOffY)
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // expand back to the Watch tab at full size
                    WatchHolder.miniActive = false
                    AppState.homeTab = HomeTab.WATCH
                    AppState.go(Screen.Home)
                },
        ) {
            // Keyed on the instance, like the pane: a renderer-gone swap
            // re-forms the mini around the fresh WebView.
            key(wv) {
                AndroidView(
                    factory = { WatchHolder.obtain(AppState.appCtx) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // pause / play (drawn glyphs, same convention as the voice bubble)
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(0x66000000))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            paused = !paused
                            WatchHolder.setPlaying(!paused)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (paused) "▶" else "❚❚", fontSize = 10.sp, color = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                // close: pause via JS, load about:blank, detach, clear holder
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(settings.accent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            WatchHolder.destroy()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = Color.White)
                }
            }
        }
    }
}
