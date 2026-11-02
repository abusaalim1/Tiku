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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

// ============================================================================
// ROUND 13 — WATCH, REBUILT. The build-44 design kept ONE singleton WebView
// and re-parented it between three hosts (pane, floating mini player,
// fullscreen) while HomeScreen crossfaded panes with AnimatedContent — two
// compositions overlapped during every tab change and fought over the same
// view; closing Watch and reopening it crashed. That pattern is DELETED.
//
// The pane now OWNS its WebView: created in remember, destroyed in
// DisposableEffect, touched by no other composable. Fullscreen lives inside
// the pane. Leaving the Watch tab destroys the WebView (acceptable + safe);
// the plain session state below lets a fresh pane resume where it left off,
// and the "Now watching" glass bar replaces the floating mini player.
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

/** YouTube thumbnail id from a watch/shorts/embed URL, or null. */
fun youtubeThumbId(url: String): String? {
    Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(url)?.let { return it.groupValues[1] }
    return Regex("/(?:shorts|embed)/([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
}

/** Bottom inset the Watch pane keeps so YouTube's own bottom nav stays
 *  clear of Tik's floating glass nav (nav pill 60dp + 14dp page margin +
 *  breathing room). */
val WatchBottomInset = 100.dp

@SuppressLint("SetJavaScriptEnabled")
private fun buildWatchWebView(
    ctx: Context,
    startUrl: String,
    onPage: (String) -> Unit,
    onTitle: (String) -> Unit,
    onShowCustom: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustom: () -> Unit,
    onRendererGone: () -> Unit,
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
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (!url.isNullOrBlank()) onPage(url)
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
 * The Watch pane. Owns its WebView end to end: created here, destroyed here,
 * fullscreen hosted here. Nothing outside this composable may touch it.
 */
@Composable
fun WatchPane() {
    val pal = LocalPalette.current
    if (Config.PREVIEW_UI_ONLY) {
        // Screenshot/preview branch: no WebView (Robolectric has no real
        // renderer). Round-12 copy kept so the suite's text checks hold.
        Box(
            Modifier.fillMaxSize().background(pal.bg).padding(bottom = WatchBottomInset),
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
    var fsView by remember { mutableStateOf<View?>(null) }
    var fsCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

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
                fsView = view
                fsCallback = callback
            },
            onHideCustom = {
                val cb = fsCallback
                fsView = null
                fsCallback = null
                runCatching { cb?.onCustomViewHidden() }
            },
            onRendererGone = { healGen++ },
        )
    }

    fun exitFullscreen() {
        // Completing the CustomViewCallback makes the chrome client run its
        // onHideCustomView, which clears the state; the guards below cover
        // the already-cleared case.
        if (fsView != null) {
            val cb = fsCallback
            fsView = null
            fsCallback = null
            runCatching { cb?.onCustomViewHidden() }
        }
    }
    BackHandler(enabled = fsView != null) { exitFullscreen() }

    DisposableEffect(webView) {
        WatchControl.navigate = { url -> runCatching { webView.loadUrl(url) } }
        WatchControl.pausePlayback = { pauseWatchWebView(webView) }
        onDispose {
            if (WatchControl.navigate != null) WatchControl.navigate = null
            if (WatchControl.pausePlayback != null) WatchControl.pausePlayback = null
            // Save BEFORE destroy; epoch-guarded so a lock-time clear wins.
            WatchSession.recordOnLeave(currentUrl, pageTitle, paneEpoch)
            if (fsView != null) {
                val cb = fsCallback
                fsView = null
                fsCallback = null
                runCatching { cb?.onCustomViewHidden() }
            }
            destroyWatchWebView(webView)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).padding(bottom = WatchBottomInset)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
        val fs = fsView
        if (fs != null) {
            AndroidView(
                factory = { fs },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

/**
 * "Now watching" — the slim glass bar that replaces the floating mini
 * player. Shows on other Tik tabs while a Watch session exists; play jumps
 * back to the Watch tab (its fresh pane resumes at [WatchSession.lastUrl]),
 * × clears the session. Rendered by MainActivity above the glass nav.
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
                    AppState.homeTab = HomeTab.WATCH
                    AppState.go(Screen.Home)
                },
            contentAlignment = Alignment.Center,
        ) { TikIcon("playFill", Modifier.size(17.dp), tint = Color.White) }
        Box(
            Modifier.size(36.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    WatchSession.clear()
                },
            contentAlignment = Alignment.Center,
        ) { TikIcon("x", Modifier.size(17.dp), tint = pal.sub) }
    }
}
