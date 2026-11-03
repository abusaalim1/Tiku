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
 * Stories shelf ("2023,05"): a single tap reads it like any other
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
                }
            }
            // ROUND 14 — LOCKED EMBED: playback is the youtube-nocookie
            // embed page itself (no watch page, no "Open App" pill, no
            // comments, no related-video rail), and every main-frame
            // navigation away from it is refused — watch pages, channel
            // pages, intent:// and market:// alike.
            // ROUND 15: the embed now loads DIRECTLY (loadUrl — see
            // loadYoutubeEmbed in Watch.kt; the old loadData iframe
            // wrapper rendered black on device). The one main-frame
            // target allowed through is this drama's embed document
            // itself, so a redirect back to it can never cancel the
            // load; subframe traffic is the player's own business.
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val req = request ?: return true
                    if (!req.isForMainFrame) return false
                    return !req.url.toString()
                        .startsWith(YOUTUBE_EMBED_PREFIX + drama.id)
                }
            }
            loadYoutubeEmbed(this, drama.id)
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

// ---------- PASSWORD GATE (reached ONLY via SecretEntry, from the
// double tap on the last story — "2023,05" — on the Stories shelf) ----------

@Composable
fun TikGateScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = tikuSettings().value
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // ROUND 8 (item 9b): escalating wrong-attempt backoff (GateGuard) — the
    // counters persist in prefs, so leaving and reopening the gate resets
    // nothing; the countdown ticks while a lockout is running.
    var lockRemainingMs by remember { mutableLongStateOf(GateGuard.lockedRemainingMs(ctx)) }
    LaunchedEffect(lockRemainingMs) {
        while (lockRemainingMs > 0) { delay(500); lockRemainingMs = GateGuard.lockedRemainingMs(ctx) }
    }
    // BUG-0: the gate distinguishes THREE states — a verifiable password, a
    // missing verifier, and a DAMAGED verifier. Only the first may ever produce
    // the words "Wrong password"; missing/damaged data routes to the Google-
    // gated rescue instead (offered only when this phone still holds the key).
    val storedHash = remember { SettingsStore.passwordHash(ctx) }
    val hasHash = storedHash != null
    val hashDamaged = hasHash && !Crypto.hashWellFormed(storedHash)
    val rescuable = remember {
        !Config.PREVIEW_UI_ONLY && FirebaseEngine.hasLocalIdentity(ctx)
    }
    // ROUND 10 PARITY: the forgot-password link belongs to EVERY signed-in
    // account, key or no key — the reset screen itself picks Mode R (key
    // present) vs Mode F (key lost). GateGuard's backoff throttles password
    // GUESSES only; it must never hide or block this link.
    val signedInUser = remember {
        !Config.PREVIEW_UI_ONLY && FirebaseEngine.signedIn
    }

    BackHandler { AppState.go(Screen.Facade) }
    FormScaffold("Your journal is locked", "Enter your password to open your journal.") {
        when {
            hasHash && !hashDamaged -> {
                PasswordField(pw, { pw = it; error = "" }, "Password")
                Spacer(Modifier.height(6.dp))
                if (lockRemainingMs > 0) {
                    Text("Too many tries in a row — take a short break and try again in ${(lockRemainingMs + 999) / 1000}s.",
                        fontSize = 13.sp, color = TikuTokens.Red, fontFamily = Manrope)
                } else {
                    Text(error, fontSize = 13.sp, color = TikuTokens.Red, fontFamily = Manrope)
                }
                if (signedInUser) {
                    // ROUND 3.1 widened this link to every key-holder; ROUND 10
                    // completes the parity: it now shows for every SIGNED-IN
                    // account regardless of hasLocalIdentity (the owner lost
                    // his key to an uninstall and was the only user with no
                    // reset path). The reset itself stays exactly as gated as
                    // before (Google re-auth + uid binding inside
                    // ResetPasswordScreen; Mode F adds the one-way warning).
                    Spacer(Modifier.height(10.dp))
                    Text("Forgot password? Reset with Google",
                        fontSize = 14.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        color = settings.accent,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) { AppState.go(Screen.PasswordReset) })
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(if (busy) "Opening…" else "Open journal", enabled = pw.isNotEmpty() && !busy && lockRemainingMs <= 0, modifier = Modifier.fillMaxWidth()) {
                    busy = true
                    scope.launch {
                        val hash = SettingsStore.passwordHash(ctx)
                        val ok = hash != null && Crypto.hashWellFormed(hash) &&
                            withContext(Dispatchers.Default) { Crypto.passwordVerify(pw, hash) }
                        if (ok) {
                            GateGuard.reset(ctx)
                            // first unlock in preview = run onboarding before the home
                            val onboarded = ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                                .getBoolean("onboarded", false)
                            if (Config.PREVIEW_UI_ONLY && !onboarded) {
                                ChatSession.unlocked = true
                                AppState.go(Screen.Welcome)
                            } else {
                                ChatSession.unlockFlow(ctx)
                            }
                        } else {
                            // ROUND 8: feed the backoff and wipe the typed
                            // guess from the field — a wrong password should
                            // never linger on screen.
                            val delayMs = GateGuard.recordFailure(ctx)
                            lockRemainingMs = GateGuard.lockedRemainingMs(ctx)
                            pw = ""
                            error = if (delayMs > 0) {
                                "Too many tries in a row — take a short break and try again in ${delayMs / 1000}s."
                            } else "Wrong password — try again."
                        }
                        busy = false
                    }
                }
            }
            rescuable -> {
                Text(
                    if (hashDamaged) "Your saved lock data on this phone looks damaged, so your password can't be checked here."
                    else "This phone still holds your journal key, but its password record is missing.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                Spacer(Modifier.height(8.dp))
                Text("You can reset the password with the Google account this journal was created with — your notes and chats stay intact.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Reset password with Google", modifier = Modifier.fillMaxWidth()) {
                    AppState.go(Screen.PasswordReset)
                }
            }
            else -> {
                Text("Set up your journal in a minute — your private corner, all yours.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                Spacer(Modifier.height(8.dp))
                Text("Used your journal on another phone before? Tap Get started, sign in with the same Google account, and your password brings everything back on this phone.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                if (signedInUser) {
                    // ROUND 10 PARITY: signed in but nothing set up on THIS
                    // phone (e.g. after a reinstall) — the forgot-password
                    // link lives here too, not only behind a saved verifier.
                    Spacer(Modifier.height(10.dp))
                    Text("Forgot password? Reset with Google",
                        fontSize = 14.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        color = settings.accent,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) { AppState.go(Screen.PasswordReset) })
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Get started", onClick = { ChatSession.unlocked = true; AppState.go(Screen.Welcome) })
            }
        }
    }
}
