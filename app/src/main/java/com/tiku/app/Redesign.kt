package com.tiku.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay

/**
 * ROUND 14 — the owner's TikuRedesign design system, ported VERBATIM from
 * the supplied file (tokens, animation modifiers, transition specs and
 * component shapes are the file's own), adapted only where real data
 * replaces the demo model:
 *  - Av renders REAL avatar content (uploaded photo -> preset/emoji art ->
 *    initial on the person's stable colour) instead of a bare letter.
 *  - glass/backdrop take a nullable HazeState: PREVIEW_UI_ONLY provides no
 *    HazeState, so surfaces fall back to the file's tint + border without
 *    the blur (same contract as build 47's GlassDesign).
 *  - Text uses the platform default font — the file sets no family.
 */

// ---- tokens (verbatim from the file) ----
val CardC = Color(0xFF1B1B1F)
val Rose = Color(0xFFFF5C8A)
val RoseDeep = Color(0xFFE23D74)
val Sub = Color(0xFF9A99A6)
val SheetC = Color(0xFF16161B)
val Danger = Color(0xFFFF453A)
val RdEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

val themeList = listOf(
    Pair(Color(0xFFFF5C8A), Color(0xFFE23D74)), Pair(Color(0xFF6AA8FF), Color(0xFF3D7BE0)),
    Pair(Color(0xFF8E7CFF), Color(0xFF6A55E0)), Pair(Color(0xFFFFB347), Color(0xFFE08A1E)),
    Pair(Color(0xFF4FD1C5), Color(0xFF22A99D))
)

/** The file's base/more reaction sets (chat menu + note sheet consume). */
val baseEmojis = listOf("❤️", "😂", "😮", "😢", "😡", "👍")
val moreEmojis = listOf("🔥", "🙏", "😍", "🎉", "💯", "😭")

/** Stable per-user colour from the file's palette hues (name hash) — used
 *  only when a Person carries no engine-assigned colour. */
fun rdAvatarColor(name: String): Color {
    val hues = listOf(Rose, Color(0xFF8E7CFF), Color(0xFFFFB347), Color(0xFF4FD1C5), Color(0xFF6AA8FF))
    return hues[(name.hashCode().mod(hues.size) + hues.size) % hues.size]
}

// ---- animation modifiers (verbatim from the file) ----
/** Entrance: fade + rise + soft scale, staggered by index. */
fun Modifier.rise(i: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(i.coerceAtMost(8) * 45L); a.animateTo(1f, tween(520, easing = RdEase)) }
    this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f; val s = 0.96f + 0.04f * a.value; scaleX = s; scaleY = s }
}

/** Chat bubble entrance: springs up from its own corner. */
fun Modifier.bubbleIn(mine: Boolean, i: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(if (i < 8) i * 30L else 0L); a.animateTo(1f, spring(0.62f, 380f)) }
    this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f); val s = 0.82f + 0.18f * a.value; scaleX = s; scaleY = s
        translationY = (1f - a.value) * 40f
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
    }
}

/** Springy pop (emoji bar, reaction chip). */
fun Modifier.pop(i: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(60L + i * 45L); a.animateTo(1f, spring(0.45f, 420f)) }
    this.graphicsLayer { val s = a.value.coerceAtLeast(0f); scaleX = s; scaleY = s; alpha = a.value.coerceIn(0f, 1f) }
}

/** Gentle idle float (notes). */
fun Modifier.bob(i: Int): Modifier = composed {
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(-2f, 2f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 350)), label = "bobY")
    this.graphicsLayer { translationY = y * density }
}

fun Modifier.press(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) 0.95f else 1f, spring(0.6f, 500f), label = "press")
    this.scale(s).clickable(src, null, onClick = onClick)
}

// ---- transition specs (verbatim from the file) ----
private fun depthOf(s: Int) = when (s) { 7, 9 -> 1; 8 -> 2; else -> 0 }
val RdScreenSpec: AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
    val dd = depthOf(targetState) - depthOf(initialState)
    val d = if (dd != 0) (if (dd > 0) 1 else -1) else if (targetState >= initialState) 1 else -1
    val deep = dd != 0
    (slideInHorizontally(tween(460, easing = RdEase)) { d * (if (deep) it / 3 else it / 6) } + fadeIn(tween(380)) + scaleIn(tween(460, easing = RdEase), initialScale = 0.96f)) togetherWith
        (slideOutHorizontally(tween(320, easing = RdEase)) { -d * it / 6 } + fadeOut(tween(220)) + scaleOut(tween(320), targetScale = 0.98f))
}
val RdSubSpec: AnimatedContentTransitionScope<String>.() -> ContentTransform = {
    val d = if (targetState.isEmpty()) -1 else 1
    (slideInHorizontally(tween(400, easing = RdEase)) { d * it / 4 } + fadeIn(tween(340))) togetherWith
        (slideOutHorizontally(tween(280, easing = RdEase)) { -d * it / 4 } + fadeOut(tween(200)))
}

// ---- haze helpers (the file's glass/backdrop, nullable-state form) ----
/** Full-screen blurred + dimmed backdrop (for overlays that are siblings of hazeSource). */
fun Modifier.rdBackdrop(h: HazeState?): Modifier =
    if (h != null) this.hazeEffect(state = h, style = HazeStyle(backgroundColor = Color.Black, tint = HazeTint(Color.Black.copy(alpha = 0.35f)), blurRadius = 28.dp, noiseFactor = 0f))
    else this.background(Color.Black.copy(alpha = 0.72f))

/** Backdrop blur + light edge. Only for nodes that are siblings of (not inside) hazeSource. */
fun Modifier.rdGlass(h: HazeState?, shape: Shape, tint: Float = 0.07f): Modifier =
    this.clip(shape)
        .then(
            if (h != null) Modifier.hazeEffect(
                state = h,
                style = HazeStyle(backgroundColor = Color.Transparent, tint = HazeTint(Color.White.copy(alpha = tint)), blurRadius = 24.dp, noiseFactor = 0f)
            ) else Modifier.background(Color(0xFF1B1B1F).copy(alpha = 0.92f))
        )
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.White.copy(0.06f))), shape)

// ---- components (verbatim from the file, Av adapted to real avatars) ----
@Composable
fun Av(person: Person, size: Int) {
    val bg = if (person.colorArgb != 0) Color(person.colorArgb) else rdAvatarColor(person.name)
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val bmp = person.photoBitmap
        when {
            bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            person.photoUrl.isNotEmpty() -> coil.compose.AsyncImage(person.photoUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            EmojiArt.assetName(person.emoji) != null -> EmojiArt(person.emoji, (size * 0.62f).dp)
            else -> Text(person.name.take(1).uppercase(), color = Color.White, fontSize = (size * 0.38f).sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight)
        }
    }
}

/** My own avatar (uploaded photo -> chosen emoji -> initial), Settings/notes. */
@Composable
fun AvMe(size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Rose), contentAlignment = Alignment.Center) {
        val bmp = AppState.myAvatar
        when {
            bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            EmojiArt.assetName(AppState.myAvatarEmoji) != null -> EmojiArt(AppState.myAvatarEmoji, (size * 0.62f).dp)
            else -> Text(AppState.myUsername.ifEmpty { "Y" }.take(1).uppercase(), color = Color.White, fontSize = (size * 0.38f).sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight)
        }
    }
}

@Composable
fun Title(t: String) = Text(t, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.rise(), fontFamily = InterTight)

@Composable
fun Section(t: String) = Text(t.uppercase(), color = Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp), fontFamily = InterTight)

@Composable
fun CardRow(onClick: () -> Unit, idx: Int = 0, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).rise(idx).clip(RoundedCornerShape(24.dp)).background(CardC).press(onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

@Composable
fun BackBtn(onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(CardC).border(1.dp, Color.White.copy(0.1f), CircleShape).press(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
fun Toggle(on: Boolean) {
    val x by animateDpAsState(if (on) 21.dp else 0.dp, spring(0.65f, 450f), label = "sw")
    val bg by animateColorAsState(if (on) Rose else Color.White.copy(0.14f), label = "swbg")
    Box(Modifier.width(52.dp).height(31.dp).clip(RoundedCornerShape(16.dp)).background(bg).padding(2.dp)) {
        Box(Modifier.offset(x = x).size(27.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun TwoLine(title: String, sub: String?, modifier: Modifier, danger: Boolean = false) {
    Column(modifier) {
        Text(title, color = if (danger) Danger else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily)
        if (!sub.isNullOrEmpty()) Text(sub, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp), fontFamily = PoppinsFamily)
    }
}

@Composable
fun ToggleRow(title: String, sub: String, on: Boolean, onClick: () -> Unit) =
    CardRow(onClick) { TwoLine(title, sub, Modifier.weight(1f)); Toggle(on) }

@Composable
fun NavRow(title: String, sub: String? = null, danger: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) =
    CardRow(onClick) {
        TwoLine(title, sub, Modifier.weight(1f), danger)
        if (chevron) Icon(Icons.Filled.KeyboardArrowRight, null, tint = Sub, modifier = Modifier.size(22.dp))
    }

@Composable
fun Pill(t: String, modifier: Modifier, accent: Boolean, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(CircleShape)
            .background(if (accent) Brush.horizontalGradient(listOf(Rose, Color(0xFFFF7AA2))) else SolidColor(Color.White.copy(0.12f)))
            .press(onClick),
        contentAlignment = Alignment.Center
    ) { Text(t, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily) }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(CardC).padding(16.dp)) {
        if (value.isEmpty()) Text(hint, color = Sub, fontSize = 15.sp, fontFamily = PoppinsFamily)
        BasicTextField(
            value, onChange, singleLine = true, textStyle = TextStyle(fontFamily = PoppinsFamily, color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Rose),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SubPage(title: String, onBack: () -> Unit, bottom: androidx.compose.ui.unit.Dp = 210.dp, content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottom)) {
        item { BackBtn(onBack); Spacer(Modifier.height(14.dp)); Title(title); Spacer(Modifier.height(14.dp)) }
        content()
    }
}

@Composable
fun MenuItem(label: String, danger: Boolean = false, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().press(onClick).padding(horizontal = 22.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (danger) Danger else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), fontFamily = PoppinsFamily)
        trailing()
    }
}

@Composable
fun Divider1() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.08f)))

// ---- notes (verbatim from the file; avatar is a caller-provided slot) ----
// ROUND 68: [hasMusic] (WS10) adds a small ♪ marker when the note
// carries a song — no other visual change to the row.
@Composable
fun NoteItem(label: String, note: String, placeholder: Boolean, heart: Boolean, idx: Int, onClick: () -> Unit, avatar: @Composable () -> Unit, hasMusic: Boolean = false) {
    val hp by rememberInfiniteTransition(label = "hp").animateFloat(1f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "hp2")
    Column(Modifier.width(76.dp).rise(idx).press(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(50.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.bob(idx).widthIn(max = 76.dp).clip(RoundedCornerShape(16.dp)).background(CardC).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) { Text(if (hasMusic) "♪ $note" else note, color = if (placeholder) Sub else Color.White, fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 2, fontFamily = PoppinsFamily) }
            if (heart) Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 10.dp).graphicsLayer { scaleX = hp; scaleY = hp }.size(24.dp).clip(CircleShape).background(CardC).border(1.dp, Color.White.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Favorite, null, tint = Rose, modifier = Modifier.size(13.dp)) }
        }
        Box(
            Modifier.padding(top = 4.dp).size(66.dp).border(2.dp, Brush.linearGradient(listOf(Rose, Color(0xFFFFB347))), CircleShape).padding(4.dp),
        ) { avatar() }
        Text(label, color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), fontFamily = InterTight)
    }
}

// ---- ROUND 68 notes helpers (WS8 mentions + WS10 music) ----
/** A YouTube video id from a pasted link (watch / shorts / embed /
 *  music.youtube.com — see [youtubeThumbId]) or a bare 11-char id;
 *  null when the text is not a YouTube link at all.
 *  ROUND 69 (#2): the note flow no longer pastes links — the song is
 *  picked in-app via [NoteSongPicker] (Watch.kt), which captures the
 *  id from the picked page's URL directly. Kept intact and unused by
 *  the UI so nothing about link parsing changes for any other caller
 *  or older data. */
private fun noteMusicVideoId(input: String): String? {
    val t = input.trim()
    if (t.isEmpty()) return null
    youtubeThumbId(t)?.let { return it }
    if (t.length == 11 && t.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }) return t
    return null
}

/** "0:20" style mm:ss for the clip-trim slider label. */
private fun noteClipLabel(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** Render a note's text with its @mentions in Rose + bold; text without
 *  a matching mention renders plain (older notes carry no mention list
 *  and read exactly as before). */
private fun noteMentionAnnotated(text: String, mentions: List<NoteMention>): AnnotatedString {
    if (mentions.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var rest = text
        while (rest.isNotEmpty()) {
            val hit = mentions
                .mapNotNull { m ->
                    val token = "@${m.username}"
                    val at = rest.indexOf(token).takeIf { it >= 0 }?.let { it to token }
                    at
                }
                .minByOrNull { it.first }
            if (hit == null) {
                append(rest)
                break
            }
            val (at, token) = hit
            append(rest.substring(0, at))
            withStyle(SpanStyle(color = Rose, fontWeight = FontWeight.Bold)) { append(token) }
            rest = rest.substring(at + token.length)
        }
    }
}

/** The locked nocookie embed for one note-music clip (WS10) — the app's
 *  existing [youtubeEmbedUrl] plus the chosen start/end seconds, so the
 *  viewer hears the trimmed part only. */
private fun noteMusicEmbedUrl(music: NoteMusic): String =
    YOUTUBE_EMBED_PREFIX + music.videoId +
        "?autoplay=1&playsinline=1&rel=0&modestbranding=1&start=${music.startSec}&end=${music.endSec}"

/** One note-music clip player: a small WebView on the app's locked
 *  nocookie embed (same route as the drama player — see Watch.kt).
 *  Created with the ACTIVITY context (LocalContext.current — the
 *  application-context WebView is the R15 black-video lesson) and
 *  destroyed on release, so closing the sheet stops playback. */
@Composable
private fun NoteMusicPlayer(music: NoteMusic) {
    val ctx = LocalContext.current
    AndroidView(
        factory = {
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                loadUrl(noteMusicEmbedUrl(music))
            }
        },
        onRelease = { wv ->
            runCatching { wv.stopLoading() }
            runCatching { wv.destroy() }
        },
        modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
    )
}

@Composable
fun NoteSheet(
    h: HazeState?, mine: Boolean, headerName: String, note: String, hearted: Boolean,
    headerAvatar: @Composable () -> Unit,
    onShare: (String) -> Unit, onDelete: () -> Unit, onHeart: () -> Unit, onReply: (String) -> Unit,
    // ---- ROUND 68 (all defaulted: the pre-68 call shape keeps working) ----
    /** WS8: the note's stored mentions (peer view renders them; my view
     *  seeds the editor's picked list so re-sharing keeps them). */
    mentions: List<NoteMention> = emptyList(),
    /** WS10: the note's attached song (peer view plays it; my view
     *  seeds the editor so re-sharing keeps it unless removed). */
    music: NoteMusic? = null,
    /** WS8: who "@" can mention — thread partners, passed by HomeScreen
     *  (the only people who can see the note). */
    mentionCandidates: List<Person> = emptyList(),
    /** WS8 + WS10: share with mentions + song; null = the plain
     *  [onShare] path (preview mode / old callers). */
    onShareFull: ((String, List<NoteMention>, NoteMusic?) -> Unit)? = null,
) {
    var text by remember(headerName, note) { mutableStateOf(if (mine) note else "") }
    // WS8: the mentions the editor has picked (seeded from the stored note).
    var pickedMentions by remember(headerName, note) { mutableStateOf(mentions) }
    // WS10: the editor's song state (seeded from the stored note).
    var showMusic by remember(headerName, note) { mutableStateOf(music != null) }
    // Set false by Remove so the stored song is NOT re-attached on Share.
    var keepMusic by remember(headerName, note) { mutableStateOf(true) }
    // ROUND 69 (#2): the picked song, captured in-app by NoteSongPicker
    // (Watch.kt) — seeded from the stored note so re-sharing an
    // untouched song keeps it (id + title ride the trim state below).
    // The build-117 paste-a-link fields are GONE: the user never
    // pastes or sees a link any more.
    var pickedVideoId by remember(headerName, note) { mutableStateOf(music?.videoId ?: "") }
    var pickedTitle by remember(headerName, note) { mutableStateOf(music?.title ?: "") }
    var songPickerOpen by remember(headerName, note) { mutableStateOf(false) }
    var clipStart by remember(headerName, note) { mutableStateOf((music?.startSec ?: 0).toFloat()) }
    var clipLen by remember(headerName, note) { mutableStateOf((music?.endSec ?: 30) - (music?.startSec ?: 0)) }
    // WS10: the peer view's player toggle (its own state, below).
    var playingMusic by remember(headerName, note) { mutableStateOf(false) }
    // WS8: the "@query" tail of the editor text, or null when not mentioning.
    val atQuery: String? = if (!mine) null else run {
        val at = text.lastIndexOf('@')
        if (at < 0) null else {
            val tail = text.substring(at + 1)
            if (tail.any { it == ' ' || it == '\n' }) null else tail
        }
    }
    val mentionSuggestions = if (atQuery == null) emptyList() else mentionCandidates
        .filter { p ->
            p.uid !in pickedMentions.map { it.uid } &&
                (p.username.contains(atQuery, ignoreCase = true) ||
                    p.name.contains(atQuery, ignoreCase = true))
        }.take(5)
    // ROUND 69 (#2): the in-app song picker (Watch.kt) — picking
    // returns the video's id + title, seeds the trim UI below, and
    // the user trims the clip exactly as in build 117. Dismissing
    // the picker changes nothing.
    if (songPickerOpen) {
        NoteSongPicker(
            onPicked = { id, title ->
                pickedVideoId = id
                pickedTitle = title
                showMusic = true
                keepMusic = true
                songPickerOpen = false
            },
            onDismiss = { songPickerOpen = false },
        )
    }
    Column(
        Modifier.padding(14.dp).fillMaxWidth().rdGlass(h, RoundedCornerShape(28.dp), 0.1f)
            .clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        if (mine) {
            Text("Share a thought", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = InterTight)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardC).padding(16.dp)) {
                if (text.isEmpty()) Text("What's on your mind? Type @ to mention someone", color = Sub, fontSize = 15.sp, fontFamily = PoppinsFamily)
                BasicTextField(text, { if (it.length <= 60) text = it }, textStyle = TextStyle(fontFamily = PoppinsFamily, color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Rose), modifier = Modifier.fillMaxWidth())
            }
            // WS8: mention picker — appears while "@…" is being typed.
            if (mentionSuggestions.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardC)
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)).padding(6.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                ) {
                    mentionSuggestions.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).press {
                                val at = text.lastIndexOf('@')
                                val before = if (at >= 0) text.substring(0, at) else text
                                text = "$before@${p.username} "
                                if (p.uid !in pickedMentions.map { it.uid }) {
                                    pickedMentions = pickedMentions + NoteMention(p.uid, p.username, p.name)
                                }
                            }.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Av(p, 32)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(p.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily)
                                if (p.username.isNotEmpty()) Text("@${p.username}", color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
                            }
                        }
                    }
                }
            }
            Text("${text.length}/60 · Visible to friends for 24 hours", color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
            // WS10: attach a song. ROUND 69 (#2): picked in-app
            // (search + tap, IG-style) — no link pasting any more;
            // the clip trim below is the build-117 one, unchanged.
            if (!showMusic) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardC)
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                        .press { showMusic = true; keepMusic = true }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = Rose, modifier = Modifier.size(20.dp))
                    Text("Add music", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp), fontFamily = PoppinsFamily)
                    Spacer(Modifier.weight(1f))
                    Text("Pick a song", color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardC).padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = Rose, modifier = Modifier.size(20.dp))
                        Text("Song", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp), fontFamily = PoppinsFamily)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Remove", color = Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.press {
                                showMusic = false; keepMusic = false; pickedVideoId = ""; pickedTitle = ""
                                clipStart = 0f; clipLen = 30
                            },
                            fontFamily = PoppinsFamily,
                        )
                    }
                    if (pickedVideoId.isBlank()) {
                        // ROUND 69 (#2): nothing picked yet — the one
                        // action is opening the in-app YouTube picker.
                        // No link field, no typing: search + tap.
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rose)
                                .press { songPickerOpen = true }.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Pick a song", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp), fontFamily = PoppinsFamily)
                            Spacer(Modifier.weight(1f))
                            Text("YouTube", color = Color.White.copy(0.75f), fontSize = 12.sp, fontFamily = PoppinsFamily)
                        }
                        Text("Search YouTube inside the app and tap a song — no link needed.", color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
                    } else {
                        // The picked song (title captured from the
                        // picked page — the user never typed it).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                pickedTitle.ifEmpty { "Song from YouTube" }, color = Color.White, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                modifier = Modifier.weight(1f), fontFamily = PoppinsFamily,
                            )
                            Text(
                                "Change song", color = Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.press { songPickerOpen = true }.padding(start = 10.dp),
                                fontFamily = PoppinsFamily,
                            )
                        }
                        // Build-117 trim, unchanged: start slider
                        // (0–180 s) + 15/30/60 s clip chips.
                        Text("Starts at ${noteClipLabel(clipStart.toInt())} · ${clipLen}s clip", color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
                        Slider(
                            value = clipStart, onValueChange = { clipStart = it },
                            valueRange = 0f..180f, steps = 0,
                            colors = SliderDefaults.colors(thumbColor = Rose, activeTrackColor = Rose, inactiveTrackColor = Color.White.copy(0.14f)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 60).forEach { len ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(999.dp))
                                        .background(if (clipLen == len) Rose else Color.White.copy(0.1f))
                                        .press { clipLen = len }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("${len}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily) }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                if (note.isNotEmpty()) Pill("Delete", Modifier.weight(1f), false, onDelete)
                Pill("Share", Modifier.weight(1f), true) {
                    // WS10 + ROUND 69 (#2): the attached song is the
                    // PICKED one (id + title captured in-app), trimmed
                    // by the slider/chips above. Removed (Remove) or
                    // never picked = no song; a stored song seeds the
                    // picked state, so sharing an edited note without
                    // touching the song keeps it (rebuilt from the
                    // same id/title + current trim). No link is ever
                    // parsed here any more, so a share can no longer
                    // fail on a bad paste.
                    val attached: NoteMusic? = if (!showMusic) {
                        if (keepMusic) music else null
                    } else if (pickedVideoId.isBlank()) {
                        null
                    } else {
                        val start = clipStart.toInt()
                        NoteMusic(pickedVideoId, pickedTitle.trim().ifEmpty { "Song from YouTube" }, start, start + clipLen)
                    }
                    // WS8: keep only mentions whose "@username" still
                    // appears in the text (a deleted token un-mentions).
                    val kept = pickedMentions.filter { text.contains("@${it.username}") }
                    if (onShareFull != null) onShareFull(text, kept, attached) else onShare(text)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerAvatar()
                Text(headerName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp), fontFamily = InterTight)
            }
            Text(
                noteMentionAnnotated(note, mentions), color = Color.White, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CardC).padding(horizontal = 16.dp, vertical = 12.dp),
                fontFamily = rememberChatFontFamily(),
            )
            // WS10: the attached song — tap Play to hear the clip.
            if (music != null) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardC)
                            .press { playingMusic = !playingMusic }.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = Rose, modifier = Modifier.size(20.dp))
                        Text(
                            music.title.ifEmpty { "Song from YouTube" }, color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp),
                            fontFamily = PoppinsFamily,
                        )
                        Text(
                            "${noteClipLabel(music.startSec)} · ${music.endSec - music.startSec}s",
                            color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily,
                        )
                        Box(
                            Modifier.padding(start = 10.dp).size(36.dp).clip(CircleShape).background(Rose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (playingMusic) Icons.Filled.Close else Icons.Filled.PlayArrow, null,
                                tint = Color.White, modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (playingMusic) NoteMusicPlayer(music)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(0.12f)).press(onHeart), contentAlignment = Alignment.Center) {
                    Icon(
                        if (hearted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                        tint = if (hearted) Rose else Color.White, modifier = Modifier.size(21.dp),
                    )
                }
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).clip(RoundedCornerShape(999.dp)).background(CardC).padding(horizontal = 16.dp, vertical = 13.dp)) {
                    if (text.isEmpty()) Text("Reply to note…", color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily)
                    BasicTextField(text, { text = it }, singleLine = true, textStyle = TextStyle(fontFamily = PoppinsFamily, color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Rose), modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(Rose).press { onReply(text) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ---- bottom bars (verbatim from the file) ----
@Composable
fun CallPill(h: HazeState?, person: Person, kindLabel: String, secs: Int, onTap: () -> Unit, onEnd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rdGlass(h, RoundedCornerShape(999.dp), 0.05f).press(onTap).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Av(person, 42)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(person.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight)
            Text("$kindLabel · ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}", color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(Danger).press(onEnd), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun NavPill(h: HazeState?, selected: Int, hasUnread: Boolean, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.Filled.Email to "Message", Icons.Filled.Call to "Call", Icons.Filled.PlayArrow to "Watch", Icons.Filled.Settings to "Setting"
    )
    BoxWithConstraints(Modifier.fillMaxWidth().rdGlass(h, RoundedCornerShape(999.dp), 0.06f).padding(6.dp)) {
        val w = maxWidth / 4
        val x by animateDpAsState(w * selected, spring(0.7f, 320f), label = "lens")
        Box(
            Modifier.offset(x = x).width(w).height(60.dp).clip(RoundedCornerShape(999.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color.White.copy(0.10f))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.35f), Color.White.copy(0.05f))), RoundedCornerShape(999.dp))
        )
        Row {
            items.forEachIndexed { i, (icon, label) ->
                val on = i == selected
                val c by animateColorAsState(if (on) Color.White else Sub, label = "tabC")
                val ic by animateColorAsState(if (on) Rose else Sub, label = "tabI")
                val sc by animateFloatAsState(if (on) 1.18f else 1f, spring(0.5f, 400f), label = "tabS")
                Column(
                    Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(999.dp)).press { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Box {
                        Icon(icon, null, tint = ic, modifier = Modifier.size(23.dp).scale(sc))
                        if (i == 0 && hasUnread) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(9.dp).clip(CircleShape).background(Rose))
                    }
                    Text(label, color = c, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp), fontFamily = PoppinsFamily)
                }
            }
        }
    }
}
