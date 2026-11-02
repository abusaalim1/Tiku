package com.tiku.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ROUND 10 — WATCH: YouTube inside the vault.
 *
 * Lives ONLY inside ChatRoot (post-gate): a "Watch" tab in HomeScreen hosts
 * [WatchPane]; nothing about it exists in the study facade, in
 * notifications, or outside FLAG_SECURE's coverage (it renders in the same
 * activity window). The WebView is owned by [WatchHolder] — a plain
 * singleton, NOT by any composition — so it survives tab switches and can
 * be re-parented between the full pane and the floating mini window
 * without losing the page or the playback position.
 *
 * LOCK SAFETY (hard rule): [WatchHolder.destroy] is called from
 * ChatSession.lock() — the single lock choke point — and from the owner's
 * relaxed quick-exit, so no video and NO AUDIO can survive leaving Tik.
 * MainActivity.onPause merely pauses playback.
 */

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

object WatchHolder {
    /** The single WebView instance (null = no Watch session). */
    var webView: WebView? = null
        private set

    /** True while the mini window should be floating over ChatRoot. */
    var miniActive by mutableStateOf(false)

    /** Fullscreen video view handed over by the WebChromeClient, if any. */
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    fun currentUrl(): String = webView?.url ?: ""

    private fun detach(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(ctx: Context): WebView {
        webView?.let { return it }
        val wv = WebView(ctx.applicationContext)
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
        wv.loadUrl("https://m.youtube.com")
        webView = wv
        return wv
    }

    /** Pause playback (JS + WebView lifecycle). Used when the app merely
     *  backgrounds without locking — the session itself survives. */
    fun pause() {
        val wv = webView ?: return
        runCatching {
            wv.evaluateJavascript(
                "document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}})",
                null,
            )
        }
        runCatching { wv.onPause() }
    }

    /** Mini-window pause/play toggle, straight into the page's video. */
    fun setPlaying(play: Boolean) {
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
     * FULL TEARDOWN — the lock path. Best-effort JS pause first, then the
     * page is blanked, detached and the WebView destroyed: after this, no
     * frame and no audio of the video can exist anywhere. Also exits any
     * fullscreen view and clears the mini state.
     */
    fun destroy() {
        exitFullscreen()
        val wv = webView ?: run { miniActive = false; return }
        // JS-pause on the INSTANCE first (blanking + destroy below are what
        // actually guarantee silence; this just stops playback cleanly).
        runCatching {
            wv.evaluateJavascript(
                "document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}})",
                null,
            )
        }
        webView = null
        miniActive = false
        runCatching { wv.stopLoading() }
        runCatching { wv.loadUrl("about:blank") }
        detach(wv)
        runCatching { wv.destroy() }
    }

    /** The pane is being left: if a video page is open, float the mini
     *  window once the tab transition has settled (the pane and the mini
     *  must never fight over the WebView in the same frame). */
    fun onPaneLeft(scope: kotlinx.coroutines.CoroutineScope) {
        if (webView == null) return
        if (!currentUrl().contains("/watch")) return
        scope.launch {
            delay(400)
            if (webView != null && AppState.homeTab != HomeTab.WATCH && ChatSession.unlocked) {
                miniActive = true
            }
        }
    }
}

/** The Watch tab itself: the holder's WebView at full size. Leaving the tab
 *  with a video open floats the mini window (see [WatchMiniOverlay]). */
@Composable
fun WatchPane() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { WatchHolder.miniActive = false }
    DisposableEffect(Unit) {
        onDispose { WatchHolder.onPaneLeft(scope) }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { WatchHolder.obtain(ctx) },
            modifier = Modifier.fillMaxSize(),
        )
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
 * tab content. Tap (not on a button) expands back to the Watch tab; the ✕
 * closes Watch entirely (pause + blank + destroy via the holder).
 */
@Composable
fun WatchMiniOverlay() {
    val tab = AppState.homeTab
    val screen = AppState.screen
    val wv = WatchHolder.webView
    val show = WatchHolder.miniActive && wv != null && tab != HomeTab.WATCH &&
        ChatSession.unlocked && (screen is Screen.Home || screen is Screen.Chat || screen is Screen.ChatDetails)
    if (!show) return
    val settings by SettingsStore.state.collectAsState()
    var paused by remember { mutableStateOf(false) }
    val winW = 184.dp
    val winH = 104.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var offX by remember { mutableStateOf(maxWidth - winW - 14.dp) }
        var offY by remember { mutableStateOf(maxHeight - winH - 96.dp) }
        LaunchedEffect(maxWidth, maxHeight) {
            offX = offX.coerceIn(8.dp, (maxWidth - winW - 8.dp).coerceAtLeast(8.dp))
            offY = offY.coerceIn(8.dp, (maxHeight - winH - 8.dp).coerceAtLeast(8.dp))
        }
        Box(
            Modifier.offset(x = offX, y = offY).size(winW, winH)
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, TikuTokens.Line, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offX = (offX + with(density) { drag.x.toDp() })
                            .coerceIn(8.dp, (maxWidth - winW - 8.dp).coerceAtLeast(8.dp))
                        offY = (offY + with(density) { drag.y.toDp() })
                            .coerceIn(8.dp, (maxHeight - winH - 8.dp).coerceAtLeast(8.dp))
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // expand back to the Watch tab at full size
                    WatchHolder.miniActive = false
                    AppState.homeTab = HomeTab.WATCH
                    AppState.go(Screen.Home)
                },
        ) {
            AndroidView(
                factory = { WatchHolder.obtain(AppState.appCtx) },
                modifier = Modifier.fillMaxSize(),
            )
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
