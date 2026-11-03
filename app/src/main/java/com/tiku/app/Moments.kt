package com.tiku.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// ============================================================================
// ROUND 14 (owner order, 2026-11-03) — MOMENTS, integrated. The owner's
// couple-memories WEB app (React + Supabase, email/password login) lives at
// tiku-moments.vercel.app; this surface opens it full-screen INSIDE the
// app from the Messages "Memories" entry — never an external browser.
// Tik-side only: it exists solely as the Screen.Moments overlay above
// Home (behind the password gate); the facade never references it.
//
// WebView discipline mirrors Watch's hard-won rules (Round 11/13): the
// view is created INSIDE this composable, lives in exactly ONE AndroidView
// slot for its whole life, is never re-parented, and is destroyed in
// DisposableEffect — so closing the surface, or any session lock /
// quick-exit (which navigates away from the whole Tik tree and
// uncomposes this screen), tears it down completely.
// ============================================================================

/** The integrated web app. Its own Supabase session (cookies + DOM
 *  storage in the app-private WebView profile) persists across opens,
 *  so the owner logs in once, inside the app. */
const val MOMENTS_URL = "https://tiku-moments.vercel.app/"
private const val MOMENTS_HOST = "tiku-moments.vercel.app"

private fun closeMoments() {
    AppState.goTik(0)
    AppState.go(Screen.Home)
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildMomentsWebView(
    ctx: Context,
    onProgress: (Int) -> Unit,
    onMainFrameError: () -> Unit,
    onLoadStarted: () -> Unit,
    onFileChooser: (ValueCallback<Array<Uri>>, String) -> Unit,
): WebView {
    val wv = WebView(ctx)
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadsImagesAutomatically = true
        mediaPlaybackRequiresUserGesture = false
        // The web app is a same-origin SPA; local files/content providers
        // are never part of it (photo picks arrive as content Uris via
        // the file chooser, which is a separate grant).
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
    }
    wv.setBackgroundColor(android.graphics.Color.BLACK)
    wv.webViewClient = object : WebViewClient() {
        // Navigation lockdown: the Moments origin stays inside the app;
        // ANY other host (http/https) is handed to the external browser
        // and never loads in-view; non-web schemes (mailto:, tel:,
        // intent:, …) go to their handler or are refused — always
        // blocked in-view.
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            val scheme = url.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                if (url.host?.lowercase() == MOMENTS_HOST) return false
                openExternal(ctx, url)
                return true
            }
            if (scheme == "about") return false
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            onLoadStarted()
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) onMainFrameError()
        }
    }
    wv.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            onProgress(newProgress)
        }

        // Photo upload is the site's core flow: without this callback the
        // page's <input type=file> is a dead button.
        override fun onShowFileChooser(
            webView: WebView?,
            callback: ValueCallback<Array<Uri>>?,
            params: FileChooserParams?,
        ): Boolean {
            if (callback == null) return false
            // Accept types arrive as MIME types ("image/*") or, from some
            // pages, bare extensions — GetContent needs a MIME type.
            val accept = params?.acceptTypes?.firstOrNull { it.contains("/") } ?: "image/*"
            onFileChooser(callback, accept)
            return true
        }
    }
    wv.loadUrl(MOMENTS_URL)
    return wv
}

private fun openExternal(ctx: Context, url: Uri) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun destroyMomentsWebView(wv: WebView) {
    runCatching { wv.stopLoading() }
    runCatching { wv.loadUrl("about:blank") }
    (wv.parent as? ViewGroup)?.removeView(wv)
    runCatching { wv.destroy() }
}

/**
 * The Moments surface: slim redesign chrome (back + "Memories" + reload,
 * thin Rose progress bar) over the one WebView. System Back walks the
 * WebView's history first, then closes to Messages. Lock / quick-exit
 * navigate the whole Tik tree away, which uncomposes this screen and
 * destroys the WebView in [DisposableEffect] — like every Tik surface.
 */
@Composable
fun MomentsScreen() {
    if (Config.PREVIEW_UI_ONLY) {
        // Screenshot/preview branch: no WebView (Robolectric has no real
        // renderer) — mirrors Watch's preview pattern.
        MomentsPlaceholder()
        return
    }

    val ctx = LocalContext.current
    // Explicit state holders (not `by remember` delegates): the WebView
    // is built inside remember{} and its client lambdas capture these
    // stable holder objects directly.
    val progressState = remember { mutableIntStateOf(0) }
    val errorState = remember { mutableStateOf(false) }
    val fileCallbackState = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val cb = fileCallbackState.value
        fileCallbackState.value = null
        cb?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    }

    val webView = remember {
        buildMomentsWebView(
            ctx = ctx.applicationContext,
            onProgress = { progressState.intValue = it },
            onMainFrameError = { errorState.value = true },
            onLoadStarted = { errorState.value = false },
            onFileChooser = { callback, accept ->
                // A stale pending request (page navigated mid-pick) is
                // cancelled before the new one is stored.
                fileCallbackState.value?.onReceiveValue(null)
                fileCallbackState.value = callback
                // The picker is an external activity: without this flag
                // MainActivity.onStop would re-lock the vault mid-pick.
                ChatSession.expectExternalActivity = true
                runCatching { pickFile.launch(accept) }
                    .onFailure {
                        fileCallbackState.value = null
                        callback.onReceiveValue(null)
                    }
            },
        )
    }

    DisposableEffect(webView) {
        onDispose {
            // If the surface dies mid-pick, never leave the page's
            // callback dangling.
            fileCallbackState.value?.onReceiveValue(null)
            fileCallbackState.value = null
            destroyMomentsWebView(webView)
        }
    }

    // Back: WebView history first, then out to Messages.
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else closeMoments()
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackBtn { closeMoments() }
            Text(
                "Memories", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(CardC)
                    .border(1.dp, Color.White.copy(0.1f), CircleShape)
                    .press { webView.reload() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
        }
        // Thin Rose load progress (the chrome client's onProgressChanged).
        Box(Modifier.fillMaxWidth().height(2.5.dp)) {
            val p = progressState.intValue
            if (p in 1..99) {
                Box(
                    Modifier.fillMaxWidth(p / 100f).fillMaxHeight().background(Rose),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The AndroidView is ALWAYS composed (single slot, never
            // swapped out) — the error state overlays it opaquely.
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (errorState.value) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(Rose.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { TikIcon("heartFill", Modifier.size(34.dp), tint = Rose) }
                    Text(
                        "Couldn't load Memories", color = Color.White, fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Text(
                        "Check your connection and try again.", color = Sub, fontSize = 14.5.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Box(
                        Modifier.padding(top = 20.dp).clip(CircleShape)
                            .background(Rose).press {
                                errorState.value = false
                                if (webView.url.isNullOrBlank()) webView.loadUrl(MOMENTS_URL)
                                else webView.reload()
                            }
                            .padding(horizontal = 26.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Retry", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** PREVIEW branch: the chrome + identity, static — no WebView exists. */
@Composable
private fun MomentsPlaceholder() {
    BackHandler { closeMoments() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackBtn { closeMoments() }
            Text(
                "Memories", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(CardC)
                    .border(1.dp, Color.White.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Rose.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { TikIcon("heartFill", Modifier.size(34.dp), tint = Rose) }
                Text(
                    "Memories", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Your private place, together", color = Sub, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
