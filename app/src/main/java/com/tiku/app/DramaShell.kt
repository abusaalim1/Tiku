package com.tiku.app

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ROUND 13 — THE K-DRAMA FACADE, the app's default surface (the study
 * facade is gone). A Korean love-story app in the owner's liquid-glass
 * design language: dubbed drama episodes streaming from YouTube, short
 * love stories to read, romantic stills to keep.
 *
 * ROUND 14 ADDENDUM — the old "For You" page (with its discreet entry
 * card) is REMOVED; the bottom nav is Home / Stories / Photos. The ONE
 * discreet door to the password gate is now the LAST story on the
 * Stories shelf ("First romance of them"): a single tap reads it like any other
 * story, a double tap routes through SecretEntry to the gate (the gate
 * itself lives at the bottom of this file, moved here unchanged from
 * the old facade).
 *
 * ROUND 16 — the tab surface (home / stories / photos + bottom nav) is
 * the owner's new DARK facade design, ported in Facade.kt and composed
 * by [FacadeShell] below. This file keeps the shell: the overlay
 * routing (player / reader / photo viewer), the store state, the
 * facade player itself, and the gate. The LIGHT palette provided in
 * [FacadeHome] now serves only the reader / viewer overlays, which
 * keep their existing design.
 *
 * Zero-trace rule: no chat data, no usernames, no messaging vocabulary,
 * and the word "Tik" appears nowhere on this surface. The facade is never
 * FLAG_SECURE (that flag is ChatRoot-only, in MainActivity).
 */

// ---------- facade shell ----------

enum class FacadeTab { HOME, STORIES, PHOTOS }

@Composable
fun FacadeHome(
    initialTab: FacadeTab = FacadeTab.HOME,
    initialStoryId: String? = null,
    initialPhoto: Int? = null,
    playerDramaId: String? = null,
) {
    // The reader / viewer overlays keep the design's LIGHT palette
    // (Rose Petal), provided locally here; the Round-16 tab surface
    // (Facade.kt) carries its own black system and ignores it.
    val lightPal = remember { buildPalette("light", 0) }
    CompositionLocalProvider(LocalPalette provides lightPal) {
        FacadeShell(initialTab, initialStoryId, initialPhoto, playerDramaId)
    }
}

@Composable
private fun FacadeShell(
    initialTab: FacadeTab,
    initialStoryId: String?,
    initialPhoto: Int?,
    playerDramaId: String?,
) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current
    var tab by remember { mutableStateOf(initialTab) }
    var storyId by remember { mutableStateOf(initialStoryId) }
    var photoIdx by remember { mutableStateOf(initialPhoto) }
    var playerId by remember { mutableStateOf(playerDramaId) }
    var favs by remember { mutableStateOf(FacadeStore.favourites(ctx)) }
    var history by remember { mutableStateOf(FacadeStore.history(ctx)) }

    // The player writes history as it disposes — refresh the row on return.
    LaunchedEffect(playerId) {
        if (playerId == null) history = FacadeStore.history(ctx)
    }

    // ROUND 54 (Part A): consume the episode gate's open requests —
    // a tapped decoy result reopens here as the shell's own player /
    // reader, indistinguishable from a facade tap.
    val gateDramaId = AppState.facadeOpenDramaId
    val gateStoryId = AppState.facadeOpenStoryId
    LaunchedEffect(gateDramaId, gateStoryId) {
        gateDramaId?.let { playerId = it; AppState.facadeOpenDramaId = null }
        gateStoryId?.let { storyId = it; AppState.facadeOpenStoryId = null }
    }

    val playerDrama = playerId?.let { DramaContent.drama(it) }
    val openStory = storyId?.let { DramaContent.story(it) }

    // Back from a non-home tab returns Home (sub-surfaces handle their own).
    BackHandler(enabled = playerDrama == null && openStory == null && photoIdx == null && tab != FacadeTab.HOME) {
        tab = FacadeTab.HOME
    }

    Box(
        Modifier.fillMaxSize().background(pal.bg)
            .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
    ) {
        when {
            playerDrama != null -> FacadePlayer(playerDrama) { playerId = null }
            openStory != null -> StoryReader(openStory) { storyId = null }
            photoIdx != null -> PhotoViewer(photoIdx!!) { photoIdx = null }
            else -> FacadeRoot(
                // ROUND 16: the tab surface is the owner's new dark
                // facade (Facade.kt) — its NavPill, motion and layout
                // are his file's; this shell keeps the overlay routing
                // (player / reader / viewer), the tab state (so the
                // back rule above and the harness entry points keep
                // working) and the store state.
                haze = hazeState,
                tab = tab.ordinal,
                onTab = {
                    tab = when (it) {
                        0 -> FacadeTab.HOME
                        1 -> FacadeTab.STORIES
                        else -> FacadeTab.PHOTOS
                    }
                },
                history = history,
                favs = favs,
                onPlay = { playerId = it.id },
                onToggleFav = { d ->
                    FacadeStore.toggleFavourite(ctx, d.id)
                    favs = FacadeStore.favourites(ctx)
                },
                onOpenStory = { storyId = it.id },
                onOpenPhoto = { photoIdx = it },
                // The last story's double-tap door (see Facade.kt).
                onSecretGate = { SecretEntry.openGate(ctx, scope) },
            )
        }
    }
}

// ---------- shared facade bits ----------

@Composable
private fun TagChip(tag: String) {
    val pal = LocalPalette.current
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(pal.ac.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(tag, fontSize = 10.5.sp, fontFamily = InterTight, fontWeight = FontWeight.SemiBold, color = pal.ac)
    }
}

@Composable
private fun GlassBackButton(onBack: () -> Unit) {
    Box(
        Modifier.size(38.dp).glassSurface(CircleShape, blur = true)
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onBack() },
        contentAlignment = Alignment.Center,
    ) { TikIcon("back", Modifier.size(20.dp)) }
}

/**
 * ROUND 15 — a bundled facade still (assets/facade/photos/), decoded
 * LOCALLY to a Bitmap. The old route fed Coil a
 * "file:///android_asset/…" string, which has no reliable fetcher and
 * left the hero (and the Photos shelf) blank/dark on the owner's
 * phone even though the files ship inside the APK. Decoding the
 * bundled bytes cannot miss: if a decode ever fails, the slot renders
 * the brand gradient — never a black box.
 */
@Composable
private fun FacadeAssetImage(
    file: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val bmp = remember(file) {
        runCatching {
            ctx.assets.open("facade/photos/$file").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier.background(
                Brush.linearGradient(listOf(pal.ac.copy(alpha = 0.55f), pal.lens)),
            ),
        )
    }
}

// ---------- PLAYER (facade-owned WebView; created on open, destroyed on close) ----------

/** ROUND 18 — the facade player's navigation policy: identical to the
 *  Watch pane's watchNavAllowed (Watch.kt — private to that file, so
 *  the policy is stated locally here): YouTube hosts and the Google
 *  sign-in flow (accounts.google.com) pass; everything else — external
 *  http(s) hosts and app-intent schemes (intent://, market://, tel: …)
 *  alike — is refused. `about:` passes so the player's own teardown
 *  load is never refused. */
private fun facadeNavAllowed(url: android.net.Uri?): Boolean {
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
@Composable
private fun FacadePlayer(drama: DramaContent.Drama, onClose: () -> Unit) {
    if (Config.PREVIEW_UI_ONLY) {
        // Screenshot/preview branch: NO WebView (the harness stays offline) —
        // a poster + play surface, mirroring the Watch pane's preview branch.
        BackHandler { onClose() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = DramaContent.thumbUrl(drama.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.55f,
            )
            Box(
                Modifier.align(Alignment.Center).size(72.dp)
                    .glassSurface(CircleShape, useLargeGlass = true),
                contentAlignment = Alignment.Center,
            ) { TikIcon("playFill", Modifier.size(30.dp), tint = Color.White) }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(
                    drama.title, fontSize = 18.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.Bold, color = Color.White,
                )
                if (drama.sub.isNotEmpty()) {
                    Text(
                        drama.sub, fontSize = 13.sp, fontFamily = InterTight,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            PlayerCloseButton(Modifier.align(Alignment.TopStart).padding(14.dp), onClose)
        }
        return
    }

    val ctx = LocalContext.current
    var fsView by remember { mutableStateOf<View?>(null) }
    var fsCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Pane-owned WebView (Part B architecture): created here, destroyed in
    // the DisposableEffect below, touched by no other composable. Fullscreen
    // is hosted INSIDE the player only. This surface is never FLAG_SECURE.
    // ROUND 15: built with the ACTIVITY context (ctx), not
    // applicationContext — the application-context WebView was a root
    // cause of the owner's black-video report on device (audio playing
    // over a permanently dark picture, here and in the Tik Watch pane).
    val webView = remember(drama.id) {
        WebView(ctx).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
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
                userAgentString = WebSettings.getDefaultUserAgent(ctx)
            }
            setBackgroundColor(android.graphics.Color.BLACK)
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null) {
                        fsView = view
                        fsCallback = callback
                    }
                }

                override fun onHideCustomView() {
                    val cb = fsCallback
                    fsView = null
                    fsCallback = null
                    runCatching { cb?.onCustomViewHidden() }
                    // ROUND 54 (Part C): leaving fullscreen hands the
                    // status bars back — shown, dark icons.
                    restoreStatusBars(ctx)
                }
            }
            // ROUND 18 — REAL WATCH PAGE: playback is the actual
            // m.youtube.com/watch page (big full-width player,
            // scrollable title / actions / comments / related) — the
            // same cure that fixed the Tik Watch pane in Round 17. The
            // Round-14 locked nocookie embed is RETIRED here too: on
            // the owner's phone YouTube rejects the embed document
            // outright with "Video player configuration error"
            // (Error 153), even loaded directly (the Round-15 route),
            // while the real watch page plays. The watch page's own
            // fullscreen button drives the ChromeClient custom view
            // above — hosted inside this player surface, Back exits
            // fullscreen first.
            // Navigation policy mirrors the Watch pane's
            // watchNavAllowed (Watch.kt — private to that file, so the
            // identical policy is stated locally as facadeNavAllowed):
            // only MAIN-FRAME requests are judged; YouTube hosts and
            // the Google sign-in flow pass, external hosts and
            // app-intent schemes are refused. The page's own
            // subframe/subresource traffic is its business.
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val req = request ?: return false
                    if (!req.isForMainFrame) return false
                    return !facadeNavAllowed(req.url)
                }
            }
            loadUrl("https://m.youtube.com/watch?v=${drama.id}")
        }
    }

    fun exitFullscreen() {
        // Completing the callback runs the client's onHideCustomView.
        if (fsView != null) {
            val cb = fsCallback
            fsView = null
            fsCallback = null
            runCatching { cb?.onCustomViewHidden() }
        }
    }
    BackHandler(enabled = fsView != null) { exitFullscreen() }
    BackHandler(enabled = fsView == null) { onClose() }

    DisposableEffect(webView) {
        onDispose {
            // ROUND 54 (Part C): the player closing restores the
            // status bars, whatever state fullscreen left behind.
            restoreStatusBars(ctx)
            // Watch history is written as the player closes (sole writer).
            FacadeStore.recordWatch(ctx, drama)
            if (fsView != null) {
                val cb = fsCallback
                fsView = null
                fsCallback = null
                runCatching { cb?.onCustomViewHidden() }
            }
            runCatching { webView.stopLoading() }
            runCatching { webView.loadUrl("about:blank") }
            (webView.parent as? ViewGroup)?.removeView(webView)
            runCatching { webView.destroy() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        val fs = fsView
        if (fs != null) {
            AndroidView(
                factory = { fs },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        } else {
            PlayerCloseButton(Modifier.align(Alignment.TopStart).padding(14.dp), onClose)
        }
    }
}

@Composable
private fun PlayerCloseButton(modifier: Modifier = Modifier, onClose: () -> Unit) {
    Box(
        modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onClose() },
        contentAlignment = Alignment.Center,
    ) { TikIcon("x", Modifier.size(18.dp), tint = Color.White) }
}

// ---------- STORY READER (the shelf itself lives in Facade.kt) ----------

@Composable
private fun StoryReader(story: DramaContent.Story, onBack: () -> Unit) {
    val pal = LocalPalette.current
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
            GlassBackButton(onBack)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    story.title, fontSize = 26.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.Bold, color = pal.tx, lineHeight = 32.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagChip(story.tag)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${story.minutes} min read", fontSize = 12.sp,
                        fontFamily = InterTight, color = pal.sub,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                Box(
                    Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(26.dp))
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        story.body.split("\n\n").forEach { para ->
                            Text(
                                para, fontSize = 16.sp, fontFamily = InterTight,
                                color = pal.tx, lineHeight = 27.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- PHOTO VIEWER (the shelf itself lives in Facade.kt) ----------

@Composable
private fun PhotoViewer(index: Int, onBack: () -> Unit) {
    val (file, caption) = DramaContent.PHOTOS[index]
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FacadeAssetImage(
            file = file,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier.align(Alignment.TopStart).padding(14.dp).size(38.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onBack() },
            contentAlignment = Alignment.Center,
        ) { TikIcon("back", Modifier.size(20.dp), tint = Color.White) }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
                .glassSurface(RoundedCornerShape(999.dp), useLargeGlass = true, blur = true)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(
                caption, fontSize = 14.sp, fontFamily = InterTight,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ---------- EPISODE GATE (ROUND 54, Part A → ROUND 55, Part A) ----------
// Reached ONLY via SecretEntry, from the double tap on the last
// story ("First romance of them") on the Stories shelf. The old
// visible password gate is RETIRED: a third person who reaches this
// screen sees an ordinary "find your episode" search panel, because
// that is all it shows. The journal password is now entered only in
// that episode box (it unlocks when it matches — see
// EpisodeGateScreen in Facade.kt, which owns the panel, the decoy
// search and the attempt order). ROUND 55: Google sign-in lives at
// the TOP OF THIS PANEL, shown only while signed out (the facade
// home itself carries no account surface at all — the Round-54 home
// chip was a live password bypass and is deleted). Signing in here
// still does NOT open Tik: a returning account lands back on the
// facade and enters through this box, password or recovery code —
// there is no other way in. Every lock trigger lands on the facade,
// and this panel is the only door back in.

@Composable
fun TikGateScreen(onGoogleSignIn: () -> Unit = {}) {
    EpisodeGateScreen(onGoogleSignIn)
}
