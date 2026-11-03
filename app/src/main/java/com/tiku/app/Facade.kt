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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        Text(title, color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text(sub, color = Sub, fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SectionTitle(t: String, idx: Int) {
    Text(
        t, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp).rise(idx)
    )
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
                    Text(s.lang, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    s.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(s.sub, color = Color(0xFFC9C8D4), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            ) { Text(s.lang, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium) }

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
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
        )
        Text(s.sub, color = Sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onOpen: (Show) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset() + 24.dp, bottom = 140.dp)
    ) {
        item { Header(DramaContent.appName, DramaContent.tagline) }
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
        Text(s.title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        Text(
            s.excerpt, color = Sub, fontSize = 17.sp, lineHeight = 24.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
        )
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(RoseSoft).padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text(s.tag, color = Rose, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("${s.mins} min read", color = Sub, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
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
        item { Header(DramaContent.storiesTitle, "Tiny love stories from Seoul"); Spacer(Modifier.height(22.dp)) }
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
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp)
        )
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
                    Text(label, color = col, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
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
    onSecretGate: () -> Unit
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
