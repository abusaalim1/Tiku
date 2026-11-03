package com.tiku.app

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * The facade runs on the design's LIGHT palette (Rose Petal brand accent)
 * regardless of the vault's own mode — provided locally in [FacadeHome].
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
    // The facade's brand palette: the design's LIGHT mode, Rose Petal —
    // independent of the vault's dark/light setting (spec Part C).
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
            else -> {
                Box(Modifier.fillMaxSize().padding(bottom = 102.dp)) {
                    when (tab) {
                        FacadeTab.HOME -> DramaHomeTab(
                            history = history,
                            favs = favs,
                            onPlay = { playerId = it.id },
                            onToggleFav = { d ->
                                FacadeStore.toggleFavourite(ctx, d.id)
                                favs = FacadeStore.favourites(ctx)
                            },
                        )
                        FacadeTab.STORIES -> StoriesTab(
                            onOpen = { storyId = it.id },
                            // The last story's double-tap door (see StoryCard).
                            onSecretGate = { SecretEntry.openGate(ctx, scope) },
                        )
                        FacadeTab.PHOTOS -> PhotosTab { photoIdx = it }
                    }
                }
                FacadeBottomNav(
                    tab,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                ) { tab = it }
            }
        }
    }
}

/** The facade's own glass bottom nav — same construction as the vault's
 *  BottomGlassNavBar (floating glass pill + sliding lens), drama tabs. */
@Composable
private fun FacadeBottomNav(tab: FacadeTab, modifier: Modifier = Modifier, onTab: (FacadeTab) -> Unit) {
    val pal = LocalPalette.current
    val tabs = listOf(
        Triple(FacadeTab.HOME, "Home", "play"),
        Triple(FacadeTab.STORIES, "Stories", "edit"),
        Triple(FacadeTab.PHOTOS, "Photos", "img"),
    )
    val selIdx = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val segW = (maxWidth - 10.dp) / 3
        val lensX by animateDpAsState(
            segW * selIdx,
            spring(dampingRatio = 0.68f, stiffness = 380f), label = "facadeLensX",
        )
        Box(
            Modifier.glassSurface(RoundedCornerShape(999.dp), useLargeGlass = true, blur = true)
                .padding(5.dp),
        ) {
            Box(
                Modifier.offset(x = lensX).width(segW).height(50.dp)
                    .clip(RoundedCornerShape(999.dp)).background(pal.lens)
                    .border(1.dp, pal.gb, RoundedCornerShape(999.dp)),
            )
            Row(Modifier.fillMaxWidth()) {
                tabs.forEach { (t, label, icon) ->
                    val on = t == tab
                    Box(Modifier.weight(1f).height(50.dp)) {
                        Column(
                            Modifier.fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onTab(t) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            TikIcon(
                                icon, Modifier.size(24.dp),
                                tint = if (on) pal.ac else pal.sub,
                                strokeWidth = if (on) 2f else 1.6f,
                            )
                            Text(
                                label, fontSize = 10.5.sp, fontFamily = InterTight,
                                fontWeight = FontWeight.Medium,
                                color = if (on) pal.ac else pal.sub,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- shared facade bits ----------

@Composable
private fun FacadeSectionTitle(title: String) {
    val pal = LocalPalette.current
    Text(
        title, fontSize = 19.sp, fontFamily = InterTight,
        fontWeight = FontWeight.Bold, color = pal.tx,
    )
}

@Composable
private fun LangChip(lang: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(lang, fontSize = 10.sp, fontFamily = InterTight, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

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

// ---------- HOME TAB ----------

@Composable
private fun DramaHomeTab(
    history: List<FacadeStore.HistoryEntry>,
    favs: Set<String>,
    onPlay: (DramaContent.Drama) -> Unit,
    onToggleFav: (DramaContent.Drama) -> Unit,
) {
    val pal = LocalPalette.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(Modifier.padding(top = 26.dp)) {
                Text(
                    DramaContent.appName, fontSize = 32.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.Bold, color = pal.tx,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    DramaContent.tagline, fontSize = 13.5.sp, fontFamily = InterTight,
                    color = pal.sub,
                )
            }
        }
        DramaContent.hero?.let { hero ->
            item { HeroCard(hero, onPlay) }
        }
        if (history.isNotEmpty()) {
            item {
                Column {
                    FacadeSectionTitle("Continue Watching")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(history, key = { it.id }) { e ->
                            val d = DramaContent.drama(e.id)
                                ?: DramaContent.Drama(e.id, e.title, "Continue watching", "", "")
                            DramaCard(d, favs.contains(d.id), onPlay, onToggleFav)
                        }
                    }
                }
            }
        }
        DramaContent.rows.forEach { row ->
            item {
                Column {
                    FacadeSectionTitle(row.title)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(row.items, key = { it.id }) { d ->
                            DramaCard(d, favs.contains(d.id), onPlay, onToggleFav)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun HeroCard(hero: DramaContent.Drama, onPlay: (DramaContent.Drama) -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 10.5f)
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onPlay(hero) },
    ) {
        AsyncImage(
            model = DramaContent.photoModel(DramaContent.HERO_PHOTO),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.38f)),
                ),
            ),
        )
        // glass info panel over the still
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)
                .glassSurface(RoundedCornerShape(22.dp), useLargeGlass = true, blur = true)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(pal.ac)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "${hero.lang} Dubbed", fontSize = 10.sp, fontFamily = InterTight,
                        fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    hero.title, fontSize = 20.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.Bold, color = pal.tx,
                )
                Text(
                    "${hero.sub} · ${hero.genre}", fontSize = 12.sp, fontFamily = InterTight,
                    color = pal.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(pal.buttonGradient),
                contentAlignment = Alignment.Center,
            ) { TikIcon("playFill", Modifier.size(20.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun DramaCard(
    drama: DramaContent.Drama,
    fav: Boolean,
    onPlay: (DramaContent.Drama) -> Unit,
    onToggleFav: (DramaContent.Drama) -> Unit,
) {
    val pal = LocalPalette.current
    Column(Modifier.width(168.dp)) {
        Box {
            AsyncImage(
                model = DramaContent.thumbUrl(drama.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onPlay(drama) },
            )
            if (drama.lang.isNotEmpty()) {
                LangChip(drama.lang, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp)
                    .glassSurface(CircleShape, blur = true)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onToggleFav(drama) },
                contentAlignment = Alignment.Center,
            ) {
                TikIcon(
                    if (fav) "heartFill" else "heart", Modifier.size(16.dp),
                    tint = if (fav) pal.ac else pal.tx,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            drama.title, fontSize = 13.5.sp, fontFamily = InterTight,
            fontWeight = FontWeight.Medium, color = pal.tx,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (drama.sub.isNotEmpty()) {
            Text(
                drama.sub, fontSize = 11.5.sp, fontFamily = InterTight, color = pal.sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
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
    val webView = remember(drama.id) {
        WebView(ctx.applicationContext).apply {
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
            // ROUND 14 — LOCKED EMBED: playback is a local page whose only
            // content is a youtube-nocookie iframe (no watch page, no "Open
            // App" pill, no comments, no related-video rail), and EVERY
            // main-frame navigation away from that document is refused —
            // watch pages, channel pages, intent:// and market:// alike.
            // (The initial loadDataWithBaseURL load is programmatic and
            // never passes through shouldOverrideUrlLoading; iframe
            // subresource loads are unaffected.)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = true
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

// ---------- STORIES TAB + READER ----------

/** The bundled id of the LAST story on the shelf — the secret door.
 *  Its card renders exactly like every other card; only its tap
 *  handling differs (see [StoryCard]). */
private const val SECRET_STORY_ID = "story-2023-05"

@Composable
private fun StoriesTab(
    onOpen: (DramaContent.Story) -> Unit,
    onSecretGate: () -> Unit,
) {
    val pal = LocalPalette.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(top = 26.dp, bottom = 6.dp)) {
                Text(
                    DramaContent.storiesTitle, fontSize = 28.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.Bold, color = pal.tx,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tiny love stories from Seoul", fontSize = 13.5.sp,
                    fontFamily = InterTight, color = pal.sub,
                )
            }
        }
        items(DramaContent.stories, key = { it.id }) { story ->
            StoryCard(
                story = story,
                onClick = { onOpen(story) },
                // Only the last story carries the hidden door; its card
                // is visually identical to all the others.
                onDoubleTap = if (story.id == SECRET_STORY_ID) onSecretGate else null,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun StoryCard(
    story: DramaContent.Story,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
) {
    val pal = LocalPalette.current
    val preview = story.body.split("\n\n").firstOrNull()?.replace('\n', ' ') ?: ""
    val interaction = remember { MutableInteractionSource() }
    // Cards without a double-tap action keep the plain clickable; the
    // one secret card uses detectTapGestures so a double tap fires the
    // gate WITHOUT the single-tap reader opening first.
    val tapModifier = if (onDoubleTap == null) {
        Modifier.clickable(
            interactionSource = interaction, indication = null,
        ) { onClick() }
    } else {
        Modifier.pointerInput(story.id) {
            detectTapGestures(
                onTap = { onClick() },
                onDoubleTap = { onDoubleTap() },
            )
        }
    }
    Box(
        Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp))
            .then(tapModifier)
            .padding(18.dp),
    ) {
        Column {
            Text(
                story.title, fontSize = 16.5.sp, fontFamily = InterTight,
                fontWeight = FontWeight.SemiBold, color = pal.tx,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                preview, fontSize = 13.sp, fontFamily = InterTight, color = pal.sub,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(story.tag)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${story.minutes} min read", fontSize = 11.5.sp,
                    fontFamily = InterTight, color = pal.sub,
                )
            }
        }
    }
}

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

// ---------- PHOTOS TAB + VIEWER ----------

@Composable
private fun PhotosTab(onOpen: (Int) -> Unit) {
    val pal = LocalPalette.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 26.dp, bottom = 2.dp)) {
                Text(
                    "Moments", fontSize = 28.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.Bold, color = pal.tx,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Cute, romantic stills to keep", fontSize = 13.5.sp,
                    fontFamily = InterTight, color = pal.sub,
                )
            }
        }
        itemsIndexed(DramaContent.PHOTOS, key = { _, p -> p.first }) { idx, (file, caption) ->
            Column {
                AsyncImage(
                    model = DramaContent.photoModel(file),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) { onOpen(idx) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    caption, fontSize = 12.5.sp, fontFamily = InterTight,
                    fontWeight = FontWeight.Medium, color = pal.tx,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(2.dp)) }
    }
}

@Composable
private fun PhotoViewer(index: Int, onBack: () -> Unit) {
    val (file, caption) = DramaContent.PHOTOS[index]
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = DramaContent.photoModel(file),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
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
