package com.tiku.app

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ROUND 16 — THE FACADE, OWNER'S NEW DESIGN. The K-drama outer app
 * (Home / Stories / Photos) is now the owner's `TikuRedesign_5` file,
 * ported directly: pure-black system, canvas-drawn tab icons, the rose
 * sliding-lens NavPill, and the file's own motion (AnimatedContent tab
 * transitions, rise stagger, press springs, poster Crossfades) — the
 * navigation and animations are the file's, unchanged.
 *
 * Only the DATA and the EXITS are wired to the real app:
 *  - Shows come from DramaContent's bundled pack (assets/facade/
 *    dramas.json); posters are each video's real YouTube poster.
 *  - Continue Watching renders ONLY FacadeStore's real watch history
 *    (absent when empty — no placeholder rows, ever).
 *  - Favourites read/write FacadeStore (persisted), keyed by drama id.
 *  - Tapping a show opens the existing FacadePlayer (DramaShell.kt);
 *    tapping a story opens the existing StoryReader; tapping a photo
 *    opens the existing PhotoViewer.
 *  - Stories come from the bundled stories.json — and the LAST one is
 *    still the secret door (see SECRET_STORY_ID below).
 *
 * Shared design-system pieces are the app's existing copies from the
 * Round-14 port of the owner's Tik file (identical values and motion
 * code): the Rose / Sub / CardC tokens and the rise / press modifiers
 * in Redesign.kt. The facade-only tokens (Bg / Ink / RoseSoft / Ease)
 * are file-private here, exactly as in the owner's file.
 *
 * Zero-trace rule (unchanged): no chat data, no usernames, and the
 * word "Tik" appears nowhere on this surface.
 */

// ---- tokens (verbatim from the owner's file; Rose / Sub / CardC are
// the shared Redesign.kt copies — same values, same design system) ----
private val Bg = Color(0xFF000000)
private val Ink = Color.White
private val RoseSoft = Color(0x29FF5C8A)
private val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/* ───────────────────────── Data ─────────────────────────
 * The owner's file shape (Show / Story / Moment), carrying one extra
 * field the demo didn't need: the real id, so taps, favourites and
 * history resolve against DramaContent / FacadeStore instead of
 * display titles (the pack contains duplicate titles).
 */

private data class Show(
    val id: String,
    val title: String, val sub: String, val lang: String,
    val g: List<Color>, val img: String? = null, val res: Int = 0
)

private data class Story(
    val id: String,
    val title: String, val excerpt: String, val tag: String, val mins: Int
)

private data class Moment(
    val title: String, val g: List<Color>, val img: String? = null, val res: Int = 0,
    val asset: String = ""
)

/** Poster underlays, in the owner's file's palette spirit — a failed or
 *  still-loading poster always sits on a designed gradient, never a
 *  black box. The hero keeps the file's own heroShow gradient. */
private val heroGradient = listOf(Color(0xFFE9C9B8), Color(0xFFB9A38F), Color(0xFF6F7A5C))

private val rowGradients = listOf(
    listOf(Color(0xFFD96A8A), Color(0xFF7A3B4A)),
    listOf(Color(0xFF3F4A3A), Color(0xFF1B1F1B)),
    listOf(Color(0xFFFF8FB0), Color(0xFF6B7BA8)),
    listOf(Color(0xFFF2C94C), Color(0xFF6FA36B)),
    listOf(Color(0xFF7BA7D9), Color(0xFF2F3E5C)),
    listOf(Color(0xFF6DC7C2), Color(0xFF2E5C63)),
    listOf(Color(0xFF8FD6B0), Color(0xFF3E6B57)),
)

private val momentGradients = listOf(
    listOf(Color(0xFFE6B567), Color(0xFF4B5A73)),
    listOf(Color(0xFFF3D9B0), Color(0xFF8A6A4A)),
    listOf(Color(0xFFFBE3EA), Color(0xFFB9A3A8)),
    listOf(Color(0xFFF59B7A), Color(0xFF6B4A5E)),
    listOf(Color(0xFF6C8FB8), Color(0xFF1F2A3D)),
    listOf(Color(0xFF3C4A66), Color(0xFF151B2B)),
    listOf(Color(0xFFFFB7C5), Color(0xFF7A4A5E)),
    listOf(Color(0xFFA8C686), Color(0xFF3E5641)),
)

private fun DramaContent.Drama.toShow(g: List<Color>, heroStyle: Boolean = false): Show = Show(
    id = id,
    title = title,
    // The file's hero sub carries the genre ("Episode 2 · Hindi Dubbed
    // · Romance · Melodrama"); row cards carry the plain sub.
    sub = if (heroStyle && genre.isNotBlank()) "$sub · $genre" else sub,
    // The hero's chip is the pack's badge ("Hindi Dubbed") — the parsed
    // Drama keeps it as lang ("Hindi"), so the badge is rebuilt here.
    lang = if (heroStyle && lang.isNotBlank()) "$lang Dubbed" else lang,
    g = g,
    img = DramaContent.thumbUrl(id),
)

private fun heroShow(): Show? =
    DramaContent.hero?.toShow(heroGradient, heroStyle = true)

private fun continueShows(history: List<FacadeStore.HistoryEntry>): List<Show> =
    history.mapIndexed { i, e ->
        val d = DramaContent.drama(e.id)
            ?: DramaContent.Drama(e.id, e.title, "Continue watching", "", "")
        d.toShow(rowGradients[i % rowGradients.size])
    }

private fun dramaRows(): List<Pair<String, List<Show>>> =
    DramaContent.rows.map { row ->
        row.title to row.items.mapIndexed { i, d ->
            d.toShow(rowGradients[i % rowGradients.size])
        }
    }

private fun facadeStories(): List<Story> =
    DramaContent.stories.map { s ->
        Story(
            id = s.id,
            title = s.title,
            // The card preview is the body's first paragraph (the same
            // derivation the old facade used); the composable's
            // 2-line ellipsis does the rest.
            excerpt = s.body.split("\n\n").firstOrNull()?.replace('\n', ' ') ?: "",
            tag = s.tag,
            mins = s.minutes,
        )
    }

private fun facadeMoments(): List<Moment> =
    DramaContent.PHOTOS.mapIndexed { i, (file, caption) ->
        Moment(
            title = caption,
            g = momentGradients[i % momentGradients.size],
            asset = file,
        )
    }

/** Dark frosted glass (the file's glassLight). The haze state is the
 *  shell's ambient one (FacadeShell marks the content as its source,
 *  as it always has); where no state exists — the screenshot harness —
 *  the pill falls back to the same tint without the blur, the app's
 *  standing no-haze contract (see Redesign.kt's glass/backdrop). */
private fun Modifier.glassLight(h: HazeState?, shape: Shape): Modifier {
    val clipped = this.clip(shape)
    val glazed = if (h != null) {
        clipped.hazeEffect(
            state = h,
            style = HazeStyle(
                backgroundColor = Color.Transparent,
                tint = HazeTint(Color.White.copy(alpha = 0.07f)),
                blurRadius = 24.dp,
                noiseFactor = 0f
            )
        )
    } else {
        clipped.background(Color.White.copy(alpha = 0.07f))
    }
    return glazed.border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.White.copy(0.06f))),
        shape
    )
}

/* ───────────────────────── Image ───────────────────────── */

@Composable
private fun Poster(img: String?, res: Int, g: List<Color>, modifier: Modifier, asset: String? = null) {
    val ctx = LocalContext.current
    // Bundled stills (the Photos shelf) are assets, not URLs — decoded
    // locally, the Round-15 FacadeAssetImage pattern, so they can never
    // miss the way the old file:// Coil models did.
    val assetBmp = remember(asset) {
        asset?.let {
            runCatching {
                ctx.assets.open("facade/photos/$it").use { s -> BitmapFactory.decodeStream(s) }
            }.getOrNull()?.asImageBitmap()
        }
    }
    var bmp by remember(img) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(img) {
        if (!img.isNullOrBlank()) {
            bmp = withContext(Dispatchers.IO) {
                val first = runCatching {
                    java.net.URL(img).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
                // Round-15 fallback: a failed hq poster retries as mq
                // before the card settles for its gradient.
                first ?: runCatching {
                    java.net.URL(img.replace("hqdefault.jpg", "mqdefault.jpg"))
                        .openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(modifier.background(Brush.linearGradient(g))) {
        when {
            assetBmp != null -> {
                Image(assetBmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            res != 0 -> {
                Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            else -> {
                Crossfade(bmp, animationSpec = tween(450), label = "poster") { b ->
                    if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

/* ───────────────────────── Shared bits ───────────────────────── */

@Composable
private fun Header(title: String, sub: String) {
    Column(Modifier.padding(horizontal = 20.dp).rise()) {
        Text(title, color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, fontFamily = InterTight)
        Text(sub, color = Sub, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp), fontFamily = PoppinsFamily)
    }
}

@Composable
private fun SectionTitle(t: String, idx: Int) {
    Text(
        t, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp).rise(idx), fontFamily = InterTight)
}

@Composable
private fun topInset() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
private fun bottomInset() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/* ───────────────────────── Home ───────────────────────── */

@Composable
private fun Hero(s: Show, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 24.dp).fillMaxWidth().height(245.dp)
            .rise(1).clip(shape).press(onPlay)
    ) {
        Poster(s.img, s.res, s.g, Modifier.fillMaxSize())
        val bar = RoundedCornerShape(28.dp)
        Row(
            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().clip(bar)
                .background(Color.Black.copy(0.38f))
                .border(1.dp, Color.White.copy(0.16f), bar)
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Box(Modifier.clip(CircleShape).background(Rose).padding(horizontal = 11.dp, vertical = 4.dp)) {
                    Text(s.lang, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily)
                }
                Text(
                    s.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp), fontFamily = InterTight)
                Text(s.sub, color = Color(0xFFC9C8D4), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = PoppinsFamily)
            }
            Box(
                Modifier.padding(start = 10.dp).size(44.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFF5C8A), Color(0xFFFF7AA2)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun ShowCard(s: Show, liked: Boolean, idx: Int, onLike: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(158.dp).rise(idx).press(onClick)) {
        Box(Modifier.fillMaxWidth().height(89.dp).clip(RoundedCornerShape(20.dp))) {
            Poster(s.img, s.res, s.g, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(0.42f)).padding(horizontal = 8.dp, vertical = 3.dp)
            ) { Text(s.lang, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = PoppinsFamily) }

            val pop by animateFloatAsState(if (liked) 1.12f else 1f, spring(0.4f, 420f), label = "heart")
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(28.dp).scale(pop).clip(CircleShape)
                    .background(Color.Black.copy(0.45f)).press(onLike),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                    tint = if (liked) Rose else Color.White, modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            s.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp), fontFamily = InterTight)
        Text(s.sub, color = Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = PoppinsFamily)
    }
}

@Composable
private fun ShowRow(
    list: List<Show>,
    section: String,
    favs: Set<String>,
    onToggleFav: (Show) -> Unit,
    onOpen: (Show) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(list.size) { i ->
            val s = list[i]
            ShowCard(s, favs.contains(s.id), i, { onToggleFav(s) }, { onOpen(s) })
        }
    }
}

@Composable
private fun HomeScreen(
    hero: Show?,
    continueList: List<Show>,
    rows: List<Pair<String, List<Show>>>,
    favs: Set<String>,
    onToggleFav: (Show) -> Unit,
    onOpen: (Show) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset() + 24.dp, bottom = 140.dp)
    ) {
        item { Header(DramaContent.appName, DramaContent.tagline) }
        // ROUND 55 (Part A): NO account surface on this home — no
        // sign-in button, no Google mark, no avatar. The Round-54
        // account row that lived here is deleted: its signed-in face
        // was a live password bypass (one tap re-ran post-auth
        // routing straight into Tik). Home is a plain drama app;
        // sign-in exists only at the top of the episode gate, and
        // only while signed out.
        if (hero != null) {
            item { Spacer(Modifier.height(0.dp)); Hero(hero) { onOpen(hero) } }
        }
        var idx = 2
        if (continueList.isNotEmpty()) {
            val ci = idx++
            item { SectionTitle("Continue Watching", ci) }
            item { ShowRow(continueList, "continue", favs, onToggleFav, onOpen) }
        }
        rows.forEach { (title, list) ->
            val ri = idx++
            item { SectionTitle(title, ri) }
            item { ShowRow(list, title, favs, onToggleFav, onOpen) }
        }
    }
}

/* ───────────────────────── Stories ───────────────────────── */

/** The bundled id of the LAST story on the shelf — the secret door.
 *  Its card renders exactly like every other card; only its tap
 *  handling differs (see [StoryCard]). */
private const val SECRET_STORY_ID = "story-2023-05"

@Composable
private fun StoryCard(s: Story, idx: Int, onClick: () -> Unit, onDoubleTap: (() -> Unit)? = null) {
    // Cards without a double-tap action keep the file's .press; the one
    // secret card uses detectTapGestures (the same technique the old
    // facade used) so a double tap fires the gate WITHOUT the single-
    // tap reader opening first — with the file's press spring kept via
    // the shared scale state below.
    var down by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (down) 0.95f else 1f, spring(0.6f, 500f), label = "press")
    val tap = if (onDoubleTap == null) {
        Modifier.press(onClick)
    } else {
        Modifier.scale(sc).pointerInput(s.id) {
            detectTapGestures(
                onPress = { down = true; tryAwaitRelease(); down = false },
                onTap = { onClick() },
                onDoubleTap = { onDoubleTap() },
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 13.dp).rise(idx)
            .clip(RoundedCornerShape(28.dp)).background(CardC).then(tap).padding(18.dp)
    ) {
        Text(s.title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, fontFamily = InterTight)
        Text(
            s.excerpt, color = Sub, fontSize = 16.sp, lineHeight = 24.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp), fontFamily = PoppinsFamily)
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(RoseSoft).padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text(s.tag, color = Rose, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily)
            }
            Text("${s.mins} min read", color = Sub, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp), fontFamily = PoppinsFamily)
        }
    }
}

@Composable
private fun StoriesScreen(
    stories: List<Story>,
    onOpen: (Story) -> Unit,
    onSecretGate: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset() + 24.dp, bottom = 140.dp)
    ) {
        item { Header(DramaContent.storiesTitle, "Short reads for every mood"); Spacer(Modifier.height(22.dp)) }
        items(stories.size) { i ->
            val s = stories[i]
            StoryCard(
                s, i + 1,
                onClick = { onOpen(s) },
                // Only the last story carries the hidden door; its card
                // is visually identical to all the others.
                onDoubleTap = if (s.id == SECRET_STORY_ID) onSecretGate else null,
            )
        }
    }
}

/* ───────────────────────── Photos ───────────────────────── */

@Composable
private fun MomentCard(m: Moment, idx: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.rise(idx).press(onClick)) {
        Poster(
            m.img, m.res, m.g,
            Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(28.dp)),
            asset = m.asset.ifEmpty { null },
        )
        Text(
            m.title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp), fontFamily = InterTight)
    }
}

@Composable
private fun PhotosScreen(moments: List<Moment>, onOpen: (Int) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset() + 24.dp, bottom = 140.dp)
    ) {
        item { Header("Moments", "Cute, romantic stills to keep"); Spacer(Modifier.height(22.dp)) }
        items(moments.chunked(2).size) { r ->
            val pair = moments.chunked(2)[r]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                pair.forEachIndexed { c, m -> MomentCard(m, r * 2 + c + 1, Modifier.weight(1f)) { onOpen(r * 2 + c) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ───────────────────────── Floating glass nav ───────────────────────── */

@Composable
private fun TabIcon(kind: Int, color: Color, filled: Boolean) {
    Canvas(Modifier.size(26.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width
        val sw = w * 0.09f
        when (kind) {
            0 -> { // play in rounded square
                val tri = Path().apply {
                    moveTo(w * 0.42f, w * 0.32f); lineTo(w * 0.71f, w * 0.5f); lineTo(w * 0.42f, w * 0.68f); close()
                }
                if (filled) {
                    drawRoundRect(color, Offset(w * 0.07f, w * 0.07f), Size(w * 0.86f, w * 0.86f), CornerRadius(w * 0.27f))
                    drawPath(tri, Color.Black, blendMode = BlendMode.Clear)
                } else {
                    drawRoundRect(
                        color, Offset(w * 0.1f, w * 0.1f), Size(w * 0.8f, w * 0.8f),
                        CornerRadius(w * 0.25f), style = Stroke(width = sw)
                    )
                    drawPath(tri, color)
                }
            }
            1 -> { // open book
                val st = if (filled) Fill else Stroke(width = sw * 0.9f)
                val o = if (filled) 0f else sw * 0.45f
                drawRoundRect(color, Offset(w * 0.05f + o, w * 0.14f + o), Size(w * 0.42f - 2 * o, w * 0.64f - 2 * o), CornerRadius(w * 0.08f), style = st)
                drawRoundRect(color, Offset(w * 0.53f + o, w * 0.14f + o), Size(w * 0.42f - 2 * o, w * 0.64f - 2 * o), CornerRadius(w * 0.08f), style = st)
                drawCircle(color, w * (if (filled) 0.09f else 0.06f), Offset(w * 0.5f, w * 0.88f))
            }
            else -> { // picture
                val mtn = Path().apply {
                    moveTo(w * 0.22f, w * 0.76f); lineTo(w * 0.42f, w * 0.53f); lineTo(w * 0.56f, w * 0.67f)
                    lineTo(w * 0.68f, w * 0.55f); lineTo(w * 0.8f, w * 0.76f); close()
                }
                if (filled) {
                    drawRoundRect(color, Offset(w * 0.07f, w * 0.07f), Size(w * 0.86f, w * 0.86f), CornerRadius(w * 0.2f))
                    drawCircle(Color.Black, w * 0.07f, Offset(w * 0.36f, w * 0.36f), blendMode = BlendMode.Clear)
                    drawPath(mtn, Color.Black, blendMode = BlendMode.Clear)
                } else {
                    drawRoundRect(
                        color, Offset(w * 0.1f, w * 0.1f), Size(w * 0.8f, w * 0.8f),
                        CornerRadius(w * 0.19f), style = Stroke(width = sw)
                    )
                    drawCircle(color, w * 0.07f, Offset(w * 0.36f, w * 0.36f))
                    drawPath(mtn, color)
                }
            }
        }
    }
}

@Composable
private fun NavPill(h: HazeState?, selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Home", "Stories", "Photos")
    val shape = RoundedCornerShape(999.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).glassLight(h, shape).padding(6.dp)
    ) {
        val w = maxWidth / 3
        val x by animateDpAsState(w * selected, spring(0.62f, 300f), label = "lens")
        // sliding lens: rose-tinted glass with a soft sheen and glowing edge
        Box(
            Modifier.offset(x = x).width(w).height(58.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(Rose.copy(0.30f), Rose.copy(0.10f))))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.14f), Color.Transparent)))
                .border(1.dp, Brush.verticalGradient(listOf(Rose.copy(0.70f), Rose.copy(0.12f))), shape)
        )
        Row {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val glow by animateFloatAsState(if (on) 1f else 0f, tween(350), label = "glow")
                val sc by animateFloatAsState(if (on) 1.1f else 1f, spring(0.45f, 380f), label = "tabS")
                val col by animateColorAsState(if (on) Color.White else Sub, tween(250), label = "tabC")
                Column(
                    Modifier.weight(1f).height(58.dp).clip(shape).press { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).graphicsLayer { alpha = glow }
                                .background(Brush.radialGradient(listOf(Rose.copy(0.40f), Color.Transparent)), CircleShape)
                        )
                        Box(Modifier.scale(sc)) { TabIcon(i, if (on) Rose else Sub, on) }
                    }
                    Text(label, color = col, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontFamily = PoppinsFamily)
                }
            }
        }
    }
}

/* ───────────────────────── Root ───────────────────────── */

/**
 * The facade's root (the owner's TikuApp, wired): tab state stays with
 * the shell (FacadeShell in DramaShell.kt) so its back handling and
 * the harness's initial-tab entry points keep working; the overlays
 * (player / reader / photo viewer) also stay with the shell. The haze
 * state is the shell's ambient one — FacadeShell already marks this
 * content as the haze source, exactly as the previous facade's nav
 * consumed it.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FacadeRoot(
    haze: HazeState?,
    tab: Int,
    onTab: (Int) -> Unit,
    history: List<FacadeStore.HistoryEntry>,
    favs: Set<String>,
    onPlay: (DramaContent.Drama) -> Unit,
    onToggleFav: (DramaContent.Drama) -> Unit,
    onOpenStory: (DramaContent.Story) -> Unit,
    onOpenPhoto: (Int) -> Unit,
    onSecretGate: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val d = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(420, easing = Ease)) { it / 6 * d } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320, easing = Ease)) { -it / 8 * d } + fadeOut(tween(200)))
                },
                label = "tab"
            ) { t ->
                when (t) {
                    0 -> HomeScreen(
                        hero = heroShow(),
                        continueList = continueShows(history),
                        rows = dramaRows(),
                        favs = favs,
                        onToggleFav = { s -> DramaContent.drama(s.id)?.let(onToggleFav) },
                        onOpen = { s -> DramaContent.drama(s.id)?.let(onPlay) },
                    )
                    1 -> StoriesScreen(
                        stories = facadeStories(),
                        onOpen = { s -> DramaContent.story(s.id)?.let(onOpenStory) },
                        onSecretGate = onSecretGate,
                    )
                    else -> PhotosScreen(
                        moments = facadeMoments(),
                        onOpen = onOpenPhoto,
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset() + 6.dp)) {
            NavPill(haze, tab, onTab)
        }
    }
}

/* ───────────────────── Account chip — DELETED (ROUND 55, Part A) ──────────
 * Round 54 put the account button here, on the home, and its
 * signed-in face (the rose initial circle) re-ran the post-auth
 * routing on tap — which walked a returning user straight into Tik
 * with no password. The owner caught it on his own phone and
 * ordered the whole surface gone: this home now shows NOTHING
 * account-related, and Google sign-in lives at the top of the
 * episode gate instead (see GateSignIn below), visible only while
 * signed out. Do not resurrect an account surface on this home. */

/* ───────────────────── Episode gate (ROUND 54, Part A) ─────────────────────
 * What the double tap on the last story opens now. On its face it is
 * a plain "find your episode" search: a box labelled "Enter your
 * episode", a Search button and a Cancel button. There is no password
 * wording, no lock imagery, no account surface and no error state —
 * because there is nothing here to explain: anything that is not a
 * credential is simply a search, and searches always succeed.
 *
 * What Search does, in order (all invisible):
 *  (i)  signed in + onboarded + the input verifies against the stored
 *       journal verifier (the EXISTING SettingsStore / Crypto check —
 *       no crypto was rebuilt) → GateGuard clears → the existing
 *       ChatSession.unlockFlow into Tik.
 *  (ii) else, signed in + the input normalizes to a recovery code and
 *       opens the account's keyRecovery blob → route into the EXISTING
 *       recovery restore flow (Screen.PasswordReset → its recovery
 *       form), where a new password is set and chats are preserved.
 *  (iii) ROUND 55: else, signed in + this device holds NO local
 *       identity for the account yet (a fresh phone) → the input is
 *       tried against the cloud keyBackup itself, via the EXISTING
 *       FirebaseEngine.restoreFromBackup — the cryptographic unwrap
 *       IS the verification. A genuine open installs the identity on
 *       this device, marks setup done and unlocks; a wrong password
 *       feeds the GateGuard and decoys like any text. (Round 54 let
 *       the home chip's Restore route carry this case; the chip is
 *       deleted, so the box carries it now — the box is the ONLY way
 *       in, for every device state.)
 *  (iv) anything else → the decoy search below.
 * A failed credential probe feeds the existing invisible GateGuard;
 * while the guard is throttling, probes are skipped entirely and the
 * search results simply show — no counter, no message, ever. Signed
 * out, the panel is a guest search: a password typed by a guest falls
 * through to results like any other text.
 *
 * ROUND 55 also puts the Google sign-in at the TOP of this panel —
 * shown ONLY while signed out (GateSignIn below). A completed
 * sign-in never opens Tik by itself: the post-auth routing returns a
 * returning account to the facade, and this box remains the only
 * door (see routeAfterGoogleAuth in MainActivity.kt). */

private enum class EpisodeAttempt { UNLOCK, RECOVERY, SEARCH }

private suspend fun episodeAttempt(ctx: android.content.Context, raw: String): EpisodeAttempt {
    if (Config.PREVIEW_UI_ONLY) return EpisodeAttempt.SEARCH
    val input = raw.trim()
    if (input.isEmpty()) return EpisodeAttempt.SEARCH
    if (GateGuard.lockedRemainingMs(ctx) > 0L) return EpisodeAttempt.SEARCH
    if (!FirebaseEngine.signedIn) return EpisodeAttempt.SEARCH
    val onboarded = ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
        .getBoolean("onboarded", false)
    if (onboarded) {
        // (i) the journal password, via the existing verifier.
        val hash = SettingsStore.passwordHash(ctx)
        if (hash != null && Crypto.hashWellFormed(hash)) {
            val ok = withContext(Dispatchers.Default) { Crypto.passwordVerify(input, hash) }
            if (ok) {
                GateGuard.reset(ctx)
                return EpisodeAttempt.UNLOCK
            }
            GateGuard.recordFailure(ctx)
        }
    }
    // (ii) a recovery code, probed against the cloud keyRecovery blob.
    if (Crypto.normalizeRecoveryCode(input).length == 26) {
        val blob = runCatching { FirebaseEngine.recoveryBlob() }.getOrNull()
        if (blob != null) {
            val opens = runCatching {
                withContext(Dispatchers.Default) { Crypto.recoveryUnwrap(blob, input) }
            }.isSuccess
            if (opens) {
                GateGuard.reset(ctx)
                return EpisodeAttempt.RECOVERY
            }
            GateGuard.recordFailure(ctx)
        }
    }
    // (iii) ROUND 55: a device holding no local identity yet (fresh
    // phone, or data cleared) has no local verifier for step (i) —
    // try the input against the cloud keyBackup itself. The unwrap
    // is the verification (AES-GCM tag): restoreFromBackup installs
    // the identity locally only when the password genuinely opens
    // the blob, so a success here is exactly as strong as step (i).
    if (!FirebaseEngine.hasLocalIdentity(ctx)) {
        when (FirebaseEngine.restoreFromBackup(ctx, input)) {
            is FirebaseEngine.RestoreResult.Ok -> {
                ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarded", true).apply()
                GateGuard.reset(ctx)
                return EpisodeAttempt.UNLOCK
            }
            is FirebaseEngine.RestoreResult.WrongPassword -> GateGuard.recordFailure(ctx)
            is FirebaseEngine.RestoreResult.Unavailable -> Unit // no backup / offline → decoy
        }
    }
    return EpisodeAttempt.SEARCH
}

/** One decoy result: a drama (optionally dressed as a specific
 *  episode) or a story, both drawn from the real facade catalogue. */
private sealed interface EpisodeHit {
    data class DramaHit(val drama: DramaContent.Drama, val g: List<Color>, val episode: Int?) : EpisodeHit
    data class StoryHit(val story: Story, val g: List<Color>) : EpisodeHit
}

private class EpisodeResults(val hits: List<EpisodeHit>, val fallback: Boolean)

/** The decoy search itself. Bare numbers become episode rows ("572"
 *  → "Episode 572" of real catalogue titles); text matches catalogue
 *  titles / genres / subs and story titles / tags; no match at all
 *  falls back to the popular shelf, the way a real search app pads a
 *  thin result set. */
private fun episodeSearch(query: String): EpisodeResults {
    val q = query.trim()
    val dramas = DramaContent.allDramas()
    if (q.isNotEmpty() && q.all { it.isDigit() }) {
        val n = q.toIntOrNull() ?: return EpisodeResults(emptyList(), fallback = true)
        return EpisodeResults(
            dramas.take(8).mapIndexed { i, d ->
                EpisodeHit.DramaHit(d, rowGradients[i % rowGradients.size], n)
            },
            fallback = false,
        )
    }
    val tokens = q.lowercase().split(Regex("\\s+")).filter { it.length >= 3 }
    fun dramaScore(d: DramaContent.Drama): Int = tokens.count { t ->
        d.title.lowercase().contains(t) || d.genre.lowercase().contains(t) ||
            d.sub.lowercase().contains(t)
    }
    val dramaHits = dramas.map { it to dramaScore(it) }
        .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    val storyHits = facadeStories().filter { s ->
        tokens.any { t -> s.title.lowercase().contains(t) || s.tag.lowercase().contains(t) }
    }
    val hits = mutableListOf<EpisodeHit>()
    dramaHits.take(8).forEachIndexed { i, d ->
        hits += EpisodeHit.DramaHit(d, rowGradients[i % rowGradients.size], null)
    }
    storyHits.take(3).forEachIndexed { i, s ->
        hits += EpisodeHit.StoryHit(s, rowGradients[(i + 2) % rowGradients.size])
    }
    if (hits.isNotEmpty()) return EpisodeResults(hits, fallback = false)
    val shelf = mutableListOf<EpisodeHit>()
    dramas.take(6).forEachIndexed { i, d ->
        shelf += EpisodeHit.DramaHit(d, rowGradients[i % rowGradients.size], null)
    }
    facadeStories().take(2).forEachIndexed { i, s ->
        shelf += EpisodeHit.StoryHit(s, rowGradients[(i + 2) % rowGradients.size])
    }
    return EpisodeResults(shelf, fallback = true)
}

/** ROUND 55 (Part A): the gate panel's Google sign-in — an ordinary
 *  app-header account action at the very top of the panel, rendered
 *  ONLY while signed out (when signed in, nothing renders here at
 *  all: no avatar, no initial, no chip). It runs the existing Google
 *  flow (the same one the Welcome screen's button runs); completing
 *  it does NOT open Tik — a returning account is routed back to the
 *  facade and still enters through the episode box below. The busy
 *  label mirrors the Welcome button's AppState.authBusy pattern. */
@Composable
private fun GateSignIn(onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .press {
                        if (!AppState.authBusy) {
                            AppState.authBusy = true
                            onClick()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(CardC),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("G", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, fontFamily = InterTight)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (AppState.authBusy) "Connecting…" else "Sign in with Google",
                    color = Color(0xFF1B1B1F), fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, fontFamily = PoppinsFamily,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
fun EpisodeGateScreen(onGoogleSignIn: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { DramaContent.load(ctx) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<EpisodeResults?>(null) }
    var searchedFor by remember { mutableStateOf("") }
    // ROUND 55: the sign-in shows only while signed out. Read once at
    // composition: a completed sign-in navigates away from this panel
    // (facade for a returning account, setup for a new one), so the
    // value never needs to flip in place.
    val signedOut = remember { !FirebaseEngine.signedIn }

    androidx.activity.compose.BackHandler {
        if (results != null) results = null else AppState.go(Screen.Facade)
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || busy) return
        busy = true
        scope.launch {
            when (episodeAttempt(ctx, q)) {
                EpisodeAttempt.UNLOCK -> { busy = false; ChatSession.unlockFlow(ctx) }
                EpisodeAttempt.RECOVERY -> { busy = false; AppState.go(Screen.PasswordReset) }
                EpisodeAttempt.SEARCH -> {
                    searchedFor = q
                    results = episodeSearch(q)
                    busy = false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(top = topInset() + 24.dp)) {
            val res = results
            if (res == null) {
                if (signedOut) GateSignIn(onGoogleSignIn)
                Header("Episodes", "Jump straight to the episode you left off")
                Spacer(Modifier.height(26.dp))
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)).background(CardC)
                        .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = Sub, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Ink, fontSize = 16.sp, fontFamily = PoppinsFamily),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Rose),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { runSearch() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Enter your episode", color = Sub, fontSize = 16.sp,
                                    fontFamily = PoppinsFamily)
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(listOf(Rose, RoseDeep)))
                            .press { runSearch() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (busy) "Searching…" else "Search", color = Color.White,
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            fontFamily = PoppinsFamily)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(CardC)
                            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(999.dp))
                            .press { AppState.go(Screen.Facade) }
                            .padding(horizontal = 26.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", color = Ink, fontSize = 16.sp, fontFamily = PoppinsFamily)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Try a show name — or an episode number, like 12.",
                    color = Sub, fontSize = 13.sp, fontFamily = PoppinsFamily,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(CardC)
                            .press { results = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("←", color = Ink, fontSize = 18.sp, fontFamily = InterTight)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Results", color = Ink, fontSize = 24.sp,
                            fontWeight = FontWeight.Bold, fontFamily = InterTight)
                        Text(
                            if (res.fallback) "Popular right now"
                            else "For “$searchedFor”",
                            color = Sub, fontSize = 13.sp, fontFamily = PoppinsFamily)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(CardC)
                            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(999.dp))
                            .press { AppState.go(Screen.Facade) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", color = Ink, fontSize = 14.sp, fontFamily = PoppinsFamily)
                    }
                }
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 30.dp),
                ) {
                    items(res.hits.size) { i ->
                        val hit = res.hits[i]
                        EpisodeResultRow(hit, i) {
                            when (hit) {
                                is EpisodeHit.DramaHit -> {
                                    AppState.facadeOpenDramaId = hit.drama.id
                                    AppState.go(Screen.Facade)
                                }
                                is EpisodeHit.StoryHit -> {
                                    AppState.facadeOpenStoryId = hit.story.id
                                    AppState.go(Screen.Facade)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeResultRow(hit: EpisodeHit, idx: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).rise(idx)
            .clip(RoundedCornerShape(24.dp)).background(CardC)
            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(24.dp))
            .press(onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (hit) {
            is EpisodeHit.DramaHit -> {
                val show = hit.drama.toShow(hit.g)
                Poster(show.img, show.res, show.g,
                    Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(hit.drama.title, color = Ink, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontFamily = InterTight)
                    Text(
                        if (hit.episode != null) {
                            val bits = listOf(hit.drama.genre, hit.drama.lang)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            if (bits.isNotBlank()) "Episode ${hit.episode} · $bits"
                            else "Episode ${hit.episode}"
                        } else hit.drama.sub.ifBlank { "Tap to watch" },
                        color = Sub, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontFamily = PoppinsFamily)
                    if (hit.episode == null && hit.drama.genre.isNotBlank()) {
                        Text(hit.drama.genre, color = Rose, fontSize = 11.5.sp,
                            fontFamily = PoppinsFamily)
                    }
                }
                Icon(Icons.Filled.PlayArrow, null, tint = Ink, modifier = Modifier.size(22.dp))
            }
            is EpisodeHit.StoryHit -> {
                Box(
                    Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(hit.g)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = Color.White, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, fontFamily = InterTight)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(hit.story.title, color = Ink, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontFamily = InterTight)
                    Text("Story · ${hit.story.mins} min read", color = Sub, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontFamily = PoppinsFamily)
                    if (hit.story.tag.isNotBlank()) {
                        Text(hit.story.tag, color = Rose, fontSize = 11.5.sp,
                            fontFamily = PoppinsFamily)
                    }
                }
            }
        }
    }
}
