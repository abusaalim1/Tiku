package com.tiku.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * CHAT — ROUND 14: the owner's TikuRedesign ChatList + overlays, ported
 * onto the real thread. Black background, theme-gradient own bubbles,
 * CardC peer bubbles with a 28dp avatar, the file's gestures (swipe-reply
 * spring, double-tap heart burst, long-press centred glass menu), the
 * file's ChatTopBar / ChatInput overlays, pinned strip, real typing
 * bubble, real voice recording + playback. Everything the file forbids is
 * gone: no timestamps, no seen ticks, no E2EE banner, no quick chips.
 * Engine behaviour is unchanged (E2EE, reply/star/pin/react/save,
 * explicit Save only for media). ROUND 18: delete is two-option —
 * "Delete for me" (local hide) and, for my own messages, "Delete for
 * everyone" (server tombstone both sides render); the chat also gains
 * the IG unread divider, the "Seen"/"Sent" caption under my last
 * message, and a glide-only follow (no scroll yank from history).
 * ROUND 19: undecryptable history (retired-key ciphertext) renders an
 * honest placeholder instead of blank bubbles; own sends ALWAYS glide
 * via the engine's explicit tail-scroll request; one coalesced follow
 * job serves every glide; the list state starts at the tail.
 */

// ================= redesign tokens (verbatim from TikuRedesign) =================
private val RdCard = Color(0xFF1B1B1F)
private val RdSheet = Color(0xFF16161B)
private val RdRose = Color(0xFFFF5C8A)
private val RdSub = Color(0xFF9A99A6)
private val RdDanger = Color(0xFFFF453A)
// RdEase is the shared declaration in Redesign.kt (identical value) —
// no private copy here. The glass helpers below are Chat-local renames of
// the file's originals (their no-HazeState fallback alphas differ from
// Redesign.kt's shared versions, so they keep Chat's exact rendering).
private val RdThemes = listOf(
    Color(0xFFFF5C8A) to Color(0xFFE23D74),
    Color(0xFF6AA8FF) to Color(0xFF3D7BE0),
    Color(0xFF8E7CFF) to Color(0xFF6A55E0),
    Color(0xFFFFB347) to Color(0xFFE08A1E),
    Color(0xFF4FD1C5) to Color(0xFF22A99D),
)
private val RdBaseEmojis = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

// ================= ROUND 22 — Instagram-style message effects =================
/** The four send effects, in picker order — the same four Instagram
 *  offers from the effects slot beside its message field (verified
 *  against IG's help/writeups): Gift (wrapped, tap to open), Fire,
 *  Confetti, and Celebration (flying hearts). [kind] is the Firestore
 *  `fx` value (see [MESSAGE_FX_KINDS]); [glyph] renders through
 *  EmojiGlyph like every emoji surface in the app. */
private data class FxDef(val kind: String, val glyph: String, val name: String, val blurb: String)

private val RdMessageFx = listOf(
    FxDef("gift", "🎁", "Gift", "Sent wrapped — tap to open"),
    FxDef("fire", "🔥", "Fire", "Flames burst over the chat"),
    FxDef("confetti", "🎉", "Confetti", "Confetti rains over the chat"),
    FxDef("hearts", "💖", "Celebration", "Hearts float up the chat"),
)

private fun fxDef(kind: String?): FxDef? = RdMessageFx.firstOrNull { it.kind == kind }

// ================= ROUND 24 — premium effects: dressing, first-display
// playback, single-emoji effects =================
/** A bubble's on-screen anchor in root (window-content) pixels, captured
 *  by the row via onGloballyPositioned for hearts / fire messages. The
 *  overlay subtracts its own root position, so the pair stays correct
 *  whatever insets the chat root sits under. */
private data class FxAnchor(val cxPx: Float, val bottomPx: Float)

/** Durable per-account "already auto-played" id log for effect
 *  animations (ROUND 24 playback rule: an effect plays the FIRST time
 *  its message is rendered for THIS account on this device — at send
 *  for the sender, on first sight for the recipient, even hours
 *  later — and never again on its own; only a bubble tap replays).
 *  ROUND 28: the caller hands in a uid-namespaced prefs file, so each
 *  signed-in account keeps its own log even on a shared phone.
 *  Stored as one comma-joined string so the cap is a true FIFO trim
 *  (newest [CAP] ids kept); message ids are client-chosen doc ids and
 *  never contain a comma. */
private class FxPlayedLog(private val prefs: android.content.SharedPreferences) {
    private val ids = LinkedHashSet<String>(
        prefs.getString(KEY, null)?.split(',')?.filter { it.isNotEmpty() } ?: emptyList(),
    )
    @Synchronized fun seen(id: String): Boolean = id in ids
    @Synchronized fun mark(id: String) {
        if (!ids.add(id)) return
        while (ids.size > CAP) { val it = ids.iterator(); it.next(); it.remove() }
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
    }
    private companion object { const val KEY = "ids"; const val CAP = 400 }
}

/** ROUND 24: if [text] (trimmed) is EXACTLY ONE emoji grapheme,
 *  returns that grapheme, else null. Strict by construction: the whole
 *  string must parse as one cluster — a base emoji codepoint plus only
 *  combining marks / variation selectors / skin tones, ZWJ-joined
 *  further bases, a regional-indicator PAIR (flag), or a keycap
 *  sequence. Any letter, digit (outside a keycap), punctuation, space
 *  or second emoji makes it null, so a normal sentence containing an
 *  emoji can never qualify. */
private fun singleEmojiGrapheme(text: String): String? {
    val t = text.trim()
    if (t.isEmpty() || t.length > 24) return null
    val cps = ArrayList<Int>(4)
    var idx = 0
    while (idx < t.length) {
        val cp = Character.codePointAt(t, idx)
        cps += cp
        idx += Character.charCount(cp)
    }
    fun isModifier(cp: Int) =
        cp in 0x1F3FB..0x1F3FF || cp == 0xFE0F || cp == 0xFE0E || cp in 0x0300..0x036F
    fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF
    fun isEmojiBase(cp: Int): Boolean = when {
        cp in 0x1F000..0x1FAFF -> true
        cp in 0x2600..0x27BF -> true   // ☀..➿ — ❤ (U+2764), ✨ (U+2728)…
        cp in 0x2B00..0x2BFF -> true   // ⭐ (U+2B50)…
        cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 -> true
        cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 -> true
        cp == 0x00A9 || cp == 0x00AE -> true
        else -> false
    }
    // flag: exactly two regional indicators
    if (cps.size == 2 && isRegional(cps[0]) && isRegional(cps[1])) return t
    // keycap: [0-9#*] + optional VS16 + U+20E3
    if ((cps.size == 2 || cps.size == 3) && cps.last() == 0x20E3 &&
        (cps[0] in 0x30..0x39 || cps[0] == 0x23 || cps[0] == 0x2A) &&
        (cps.size == 2 || cps[1] == 0xFE0F)
    ) return t
    // general cluster: base modifiers* (ZWJ base modifiers*)*
    if (!isEmojiBase(cps[0])) return null
    var i = 1
    while (i < cps.size && isModifier(cps[i])) i++
    while (i < cps.size) {
        if (cps[i] != 0x200D) return null // ZWJ
        i++
        if (i >= cps.size || !isEmojiBase(cps[i])) return null
        i++
        while (i < cps.size && isModifier(cps[i])) i++
    }
    return t
}

/** ROUND 24: the full-screen effect a message earns PURELY from its
 *  text — a lone emoji from a known family (no schema field; every
 *  build derives it identically from the decrypted text). Returns an
 *  RdFxOverlay kind, or null. Fx-carrying, media, tombstone and
 *  undecryptable messages never qualify. */
private fun singleEmojiOverlayKind(m: TikuMessage): String? {
    if (m.kind != MsgKind.TEXT || m.fx != null || m.undecryptable || m.deletedForEveryone) return null
    val g = singleEmojiGrapheme(m.text) ?: return null
    return when (g.replace("\uFE0F", "")) {
        // ROUND 25: ❤ and 😘 are NOT here any more — they belong to
        // the owner's supplied design kinds ([singleEmojiDesignKind]
        // for ❤; [singleEmojiFx2Kind] for 😘 since ROUND 68/WS16)
        // and every caller checks those mappings first.
        // ROUND 66: 🥰, 😍, 🔥 and 🤣 are NOT here any more either —
        // they belong to the owner's Chat Emoji Effects file
        // ([singleEmojiFx2Kind]) and every caller checks that mapping
        // before this one. 😂 alone keeps the Round-24 laugh kind.
        "💖", "💕", "💗", "💓", "💞" -> "hearts"
        "🎉", "🎊", "🥳" -> "confetti"
        "😂" -> "laugh"
        "✨", "🌟", "⭐" -> "sparkles"
        else -> null
    }
}

/** ROUND 25: the owner's own supplied emoji-effect design
 *  (EmojiFx.kt). A lone ❤ / 🫣 / 👀 / 😚 / 🤭 plays that
 *  design's centre-stage animation via [EmojiFxOverlay] INSTEAD of a
 *  Round-24 particle kind. Returns the design's kind index
 *  (0=heart, 1=kiss, 2=peek, 3=eyes, 4=kissing, 5=giggle), or null.
 *  Same qualification gate as [singleEmojiOverlayKind]: plain TEXT,
 *  no fx field, exactly one strict grapheme. ❤ accepts U+2764 with
 *  or without VS16 (the compare strips it, like the mapping above).
 *  Every caller checks this BEFORE [singleEmojiOverlayKind].
 *  ROUND 68 (WS16): 😘 no longer routes here — it plays the v10
 *  'kiss' spec via [singleEmojiFx2Kind] index 1 (callers check Fx2
 *  first, so kind 1 is unreachable now and is dropped). */
private fun singleEmojiDesignKind(m: TikuMessage): Int? {
    if (m.kind != MsgKind.TEXT || m.fx != null || m.undecryptable || m.deletedForEveryone) return null
    val g = singleEmojiGrapheme(m.text) ?: return null
    return when (g.replace("\uFE0F", "")) {
        "❤" -> 0
        "🫣" -> 2
        "👀" -> 3
        "😚" -> 4
        "🤭" -> 5
        else -> null
    }
}

/** ROUND 66: the owner's "Chat Emoji Effects" file (EmojiFx2.kt) —
 *  a lone emoji covered by that file's spec table plays the file's
 *  own native show via [EmojiFx2Overlay] INSTEAD of any Round-24/25
 *  effect. Returns the demo's SP spec index, or null. Same
 *  qualification gate as [singleEmojiDesignKind] (plain TEXT, no fx
 *  field, not undecryptable/tombstone, exactly one strict grapheme).
 *  Every caller checks this BEFORE [singleEmojiDesignKind] and
 *  [singleEmojiOverlayKind]. ROUND 68 (WS16): the v10 file's specs
 *  are routed here too — 😘 now plays the v10 'kiss' spec (index 1;
 *  the owner ordered every covered emoji changed except 🖕/🍑, and
 *  the 😘 flag to him drew no veto), and the 4 new v10 specs append
 *  at 21–24 (💋 😋 🌚 💦). ❤ accepts U+2764 with or without VS16
 *  (the compare strips it, like the mappings above). */
private fun singleEmojiFx2Kind(m: TikuMessage): Int? {
    if (m.kind != MsgKind.TEXT || m.fx != null || m.undecryptable || m.deletedForEveryone) return null
    val g = singleEmojiGrapheme(m.text) ?: return null
    return when (g.replace("\uFE0F", "")) {
        "❤" -> 0
        "😘" -> 1
        "😍" -> 2
        "🥰" -> 3
        "🫶" -> 4
        "👄" -> 5
        "😏" -> 6
        "😎" -> 7
        "🍑" -> 8
        "🔥" -> 9
        "🫣" -> 10
        "🤭" -> 11
        "👀" -> 12
        "🥺" -> 13
        "😭" -> 14
        "💔" -> 15
        "🤬" -> 16
        "🖕" -> 17
        "🤣" -> 18
        "😴" -> 19
        "🙄" -> 20
        "💋" -> 21
        "😋" -> 22
        "🌚" -> 23
        "💦" -> 24
        else -> null
    }
}

/** One deterministic overlay particle. The whole set is built ONCE per
 *  playback from a fixed seed (remembered on the play key) and every
 *  frame is a pure function of the progress value — the animation
 *  allocates nothing per frame beyond the fixed set (~40 particles). */
private data class FxParticle(
    val xFrac: Float, val delay: Float, val span: Float,
    val sizeDp: Float, val swayAmp: Float, val swayFreq: Float,
    val spin: Float, val variant: Int,
    /** ROUND 24: vertical field position for the stationary sparkles
     *  kind (other kinds travel and ignore it). Drawn LAST from the
     *  LCG so every pre-round-24 field keeps its exact old value. */
    val yFrac: Float,
)

private fun fxParticles(kind: String, count: Int): List<FxParticle> {
    // a tiny deterministic LCG — identical particle fields on every
    // device and every replay, no shared Random state to mutate.
    var s = kind.hashCode() * 1103515245 + 12345
    fun next(): Float {
        s = s * 1103515245 + 12345
        return ((s ushr 16) and 0x7FFF) / 32767f
    }
    val (minSize, maxSize) = when (kind) {
        "confetti" -> 6f to 12f
        "hearts" -> 20f to 38f
        "kiss" -> 20f to 36f
        "laugh" -> 26f to 44f
        "sparkles" -> 15f to 28f
        else -> 22f to 46f // fire
    }
    return List(count) {
        FxParticle(
            xFrac = next(),
            delay = next() * 0.22f,
            span = 0.62f + next() * 0.30f,
            sizeDp = minSize + next() * (maxSize - minSize),
            swayAmp = 6f + next() * 16f,
            swayFreq = 0.6f + next() * 1.1f,
            spin = (next() - 0.5f) * 2.4f,
            variant = (next() * 997f).toInt(),
            yFrac = next(),
        )
    }
}

/** The one-shot effect overlay (gift has no overlay; its wrap IS the
 *  effect). Kinds: the fx kinds "fire" / "confetti" / "hearts" plus the
 *  ROUND 24 single-emoji kinds "kiss" / "laugh" / "sparkles" ("hearts",
 *  "confetti" and "fire" are shared with the emoji families). [playKey]
 *  identifies one playback: the progress Animatable runs 0→1 over
 *  ~2.6 s exactly once per key, then [onDone] fires (the caller clears
 *  the playback only if the key is still current, so a replay started
 *  mid-flight is never cancelled by its predecessor's finally). The
 *  overlay carries NO pointer input — taps fall through to the chat
 *  beneath it.
 *
 *  ROUND 24 anchoring: for an fx hearts / fire playback the caller
 *  passes the bubble's [anchor]; hearts then rise from the bubble and
 *  flames erupt around it instead of filling the screen. Confetti and
 *  every emoji-kind playback stay screen-wide (anchor null), exactly
 *  like Instagram. */
@Composable
private fun RdFxOverlay(kind: String, playKey: String, anchor: FxAnchor? = null, onDone: () -> Unit) {
    val progress = remember(playKey) { Animatable(0f) }
    LaunchedEffect(playKey) {
        progress.snapTo(0f)
        try {
            progress.animateTo(1f, tween(2600, easing = LinearEasing))
        } finally {
            onDone()
        }
    }
    val particles = remember(playKey, kind) {
        fxParticles(
            kind,
            when (kind) {
                "confetti" -> 42; "hearts" -> 24; "kiss" -> 24
                "laugh" -> 18; "sparkles" -> 30; else -> 30
            },
        )
    }
    val palette = remember {
        listOf(RdRose, Color(0xFFFFC857), Color.White, Color(0xFFFF8A5C), Color(0xFF8E7CFF), Color(0xFF4FD1C5))
    }
    // The overlay's own root position, so an anchor captured in root
    // pixels converts into this box's local dp space exactly.
    var selfOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { selfOrigin = it.positionInRoot() },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val p = progress.value
        val aCx = anchor?.let { (it.cxPx - selfOrigin.x) / density }
        val aBottom = anchor?.let { (it.bottomPx - selfOrigin.y) / density }
        if (kind == "fire") {
            val glowAlpha = (sin(p * 3.14159f).coerceIn(0f, 1f)) * 0.30f
            if (aCx != null && aBottom != null) {
                // ROUND 24: anchored — the glow pools around the bubble
                Box(
                    Modifier.offset((aCx - 120f).dp, (aBottom - 132f).dp).size(240.dp, 150.dp)
                        .graphicsLayer { alpha = glowAlpha }
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFFF6A00).copy(0.55f), Color(0xFFFF3D00).copy(0.25f), Color.Transparent),
                            ),
                        ),
                )
            } else {
                // a warm glow pooled at the bottom while the flames run
                Box(
                    Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = glowAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xFFFF6A00).copy(0.55f), Color(0xFFFF3D00).copy(0.8f)),
                            ),
                        ),
                )
            }
        }
        particles.forEach { pt ->
            val local = ((p - pt.delay) / pt.span).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val fadeIn = (local / 0.12f).coerceIn(0f, 1f)
            val sway = sin((local * pt.swayFreq + pt.variant) * 6.28318f) * pt.swayAmp
            when (kind) {
                "confetti" -> {
                    val x = pt.xFrac * w + sway
                    val y = -30f + local * (h + 60f)
                    Box(
                        Modifier.offset(x.dp, y.dp)
                            .graphicsLayer {
                                rotationZ = pt.spin * local * 360f
                                alpha = fadeIn * (1f - ((local - 0.75f) / 0.25f).coerceIn(0f, 1f))
                            }
                            .size(pt.sizeDp.dp, (pt.sizeDp * 0.62f).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(palette[pt.variant % palette.size]),
                    )
                }
                "hearts", "kiss" -> {
                    // hearts rise from the bubble when anchored (the fx
                    // playback), else float up the whole chat (emoji
                    // playback, and every kiss). kiss mixes 💋 in.
                    val anchored = aCx != null && aBottom != null
                    val x = if (anchored) (aCx!! - 160f) + pt.xFrac * 320f + sway else pt.xFrac * w + sway
                    val y = if (anchored) (aBottom!! + 24f) - local * (h * 0.72f) else (h + 30f) - local * (h + 70f)
                    Box(
                        Modifier.offset(x.dp, y.dp)
                            .graphicsLayer {
                                alpha = fadeIn * (1f - ((local - 0.6f) / 0.4f).coerceIn(0f, 1f))
                                val sc = 0.7f + 0.4f * local
                                scaleX = sc; scaleY = sc
                            },
                    ) {
                        EmojiGlyph(
                            when {
                                kind == "kiss" && pt.variant % 3 == 0 -> "💋"
                                pt.variant % 3 == 0 -> "❤️"
                                else -> "💖"
                            },
                            pt.sizeDp.dp,
                        )
                    }
                }
                "laugh" -> {
                    // 😂 glyphs pop in oversized, then float up swaying
                    val x = pt.xFrac * w + sway
                    val y = (h + 30f) - local * (h * 0.85f + 60f)
                    val pop = if (local < 0.16f) 0.4f + (local / 0.16f) * 0.75f
                        else 1.15f - 0.15f * ((local - 0.16f) / 0.84f)
                    Box(
                        Modifier.offset(x.dp, y.dp)
                            .graphicsLayer {
                                alpha = fadeIn * (1f - ((local - 0.65f) / 0.35f).coerceIn(0f, 1f))
                                scaleX = pop; scaleY = pop
                                rotationZ = sin(local * 3.14159f) * pt.spin * 16f
                            },
                    ) { EmojiGlyph(if (pt.variant % 4 == 3) "🤣" else "😂", pt.sizeDp.dp) }
                }
                "sparkles" -> {
                    // stationary field: each sparkle twinkles in place
                    // (alpha + scale pulse on its own phase) under a
                    // slow fade-in / fade-out envelope
                    val x = pt.xFrac * w + sway * 0.3f
                    val y = pt.yFrac * (h - 40f) + 10f
                    val tw = abs(sin(local * 9.42477f + pt.variant * 1.7f))
                    val env = (local / 0.15f).coerceIn(0f, 1f) *
                        (1f - ((local - 0.7f) / 0.3f).coerceIn(0f, 1f))
                    Box(
                        Modifier.offset(x.dp, y.dp)
                            .graphicsLayer {
                                alpha = env * (0.30f + 0.70f * tw)
                                val sc = 0.7f + 0.5f * tw
                                scaleX = sc; scaleY = sc
                            },
                    ) {
                        EmojiGlyph(
                            when (pt.variant % 3) { 0 -> "✨"; 1 -> "🌟"; else -> "⭐" },
                            pt.sizeDp.dp,
                        )
                    }
                }
                else -> { // fire — flames erupt around the bubble when
                    // anchored, else rise through the lower chat
                    val anchored = aCx != null && aBottom != null
                    val x = if (anchored) (aCx!! - 170f) + pt.xFrac * 340f + sway * 0.5f else pt.xFrac * w + sway * 0.5f
                    val y = if (anchored) (aBottom!! - pt.sizeDp) - local * (h * 0.38f) else (h - pt.sizeDp) - local * (h * 0.72f)
                    val flicker = 0.85f + 0.30f * sin(local * 40f + pt.variant * 2.1f)
                    Box(
                        Modifier.offset(x.dp, y.dp)
                            .graphicsLayer {
                                alpha = (fadeIn * (1f - ((local - 0.55f) / 0.45f).coerceIn(0f, 1f))).coerceIn(0f, 1f)
                                scaleX = flicker; scaleY = flicker
                            },
                    ) { EmojiGlyph("🔥", pt.sizeDp.dp) }
                }
            }
        }
    }
}

/** Avatar fallback hues: the redesign file's people colours + extraColors. */
private val RdAvatarHues = listOf(
    Color(0xFFFF5C8A), Color(0xFF8E7CFF), Color(0xFFFFB347), Color(0xFF4FD1C5),
    Color(0xFF6AA8FF), Color(0xFFB07A5A), Color(0xFF5A82B0), Color(0xFF4F9A7E),
)

// ================= the file's animation modifiers (verbatim) =================
/** ROUND 20 — the per-bubble spring entrance (rdBubbleIn: scale + rise +
 *  fade, replayed every time a row composed) is GONE. It ran on top of
 *  the list's own follow glide, so a sent/arriving bubble visibly
 *  bounced twice, and history rows re-animated on every scroll back.
 *  Instagram has no such thing: the bubble is simply THERE and the list
 *  shifts once. Message motion now lives in exactly one place — the
 *  coalesced tail glide (glideToTail) plus the IME glue below. */

/** Springy pop (emoji bar, reaction chip). */
private fun Modifier.rdPop(i: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(60L + i * 45L); a.animateTo(1f, spring(0.45f, 420f)) }
    this.graphicsLayer { val s = a.value.coerceAtLeast(0f); scaleX = s; scaleY = s; alpha = a.value.coerceIn(0f, 1f) }
}

/** Press feedback: 0.95 spring scale on every tappable (the file's .press). */
private fun Modifier.rdPress(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) 0.95f else 1f, spring(0.6f, 500f), label = "rdPress")
    this.scale(s).clickable(src, null, onClick = onClick)
}

/** The file's .glass: backdrop blur + light edge. When no HazeState is
 *  provided (preview builds + the screenshot harness) the same shape falls
 *  back to the sheet colour at near-full alpha, exactly like build 47's
 *  glass fallbacks. (Chat-local copy of Redesign.kt's rdGlass with this
 *  file's original fallback alpha.) */
private fun Modifier.chatGlass(h: HazeState?, shape: Shape, tint: Float = 0.07f): Modifier =
    if (h != null) {
        this.clip(shape)
            .hazeEffect(
                state = h,
                style = HazeStyle(backgroundColor = Color.Transparent, tint = HazeTint(Color.White.copy(alpha = tint)), blurRadius = 24.dp, noiseFactor = 0f),
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.White.copy(0.06f))), shape)
    } else {
        this.clip(shape)
            .background(RdCard.copy(alpha = 0.94f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.White.copy(0.06f))), shape)
    }

/** The file's .backdrop for the long-press menu (blur + dim). */
private fun Modifier.chatBackdrop(h: HazeState?): Modifier =
    if (h != null) {
        this.hazeEffect(state = h, style = HazeStyle(backgroundColor = Color.Black, tint = HazeTint(Color.Black.copy(alpha = 0.35f)), blurRadius = 28.dp, noiseFactor = 0f))
    } else {
        this.background(Color.Black.copy(alpha = 0.55f))
    }

/** Redesign avatar on real data: uploaded photo → photo URL → preset /
 *  emoji art → initial letter on a stable per-user hue (hashed from the
 *  file's palette hues by uid). */
@Composable
private fun RdAv(person: Person, size: Int) {
    val seed = person.uid.ifEmpty { person.name }
    val hue = RdAvatarHues[((seed.hashCode() % RdAvatarHues.size) + RdAvatarHues.size) % RdAvatarHues.size]
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(hue),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = person.photoBitmap
        when {
            bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            person.photoUrl.isNotEmpty() -> coil.compose.AsyncImage(
                person.photoUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            EmojiArt.assetName(person.emoji) != null -> EmojiArt(person.emoji, (size * 0.62f).dp)
            else -> Text(
                person.name.take(1).uppercase(), color = Color.White,
                fontSize = (size * 0.38f).sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
        }
    }
}

// ================= replies =================
/** The quote label a reply attaches — the message text, or a kind label
 *  for media. Shared by swipe-to-reply and the long-press menu so both
 *  paths attach exactly the same quote (and note replies arrive with
 *  their "Note: …" snapshot in the same field). */
fun replyQuoteLabel(m: TikuMessage): String = if (m.undecryptable) "This message can't be opened on this phone"
    else if (m.deletedForEveryone) "This message was deleted"
    // ROUND 22: quoting a gift message snapshots the gift label, never
    // the sealed text — the quote rides encrypted like every snapshot.
    else if (m.fx == "gift") "🎁 Gift" else when (m.kind) {
    MsgKind.IMAGE -> "Photo"
    MsgKind.VOICE -> "Voice message"
    MsgKind.VIDEO -> "Video"
    MsgKind.AUDIO -> "Audio file"
    else -> m.text
}.take(60)

/** ROUND 57 (Fix 1) — the in-bubble clock: every message bubble shows
 *  its own time, WhatsApp-style, small at the bubble's bottom-end
 *  corner. Formatted from the message's [TikuMessage.tsMs] as
 *  "h:mm a" in the local timezone; a message with no clock yet (the
 *  preview seeds) falls back to its preformatted [TikuMessage.time].
 *  (The day separators above the bubbles are untouched, and so is the
 *  Seen/Sent caption under the last own message.) */
private fun bubbleTimeText(m: TikuMessage): String =
    if (m.tsMs > 0L) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.tsMs))
    else m.time

/** ROUND 57 (Fix 1) — the bubble's bottom-end time, ~10.5 sp in a muted
 *  alpha of the bubble's own ink. On my own messages ([showTicks]) a
 *  compact tick rides beside it once the send has landed: ✓ sent,
 *  ✓✓ seen by the peer — the per-bubble form of the read state the
 *  Seen/Sent caption under the last own message also reports.
 *  ROUND 61c: TEXT bubbles rendered this via [RdTimedText] (an overlay
 *  beside the last text line, zero added height); the media, game
 *  and view-once bubbles below still place it directly.
 *  ROUND 63: [RdTimedText] is now an IN-FLOW custom Layout — this chip
 *  is a real measured child placed beside the last text line or, when
 *  that line leaves no room, on its own line below the text. The
 *  overlay form is gone (see RdTimedText's doc for the diagnosis). */
@Composable
private fun RdBubbleTime(m: TikuMessage, color: Color, showTicks: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(bubbleTimeText(m), color = color, fontSize = 10.5.sp, fontFamily = PoppinsFamily)
        if (showTicks && m.me && m.status == SendStatus.SENT) {
            Spacer(Modifier.width(4.dp))
            Text(
                if (m.seenByPeer) "✓✓" else "✓",
                color = color, fontSize = 10.5.sp, fontFamily = PoppinsFamily,
            )
        }
    }
}

/** ROUND 63 — where a text bubble's LAST line sits, in raw pixels from
 *  the text's own layout result: the last line's right edge, its top
 *  edge, and the text block's full size. [RdTimedText] places its
 *  in-flow time chip from these numbers. */
private data class RdTimeAnchor(
    val lastLineRight: Int,
    val lastLineTop: Int,
    val textWidth: Int,
    val textHeight: Int,
)

/** ROUND 63 — a TEXT bubble's message text with its Round 57 in-bubble
 *  time (and ticks) as a REAL, MEASURED part of the layout — the fix
 *  for the collision in the owner's screenshot (a reply bubble whose
 *  near-full last line, ending in an emoji, had "12:54 PM ✓✓" drawn
 *  straight over its words).
 *
 *  Why Round 61c's overlay broke (the diagnosed mechanism): the chip
 *  was a sibling overlay wrapped in a layout modifier reporting 0×0,
 *  placed from the text's onTextLayout result. Its "beside" test
 *  compared lastLineRight + gap + an ESTIMATED chip width against
 *  the old maxContentWidth parameter — the bubble's theoretical cap,
 *  not space any layout had actually reserved. When the last line ran
 *  to within an estimated chip width of that cap — exactly the reply /
 *  long-wrapped-line geometry — the code took its fallback branch and
 *  placed the chip at textHeight − chipEstH + 10 dp: i.e. mostly INSIDE
 *  the last line's line box, over the bubble's 11 dp bottom-padding
 *  strip. That only clears the text if the last line's glyphs stop
 *  ~5 dp above the line box's bottom edge (the "bottom leading below
 *  the baseline" the 61c notes assumed). With the chat's emoji
 *  fallback font the line box is tight — the final emoji and the
 *  letters' descenders reach the bottom of the box — so the chip,
 *  ~15 dp tall against an 11 dp strip, landed ON the glyphs. The
 *  estimate was the second failure in the same branch: the real
 *  Poppins chip can be wider than the 0.69 em/char guess, so a line
 *  judged "beside fits" could also leave the real chip touching the
 *  text it was placed next to.
 *
 *  The replacement — an in-flow custom [Layout] with two measured
 *  children (message text, time chip), WhatsApp's geometry:
 *  - The text is ALWAYS measured at the layout's full incoming width,
 *    so its wrap — and therefore its onTextLayout anchor — is exactly
 *    what the plain bubble would produce, in both placements. The
 *    anchor can never oscillate between placements.
 *  - BESIDE case (the measured chip fits in the empty tail of the
 *    last line: lastLineRight + 8 dp gap + chip width ≤ available
 *    width, and the chip is no taller than the last line's box): the
 *    chip is placed in that tail, bottom-aligned with the text. The
 *    layout's reported width GROWS to lastLineRight + gap + chip
 *    width when the text block itself is narrower, so the chip is
 *    contained inside the bubble; the reported height is the text's
 *    own height — short and multi-line bubbles keep their exact
 *    pre-Round-57 height, nothing is reserved above or below.
 *  - FLOW-DOWN case (the last line genuinely leaves no room): the
 *    chip is placed on its own line below the text, right-aligned,
 *    and the layout's reported height grows by exactly the chip's
 *    height + 2 dp — the ONLY case that adds height, and only for
 *    bubbles whose last line is near-full.
 *  In both cases the chip occupies a rectangle no glyph can reach:
 *  beside, it starts a full gap past the last line's measured right
 *  edge and stays inside that line's vertical band (the height
 *  guard); below, it starts under the text block entirely. No
 *  estimates anywhere — the chip is measured with its real font.
 *
 *  The message text renders EXACTLY as RdLinkedText rendered it (same
 *  plain / linkified paths, same link tap handling) — its semantics
 *  string stays precisely the message string, because the time
 *  remains its own composable, never appended characters. Until the
 *  first onTextLayout lands (one frame) the chip provisionally takes
 *  the flow-down slot; it settles before any idle frame completes. */
@Composable
private fun RdTimedText(
    m: TikuMessage,
    text: String,
    color: Color,
    linkColor: Color,
    fontSize: TextUnit,
    fontFamily: FontFamily?,
    timeColor: Color,
    showTicks: Boolean,
) {
    var anchor by remember(m.id, text, fontSize) { mutableStateOf<RdTimeAnchor?>(null) }
    val onLayout: (TextLayoutResult) -> Unit = { lr ->
        val last = (lr.lineCount - 1).coerceAtLeast(0)
        anchor = RdTimeAnchor(
            lastLineRight = lr.getLineRight(last).toInt(),
            lastLineTop = lr.getLineTop(last).toInt(),
            textWidth = lr.size.width,
            textHeight = lr.size.height,
        )
    }
    Layout(
        content = {
            val hasLink = remember(text) { firstLinkIn(text) != null }
            if (!hasLink) {
                Text(
                    text, color = color, fontSize = fontSize, fontFamily = fontFamily,
                    onTextLayout = onLayout,
                )
            } else {
                val annotated = remember(text, linkColor) { linkifyMessageText(text, linkColor) }
                val ctx = LocalContext.current
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    annotated,
                    color = color, fontSize = fontSize, fontFamily = fontFamily,
                    onTextLayout = { layout = it; onLayout(it) },
                    modifier = Modifier.pointerInput(annotated) {
                        detectTapGestures(onTap = { pos ->
                            val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                            annotated.getStringAnnotations(LINK_ANNOTATION, off, off)
                                .firstOrNull()?.let { openLinkInBrowser(ctx, it.item) }
                        })
                    },
                )
            }
            RdBubbleTime(m, color = timeColor, showTicks = showTicks)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val textP = measurables[0].measure(loose)
        val chipP = measurables[1].measure(loose)
        val gapPx = 8.dp.roundToPx()
        val a = anchor
        val beside = a != null &&
            a.lastLineRight + gapPx + chipP.width <= constraints.maxWidth &&
            chipP.height <= textP.height - a.lastLineTop
        if (beside) {
            val w = maxOf(textP.width, a!!.lastLineRight + gapPx + chipP.width)
                .coerceAtMost(constraints.maxWidth)
            layout(w, textP.height) {
                textP.placeRelative(0, 0)
                chipP.placeRelative(
                    a.lastLineRight + gapPx,
                    textP.height - chipP.height,
                )
            }
        } else {
            val downGapPx = 2.dp.roundToPx()
            val w = maxOf(textP.width, chipP.width).coerceAtMost(constraints.maxWidth)
            layout(w, textP.height + downGapPx + chipP.height) {
                textP.placeRelative(0, 0)
                chipP.placeRelative(w - chipP.width, textP.height + downGapPx)
            }
        }
    }
}

/** "3:24" media duration label (video / audio bubbles). */
private fun fmtDurSec(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** "12 MB" / "840 KB" payload size label for not-yet-loaded media. */
private fun fmtMediaSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

// ================= call events (round 4, kept verbatim) =================
/** Perspective-correct label for a call event. [iAmCaller] = the viewer is
 *  the one who placed the call. Owner's copy rules: no answer → caller sees
 *  "No answer", callee sees "Missed call"; declined → both see it; ended →
 *  "Call ended · duration" on both sides. */
fun callEventLabel(m: TikuMessage, iAmCaller: Boolean): String {
    val kindWord = if (m.callVideo) "Video call" else "Call"
    return when (m.callOutcome) {
        "ended" -> {
            val base = "$kindWord ended"
            if (m.callDurationSec > 0) "$base · ${"%d:%02d".format(m.callDurationSec / 60, m.callDurationSec % 60)}" else base
        }
        "declined" -> if (iAmCaller) "Call declined" else "You declined the call"
        "noanswer" -> if (iAmCaller) "No answer" else if (m.callVideo) "Missed video call" else "Missed call"
        else -> kindWord
    }
}

/** Centred system pill for a call event — build 47's pill, restyled to
 *  the redesign's CardC. Never a bubble, never side-aligned. */
@Composable
fun CallEventPill(m: TikuMessage) {
    val iAmCaller = m.callMine
    val missedIn = m.callOutcome == "noanswer" && !iAmCaller
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(RdCard)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Call, null, Modifier.size(13.dp), tint = if (missedIn) RdDanger else RdSub)
            Spacer(Modifier.width(6.dp))
            Text(
                "${callEventLabel(m, iAmCaller)} · ${m.time}",
                fontSize = 12.sp, color = RdSub, fontFamily = PoppinsFamily,)
        }
    }
}

/** ROUND 18 — the delete-for-everyone TOMBSTONE bubble: renders in place
 *  of a message whose sender deleted it for both sides (delAll). Muted
 *  card + blocked-circle vector icon + italic label — no content, no
 *  reactions, no media. Side follows the original sender (mine right,
 *  theirs left with avatar), exactly like IG. Long-press still opens the
 *  message menu (which offers Delete-for-me for the tombstone itself). */
@Composable
private fun RdTombstoneRow(m: TikuMessage, person: Person, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.me) { RdAv(person, 28); Spacer(Modifier.width(8.dp)) }
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(RdCard)
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(22.dp))
                .pointerInput(m.id) {
                    detectTapGestures(onLongPress = { onMenu() })
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Block, null, Modifier.size(15.dp), tint = RdSub)
            Spacer(Modifier.width(7.dp))
            Text(
                "This message was deleted",
                color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic, fontFamily = InterFamily,)
        }
    }
}

/** ROUND 30 — OWNER-ONLY vault rendering of an incoming tombstone.
 *  Composed ONLY on the owner's phone and ONLY when the vault holds a copy
 *  of this message (see OwnerVault): the owner's chat renders the message
 *  from the local vault instead of the tombstone placeholder. Every other
 *  device — and any tombstone this phone never loaded — keeps the plain
 *  RdTombstoneRow. TEXT renders as a normal peer bubble with a small
 *  "Deleted" marker under it; MEDIA renders blurred behind a
 *  "Deleted — tap to view" scrim until tapped (the reveal is per-row UI
 *  state only), then behaves like the real media: image/video open the
 *  full viewers (whose explicit Save-to-device works off the vault file),
 *  voice/audio play from the vault file. Long-press opens the tombstone's
 *  usual menu (Delete-for-me). Reply is deliberately NOT offered here:
 *  a reply would upload an encrypted quote snapshot of the deleted
 *  content back to the server. */
@Composable
private fun RdVaultDeletedRow(
    m: TikuMessage,
    entry: OwnerVault.Entry,
    person: Person,
    theme: ChatTheme?,
    textSp: Float,
    playing: Boolean,
    onMenu: () -> Unit,
    onPlayToggle: (TikuMessage) -> Unit,
    onOpenImage: (TikuMessage) -> Unit,
    onOpenVideo: (TikuMessage) -> Unit,
) {
    val emojiFont = rememberChatFontFamily() // ROUND 48: Inter + iOS-emoji chain (see EmojiArt)
    val bubbleText = if (theme == null) Color.White else theme.peerText
    var revealed by remember(m.id) { mutableStateOf(false) }
    // The synthetic message the viewers / voice player consume: content
    // from the vault, identity (id / sender / time) from the tombstone.
    val vm = remember(m.id, entry.mediaFile, entry.text) {
        m.copy(
            deletedForEveryone = false,
            text = entry.text,
            localPath = OwnerVault.mediaFile(entry)?.absolutePath,
            voiceDurSec = entry.durSec,
            amps = entry.amps,
            mimeType = entry.mime,
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        RdAv(person, 28); Spacer(Modifier.width(8.dp))
        Column {
            when (entry.kind) {
                "text" -> Column(
                    Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (theme != null) SolidColor(theme.peerBubble) else SolidColor(RdCard))
                        .pointerInput(m.id) {
                            detectTapGestures(onLongPress = { onMenu() })
                        }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Text(
                        entry.text, color = bubbleText,
                        fontSize = textSp.sp, fontFamily = emojiFont,
                    )
                }
                "image" -> VaultImageBox(
                    entry = entry, revealed = revealed,
                    onLongPress = onMenu,
                    onTap = { bmp ->
                        if (!revealed) revealed = true
                        else if (bmp != null) onOpenImage(vm.copy(photo = bmp))
                    },
                )
                "video" -> VaultVideoBox(
                    entry = entry, revealed = revealed,
                    onLongPress = onMenu,
                    onTap = {
                        if (!revealed) revealed = true
                        else onOpenVideo(vm)
                    },
                )
                else -> {
                    // voice / audio: no picture to blur — the bubble sits
                    // under the Deleted scrim until a tap reveals it, and
                    // only then can it play (from the vault file).
                    if (!revealed) {
                        Box(
                            Modifier.pointerInput(m.id) {
                                detectTapGestures(
                                    onLongPress = { onMenu() },
                                    onTap = { revealed = true },
                                )
                            },
                        ) {
                            RdVoiceBubble(vm, false, { revealed = true }, tint = bubbleText)
                            DeletedScrim(compact = true)
                        }
                    } else {
                        Box(
                            Modifier.pointerInput(m.id) {
                                detectTapGestures(onLongPress = { onMenu() })
                            },
                        ) {
                            RdVoiceBubble(vm, playing, { onPlayToggle(vm) }, tint = bubbleText)
                        }
                    }
                }
            }
            // The small "Deleted" marker — under every vault-rendered row,
            // so the state stays visible after a media reveal too.
            Row(
                Modifier.padding(start = 2.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, Modifier.size(10.dp), tint = RdSub)
                Spacer(Modifier.width(4.dp))
                Text("Deleted", color = RdSub, fontSize = 10.5.sp, fontFamily = PoppinsFamily)
            }
        }
    }
}

/** The blurred vault image in the photo bubble's fixed frame. Blur is a
 *  RenderEffect (Modifier.blur) on API 31+, and a cached downscale-blur
 *  bitmap below that — the fallback is pre-computed off the main thread. */
@Composable
private fun VaultImageBox(
    entry: OwnerVault.Entry,
    revealed: Boolean,
    onLongPress: () -> Unit,
    onTap: (android.graphics.Bitmap?) -> Unit,
) {
    val bmp = remember(entry.mediaFile) { OwnerVault.loadBitmap(entry) }
    val blurred by produceState<android.graphics.Bitmap?>(null, bmp) {
        val src = bmp
        value = if (src == null || Build.VERSION.SDK_INT >= 31) null
            else withContext(Dispatchers.Default) { vaultDownscaleBlur(src) }
    }
    Box(
        Modifier.size(220.dp, 260.dp).background(RdCard)
            .pointerInput(entry.msgId, revealed) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onTap(bmp) },
                )
            },
    ) {
        if (bmp != null) {
            when {
                revealed -> Image(
                    bmp.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                Build.VERSION.SDK_INT >= 31 -> Image(
                    bmp.asImageBitmap(), null,
                    Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop,
                )
                blurred != null -> Image(
                    blurred!!.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                else -> Box(Modifier.fillMaxSize().background(RdCard))
            }
        }
        if (!revealed) DeletedScrim(compact = false)
    }
}

/** The blurred vault video in the video bubble's fixed frame: the poster
 *  (lifted off the main thread) wears the same blur treatment; revealed,
 *  the tap hands the vault file to the full video viewer. */
@Composable
private fun VaultVideoBox(
    entry: OwnerVault.Entry,
    revealed: Boolean,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
) {
    val path = remember(entry.mediaFile) { OwnerVault.mediaFile(entry)?.absolutePath }
    val poster by produceState<android.graphics.Bitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(path)
                    r.frameAtTime
                } finally {
                    r.release()
                }
            }.getOrNull()
        }
    }
    val blurredPoster by produceState<android.graphics.Bitmap?>(null, poster) {
        value = if (poster == null || Build.VERSION.SDK_INT >= 31) null
            else withContext(Dispatchers.Default) { vaultDownscaleBlur(poster!!) }
    }
    Box(
        Modifier.size(220.dp, 260.dp).background(RdCard)
            .pointerInput(entry.msgId, revealed) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onTap() },
                )
            },
    ) {
        if (poster != null) {
            when {
                revealed -> Image(
                    poster!!.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                Build.VERSION.SDK_INT >= 31 -> Image(
                    poster!!.asImageBitmap(), null,
                    Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop,
                )
                blurredPoster != null -> Image(
                    blurredPoster!!.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            }
            if (revealed) Box(Modifier.fillMaxSize().background(Color.Black.copy(0.18f)))
        }
        if (revealed) {
            Box(
                Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape)
                    .background(Color.Black.copy(0.45f)).border(1.dp, Color.White.copy(0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(0.45f)).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(fmtDurSec(entry.durSec), color = Color.White, fontSize = 12.sp, fontFamily = PoppinsFamily)
            }
        }
        if (!revealed) DeletedScrim(compact = false)
    }
}

/** The "Deleted — tap to view" scrim over unrevealed vault media. */
@Composable
private fun DeletedScrim(compact: Boolean) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(0.55f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Delete, null, Modifier.size(13.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text(
                if (compact) "Deleted — tap to open" else "Deleted — tap to view",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,)
        }
    }
}

/** Classic downscale-blur for API < 31 (where Modifier.blur has no
 *  RenderEffect to ride): shrink to a 14-px-wide strip and scale back up
 *  with filtering, twice — the result reads as a strong gaussian-ish blur
 *  and is computed once, off the main thread, by the caller. */
private fun vaultDownscaleBlur(src: android.graphics.Bitmap): android.graphics.Bitmap {
    val w = 14
    val h = maxOf(1, (src.height * (w.toFloat() / src.width)).toInt())
    var cur = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    cur = android.graphics.Bitmap.createScaledBitmap(cur, src.width, src.height, true)
    val small2 = android.graphics.Bitmap.createScaledBitmap(cur, w, h, true)
    return android.graphics.Bitmap.createScaledBitmap(small2, src.width, src.height, true)
}

/** ROUND 19 — the undecryptable placeholder: renders in place of a
 *  message whose ciphertext this phone cannot open (history written
 *  under a retired keypair, e.g. from before a journal reset). ONE
 *  muted row — faint card, small italic sub-coloured label — never a
 *  blank bubble. Side follows the original sender (mine right, theirs
 *  left with avatar). No reactions / reply / star / media on it: the
 *  long-press menu offers Delete-for-me only (see the menu overlay). */
@Composable
private fun RdUndecryptableRow(m: TikuMessage, person: Person, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.me) { RdAv(person, 28); Spacer(Modifier.width(8.dp)) }
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(0.04f))
                .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(22.dp))
                .pointerInput(m.id) {
                    detectTapGestures(onLongPress = { onMenu() })
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "This message can't be opened on this phone",
                color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic, fontFamily = InterFamily,)
        }
    }
}

/** ROUND 18 — the IG unread divider: a centred pill rendered directly
 *  above the first unread incoming message, labelled with the unread
 *  count captured when the chat was opened. Owner's exact rule: counts
 *  above 5 collapse to "4+ new messages". */
@Composable
private fun RdUnreadDivider(count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(RdCard)
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                when {
                    count > 5 -> "4+ new messages"
                    count == 1 -> "1 new message"
                    else -> "$count new messages"
                },
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RdRose, fontFamily = InterTight,)
        }
    }
}

// ================= image viewer (round 8, restyled dark) =================
/**
 * Full-screen image viewer: tap an image message → the photo fills the
 * screen (fit), pinch to zoom (1–4×, transformable — transform-only, no
 * layout work), ✕ or Back closes. Saving is the EXPLICIT "Save to device"
 * button (the same MediaSaver pipeline as the message menu) — opening the
 * viewer never writes anything anywhere. Rendered as an overlay INSIDE
 * the chat (same activity window), so FLAG_SECURE + zero-trace behaviour
 * are inherited rather than re-implemented on a dialog window.
 */
@Composable
fun ImageViewerOverlay(m: TikuMessage, saveAllowed: Boolean = true, onClose: () -> Unit) {
    val bmp = m.photo
    if (bmp == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    BackHandler { onClose() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        if (zoom > 1f) { panX += panChange.x; panY += panChange.y } else { panX = 0f; panY = 0f }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xF2000000)).testTag("image_viewer"),
    ) {
        Image(
            bmp.asImageBitmap(), null,
            Modifier.fillMaxSize().padding(12.dp)
                .graphicsLayer {
                    scaleX = zoom; scaleY = zoom
                    translationX = panX; translationY = panY
                }
                .transformable(transformState),
            contentScale = ContentScale.Fit,
        )
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(RdCard)
                    .border(1.dp, Color.White.copy(0.12f), CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, Modifier.size(20.dp), tint = Color.White) }
            // ROUND 31: the Save chip exists only when saving is
            // allowed — a partner's view-once photo hides it entirely
            // (WhatsApp parity), except under the owner's override.
            if (saveAllowed) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(RdCard)
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(18.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (Config.PREVIEW_UI_ONLY) {
                            AppState.toast("Saved to device")
                        } else {
                            val ok = MediaSaver.saveToDevice(AppState.appCtx, m)
                            AppState.toast(if (ok) "Saved to device" else "Couldn't save")
                        }
                        onClose()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SaveAlt, null, Modifier.size(17.dp), tint = Color.White)
                Spacer(Modifier.width(7.dp))
                Text("Save to device", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = PoppinsFamily)
            }
            }
        }
    }
}

/**
 * Full-screen video player (round 15): the decrypted cache file plays in
 * a system VideoView with the standard media controller (play/pause +
 * seek), ✕ or Back closes. Same overlay pattern as the image viewer —
 * rendered INSIDE the chat so FLAG_SECURE covers it — and the same
 * explicit Save-to-device button; opening it writes nothing anywhere.
 */
@Composable
fun VideoViewerOverlay(m: TikuMessage, saveAllowed: Boolean = true, onUnavailable: () -> Unit = {}, onClose: () -> Unit) {
    val path = m.localPath
    if (path == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    // ROUND 34: a held path whose cache file is GONE (OS cache purge)
    // used to open a player that hung forever on a black "buffering"
    // screen — no error listener, no way out but Back. Refuse the dead
    // file up front and let the caller reload it instead.
    if (!File(path).exists()) {
        LaunchedEffect(Unit) { onUnavailable(); onClose() }
        return
    }
    BackHandler { onClose() }
    var vv by remember { mutableStateOf<android.widget.VideoView?>(null) }
    DisposableEffect(path) { onDispose { runCatching { vv?.stopPlayback() } } }
    Box(
        Modifier.fillMaxSize().background(Color(0xF2000000)).testTag("video_viewer"),
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { c ->
                android.widget.VideoView(c).apply {
                    setVideoPath(path)
                    val mc = android.widget.MediaController(c)
                    mc.setAnchorView(this)
                    setMediaController(mc)
                    setOnPreparedListener { start() }
                    // ROUND 34: a corrupt/unplayable file is a spoken
                    // error + close, never an eternal black screen.
                    setOnErrorListener { _, _, _ ->
                        AppState.toast("Couldn't play this video")
                        onClose()
                        true
                    }
                    vv = this
                }
            },
            modifier = Modifier.fillMaxSize().padding(12.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(RdCard)
                    .border(1.dp, Color.White.copy(0.12f), CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, Modifier.size(20.dp), tint = Color.White) }
            // ROUND 31: the Save chip exists only when saving is
            // allowed — a partner's view-once video hides it entirely
            // (WhatsApp parity), except under the owner's override.
            if (saveAllowed) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(RdCard)
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(18.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (Config.PREVIEW_UI_ONLY) {
                            AppState.toast("Saved to device")
                        } else {
                            val ok = MediaSaver.saveToDevice(AppState.appCtx, m)
                            AppState.toast(if (ok) "Saved to device" else "Couldn't save")
                        }
                        onClose()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SaveAlt, null, Modifier.size(17.dp), tint = Color.White)
                Spacer(Modifier.width(7.dp))
                Text("Save to device", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = PoppinsFamily)
            }
            }
        }
    }
}

// ================= message bubbles (the file's BubbleRow) =================
private fun Modifier.rdDrawLeftBar(): Modifier = this.drawBehind {
    drawRect(Color.White.copy(0.55f), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
}

/** ROUND 24 — the sealed gift as Instagram's actual wrap: the bubble
 *  IS a pink gift — rose→pink gradient pill, a white ribbon band down
 *  the middle crossed by a horizontal band, and a 🎀 bow riding the
 *  top edge. NO text of the message is composed anywhere in it (the
 *  seal is structural, not a caption over hidden text). [unwrap] is
 *  the 0→1 tap-out animation: bands + bow fade (the bow also swells)
 *  before the caller flips the gift open and the real bubble pops in.
 *  The bow is a child of the UN-clipped root box so it can overlap the
 *  pill's edge — the bubble call site passes withBow = false and draws
 *  its bow in the row's dressing layer instead, because the bubble
 *  column clips its own content. Shared by the bubble, the long-press
 *  menu preview and (mini) the picker tray. */
@Composable
private fun RdGiftWrapPill(unwrap: Float = 0f, withBow: Boolean = true, mini: Boolean = false) {
    val band = if (mini) 9.dp else 13.dp
    Box {
        Box(
            Modifier.width(if (mini) 96.dp else 124.dp).height(if (mini) 40.dp else 56.dp)
                .clip(RoundedCornerShape(if (mini) 14.dp else 20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF7EB0), Color(0xFFF43F8E)))),
        ) {
            Box(
                Modifier.width(band).fillMaxHeight().align(Alignment.Center)
                    .graphicsLayer { alpha = 1f - unwrap }
                    .background(Color.White.copy(0.92f)),
            )
            Box(
                Modifier.height(band).fillMaxWidth().align(Alignment.Center)
                    .graphicsLayer { alpha = 1f - unwrap }
                    .background(Color.White.copy(0.92f)),
            )
        }
        if (withBow) {
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = if (mini) (-8).dp else (-11).dp)
                    .graphicsLayer {
                        alpha = 1f - unwrap
                        val s = 1f + 0.45f * unwrap
                        scaleX = s; scaleY = s
                    },
            ) { EmojiGlyph("🎀", if (mini) 18.dp else 24.dp) }
        }
    }
}

// ================= ROUND 24 — persistent bubble dressing =================
// Instagram's effects are not a passing overlay: the sent bubble WEARS
// the effect, on both phones, forever. These composables are children
// of the bubble's wrap Box (see RdBubbleRow) — glyphs pinned over the
// bubble's corners/edges, drawn outside the bubble column so its clip
// can't cut them, and laid out so they never change the bubble's own
// size or position (overflow only, via align + offset).

/** fx="hearts": small pink hearts pinned around the corners. */
@Composable
private fun BoxScope.RdFxHeartsDressing() {
    Box(
        Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-9).dp)
            .graphicsLayer { rotationZ = -14f },
    ) { EmojiGlyph("💗", 17.dp) }
    Box(
        Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-10).dp)
            .graphicsLayer { rotationZ = 12f },
    ) { EmojiGlyph("🩷", 15.dp) }
    Box(
        Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 7.dp)
            .graphicsLayer { rotationZ = -8f },
    ) { EmojiGlyph("💖", 14.dp) }
    Box(
        Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 6.dp)
            .graphicsLayer { rotationZ = 10f; alpha = 0.9f },
    ) { EmojiGlyph("🩷", 12.dp) }
}

/** ROUND 34 — the OPENED gift's wrap. Instagram keeps the message
 *  INSIDE the gift after unwrapping, so the revealed bubble keeps the
 *  wrap's identity permanently: the sealed pill's rose→pink gradient
 *  as a full text-bearing bubble background, its ribbon cross softened
 *  to a whisper behind the text, drawn at the dressing box's exact
 *  bounds (matchParentSize) behind the bubble column — which stays
 *  transparent for opened gifts, exactly like the sealed state. The
 *  🎀 rides the caller's un-clipped dressing layer (see the row), the
 *  same trick as the sealed bow.
 *
 *  ROUND 39 — the wrap reads as ONE box, not layers (the owner's
 *  order: "border ko link kardo" — the frame must connect). A single
 *  geometry governs everything: [shape] is the very RoundedCornerShape
 *  instance the bubble column clips itself with (the row passes it
 *  in), and inside it, in draw order: the gradient; the ribbon bands
 *  spanning the FULL box edge-to-edge (fillMaxHeight / fillMaxWidth,
 *  so each band's ends reach the frame on both sides); and finally a
 *  CONTINUOUS soft-white border stroke on that same shape, drawn
 *  LAST as an overlay so the frame line is unbroken and the bands
 *  visibly run INTO it — linked, no floating segments, no gaps. The
 *  caller's 🎀 sits at top-centre, exactly where the vertical band
 *  meets the top border — the classic knot point. */
@Composable
private fun BoxScope.RdOpenedGiftWrap(shape: RoundedCornerShape) {
    Box(
        Modifier.matchParentSize().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF7EB0), Color(0xFFF43F8E)))),
    ) {
        Box(
            Modifier.width(11.dp).fillMaxHeight().align(Alignment.Center)
                .background(Color.White.copy(0.30f)),
        )
        Box(
            Modifier.height(11.dp).fillMaxWidth().align(Alignment.Center)
                .background(Color.White.copy(0.30f)),
        )
        // The frame: one continuous stroke on the box's own shape,
        // over the band ends so ribbon and border read as one box.
        Box(Modifier.matchParentSize().border(1.75.dp, Color.White.copy(0.55f), shape))
    }
}

/** fx="fire", behind the bubble: a warm radial glow hugging it,
 *  breathing on a slow reverse loop. */
@Composable
private fun BoxScope.RdFxFireGlow() {
    val tr = rememberInfiniteTransition(label = "fxFireGlow")
    val a by tr.animateFloat(
        initialValue = 0.72f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(840, easing = LinearEasing), RepeatMode.Reverse),
        label = "fxFireGlowA",
    )
    Box(
        Modifier.matchParentSize()
            .graphicsLayer { scaleX = 1.28f; scaleY = 1.38f; alpha = a }
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFFF7A00).copy(0.42f), Color(0xFFFF3D00).copy(0.16f), Color.Transparent),
                ),
            ),
    )
}

/** fx="fire", in front: small flames licking the top edge and bottom
 *  corners, flickering on two cheap infinite loops. The bubble's own
 *  colours are untouched — the fire dresses it, never recolours it. */
@Composable
private fun BoxScope.RdFxFireFlames() {
    val tr = rememberInfiniteTransition(label = "fxFireFlames")
    val f1 by tr.animateFloat(
        initialValue = 0.92f, targetValue = 1.09f,
        animationSpec = infiniteRepeatable(tween(620, easing = LinearEasing), RepeatMode.Reverse),
        label = "fxFireF1",
    )
    val f2 by tr.animateFloat(
        initialValue = 1.07f, targetValue = 0.93f,
        animationSpec = infiniteRepeatable(tween(930, easing = LinearEasing), RepeatMode.Reverse),
        label = "fxFireF2",
    )
    Box(
        Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = (-11).dp)
            .graphicsLayer { scaleX = f1; scaleY = f1 },
    ) { EmojiGlyph("🔥", 16.dp) }
    Box(
        Modifier.align(Alignment.TopCenter).offset(y = (-13).dp)
            .graphicsLayer { scaleX = f2; scaleY = f2 },
    ) { EmojiGlyph("🔥", 19.dp) }
    Box(
        Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-11).dp)
            .graphicsLayer { scaleX = f1; scaleY = f1 },
    ) { EmojiGlyph("🔥", 15.dp) }
    Box(
        Modifier.align(Alignment.BottomStart).offset(x = (-3).dp, y = 6.dp)
            .graphicsLayer { scaleX = f2; scaleY = f2 },
    ) { EmojiGlyph("🔥", 13.dp) }
    Box(
        Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 6.dp)
            .graphicsLayer { scaleX = f1; scaleY = f1 },
    ) { EmojiGlyph("🔥", 13.dp) }
}

/** fx="confetti": a deterministic sprinkle of dots + short dashes
 *  riding the bubble's rim (seeded by the message id — identical on
 *  every device, every frame, no per-frame allocation beyond the
 *  fixed dozen) plus one 🎉 at the top-right corner. Drawn just
 *  inside the rim: a Canvas cannot paint past its own bounds, and
 *  enlarging it would move layout — the glyph supplies the overflow. */
@Composable
private fun BoxScope.RdFxConfettiDressing(seed: Int) {
    val palette = remember {
        listOf(RdRose, Color(0xFFFFC857), Color.White, Color(0xFFFF8A5C), Color(0xFF8E7CFF), Color(0xFF4FD1C5))
    }
    Canvas(Modifier.matchParentSize()) {
        var s = seed * 1103515245 + 12345
        fun next(): Float {
            s = s * 1103515245 + 12345
            return ((s ushr 16) and 0x7FFF) / 32767f
        }
        val inset = 3.dp.toPx()
        repeat(12) { k ->
            val frac = next()
            val col = palette[k % palette.size]
            val pt = when (k % 4) {
                0 -> Offset(frac * size.width, inset)
                1 -> Offset(size.width - inset, frac * size.height)
                2 -> Offset(frac * size.width, size.height - inset)
                else -> Offset(inset, frac * size.height)
            }
            if (k % 3 == 2) {
                val ang = next() * 3.14159f
                val len = 5.dp.toPx()
                drawLine(
                    col, pt,
                    Offset(pt.x + len * kotlin.math.cos(ang), pt.y + len * kotlin.math.sin(ang)),
                    strokeWidth = 1.8.dp.toPx(),
                )
            } else {
                drawCircle(col, (1.5f + next() * 1.4f).dp.toPx(), pt)
            }
        }
    }
    Box(
        Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-9).dp)
            .graphicsLayer { rotationZ = 14f },
    ) { EmojiGlyph("🎉", 15.dp) }
}

/** ROUND 24 — the picker tray's live preview: the CURRENT typed text
 *  rendered as a mini dressed bubble for this effect, like Instagram's
 *  tray (your own words inside each effect before you pick). Gift
 *  previews as the sealed mini wrap — text hidden, as the real bubble
 *  will be. Static small-scale variants of the dressing on purpose:
 *  the tray needs a thumbnail, not the full animated dressing. */
@Composable
private fun RdFxMiniPreview(kind: String, text: String, acc: Pair<Color, Color>) {
    when (kind) {
        "gift" -> RdGiftWrapPill(mini = true)
        else -> Box {
            Box(
                Modifier.widthIn(max = 120.dp).clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(acc.first, acc.second)))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text, color = Color.White, fontSize = 11.5.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = rememberChatFontFamily(),)
            }
            when (kind) {
                "hearts" -> {
                    Box(
                        Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-6).dp)
                            .graphicsLayer { rotationZ = -14f },
                    ) { EmojiGlyph("💗", 11.dp) }
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 5.dp)
                            .graphicsLayer { rotationZ = 10f },
                    ) { EmojiGlyph("🩷", 10.dp) }
                }
                "fire" -> {
                    Box(
                        Modifier.align(Alignment.TopCenter).offset(y = (-7).dp),
                    ) { EmojiGlyph("🔥", 11.dp) }
                    Box(
                        Modifier.align(Alignment.BottomStart).offset(x = (-3).dp, y = 4.dp),
                    ) { EmojiGlyph("🔥", 9.dp) }
                }
                "confetti" -> {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp)
                            .graphicsLayer { rotationZ = 14f },
                    ) { EmojiGlyph("🎉", 11.dp) }
                }
            }
        }
    }
}

/** Instagram-style per the file: my messages on the right (theme
 *  gradient), theirs on the left (CardC + 28dp avatar). Gestures are the
 *  file's own: horizontal drag springs back and arms reply past 70px,
 *  double-tap hearts (+ burst), long-press opens the menu. */
@Composable
private fun RdBubbleRow(
    i: Int,
    m: TikuMessage,
    person: Person,
    acc: Pair<Color, Color>,
    textSp: Float,
    playing: Boolean,
    onPlayToggle: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onMenu: () -> Unit,
    onMediaTap: () -> Unit,
    onRetry: () -> Unit,
    /** ROUND 22: for a gift message, whether THIS device has opened it;
     *  taps on any effect message route to [onFxTap] (gift → unwrap,
     *  fire / confetti / hearts → replay the overlay). */
    fxOpened: Boolean = false,
    onFxTap: (TikuMessage) -> Unit = {},
    /** ROUND 24: the bubble's on-screen anchor (root px), reported on
     *  layout for hearts / fire messages only — the caller keeps it so
     *  the message's overlay playback can erupt AROUND this bubble. */
    onBubblePositioned: (FxAnchor) -> Unit = {},
    /** ROUND 22 (insta port): true only while this row is a FRESH tail
     *  arrival inside its pop window (the caller tracks first-seen
     *  ids — history and loadOlder rows always pass false). Drives the
     *  graphicsLayer entrance pop; see the layer itself for why it can
     *  never disturb layout or the follow glide. */
    animateIn: Boolean = false,
    /** ROUND 26: the shared chat theme, or null = Default. When set,
     *  the bubble BASE colour + its text colour come from the theme —
     *  the fx dressing overlays are untouched, and media bubbles / the
     *  sealed gift wrap keep their own rendering. */
    theme: ChatTheme? = null,
) {
    val scope = rememberCoroutineScope()
    // ROUND 26: this bubble's text colour under the theme (on Default
    // it is Color.White — the exact pre-round constant).
    // ROUND 34: an OPENED gift keeps its wrap (see RdOpenedGiftWrap) —
    // its text is white on the wrap pink, whatever the theme says.
    val bubbleText = when {
        m.fx == "gift" && fxOpened -> Color.White
        theme == null -> Color.White
        m.me -> theme.ownText
        else -> theme.peerText
    }
    val off = remember { Animatable(0f) }
    val heart = remember { Animatable(0f) }
    // ROUND 22: the gift reveal pop — when this row's gift flips to
    // opened AFTER its wrap actually rendered on this screen, the
    // revealed bubble springs 0.82 → 1 once. Rows composed already-open
    // (history, another device... same device after restart) never
    // animate: giftSeenWrapped only flips while the sealed wrap is up.
    val giftPop = remember(m.id) { Animatable(1f) }
    var giftSeenWrapped by remember(m.id) { mutableStateOf(false) }
    LaunchedEffect(fxOpened) {
        if (m.fx == "gift" && !fxOpened) giftSeenWrapped = true
        if (fxOpened && m.fx == "gift" && giftSeenWrapped) {
            giftPop.snapTo(0.82f)
            giftPop.animateTo(1f, spring(0.5f, 380f))
        }
    }
    // ROUND 24: the sealed-gift unwrap — a tap first runs this 0→1
    // (~260 ms: ribbon bands fade, the bow swells and fades), THEN the
    // caller flips the gift open and the giftPop spring above brings
    // the real bubble in. fxOpened is read through a state holder
    // because the tap gesture lambda below is keyed on (id, reactions)
    // and would otherwise capture a stale value.
    val sealedGift = m.fx == "gift" && !fxOpened
    // ROUND 34: the opened gift — no longer a plain bubble (the owner's
    // order): it keeps the wrap identity via RdOpenedGiftWrap behind a
    // transparent bubble column, exactly like the sealed state does.
    val openedGift = m.fx == "gift" && fxOpened
    val unwrapP = remember(m.id) { Animatable(0f) }
    val fxOpenedState = rememberUpdatedState(fxOpened)
    // ROUND 24: a lone-emoji message renders its glyph large, IG-style.
    val loneEmoji = remember(m.text) {
        if (m.kind == MsgKind.TEXT) singleEmojiGrapheme(m.text) else null
    }
    // ROUND 22 (insta port): the entrance pop — an Animatable driven
    // purely through graphicsLayer (alpha / scale / translationY), so
    // it never feeds back into the row's layout size and can never
    // fight the follow glide (the round-20 lesson: only layout-
    // affecting entrances fought the scroll, which is why rdBubbleIn
    // died; this port deliberately revives ONLY the transform half of
    // the reference's MessageBubble). History rows never arm it
    // (animateIn=false) and sit at 1f.
    val enterPop = remember(m.id) { Animatable(1f) }
    LaunchedEffect(m.id, animateIn) {
        if (animateIn) {
            enterPop.snapTo(0f)
            enterPop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // ROUND 22 (insta port): when a double-tap ADDS my ❤️, the
    // reaction chip pops with the reference's bouncy spring
    // (animateFloatAsState + DampingRatioMediumBouncy). Armed only by
    // double-tap — picks from the reaction bar keep the chip's usual
    // rdPop — and disarmed when my heart leaves, so the next add pops
    // anew. The layer is only attached while armed, so the 0f rest
    // value can never hide a chip the bar created.
    var heartChipPop by remember(m.id) { mutableStateOf(false) }
    val heartChipScale by animateFloatAsState(
        targetValue = if (heartChipPop) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heartChipPop",
    )
    val myHearted = m.reactions[if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid()] == "❤️"
    LaunchedEffect(myHearted) { if (!myHearted) heartChipPop = false }
    // ROUND 22: mixed text renders through the owner's iOS emoji font
    // (emoji draw as iOS bitmaps, Latin falls back to the platform
    // fonts — see EmojiArt.kt / worker A's report §5).
    val emojiFont = rememberChatFontFamily() // ROUND 48: Inter + iOS-emoji chain (see EmojiArt)
    var heartKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(heartKey) { if (heartKey > 0) { heart.snapTo(0f); heart.animateTo(1f, tween(800, easing = LinearEasing)) } }
    val media = m.kind == MsgKind.IMAGE || m.kind == MsgKind.VIDEO
    // ROUND 31 — view-once state for THIS row. The counts live in the
    // per-account prefs (VoStore); reading its stateVersion here
    // subscribes the row, so a consume in the tap path re-renders the
    // bubble (the remaining count, and the flip to "Opened" at zero).
    // The owner's unlimited override never expires. My own sends are
    // never gated — they only get the status line below the bubble.
    val voCtx = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") VoStore.stateVersion // subscription read
    val voIncoming = m.vo != null && !m.me
    val voBypass = voIncoming && VoStore.ownerBypass(voCtx)
    val voUsed = if (voIncoming) VoStore.opensUsed(voCtx, m.id) else 0
    val voExpired = voIncoming && !voBypass && voUsed >= (m.vo ?: 0)
    val voRemaining = if (m.vo != null) (m.vo!! - voUsed).coerceAtLeast(0) else 0
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!m.me) { RdAv(person, 28); Spacer(Modifier.width(8.dp)) }
            Box(contentAlignment = Alignment.Center) {
                Column(
                    Modifier.offset { IntOffset(off.value.roundToInt(), 0) }
                        .graphicsLayer {
                            // ROUND 22: two draw-only pops share this
                            // layer — the insta-port entrance pop
                            // (reference MessageBubble: alpha 0→1,
                            // scale 0.6→1, translationY +40dp→0,
                            // transformOrigin at the sender's bottom
                            // corner) and the gift reveal pop
                            // (identity for every non-gift row, and
                            // for gifts at rest — giftPop sits at 1f,
                            // and enterPop sits at 1f for every
                            // non-fresh row, so at rest this layer is
                            // an exact identity). Pure graphicsLayer
                            // transforms never change the row's layout
                            // size or position, so the follow glide's
                            // measured deltas stay exact and the two
                            // animations simply run in parallel.
                            val ep = enterPop.value
                            val gs = if (m.fx == "gift") giftPop.value else 1f
                            val s = (0.6f + 0.4f * ep) * gs
                            scaleX = s; scaleY = s
                            alpha = (ep * gs).coerceIn(0f, 1f)
                            translationY = (1f - ep) * 40.dp.toPx()
                            transformOrigin = TransformOrigin(if (m.me) 1f else 0f, 1f)
                        }
                        .pointerInput(m.id, m.me) {
                            detectHorizontalDragGestures(
                                onDragEnd = { if (abs(off.value) > 70f) onReply(); scope.launch { off.animateTo(0f, spring(0.6f, 400f)) } },
                                onDragCancel = { scope.launch { off.animateTo(0f) } },
                            ) { change, dx ->
                                change.consume()
                                scope.launch { off.snapTo((off.value + dx).coerceIn(if (m.me) -130f else 0f, if (m.me) 0f else 130f)) }
                            }
                        }
                        .pointerInput(m.id, m.reactions) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // ROUND 22 (insta port): double-tap
                                    // TOGGLES my ❤️ through the existing
                                    // reaction path — the caller's
                                    // react() adds it, or removes it
                                    // when it is already mine, exactly
                                    // like picking ❤️ in the reaction
                                    // bar (the old row gate suppressed
                                    // the toggle-off whenever ANY
                                    // reaction value read "❤️" first).
                                    // The heart burst plays on every
                                    // double-tap, as before; a tap that
                                    // ADDS the heart also arms the
                                    // chip's bouncy pop.
                                    if (!myHearted) heartChipPop = true
                                    onReact("❤️")
                                    heartKey++
                                },
                                onLongPress = { onMenu() },
                                onTap = {
                                    // ROUND 22 + 24: an effect message's
                                    // tap belongs to the effect. A SEALED
                                    // gift first plays its unwrap (bands
                                    // fade, bow swells — see unwrapP) and
                                    // only then routes to the caller,
                                    // which flips it open into the pop.
                                    // Any other fx, and any lone-emoji
                                    // message with a text-derived effect,
                                    // replays its animation. Effect
                                    // messages are TEXT-only, so the
                                    // media routing below is unreachable
                                    // for them.
                                    if (m.fx == "gift" && !fxOpenedState.value) {
                                        if (unwrapP.value == 0f) {
                                            scope.launch {
                                                unwrapP.animateTo(1f, tween(260, easing = LinearEasing))
                                                onFxTap(m)
                                            }
                                        }
                                    }
                                    else if (m.fx != null || singleEmojiOverlayKind(m) != null || singleEmojiDesignKind(m) != null || singleEmojiFx2Kind(m) != null) onFxTap(m)
                                    // image opens its viewer once decoded;
                                    // a video tap either opens its player
                                    // (loaded) or starts the tap-to-load —
                                    // the caller decides by localPath.
                                    else if (m.kind == MsgKind.IMAGE && m.photo != null) onMediaTap()
                                    else if (m.kind == MsgKind.VIDEO) onMediaTap()
                                },
                            )
                        },
                    horizontalAlignment = if (m.me) Alignment.End else Alignment.Start,
                ) {
                    // ROUND 39: ONE corner geometry for the whole bubble —
                    // this single shape instance clips the bubble column
                    // below AND frames the opened gift's wrap, so the
                    // gift's border can never drift from the bubble it
                    // wraps (the value is the pre-round 22.dp constant).
                    val bubbleShape = RoundedCornerShape(22.dp)
                    // ROUND 24: the bubble's dressing wrap. The fire glow
                    // sits BEHIND the bubble, the dressing glyphs/canvas sit
                    // IN FRONT — all children of this box, whose bounds are
                    // exactly the bubble's (dressing overflows via align +
                    // offset, never via layout). hearts / fire rows also
                    // report the box's root position as the overlay anchor.
                    Box(
                        Modifier.then(
                            if (m.fx == "hearts" || m.fx == "fire") Modifier.onGloballyPositioned { c ->
                                val pos = c.positionInRoot()
                                onBubblePositioned(FxAnchor(pos.x + c.size.width / 2f, pos.y + c.size.height))
                            } else Modifier,
                        ),
                    ) {
                        if (m.fx == "fire") RdFxFireGlow()
                        // ROUND 34: the opened gift's wrap, behind the
                        // (transparent) bubble column — the message sits
                        // INSIDE the gift, permanently, both sides.
                        // ROUND 39: it takes the bubble's own shape so
                        // its border IS the bubble's outline.
                        if (openedGift) RdOpenedGiftWrap(bubbleShape)
                        Column(
                            Modifier.widthIn(max = 280.dp).clip(bubbleShape)
                                .background(if (media) SolidColor(RdCard) else if (sealedGift || openedGift) SolidColor(Color.Transparent) else if (theme != null) SolidColor(if (m.me) theme.ownBubble else theme.peerBubble) else if (m.me) Brush.linearGradient(listOf(acc.first, acc.second)) else SolidColor(RdCard))
                                .then(if (media || sealedGift) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 11.dp)),
                        ) {
                            when (m.kind) {
                                // ROUND 31: an incoming view-once message
                                // renders the vo pill, never the picture —
                                // the media shows only inside the viewer,
                                // and opening it is what consumes a view.
                                MsgKind.IMAGE -> if (voIncoming) RdVoBubble(m, voRemaining, voExpired, voBypass) else RdImageBubble(m)
                                MsgKind.VIDEO -> if (voIncoming) RdVoBubble(m, voRemaining, voExpired, voBypass) else RdVideoBubble(m)
                                MsgKind.VOICE, MsgKind.AUDIO -> RdVoiceBubble(m, playing, onPlayToggle, tint = bubbleText, timeText = bubbleTimeText(m))
                                else -> {
                                    if (sealedGift) {
                                        // ROUND 24: the sealed gift — the
                                        // bubble IS the wrap (the pink pill
                                        // replaces the themed bubble; see the
                                        // transparent background / no padding
                                        // above) and the text is NOT composed
                                        // at all in this branch, so nothing
                                        // on screen (or in an accessibility
                                        // read of the bubble) can leak it.
                                        // The bow rides the dressing layer
                                        // below: this column clips content.
                                        RdGiftWrapPill(unwrapP.value, withBow = false)
                                    } else {
                                        // ROUND 28: no "🎁 Gift" header label
                                        // above the text (owner's order).
                                        // ROUND 34: the bubble itself is no
                                        // longer plain either — the wrap
                                        // layer behind this column (see
                                        // RdOpenedGiftWrap) keeps the text
                                        // INSIDE the gift, Instagram-style,
                                        // and the text colour is forced
                                        // white on the wrap (bubbleText).
                                        // The sealed wrap above is untouched;
                                        // list previews still say
                                        // "Gift message".
                                        if (m.replyQuote != null) {
                                            Text(
                                                m.replyQuote, color = bubbleText.copy(0.75f), fontSize = 12.sp, maxLines = 1,
                                                fontFamily = emojiFont,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(bottom = 6.dp).rdDrawLeftBar().padding(start = 8.dp),
                                            )
                                        }
                                        // ROUND 46: links inside the text
                                        // are detected + styled, and the
                                        // first one grows an IG-style
                                        // preview card under the text (see
                                        // LinkPreview.kt). The sealed gift
                                        // never reaches this branch — its
                                        // text is not composed at all, so
                                        // nothing can be fetched for it.
                                        val msgLink = remember(m.text) { firstLinkIn(m.text) }
                                        // ROUND 63: the text carries its
                                        // in-bubble time IN FLOW beside
                                        // its last line (RdTimedText) —
                                        // no layout row under the text
                                        // (Round 57) and no overlay
                                        // (Round 61c), so the bubble
                                        // keeps its build-93 height
                                        // unless the last line is full.
                                        RdTimedText(
                                            m = m,
                                            text = m.text, color = bubbleText,
                                            linkColor = if (m.me || openedGift) Color.White else RdRose,
                                            fontSize = if (loneEmoji != null) 34.sp else textSp.sp,
                                            fontFamily = emojiFont,
                                            timeColor = bubbleText.copy(alpha = 0.65f),
                                            showTicks = true,
                                        )
                                        if (msgLink != null) {
                                            Spacer(Modifier.height(8.dp))
                                            RdLinkPreviewCard(
                                                url = msgLink,
                                                cardBg = bubbleText.copy(alpha = 0.08f),
                                                borderColor = bubbleText.copy(alpha = 0.14f),
                                                titleColor = bubbleText,
                                                descColor = bubbleText.copy(alpha = 0.72f),
                                                hostColor = bubbleText.copy(alpha = 0.55f),
                                                onLongPress = onMenu,
                                            )
                                        }
                                        // ROUND 63: no time row here —
                                        // the time rides in flow beside
                                        // the text's last line inside
                                        // RdTimedText above (or on its
                                        // own line under a full last
                                        // line). The sealed gift above
                                        // never reaches this branch; an
                                        // opened gift does, like any text.
                                    }
                                }
                            }
                        }
                        when (m.fx) {
                            "hearts" -> RdFxHeartsDressing()
                            "fire" -> RdFxFireFlames()
                            "confetti" -> RdFxConfettiDressing(m.id.hashCode())
                        }
                        if (sealedGift) {
                            // the gift bow, overlapping the wrap's top edge —
                            // out here because the bubble column clips its own
                            // content; it swells + fades with the unwrap.
                            Box(
                                Modifier.align(Alignment.TopCenter).offset(y = (-12).dp)
                                    .graphicsLayer {
                                        alpha = 1f - unwrapP.value
                                        val s = 1f + 0.45f * unwrapP.value
                                        scaleX = s; scaleY = s
                                    },
                            ) { EmojiGlyph("🎀", 26.dp) }
                        }
                        if (openedGift) {
                            // ROUND 34: the opened wrap's bow — a smaller
                            // 🎀 pinned at the top edge, permanent like the
                            // wrap itself (the unwrap bow above has faded
                            // out by the time this state exists). Out here
                            // for the same clip reason as the sealed bow.
                            // ROUND 39: TopCenter + the −10 dp offset put
                            // the 20 dp glyph's centre exactly ON the top
                            // border, at the point where the wrap's
                            // vertical band meets it — the knot point.
                            Box(
                                Modifier.align(Alignment.TopCenter).offset(y = (-10).dp),
                            ) { EmojiGlyph("🎀", 20.dp) }
                        }
                    }
                    if (m.starredByMe) {
                        Text("★", color = Color(0xFFFFC857), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                    val reactions = m.reactions.values.distinct()
                    if (reactions.isNotEmpty()) {
                        Row(
                            Modifier.rdPop(0)
                                // ROUND 22 (insta port): the bouncy
                                // chip pop, attached only while armed
                                // by a double-tap add (see heartChipPop)
                                .then(
                                    if (heartChipPop) Modifier.graphicsLayer {
                                        scaleX = heartChipScale; scaleY = heartChipScale
                                    } else Modifier,
                                )
                                .offset(x = if (m.me) (-8).dp else 8.dp, y = (-8).dp)
                                .clip(CircleShape).background(RdCard).border(1.dp, Color.Black, CircleShape)
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            reactions.forEach { e -> EmojiGlyph(e, 14.dp) }
                        }
                    }
                }
                val p = heart.value
                if (p > 0f && p < 1f) {
                    Box(
                        Modifier.graphicsLayer {
                            val s = if (p < 0.25f) 1.4f * (p / 0.25f) else 1.4f - 0.4f * ((p - 0.25f) / 0.75f)
                            scaleX = s; scaleY = s
                            alpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
                            translationY = -p * 90f
                        },
                    ) { EmojiGlyph("❤️", 56.dp) }
                }
            }
        }
        // FAILED outgoing bubble: plain reason + tap-to-retry (round 3 —
        // a failed send must never be invisible again).
        if (m.me && m.status == SendStatus.FAILED) {
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "Couldn't send — tap to retry",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RdDanger,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onRetry() }, fontFamily = PoppinsFamily,)
            }
        }
        // ROUND 31 — the SENDER's view-once line under his own bubble:
        // the allowance while views remain ("View once" / "View
        // twice"), flipping to "Opened" when the recipient's client
        // reports the allowance exhausted (voDone on the doc, read back
        // by decryptDoc like every other metadata field).
        if (m.me && m.vo != null && m.status != SendStatus.FAILED) {
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (m.voDone) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null,
                    tint = RdSub, modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (m.voDone) "Opened" else if (m.vo == 1) "View once" else "View twice",
                    color = RdSub, fontSize = 11.5.sp, fontFamily = PoppinsFamily,)
            }
        }
    }
}

/** The file's fixed 220×260 image box, fed by the real decrypted bitmap. */
@Composable
private fun RdImageBubble(m: TikuMessage) {
    val emojiFont = rememberChatFontFamily() // ROUND 48 fix: caption text uses the chat chain
    val bmp = m.photo
    Box(Modifier.size(220.dp, 260.dp).background(RdCard)) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // ROUND 57 (Fix 1): the time chip over the photo's bottom-end
        // corner (WhatsApp's media layout), on a small dark scrim so it
        // stays readable over any photo.
        // ROUND 63: caption and chip now share ONE bottom Row inside
        // the photo frame — the caption takes the Row's weighted slot
        // and wraps inside it, the chip keeps its bottom-end corner,
        // so a long caption's last line can never run under the chip
        // (previously the caption was a full-width BottomStart Text
        // and the chip a BottomEnd overlay in the same corner — the
        // same collision family as the text bubbles). With no caption
        // the weighted spacer leaves the chip exactly where it was.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (m.text.isNotEmpty()) {
                Text(
                    m.text, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = 4.dp),
                    fontFamily = emojiFont,
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(0.45f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                RdBubbleTime(m, color = Color.White.copy(alpha = 0.92f), showTicks = true)
            }
        }
    }
}

/** ROUND 15 — the video bubble, in the photo bubble's fixed frame. The
 *  poster frame (lifted from the decrypted file at load time) fills it
 *  once the payload is on this device; before that it is a labelled
 *  placeholder — duration + size + "Tap to load" — because video
 *  payloads never auto-download. Tapping is wired by the row: loaded →
 *  the player, unloaded → fetchMedia. */
@Composable
private fun RdVideoBubble(m: TikuMessage) {
    val loaded = m.localPath != null
    Box(Modifier.size(220.dp, 260.dp).background(RdCard)) {
        val poster = m.photo
        if (poster != null) {
            Image(poster.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.18f)))
        }
        Box(
            Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape)
                .background(Color.Black.copy(0.45f)).border(1.dp, Color.White.copy(0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(0.45f)).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(fmtDurSec(m.voiceDurSec), color = Color.White, fontSize = 12.sp, fontFamily = PoppinsFamily)
            // ROUND 63: the load/size label takes the bar's weighted
            // slot, end-aligned and ellipsised — it used to sit after a
            // weighted spacer at natural width, so a long label plus
            // the time could exceed the fixed 220 dp bar and squeeze
            // the time at its end. In flow, the label yields first.
            Text(
                when {
                    !loaded && m.mediaBytes > 0 -> "Tap to load · ${fmtMediaSize(m.mediaBytes)}"
                    !loaded -> "Tap to load"
                    m.mediaBytes > 0 -> fmtMediaSize(m.mediaBytes)
                    else -> ""
                },
                color = Color.White.copy(0.85f), fontSize = 11.5.sp, fontFamily = PoppinsFamily,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // ROUND 57 (Fix 1): the time at the bar's end — the video
            // bubble's bottom corner.
            Spacer(Modifier.width(8.dp))
            Text(
                bubbleTimeText(m), color = Color.White.copy(0.85f), fontSize = 10.5.sp,
                fontFamily = PoppinsFamily,)
        }
    }
}

/** ROUND 31 — the view-once arm row inside the attach sheet and the
 *  camera chooser: three chips (Always / Once · 1× / Twice · 2×) that
 *  arm a view allowance for the NEXT photo or video sent from that
 *  sheet. Sends in this app are instant — there is no preview/confirm
 *  step to hang a selector on — so the choice lives where the send is
 *  picked, the fx picker's arm pattern miniaturised. The arm is
 *  consumed by the next photo/video send only; audio and text ignore
 *  it, and it survives the sheet closing until a send consumes it. */
@Composable
private fun RdVoArmRow(armed: Int?, onPick: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Text("Next photo or video can be viewed", color = RdSub, fontSize = 12.5.sp, fontFamily = PoppinsFamily)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RdVoChip("Always", armed == null) { onPick(null) }
            RdVoChip("Once · 1×", armed == 1) { onPick(1) }
            RdVoChip("Twice · 2×", armed == 2) { onPick(2) }
        }
    }
}

@Composable
private fun RdVoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) RdRose else RdCard)
            .border(1.dp, if (selected) RdRose else Color.White.copy(0.14f), RoundedCornerShape(999.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            label, color = Color.White, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontFamily = PoppinsFamily,)
    }
}

/** ROUND 31 — the RECIPIENT's view-once bubble (WhatsApp shape): an
 *  eye disc + "Photo" / "Video" + the remaining-views line, inside the
 *  media bubble's own shell (the caller keeps the RdCard frame — only
 *  the inner content swaps, exactly like the video bubble's unloaded
 *  placeholder). The picture itself is NEVER drawn here: opening the
 *  bubble is what consumes a view (wired by the row's tap → the
 *  caller's onMediaTap). [expired] renders the spent "Opened" state;
 *  [bypass] is the owner's unlimited override (his views never run
 *  out, so his line says so). */
@Composable
private fun RdVoBubble(m: TikuMessage, remaining: Int, expired: Boolean, bypass: Boolean) {
    val isVideo = m.kind == MsgKind.VIDEO
    // ROUND 57 (Fix 1): the row now rides in a Column so the in-bubble
    // time can sit at the bubble's bottom-end corner, like every other
    // bubble type.
    Column(Modifier.width(220.dp).padding(horizontal = 16.dp, vertical = 15.dp)) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (expired) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null,
                tint = Color.White.copy(if (expired) 0.55f else 1f), modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isVideo) "Video" else "Photo",
                color = Color.White.copy(if (expired) 0.55f else 1f),
                fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
            Text(
                when {
                    expired -> "Opened"
                    bypass -> "Unlimited views"
                    isVideo && m.localPath == null -> "Tap to load"
                    remaining == 1 -> "1 view left"
                    else -> "$remaining views left"
                },
                color = RdSub, fontSize = 12.sp, fontFamily = PoppinsFamily,)
        }
        if (!expired) {
            Text("${m.vo}×", color = RdRose, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = InterTight)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        RdBubbleTime(m, color = Color.White.copy(alpha = 0.6f), showTicks = true)
    }
    }
}

/** The file's voice bubble, driven by REAL playback: the play disc
 *  toggles the decrypted file through VoicePlayer (wired by the caller);
 *  the waveform bars are the message's real recorded amplitudes and the
 *  played portion lights up over the real duration. */
@Composable
private fun RdVoiceBubble(
    m: TikuMessage, playing: Boolean, onToggle: () -> Unit,
    /** ROUND 26: the bubble's theme text colour. Defaults to White —
     *  the exact pre-round rendering. */
    tint: Color = Color.White,
    /** ROUND 57 (Fix 1): the in-bubble time, rendered after the
     *  duration at the bubble's bottom-end. Null = no time (the
     *  owner-vault rows keep their exact pre-round shape). */
    timeText: String? = null,
) {
    val prog = remember(m.id) { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            prog.snapTo(0f)
            try {
                prog.animateTo(1f, tween(m.voiceDurSec.coerceAtLeast(1) * 1000, easing = LinearEasing))
            } finally {
                prog.snapTo(0f)
            }
        } else {
            prog.snapTo(0f)
        }
    }
    val bars = remember(m.id, m.amps) {
        if (m.amps.size == 22) m.amps.map { it.coerceIn(4, 22) }
        else List(22) { 6 + (it * 37 + m.voiceDurSec * 13) % 17 }
    }
    Row(Modifier.widthIn(min = 190.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(tint.copy(0.25f)).rdPress(onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (playing) "❚❚" else "▶", color = tint, fontSize = 12.sp)
        }
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            bars.forEachIndexed { k, h ->
                Box(
                    Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp))
                        .background(tint.copy(if (k < prog.value * bars.size) 1f else 0.45f)),
                )
            }
        }
        Text(
            "${m.voiceDurSec / 60}:${(m.voiceDurSec % 60).toString().padStart(2, '0')}",
            color = tint, fontSize = 12.sp, fontFamily = PoppinsFamily,)
        if (timeText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                timeText, color = tint.copy(alpha = 0.65f), fontSize = 10.5.sp,
                fontFamily = PoppinsFamily,)
        }
    }
}

/** The file's TypingBubble (3 bouncing dots + avatar) on real peer
 *  typing. ROUND 22 (insta port): the bubble's own pop-in and the dot
 *  cycle now run the REFERENCE TypingBubble's exact numbers — pop
 *  spring (0.55 / StiffnessMediumLow, scale 0.6→1, rise 30dp,
 *  transformOrigin bottom-left) and per-dot tween(450,
 *  FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(k × 150),
 *  translationY −6dp·y, alpha 0.5 + 0.5·y. The bubble's styling —
 *  RdCard, 22dp corners, 16×15 padding, 7dp RdSub dots, avatar — is
 *  Tiku's own and unchanged. */
@Composable
private fun RdTypingBubble(person: Person, theme: ChatTheme? = null) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        RdAv(person, 28); Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(theme?.peerBubble ?: RdCard).padding(horizontal = 16.dp, vertical = 15.dp)
                .graphicsLayer {
                    val p = pop.value
                    alpha = p.coerceIn(0f, 1f)
                    val s = 0.6f + 0.4f * p
                    scaleX = s; scaleY = s
                    translationY = (1f - p) * 30.dp.toPx()
                    transformOrigin = TransformOrigin(0f, 1f)
                },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val t = rememberInfiniteTransition(label = "typing")
            repeat(3) { k ->
                val y by t.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(450, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                        initialStartOffset = StartOffset(k * 150),
                    ),
                    label = "d$k",
                )
                Box(
                    Modifier.size(7.dp)
                        .graphicsLayer {
                            translationY = -6.dp.toPx() * y
                            alpha = 0.5f + 0.5f * y
                        }
                        .clip(CircleShape).background(theme?.peerText ?: RdSub),
                )
            }
        }
    }
}

/** ROUND 37 — one AI (Tika) message. Rendered LEFT-aligned with a
 *  small "✨ Tika AI" label, whichever member's client posted it: the
 *  doc's senderId stays the asking user (they authored it through
 *  their own encrypted send path), but the content is Tika's, so the
 *  bubble reads as a third voice in the chat — Meta-AI style. The
 *  bubble wears the peer side's theme colours; an image opens the
 *  ordinary viewer (Save included — a generated picture is meant to
 *  be kept); long-press reaches the ordinary message menu. Nothing
 *  else of RdBubbleRow's machinery applies: no fx dressing, no
 *  view-once gate, no voice plumbing. */
@Composable
private fun RdAiBubbleRow(
    m: TikuMessage,
    theme: ChatTheme?,
    textSp: Float,
    onMenu: () -> Unit,
    onOpenImage: (TikuMessage) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiGlyph("✨", 13.dp)
            Spacer(Modifier.width(5.dp))
            Text(
                "Tika AI", color = RdRose, fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,)
        }
        Spacer(Modifier.height(4.dp))
        val bmp = m.photo
        if (m.kind == MsgKind.IMAGE && bmp != null) {
            // ROUND 57 (Fix 1): the image rides in a Box so the time
            // chip can sit over its bottom-end corner, like the
            // ordinary photo bubble.
            Box {
                Image(
                    bmp.asImageBitmap(), null,
                    Modifier.widthIn(max = 240.dp)
                        .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                        .clip(RoundedCornerShape(22.dp))
                        .pointerInput(m.id) {
                            detectTapGestures(
                                onTap = { onOpenImage(m) },
                                onLongPress = { onMenu() },
                            )
                        },
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(0.45f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    RdBubbleTime(m, color = Color.White.copy(alpha = 0.92f), showTicks = false)
                }
            }
        } else {
            // ROUND 46: AI text wears the same bubble skin as before,
            // now as a Column so a detected link can carry its styled
            // spans + preview card (see LinkPreview.kt). The long-press
            // menu detector rides the Column, so it covers the card too.
            val aiText = if (m.kind == MsgKind.IMAGE) "✨ Photo" else m.text
            val aiLink = remember(aiText) { firstLinkIn(aiText) }
            val aiInk = theme?.peerText ?: Color.White
            Column(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(theme?.peerBubble ?: RdCard)
                    .pointerInput(m.id) {
                        detectTapGestures(onLongPress = { onMenu() })
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                // ROUND 63: same in-flow conversion as the ordinary
                // text bubble — the AI bubble shares the RdLinkedText
                // structure exactly, so it takes the same RdTimedText
                // (no ticks — the bubble renders as Tika's, whichever
                // member's client posted it).
                RdTimedText(
                    m = m,
                    text = aiText, color = aiInk, linkColor = RdRose,
                    fontSize = textSp.sp, fontFamily = rememberChatFontFamily(),
                    timeColor = aiInk.copy(alpha = 0.6f),
                    showTicks = false,
                )
                if (aiLink != null) {
                    Spacer(Modifier.height(8.dp))
                    RdLinkPreviewCard(
                        url = aiLink,
                        cardBg = aiInk.copy(alpha = 0.08f),
                        borderColor = aiInk.copy(alpha = 0.14f),
                        titleColor = aiInk,
                        descColor = aiInk.copy(alpha = 0.72f),
                        hostColor = aiInk.copy(alpha = 0.55f),
                        onLongPress = onMenu,
                    )
                }
                // ROUND 63: no time row here — the time rides in
                // flow inside RdTimedText above.
            }
        }
    }
}

/** ROUND 41 — the game card palette: the generated games' own cream
 *  world (the colour law), so a game message reads as a little paper
 *  game box sitting in the dark chat. */
private val GameCardCream = Color(0xFFFFF6E3)
private val GameCardInk = Color(0xFF33261A)

/** ROUND 41 — one AI-built game message (see GamePlayer.kt). A cream
 *  card aligned like an ordinary bubble (mine right, theirs left):
 *  🎮 + the game's title (the asker's own words from the command) +
 *  "2-player · tap to play", over a ~210 dp LIVE preview of the game
 *  itself once its bytes are on this phone. Games are tap-to-load
 *  like every file kind (house media law — nothing auto-downloads):
 *  before the load, the preview area is the load affordance with the
 *  payload size; a FAILED own send turns the tap into a retry. The
 *  code is NEVER shown — the card shows the game or nothing.
 *  Long-press reaches the ordinary message menu. */
@Composable
private fun RdGameCard(
    m: TikuMessage,
    person: Person,
    onMenu: () -> Unit,
    onLoad: () -> Unit,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
) {
    val loadedPath = m.localPath?.takeIf { File(it).exists() }
    val loaded = loadedPath != null
    val failed = m.me && m.status == SendStatus.FAILED
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
        ) {
            Column(
                Modifier.width(264.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(GameCardCream)
                    .border(1.dp, GameCardInk.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                    .pointerInput(m.id, loaded, failed) {
                        detectTapGestures(
                            onTap = {
                                when {
                                    failed -> onRetry()
                                    loaded -> onPlay()
                                    else -> onLoad()
                                }
                            },
                            onLongPress = { onMenu() },
                        )
                    }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiGlyph("🎮", 19.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.gameTitle.ifBlank { "Game" },
                            color = GameCardInk, fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = InterTight,)
                        Text(
                            // ROUND 68 (WS6): a received game is an invite —
                            // the card says so; own games keep the old line.
                            if (m.me) "2-player · tap to play"
                            else "${person.name} invited you to play · tap to join",
                            color = GameCardInk.copy(alpha = 0.55f), fontSize = 11.5.sp, fontFamily = PoppinsFamily,)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GameCardInk.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Couldn't send", color = Color(0xFFB3261E),
                                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily)
                            Text("Tap to retry", color = GameCardInk.copy(alpha = 0.55f),
                                fontSize = 12.sp, fontFamily = PoppinsFamily)
                        }
                        !loaded -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmojiGlyph("🎮", 30.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (m.mediaBytes > 0) "Tap to load game · ${fmtMediaSize(m.mediaBytes)}"
                                else "Tap to load game",
                                color = GameCardInk.copy(alpha = 0.7f), fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,)
                        }
                        Config.PREVIEW_UI_ONLY -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // House rule (Moments/Watch): no WebView
                            // under the screenshot harness — the card
                            // shows its static face instead.
                            EmojiGlyph("🎮", 40.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                m.gameTitle.ifBlank { "Game" },
                                color = GameCardInk, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
                        }
                        else -> RdGamePreview(m = m, path = loadedPath, peerName = person.name)
                    }
                }
                if (m.me && m.status == SendStatus.SENDING) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sending…", color = GameCardInk.copy(alpha = 0.5f),
                        fontSize = 11.5.sp, fontFamily = PoppinsFamily,)
                }
                // ROUND 57 (Fix 1): the time at the game card's
                // bottom-end corner, like every other bubble.
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    RdBubbleTime(m, color = GameCardInk.copy(alpha = 0.55f), showTicks = true)
                }
            }
        }
    }
}

/** ROUND 41 — the game card's live mini preview: the game's actual
 *  first frame in a small WebView (built once per message, destroyed
 *  with the row — Moments discipline), running on the
 *  [GamePreviewNative] bridge with the shared shim, so the game
 *  renders its initial board but can never act as a third player.
 *  Touch is swallowed whole — the card's own gesture layer underneath
 *  owns taps (play) and long-presses (menu); a transparent overlay
 *  keeps that true even over the WebView's surface. If the file
 *  vanished between the exists() check and the read, the area simply
 *  stays cream.
 *
 *  ROUND 52 (item 2 — the owner's screenshot: the card frozen on the
 *  game's own "Tic-Tac-Toe / Loading…" screen): the Round-41 bridge
 *  was fully inert (state() always null, setState a no-op), which
 *  starves the exact pattern the generation contract produces — the
 *  host-side game creates its initial state, publishes it with
 *  setState, and renders when the sync layer delivers it back. The
 *  preview bridge now EMULATES the sync layer locally (setState
 *  stores + echoes straight back into the page; nothing leaves the
 *  device). Delivery happens at page-ready with whatever the game
 *  already holds (null = the contract's start call) and once more
 *  ~700 ms later for games that attach their callback late. If the
 *  page STILL shows nothing but its loading screen ~4.5 s after
 *  page-ready, the preview area swaps to the card's static facade
 *  (title + "Tap to play") — a stuck "Loading…" is never the
 *  resting state of this card. */
@Composable
private fun RdGamePreview(m: TikuMessage, path: String, peerName: String) {
    val ctx = LocalContext.current
    val html by produceState<String?>(null, m.id, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { File(path).readText(Charsets.UTF_8) }.getOrNull()
        }
    }
    val wvHolder = remember(m.id) { mutableStateOf<android.webkit.WebView?>(null) }
    var pageReadyAt by remember(m.id) { mutableStateOf(0L) }
    var stuck by remember(m.id) { mutableStateOf(false) }
    val previewNative = remember(m.id) {
        GamePreviewNative(if (m.me) "host" else "guest", peerName) { json ->
            // The sync-layer echo: a state the game just published is
            // delivered straight back into the page (see GamePlayer).
            wvHolder.value?.let { deliverGameState(it, json) }
        }
    }
    val webView = remember(m.id) {
        buildGameWebView(ctx, previewNative, onPageReady = {
            // First frame: re-assert the shim, then deliver the
            // current state (initially null — the contract's start
            // call; a game that renders only inside its onState
            // callback still draws its initial board).
            val w = wvHolder.value ?: return@buildGameWebView
            runCatching { w.evaluateJavascript(TIKU_GAME_SHIM, null) }
            deliverGameState(w, previewNative.state())
            pageReadyAt = System.currentTimeMillis()
        }).also {
            it.setOnTouchListener { _, _ -> true }
            wvHolder.value = it
        }
    }
    DisposableEffect(m.id) {
        onDispose { destroyGameWebView(webView) }
    }
    val doc = html
    LaunchedEffect(m.id, doc) {
        if (doc != null) {
            webView.loadDataWithBaseURL(null, injectGameShim(doc), "text/html", "utf-8", null)
        }
    }
    // ROUND 52: the late-attach re-delivery — a game that registers
    // its onState callback after the first delivery still receives
    // the freshest state once, shortly after.
    LaunchedEffect(m.id, pageReadyAt) {
        if (pageReadyAt == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        if (!stuck) wvHolder.value?.let { deliverGameState(it, previewNative.state()) }
    }
    // ROUND 52: the stuck probe. A game that published a state is
    // past loading by construction; otherwise read the page's own
    // visible text — a lone short "Loading…" (or an empty page) after
    // the bounded wait means the game never got past its loader, and
    // the static facade takes the area instead.
    LaunchedEffect(m.id, pageReadyAt) {
        if (pageReadyAt == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(4_500)
        if (stuck || previewNative.producedState) return@LaunchedEffect
        val w = wvHolder.value ?: return@LaunchedEffect
        w.evaluateJavascript(
            "(function(){return document.body ? (document.body.innerText || '') : ''})()",
        ) { raw ->
            val text = raw?.trim('"', ' ')?.replace("\\n", " ")?.trim().orEmpty()
            if (!previewNative.producedState &&
                (text.isEmpty() || (text.contains("loading", ignoreCase = true) && text.length < 160))
            ) {
                runCatching { w.stopLoading() }
                stuck = true
            }
        }
    }
    if (doc != null) {
        if (stuck) {
            // The card's static facade (the harness face's language):
            // the game exists and plays on tap — it just never
            // painted its first frame in the preview.
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmojiGlyph("🎮", 40.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    m.gameTitle.ifBlank { "Game" },
                    color = GameCardInk, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tap to play",
                    color = GameCardInk.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = PoppinsFamily,)
            }
        } else {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** ROUND 37 — the transient "Tika is thinking…" indicator: the
 *  typing bubble's visual language (peer-coloured pill, bouncing
 *  dots) carrying the label, composed only while this phone's ask is
 *  in flight. Purely local — it is never written anywhere. */
@Composable
private fun RdAiThinkingBubble(label: String, theme: ChatTheme? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(theme?.peerBubble ?: RdCard)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, color = theme?.peerText ?: Color.White,
                fontSize = 12.5.sp, fontStyle = FontStyle.Italic, fontFamily = PoppinsFamily,)
            Spacer(Modifier.width(9.dp))
            val t = rememberInfiniteTransition(label = "aiTyping")
            repeat(3) { k ->
                val y by t.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(450, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                        initialStartOffset = StartOffset(k * 150),
                    ),
                    label = "aiD$k",
                )
                Box(
                    Modifier.size(6.dp)
                        .graphicsLayer {
                            translationY = -5.dp.toPx() * y
                            alpha = 0.5f + 0.5f * y
                        }
                        .clip(CircleShape).background(theme?.peerText ?: RdSub),
                )
                if (k < 2) Spacer(Modifier.width(4.dp))
            }
        }
    }
}

// ================= top bar + input (the file's overlays) =================
@Composable
private fun RdChatTopBar(
    h: HazeState?,
    title: String,
    status: String,
    person: Person,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onCall: () -> Unit,
    onVideoCall: () -> Unit,
    // ROUND 68 (WS9): tapping the avatar opens the full profile
    // picture; the name column still opens chat info.
    onPhotoClick: () -> Unit = {},
) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth().chatGlass(h, RoundedCornerShape(999.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).rdPress(onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(999.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ROUND 68 (WS9): the avatar alone opens the full photo;
            // the name column alone opens chat info.
            Box(Modifier.padding(horizontal = 6.dp).rdPress(onPhotoClick)) { RdAv(person, 38) }
            Column(Modifier.weight(1f).rdPress(onInfo)) {
                Text(
                    title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = InterTight,)
                Text(status, color = RdSub, fontSize = 12.sp, fontFamily = PoppinsFamily)
            }
        }
        // ROUND 15: video call sits next to audio call (owner's device
        // report: the header had voice only). Same startCall path, video.
        Box(Modifier.size(40.dp).clip(CircleShape).rdPress(onVideoCall), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Box(Modifier.size(40.dp).clip(CircleShape).rdPress(onCall), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** ROUND 42: the composer's slash commands — the sheet above the
 *  input row lists these while the field holds a bare "/"-prefixed
 *  token; tapping one fills "<name> " into the field. Mirrors the
 *  commands sendCurrent already parses (Rounds 37 + 41); this list is
 *  display-only — sending logic never reads it. */
private data class SlashCommand(val name: String, val desc: String)

private val SlashCommands = listOf(
    SlashCommand("/tiku", "Ask AI a question"),
    SlashCommand("/tika", "AI creates a photo"),
    SlashCommand("/babeg", "AI creates a game"),
)

/** ROUND 44: the composer's command-bold transformation (owner's ask:
 *  a typed command must LOOK like a command in the box, not like normal
 *  text). While the field starts with "/", the leading token — index 0
 *  through the first space, or the whole field while the command is
 *  still being typed — renders [FontWeight.Bold] over the field's
 *  normal weight; everything after the first space stays plain. The
 *  rule is deliberately dumb (no command-table lookup): a half-typed
 *  "/ti" bolds too, which is exactly the feedback wanted mid-typing.
 *  Pure [VisualTransformation]: the output is the SAME string with one
 *  span added, so [OffsetMapping.Identity] is exact — caret, selection
 *  and IME composition offsets are untouched, the stored String never
 *  changes, and the Round-42 slash sheet (which watches the raw value)
 *  is unaffected. Non-"/" text passes through unstyled. */
private object CommandBoldTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!raw.startsWith("/")) return TransformedText(text, OffsetMapping.Identity)
        val end = raw.indexOf(' ').let { if (it < 0) raw.length else it }
        if (end <= 1) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(raw)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, end)
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
fun RdChatInput(
    h: HazeState?,
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    accent: Color,
    replyText: String?,
    onClearReply: () -> Unit,
    recording: Boolean,
    recSecs: Int,
    onPlus: () -> Unit,
    onMic: () -> Unit,
    onRecCancel: () -> Unit,
    onRecSend: () -> Unit,
    /** ROUND 15: composer camera (IG-style). Default no-op keeps direct
     *  framings of this input (screenshot harness) compiling unchanged. */
    onCamera: () -> Unit = {},
    /** ROUND 22: the armed send effect (fx kind, null = none) + its
     *  controls. The sparkles button takes the composer's left slot
     *  whenever the field holds text (IG's effects-slot behaviour — the
     *  camera sits there only while the field is empty); the armed chip
     *  rides above the row with a ✕ to disarm. Defaults keep direct
     *  framings of this input compiling unchanged. */
    fxArmed: String? = null,
    onFxClick: () -> Unit = {},
    onFxDisarm: () -> Unit = {},
    /** ROUND 26: the shared chat theme, or null = Default. When set,
     *  the composer pill + reply strip + fx chip wear the theme's
     *  composer colours instead of the glass skin. */
    theme: ChatTheme? = null,
    /** ROUND 57 (Fix 2): edit mode — while true, the "Editing message"
     *  banner rides above the pill and the caller's Send replaces the
     *  edited message instead of posting a new one; [onCancelEdit] is
     *  the banner's ✕. Defaults keep direct framings of this input
     *  (screenshot harness) compiling unchanged. */
    editing: Boolean = false,
    onCancelEdit: () -> Unit = {},
) {
    // ROUND 26: themed composer colours. Every value falls back to the
    // EXACT pre-round expression when theme == null, so the Default
    // composer renders identically to before this round.
    val contentCol = theme?.composerText ?: Color.White
    val subCol = theme?.composerText?.copy(alpha = 0.62f) ?: RdSub
    val faintBg = theme?.composerText?.copy(alpha = 0.12f) ?: Color.White.copy(0.12f)
    fun barMod(shape: RoundedCornerShape): Modifier =
        if (theme != null) Modifier.clip(shape).background(theme.composerBar)
            .border(1.dp, theme.composerText.copy(alpha = 0.10f), shape)
        else Modifier.chatGlass(h, shape)
    // ROUND 15: the camera button lives only while the field is EMPTY and
    // UNFOCUSED — tapping into the field to type takes focus and the
    // button animates away (owner's exact ask).
    var fieldFocused by remember { mutableStateOf(false) }
    // ROUND 52 (item 8): the field binds a TextFieldValue MIRROR of
    // the caller's String state. The String-only binding left the
    // selection in the text field's internal state, and the slash
    // sheet's tap — a wholesale String replacement through onChange —
    // reset that selection to 0: the caret landed at the LEFT of the
    // inserted "/tika " and the owner's next keystrokes went INSIDE
    // the token, breaking the command apart (his exact report). The
    // mirror makes selection ours: typing flows straight through
    // (the field's own TextFieldValue is adopted verbatim, caret and
    // IME composition untouched), programmatic fills (a send clearing
    // the field, the slash insert below) set the caret deliberately.
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // ROUND 57 (Fix 4): the field's internal scroll state for the
    // height cap (see the field's modifier below). While the caret
    // sits at the very end of the text, growth scrolls the newest
    // line into view inside the field; a caret parked mid-text (an
    // edit in progress) is never yanked.
    val composerScroll = rememberScrollState()
    LaunchedEffect(value) {
        if (tfv.selection.end >= value.length) {
            composerScroll.animateScrollTo(composerScroll.maxValue)
        }
    }
    LaunchedEffect(value) {
        // External change (send-clear, prefill): adopt the new text
        // with the caret at its end. Changes that came FROM the field
        // already match tfv.text and are left exactly as they are.
        if (value != tfv.text) tfv = TextFieldValue(value, TextRange(value.length))
    }
    /** ROUND 52: the slash-sheet tap. Replaces ONLY the first token
     *  (the sheet is visible solely for a bare token, but any text
     *  after it is preserved verbatim behind the inserted command), so
     *  "/ti" + tap yields exactly "/tika " — never "/ti/tika", never a
     *  doubled slash — with the caret placed right after the command
     *  and its trailing space, where typing continues. */
    fun insertSlashCommand(cmd: SlashCommand) {
        val tokenEnd = value.indexOf(' ').let { if (it < 0) value.length else it }
        val suffix = value.substring(tokenEnd).trimStart()
        val inserted = cmd.name + " " + suffix
        val caret = cmd.name.length + 1
        tfv = TextFieldValue(inserted, TextRange(caret))
        onChange(inserted)
    }
    // ROUND 22: typed emoji render as iOS glyphs while typing (the
    // same font the bubbles use — EmojiArt.kt, worker A §5).
    val emojiFont = rememberChatFontFamily() // ROUND 48: Inter + iOS-emoji chain (see EmojiArt)
    val showCamera = value.isEmpty() && !fieldFocused
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ROUND 57 (Fix 2): the editing banner — WhatsApp's pattern:
        // a strip above the pill naming the mode, with a ✕ that
        // cancels back to normal compose. It borrows the reply strip's
        // glass language directly below.
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                Modifier.fillMaxWidth().then(barMod(RoundedCornerShape(20.dp))).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Text(
                    "Editing message", color = accent, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    fontFamily = PoppinsFamily,
                )
                Text("✕", color = contentCol, fontSize = 16.sp, modifier = Modifier.rdPress(onCancelEdit).padding(6.dp))
            }
        }
        AnimatedVisibility(
            visible = replyText != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                Modifier.fillMaxWidth().then(barMod(RoundedCornerShape(20.dp))).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Replying", color = accent, fontSize = 11.sp, fontFamily = PoppinsFamily)
                    Text(replyText ?: "", color = subCol, fontSize = 13.sp, maxLines = 1, fontFamily = emojiFont)
                }
                Text("✕", color = contentCol, fontSize = 16.sp, modifier = Modifier.rdPress(onClearReply).padding(6.dp))
            }
        }
        // ROUND 22: the armed-effect chip (mirrors the reply strip's
        // glass language). lastFxDef keeps the chip's content composed
        // through its own exit animation after disarm.
        var lastFxDef by remember { mutableStateOf<FxDef?>(null) }
        LaunchedEffect(fxArmed) { if (fxArmed != null) lastFxDef = fxDef(fxArmed) }
        AnimatedVisibility(
            visible = fxArmed != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val def = fxDef(fxArmed) ?: lastFxDef
            if (def != null) {
                Row(
                    Modifier.then(barMod(RoundedCornerShape(999.dp))).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EmojiGlyph(def.glyph, 16.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(def.name, color = contentCol, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, fontFamily = PoppinsFamily)
                    Text(
                        "✕", color = subCol, fontSize = 13.sp,
                        modifier = Modifier.rdPress(onFxDisarm).padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
        // ROUND 42: the slash-command sheet — the owner's ask: typing
        // "/" pops the command list so a tap replaces typing it all.
        // Visible only while the field is a bare slash-prefixed
        // SINGLE token (no space yet), with no reply target and no
        // armed effect (the two states sendCurrent treats specially);
        // the live filter is a plain prefix match on the token. A tap
        // fills "<name> " — the trailing space ends the token, so the
        // sheet closes itself and never reopens over the finished
        // command. Focus and the IME are never touched. (ROUND 52:
        // the tap goes through insertSlashCommand, which also places
        // the caret after the trailing space — the Round-42 claim
        // that a plain String rewrite did that was wrong on device:
        // the caret reset to 0 and typing broke the token.)
        // lastSlashMatches keeps the rows composed through the exit
        // animation, the fx chip's trick above.
        val slashMatches = if (
            value.startsWith("/") && !value.contains(' ') &&
            replyText == null && fxArmed == null && !recording && !editing
        ) SlashCommands.filter { it.name.startsWith(value.lowercase()) } else emptyList()
        var lastSlashMatches by remember { mutableStateOf<List<SlashCommand>>(emptyList()) }
        LaunchedEffect(slashMatches) { if (slashMatches.isNotEmpty()) lastSlashMatches = slashMatches }
        AnimatedVisibility(
            visible = slashMatches.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val rows = slashMatches.ifEmpty { lastSlashMatches }
            if (rows.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().then(barMod(RoundedCornerShape(24.dp))).padding(vertical = 6.dp),
                ) {
                    rows.forEachIndexed { i, cmd ->
                        if (i > 0) Box(
                            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp)
                                .background(contentCol.copy(alpha = 0.07f)),
                        )
                        Row(
                            Modifier.fillMaxWidth().rdPress { insertSlashCommand(cmd) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cmd.name, color = contentCol, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = InterTight)
                                Text(cmd.desc, color = subCol, fontSize = 12.5.sp, fontFamily = PoppinsFamily)
                            }
                            Text("›", color = subCol, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        // ROUND 57 (Fix 4): the pill's corners are a FIXED 28 dp (was
        // 999 dp): a fully-round pill whose field grows uncapped is
        // what ballooned into a giant circle on long text — with a
        // fixed radius + the field's 150 dp cap below, the bar keeps
        // one stable rounded-rectangle shape at every height, and its
        // buttons ride the BOTTOM (WhatsApp-style) while it is tall.
        Row(
            Modifier.fillMaxWidth().then(barMod(RoundedCornerShape(28.dp))).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (recording) {
                val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
                    1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot",
                )
                Box(Modifier.padding(start = 14.dp).size(10.dp).clip(CircleShape).background(RdDanger.copy(alpha = pulse)))
                Text(
                    "${recSecs / 60}:${(recSecs % 60).toString().padStart(2, '0')}",
                    color = contentCol, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 12.dp), fontFamily = PoppinsFamily,)
                Text("Cancel", color = subCol, modifier = Modifier.rdPress(onRecCancel).padding(10.dp), fontFamily = PoppinsFamily)
                Box(Modifier.size(42.dp).clip(CircleShape).background(accent).rdPress(onRecSend), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            } else {
                // ROUND 22: the effects button — the left slot whenever
                // the field holds text (the camera below owns the slot
                // only while it is empty, so the two swap in place).
                // A vector sparkles glyph (Icons.kt), never an emoji.
                // Lit in the accent while an effect is armed.
                AnimatedVisibility(
                    visible = value.isNotBlank(),
                    enter = fadeIn(tween(160)) + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    Box(
                        Modifier.padding(end = 6.dp).size(40.dp).clip(CircleShape)
                            .background(if (fxArmed != null) accent else faintBg)
                            .rdPress(onFxClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        TikuSparklesIcon(Modifier.size(20.dp), tint = if (fxArmed != null) Color.White else contentCol)
                    }
                }
                AnimatedVisibility(
                    visible = showCamera,
                    enter = fadeIn(tween(160)) + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    Box(
                        Modifier.padding(end = 6.dp).size(40.dp).clip(CircleShape).background(accent)
                            .testTag("composer_camera").rdPress(onCamera),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(faintBg).rdPress(onPlus), contentAlignment = Alignment.Center) {
                    Text("+", color = contentCol, fontSize = 24.sp)
                }
                // ROUND 62 (Bug B): the field wrapper carries a
                // 42 dp minimum height (the send disc's height) and
                // centers its content — Round 57 (Fix 4) set the pill
                // Row to Alignment.Bottom so the BUTTONS ride the
                // bottom while the field is tall, but at rest that
                // also bottom-aligned the single-line field inside
                // the 42 dp row, so the hint/typed text sat LOW in
                // the pill instead of centered. With the min height
                // + CenterStart, the resting single line is exactly
                // centered; once the field grows past 42 dp it fills
                // the wrapper and the cap/scroll behaviour is
                // unchanged.
                Box(
                    Modifier.weight(1f).heightIn(min = 42.dp).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text("Message…", color = subCol, fontSize = 15.sp, fontFamily = emojiFont)
                    BasicTextField(
                        // ROUND 52: the TextFieldValue mirror (see
                        // tfv above) — manual typing adopts the
                        // field's own value verbatim, so caret and
                        // composition behave exactly as before; only
                        // programmatic fills differ.
                        value = tfv,
                        onValueChange = { nv ->
                            tfv = nv
                            if (nv.text != value) onChange(nv.text)
                        },
                        textStyle = TextStyle(color = contentCol, fontSize = 15.sp, fontFamily = emojiFont),
                        cursorBrush = SolidColor(accent),
                        // ROUND 44: a leading "/" command token renders
                        // Bold while typing (see CommandBoldTransformation).
                        visualTransformation = CommandBoldTransformation,
                        // ROUND 57 (Fix 4): the height cap — the field
                        // grows with the text up to 150 dp (≈6 lines),
                        // then scrolls INTERNALLY (composerScroll keeps
                        // the caret's line in view). The text itself is
                        // never truncated, capped or blocked: unlimited
                        // typing, bounded box.
                        modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)
                            .verticalScroll(composerScroll)
                            .onFocusChanged { fieldFocused = it.isFocused },
                    )
                }
                val has = value.isNotBlank()
                val bg by animateColorAsState(if (has) accent else faintBg, label = "sendBg")
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(bg).rdPress { if (has) onSend() else onMic() },
                    contentAlignment = Alignment.Center,
                ) {
                    // ROUND 22 (insta port): the mic ↔ send swap rides
                    // the reference input bar's AnimatedContent
                    // transition — (fadeIn(tween 150) + scaleIn(
                    // spring(0.5, StiffnessMedium), initialScale 0.5))
                    // togetherWith fadeOut(tween 100)). Purely visual:
                    // the 42dp disc, its colour animation, the tap
                    // target and the slot's size-neutrality (the
                    // composer-height contract) are all unchanged.
                    AnimatedContent(
                        targetState = has,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(spring(0.5f, Spring.StiffnessMedium), initialScale = 0.5f))
                                .togetherWith(fadeOut(tween(100)))
                        },
                        label = "sendSwap",
                    ) { hasText ->
                        if (hasText) Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        else Icon(Icons.Filled.Mic, null, tint = if (theme != null) contentCol else Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** The file's MenuItem row (label left, trailing slot right). */
@Composable
private fun RdMenuItem(label: String, danger: Boolean = false, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rdPress(onClick).padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (danger) RdDanger else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), fontFamily = PoppinsFamily)
        trailing()
    }
}

@Composable
private fun RdDivider1() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.08f)))

private fun startRdRecording(recorder: VoiceRecorder, onStarted: () -> Unit) {
    runCatching { recorder.start(); onStarted() }
        .onFailure {
            // ROUND 20: the failure REASON is part of the toast + the log —
            // a bare "couldn't start" hid prepare()/mic-busy failures
            // behind a permanently dead-feeling button.
            Log.e("TikuVoice", "voice record start failed", it)
            AppState.toast("Couldn't start recording — ${it.message ?: "microphone unavailable"}")
        }
}

/** ROUND 15: copy a picked content Uri into an app-cache file — the
 *  media pipeline works on real files, and the copy outlives the
 *  picker's temporary Uri grant. Null when the stream can't be read. */
private fun copyUriToCacheFile(ctx: android.content.Context, uri: android.net.Uri, prefix: String): File? = runCatching {
    val mime = ctx.contentResolver.getType(uri)
    val ext = mime?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
    val f = File(ctx.cacheDir, "${prefix}_${System.currentTimeMillis()}.$ext")
    val input = ctx.contentResolver.openInputStream(uri) ?: return null
    input.use { inp -> f.outputStream().use { out -> inp.copyTo(out) } }
    f
}.getOrNull()

/** ROUND 43: decode a camera capture with bounds — sample down so
 *  the long edge lands at or under ~2560 px BEFORE the full decode.
 *  The send pipeline compresses to 1280 anyway (MediaStore), so the
 *  lost pixels cost nothing; what it buys is survival: a naive
 *  decodeFile of a 50–200 MP OEM capture can OOM inside the activity
 *  -result callback and take the send (or the app) down with it.
 *  Null when the file is missing or undecodable. */
private fun decodeCaptureBounded(f: File): Bitmap? = runCatching {
    if (!f.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > 2560) sample *= 2
    BitmapFactory.decodeFile(
        f.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}.getOrNull()

/** ROUND 15: duration (seconds) of a media file, 0 when unreadable. */
private fun mediaDurationSec(f: File): Int = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(f.absolutePath)
        ((r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toLong() / 1000).toInt()
    } finally {
        runCatching { r.release() }
    }
}.getOrDefault(0)

// ================= the chat screen =================
@Composable
fun ChatScreen(threadId: String) {
    // The thread can be momentarily absent (search -> open, listener
    // refire) or flicker null<->present. This wrapper runs ONLY the
    // threads collection; the full screen (with all its hooks) composes
    // only when the thread exists, so hook order can never break.
    BackHandler { AppState.go(Screen.Home) }
    val threads by AppState.threads.collectAsState()
    val thread = threads.firstOrNull { it.id == threadId }
    if (thread == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Chat unavailable", color = RdSub, fontSize = 13.sp, fontFamily = PoppinsFamily)
        }
        return
    }
    RdChatContent(thread)
}

@Composable
private fun RdChatContent(thread: ChatThread) {
    val threadId = thread.id
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val person = thread.person
    val globalSettings by SettingsStore.state.collectAsState()
    val textSp = globalSettings.textSizeSp
    // Per-chat theme = the file's themeList pair; the stored int rides the
    // EXISTING per-chat theme storage (ThemeCodec space), with the global
    // appearance theme as the fallback (effectiveSettings resolves both).
    val themeIdx = thread.effectiveSettings(globalSettings).accentIdx
    val acc = RdThemes[themeIdx.coerceIn(0, RdThemes.lastIndex)]
    // ROUND 26: the shared wallpaper theme. `thread` streams from the
    // threads listener, so a theme set on EITHER phone re-skins this
    // open chat live. null = Default: every render site below guards on
    // chatTheme != null and the Default path is byte-for-byte today's.
    // ROUND 35: colours resolve through paletteFor — a custom theme
    // ("custom:<palette>") wears its palette's colours; a bare
    // "custom" yields null = Default colours while its photo (below)
    // still applies. Built-in ids resolve exactly as byId did.
    val chatTheme = ChatThemes.paletteFor(thread.themeId)
    // ROUND 30: subscribe to the owner vault's mutation tick — a tombstone
    // landing while this chat is open re-renders its row from the vault.
    // (For non-owners the vault never mutates; the read is one int.)
    val vaultTick = OwnerVault.stateVersion
    val haze = LocalHazeState.current
    val blockedUids by AppState.blockedUids.collectAsState()
    val partnerBlocked = person.uid in blockedUids
    val typingMap by AppState.typing.collectAsState()
    // ROUND 22: the peer's typing entry is an EPOCH stamp (their
    // clock) — "typing" only while it is younger than 5 s. Refresh
    // writes (≤ every 3 s from their composer) refire this screen via
    // the chat-doc listener, but a peer who dies mid-typing produces
    // no final write — so while a stamp is live, a local 500 ms clock
    // re-evaluates the window and ages it out on screen on its own.
    val peerTypingAt = typingMap[threadId] ?: 0L
    var typingClock by remember(threadId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(threadId, peerTypingAt) {
        typingClock = System.currentTimeMillis()
        while (peerTypingAt > 0L && System.currentTimeMillis() - peerTypingAt < 5_000L) {
            delay(500)
            typingClock = System.currentTimeMillis()
        }
        typingClock = System.currentTimeMillis()
    }
    val peerTyping = peerTypingAt > 0L && typingClock - peerTypingAt < 5_000L

    var input by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<TikuMessage?>(null) }
    /** ROUND 57 (Fix 2): the message being edited, or null. Set by the
     *  long-press menu's Edit (own text messages only): the composer
     *  loads the message's current text, the "Editing message" banner
     *  shows, and sendCurrent REPLACES this message via
     *  [ChatRepo.editText] instead of posting a new one. Cancelling
     *  (banner ✕) restores normal compose mode. */
    var editingMsg by remember(threadId) { mutableStateOf<TikuMessage?>(null) }
    var menuMsg by remember { mutableStateOf<TikuMessage?>(null) }
    var attachOpen by remember { mutableStateOf(false) }
    var viewerMsg by remember { mutableStateOf<TikuMessage?>(null) }
    var videoViewerMsg by remember { mutableStateOf<TikuMessage?>(null) }
    /** ROUND 41: the game message whose full-screen player is open
     *  (GamePlayer.kt), or null. Only ever set for a LOADED game —
     *  the card gates the tap (unloaded → fetchMedia first). */
    var gamePlayerMsg by remember { mutableStateOf<TikuMessage?>(null) }
    /** ROUND 68 (WS9): the full profile picture is open. */
    var showPhoto by remember { mutableStateOf(false) }
    // ---- ROUND 60: Nikah ceremony (Nikah.kt / NikahUi.kt) ----
    /** The full-screen live ceremony is open on this chat. */
    var nikahOpen by remember(threadId) { mutableStateOf(false) }
    /** The role pick ("I am the groom / bride") shown before creating
     *  a ceremony from the attach sheet. */
    var nikahRolePick by remember(threadId) { mutableStateOf(false) }
    /** The keepsake message whose certificate is being viewed. */
    var nikahCertMsg by remember { mutableStateOf<TikuMessage?>(null) }
    /** My display name for ceremony surfaces (same derivation the game
     *  player uses below). */
    val myDisplayName = if (Config.PREVIEW_UI_ONLY) "You"
        else FirebaseEngine.myPerson.name.takeIf { it.isNotBlank() && it != "You" }
            ?: AppState.myUsername.ifEmpty { "You" }
    /** The ceremony session state for THIS chat (NikahStore's doc
     *  listener) — drives the invite card's live state + rejoin banner. */
    val nikahSession by NikahStore.flow(threadId).collectAsState()
    LaunchedEffect(threadId) {
        if (!Config.PREVIEW_UI_ONLY) {
            NikahStore.watch(threadId, person.uid, myDisplayName, thread.displayName)
        }
    }
    var camChooserOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableIntStateOf(0) }
    val recorder = remember { VoiceRecorder(ctx) }
    val player = remember { VoicePlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }
    // ---- ROUND 22: message-effects state ----
    /** The effect armed for the NEXT send (fx kind), or null. Chosen in
     *  the picker sheet, shown as the chip above the composer, consumed
     *  by sendCurrent. */
    var armedFx by remember(threadId) { mutableStateOf<String?>(null) }
    /** ROUND 31: the view-once allowance armed for the NEXT photo/video
     *  send (1 or 2), or null = unlimited. Chosen in the attach sheet /
     *  camera chooser (see RdVoArmRow), consumed by whichever media
     *  launcher fires next — the media twin of [armedFx]. */
    var armedVo by remember(threadId) { mutableStateOf<Int?>(null) }
    /** ROUND 37: the in-flight Tika ask for THIS chat — the transient
     *  indicator's label ("Tika is thinking…" / "Tika is creating…"),
     *  null = idle. It doubles as the single-flight gate: a second
     *  command while one runs is refused with a toast. LOCAL ONLY by
     *  design — no doc, no typing stamp; the partner simply sees the
     *  AI message land when the answer is ready. */
    var aiThinking by remember(threadId) { mutableStateOf<String?>(null) }
    var fxPickerOpen by remember(threadId) { mutableStateOf(false) }
    /** The running overlay playback: fx kind to play key ("<msgId>#<n>").
     *  The counter makes every playback — first play or tap replay — a
     *  fresh key, so the overlay's Animatable restarts cleanly. */
    var fxPlay by remember(threadId) { mutableStateOf<Pair<String, String>?>(null) }
    var fxPlaySeq by remember(threadId) { mutableIntStateOf(0) }
    /** The anchor of the running playback: fx hearts / fire erupt
     *  around their bubble (the row reports its position into
     *  [fxAnchorMap]); every other playback is screen-wide (null). */
    var fxPlayAnchor by remember(threadId) { mutableStateOf<FxAnchor?>(null) }
    /** ROUND 25: the running EmojiFxOverlay playback (the owner's
     *  design): design kind index to play key, same "<msgId>#<n>"
     *  scheme as [fxPlay]. The two playbacks are mutually exclusive —
     *  starting one clears the other, so a single effect owns the
     *  screen at a time, exactly like the single-state Round-24
     *  design. */
    var emojiFxPlay by remember(threadId) { mutableStateOf<Pair<Int, String>?>(null) }
    /** ROUND 66: the running EmojiFx2Overlay playback (the owner's
     *  Chat Emoji Effects file): SP spec index to play key, same
     *  "<msgId>#<n>" scheme. Mutually exclusive with [fxPlay] and
     *  [emojiFxPlay] — starting any one playback clears the other
     *  two, so a single effect owns the screen at a time. */
    var emojiFx2Play by remember(threadId) { mutableStateOf<Pair<Int, String>?>(null) }
    /** Bubble anchors by message id. A plain map on purpose: it is
     *  read only at play time, so layout callbacks never recompose. */
    val fxAnchorMap = remember(threadId) { mutableMapOf<String, FxAnchor>() }
    /** ROUND 24 — the durable played logs (fx kinds in one file,
     *  single-emoji effects in the other). An effect auto-plays the
     *  FIRST time its message is rendered for this account and never
     *  again on its own; only a bubble tap replays, bypassing the logs
     *  on purpose. (ROUND 22's in-memory tail-only set + priming are
     *  gone: first DISPLAY is the trigger now, not becoming the tail.)
     *  ROUND 28 — the logs are PER ACCOUNT: the prefs file name carries
     *  the signed-in uid. The old per-device files let the sender's
     *  play consume the recipient's first view whenever both accounts
     *  shared one phone, so the other side never saw the effect; now
     *  every account's first view plays. The pre-28 files are simply
     *  abandoned (effects may auto-play once more per account — the
     *  accepted migration). The remember is keyed on the uid, so an
     *  account switch rebinds the logs even if this screen never left
     *  composition; a missing uid falls back to the "anon" namespace
     *  instead of crashing. */
    val fxUid = (if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid())
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .take(64)
        .ifEmpty { "anon" }
    val fxPlayedLog = remember(fxUid) { FxPlayedLog(ctx.getSharedPreferences("tiku_fx_played_$fxUid", Context.MODE_PRIVATE)) }
    val emojiFxPlayedLog = remember(fxUid) { FxPlayedLog(ctx.getSharedPreferences("tiku_emoji_fx_played_$fxUid", Context.MODE_PRIVATE)) }
    // ---- ROUND 22 (insta port): bubble entrance-pop bookkeeping ----
    /** Ids this screen has already accounted for: everything present
     *  at prime time (the whole loaded history) plus every arrival
     *  since. Only a TAIL id outside this set is "fresh" — prepends
     *  from loadOlder join the set silently, so history can never pop,
     *  no matter when its rows first compose. */
    val popSeenIds = remember(threadId) { mutableSetOf<String>() }
    var popPrimed by remember(threadId) { mutableStateOf(false) }
    /** The tail ids inside their pop window. Freshness expires after
     *  ~1.5 s, so a row first composed later (the user was scrolled
     *  up when it landed) renders statically instead of popping out
     *  of context. */
    var popFresh by remember(threadId) { mutableStateOf<Set<String>>(emptySet()) }
    /** Opened gift ids — per-account and DURABLE (SharedPreferences):
     *  a gift unwraps once for this account and stays a normal bubble
     *  across restarts, exactly like IG. Local-only by design: nothing
     *  about opening is written to the server. ROUND 28b — the prefs
     *  file carries the same uid namespace as the played logs above:
     *  per-device storage let the sender's unwrap consume the
     *  recipient's sealed surprise whenever both accounts shared one
     *  phone; now every account unwraps for itself. The pre-28b file
     *  is abandoned like the pre-28 logs (gifts show sealed once more
     *  per account — the accepted migration). The remember is keyed
     *  on the uid, so an account switch rebinds it. */
    val giftPrefs = remember(fxUid) { ctx.getSharedPreferences("tiku_fx_gifts_$fxUid", Context.MODE_PRIVATE) }
    var openedGifts by remember(threadId, fxUid) {
        mutableStateOf(giftPrefs.getStringSet("opened", emptySet())?.toSet() ?: emptySet())
    }
    fun playFx(kind: String, msgId: String, anchored: Boolean = false) {
        fxPlaySeq += 1
        emojiFxPlay = null
        emojiFx2Play = null
        fxPlayAnchor = if (anchored) fxAnchorMap[msgId] else null
        fxPlay = kind to "$msgId#$fxPlaySeq"
    }
    /** ROUND 25: start an EmojiFxOverlay playback. Screen-wide by
     *  design (the design's stage IS the full chat), so any running
     *  RdFxOverlay playback and its anchor are cleared. */
    fun playEmojiFx(kindIdx: Int, msgId: String) {
        fxPlaySeq += 1
        fxPlay = null
        fxPlayAnchor = null
        emojiFx2Play = null
        emojiFxPlay = kindIdx to "$msgId#$fxPlaySeq"
    }
    /** ROUND 66: start an EmojiFx2Overlay playback. Screen-wide by
     *  design (the file's stage IS the full chat), so any running
     *  RdFxOverlay / EmojiFxOverlay playback is cleared. */
    fun playEmojiFx2(specIdx: Int, msgId: String) {
        fxPlaySeq += 1
        fxPlay = null
        fxPlayAnchor = null
        emojiFxPlay = null
        emojiFx2Play = specIdx to "$msgId#$fxPlaySeq"
    }
    fun openGift(id: String) {
        if (id in openedGifts) return
        openedGifts = openedGifts + id
        giftPrefs.edit().putStringSet("opened", openedGifts).apply()
    }
    // ROUND 19: the list state STARTS at the tail (the thread's messages
    // are already in state when this screen first composes) — opening a
    // chat never flashes the top of the history before settling. The
    // settle effect below remains as the backstop for lists that grow
    // into their first page a beat later.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = thread.messages.filter { !it.deletedForMe }.lastIndex.coerceAtLeast(0),
    )
    // ROUND 20 — the composer's height is MEASURED (onSizeChanged on the
    // bottom overlay below) and the message list is bounded by exactly
    // that height: the tail can never slide under the composer card —
    // reply strip up, recording row up, keyboard open or closed. The old
    // layout ran the list full-height underneath the floating composer
    // with a GUESSED 130dp bottom content padding; any composer state
    // taller than the guess (or the IME resize mid-glide) ate the tail.
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val composerHeight = with(LocalDensity.current) { composerHeightPx.toDp() }
    val startCall = rememberCallStarter()
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // ROUND 15 — attach-sheet glitch fix: with the keyboard open, the
    // sheet used to render as a small floating glass pill mid-screen
    // (the IME window owned the bottom of the screen, so the sheet's
    // bottom anchor floated above it over the chat). Opening the sheet
    // now dismisses the keyboard + drops field focus FIRST, so the
    // sheet always presents anchored to the real bottom edge.
    LaunchedEffect(attachOpen) {
        if (attachOpen) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    DisposableEffect(threadId) {
        onDispose {
            player.stop()
            // ROUND 20: never leave a live recorder behind — an orphaned
            // MediaRecorder keeps the mic busy and makes the NEXT record
            // attempt fail on several devices.
            recorder.stop(false)
            // ROUND 22: leaving the thread drops my typing stamp
            // (send + emptied-field clear it too; the peer-side 5 s
            // aging is the backstop if this write is ever lost).
            AppState.repo.setTyping(threadId, false)
            AppState.repo.closeThread(threadId)
        }
    }

    // Photo picker: decode -> the real encrypted image send. (Video and
    // audio-file pickers live with the round-15 launchers below.)
    // ROUND 63 (multi-photo): PickMultipleVisualMedia capped at 10 by
    // the picker itself (the system picker enforces maxItems where it
    // provides the UI; the fallback contract returns what it returns,
    // so the cap is ALSO enforced in-app below with the owner's toast).
    // Every selected image becomes its OWN ordinary encrypted photo
    // message through the exact single-photo pipeline (sendPhotoVo ->
    // deliverMedia) — no new message shape, so old builds receive and
    // render them as ordinary photo messages. The decode+dispatch loop
    // runs sequentially, in selection order, on a background
    // dispatcher: the composer never blocks, each send's optimistic
    // bubble lands in order, and one image's failure (unreadable file,
    // or its delivery failing later) neither stops nor silently drops
    // the rest — a failed delivery surfaces exactly like a failed
    // single photo (FAILED bubble + tap-to-retry in RdBubbleRow).
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            // ROUND 31: the armed view-once allowance rides this pick
            // and is consumed by it (the media twin of the fx arm) —
            // with a multi-pick the whole batch is the one pick, so
            // the allowance applies to every photo in it.
            val vo = armedVo; armedVo = null
            val picked = if (uris.size > 10) {
                AppState.toast("Max 10 photos")
                uris.take(10)
            } else uris
            val resolver = ctx.contentResolver
            scope.launch {
                for (uri in picked) {
                    val bmp = withContext(Dispatchers.IO) {
                        runCatching {
                            resolver.openInputStream(uri)?.use { s -> BitmapFactory.decodeStream(s) }
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        AppState.repo.sendPhotoVo(threadId, bmp, vo)
                    } else {
                        AppState.toast("Couldn't read one photo — the rest still sent")
                    }
                }
            }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRdRecording(recorder) { recording = true }
        else AppState.toast("Microphone permission needed for voice notes")
    }

    // ---- ROUND 15: video + audio file pickers (attach sheet) ----
    // Both copy the pick into an app-cache file first: the pipeline works
    // on real files, and the copy outlives the picker's Uri grant.
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val f = copyUriToCacheFile(ctx, uri, "vid")
            if (f != null) {
                // ROUND 31: the armed view-once allowance rides this pick.
                val vo = armedVo; armedVo = null
                AppState.repo.sendVideoVo(threadId, f, ctx.contentResolver.getType(uri) ?: "video/mp4", mediaDurationSec(f), vo)
            } else AppState.toast("Couldn't read that video")
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val f = copyUriToCacheFile(ctx, uri, "aud")
            if (f != null) {
                AppState.repo.sendAudio(threadId, f, ctx.contentResolver.getType(uri) ?: "audio/mpeg", mediaDurationSec(f))
            } else AppState.toast("Couldn't read that audio file")
        }
    }

    // ---- ROUND 15: composer camera (IG-style) ----
    // Captures land in app CACHE via FileProvider and are NEVER written
    // to the gallery: the photo is decoded -> sent -> the cache file is
    // deleted; the video file stays in cache as the sender's playable
    // copy (the engine's rule for captured/picked media files).
    //
    // ROUND 43 — WHY CAMERA + VIEW-ONCE SILENTLY NEVER SENT (owner
    // bug, build 74): the pending capture (file) and the armed
    // allowance were read at RESULT time from plain `remember` state.
    // MainActivity declares NO android:configChanges, so a rotation
    // while the camera app is open — or an OEM teardown of the
    // stopped activity under memory pressure from the camera app —
    // destroys and recreates this composition mid-capture. The
    // ActivityResultRegistry still redelivers the result to the
    // re-registered launcher, but the fresh callback found
    // camPhotoFile == null (and armedVo == null) and the guard
    // swallowed the capture whole: no send, no error, nothing. The
    // gallery path never depended on remembered state — its result
    // carries the Uri, and its allowance degrades at worst to Always
    // — which is why ONLY the camera path vanished. The fix is
    // structural: the pending capture path AND the allowance are
    // snapshotted at LAUNCH into rememberSaveable state (which rides
    // the activity's saved-instance state across recreation), the
    // decode is bounds-limited (a full-res OEM capture decodeFile
    // could OOM the callback), and every failure on the result path
    // is SPOKEN with a toast instead of swallowed.
    var camPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var camVideoPath by rememberSaveable { mutableStateOf<String?>(null) }
    /** The view-once arm as it stood when the pending capture was
     *  LAUNCHED (-1 = Always / unarmed, 1 / 2 = the allowance). A
     *  plain MutableState<Int> on purpose: it is the state shape
     *  rememberSaveable's default saver is guaranteed to carry. */
    var camVoCode by rememberSaveable { mutableStateOf(-1) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = camPhotoPath
        val vo = camVoCode.takeIf { it in 1..2 }
        camPhotoPath = null
        camVoCode = -1
        val f = path?.let { File(it) }
        if (!ok) {
            // user cancelled the camera — not a failure; the live arm
            // survives for the next pick, as before.
            if (f != null) runCatching { f.delete() }
            return@rememberLauncherForActivityResult
        }
        val bmp = f?.let { decodeCaptureBounded(it) }
        if (f == null || bmp == null) {
            // ROUND 43: never silent again — a capture that cannot be
            // read says so (the arm stays armed for the retry).
            AppState.toast("Couldn't send that photo — please try again")
            if (f != null) runCatching { f.delete() }
            return@rememberLauncherForActivityResult
        }
        // ROUND 31 + 43: the allowance snapshotted at launch rides
        // this capture; the send consumes the live arm too.
        // ROUND 67: the send itself is guarded too — a synchronous
        // throw out of this callback would kill the process exactly
        // like the launch-side crash did.
        armedVo = null
        val sent = runCatching { AppState.repo.sendPhotoVo(threadId, bmp, vo) }
        runCatching { f.delete() }
        if (sent.isFailure) {
            Log.e("TikuCamera", "camera photo send failed", sent.exceptionOrNull())
            AppState.toast("Couldn't send that photo — please try again")
        }
    }
    val recordVideoCapture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val path = camVideoPath
        val vo = camVoCode.takeIf { it in 1..2 }
        camVideoPath = null
        camVoCode = -1
        val f = path?.let { File(it) }
        if (ok && f != null && f.exists() && f.length() > 0) {
            // ROUND 31 + 43: launch-time allowance snapshot, as above.
            // ROUND 67: guarded send, same reasoning as takePhoto.
            armedVo = null
            val sent = runCatching {
                AppState.repo.sendVideoVo(threadId, f, "video/mp4", mediaDurationSec(f), vo)
            }
            if (sent.isFailure) {
                Log.e("TikuCamera", "camera video send failed", sent.exceptionOrNull())
                AppState.toast("Couldn't send that video — please try again")
            }
        } else {
            if (f != null) runCatching { f.delete() }
            // ROUND 43: a "successful" capture with no usable file is
            // a failure and says so (a cancel stays silent).
            if (ok) AppState.toast("Couldn't send that video — please try again")
        }
    }
    // ---- ROUND 67 — WHY OPENING THE CAMERA KILLED THE APP (owner,
    // build 111: "Tiku keeps stopping" the moment the camera opens
    // from a chat) ----
    // The launch side of this path was the ONE unguarded surface the
    // Round-43 hardening never touched: launchCameraPhoto/Video
    // called FileProvider.getUriForFile + launcher.launch(...) bare,
    // synchronously inside the chooser row's tap handler, with NO
    // permission check and NO handler check. ActivityResultLauncher
    // .launch() THROWS on exactly the devices where a step fails:
    // ActivityNotFoundException when no activity can handle
    // ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE (camera app
    // disabled or removed, a restricted profile, or a device with
    // no camera at all — the manifest declares the camera feature
    // required=false, so the app installs there), SecurityException
    // on OEM builds that enforce the caller's CAMERA permission
    // around capture intents (the app declares CAMERA but never
    // checked or requested it on this path), IllegalArgumentException
    // out of FileProvider, IllegalStateException out of a launcher
    // whose lifecycle moved on. Any of them propagated uncaught out
    // of the tap and took the process down — the system dialog.
    // The fix keeps the working path byte-identical (same contracts,
    // same cache files, same FileProvider authority, same result
    // handling above) and changes only what happens AROUND it:
    //  1. CAMERA permission is verified BEFORE any open attempt; if
    //     it is missing it is requested and AWAITED — the camera
    //     opens only from the permission callback, on grant. The
    //     pending kind rides rememberSaveable (the Round-43 lesson:
    //     plain remember state dies with a mid-flight recreation).
    //  2. Denial is spoken and routes to the gallery: a toast plus
    //     the attach sheet (whose Photo row IS the gallery picker).
    //  3. The open attempt itself (file, FileProvider URI, launch)
    //     runs inside runCatching end-to-end; ANY failure is logged
    //     with its exception and lands on the same graceful surface
    //     (toast + gallery sheet) instead of process death. A failed
    //     open also drops the pending-capture state and the session
    //     lock's external-activity grace, since no external activity
    //     is in fact starting.
    /** The graceful camera-failure surface: log the real exception,
     *  clear the pending capture + lock grace, toast, and offer the
     *  gallery (the attach sheet) as the fallback. */
    fun failCameraOpen(e: Throwable) {
        Log.e("TikuCamera", "camera open failed", e)
        camPhotoPath = null
        camVideoPath = null
        camVoCode = -1
        ChatSession.expectExternalActivity = false
        AppState.toast("Camera couldn't open — try the gallery")
        attachOpen = true
    }
    fun launchCameraPhotoNow() {
        runCatching {
            val f = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
            camPhotoPath = f.absolutePath
            camVoCode = armedVo ?: -1
            ChatSession.expectExternalActivity = true
            takePhoto.launch(FileProvider.getUriForFile(ctx, "tiku.app.fileprovider", f))
        }.onFailure { failCameraOpen(it) }
    }
    fun launchCameraVideoNow() {
        runCatching {
            val f = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.mp4")
            camVideoPath = f.absolutePath
            camVoCode = armedVo ?: -1
            ChatSession.expectExternalActivity = true
            recordVideoCapture.launch(FileProvider.getUriForFile(ctx, "tiku.app.fileprovider", f))
        }.onFailure { failCameraOpen(it) }
    }
    /** Which capture the in-flight CAMERA permission request belongs
     *  to: 0 = none, 1 = photo, 2 = video. rememberSaveable for the
     *  same reason as the pending capture paths above. */
    var pendingCamKind by rememberSaveable { mutableStateOf(0) }
    val askCameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingCamKind
        pendingCamKind = 0
        if (granted) {
            when (kind) {
                1 -> launchCameraPhotoNow()
                2 -> launchCameraVideoNow()
            }
        } else {
            // Denial is a graceful in-app surface, never a dead end:
            // say so, and offer the gallery (attach sheet) instead.
            Log.w("TikuCamera", "CAMERA permission denied (pending kind=$kind)")
            AppState.toast("Camera permission needed — you can pick from the gallery instead")
            attachOpen = true
        }
    }
    fun launchCameraPhoto() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraPhotoNow()
        } else {
            pendingCamKind = 1
            runCatching { askCameraPerm.launch(Manifest.permission.CAMERA) }
                .onFailure { pendingCamKind = 0; failCameraOpen(it) }
        }
    }
    fun launchCameraVideo() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraVideoNow()
        } else {
            pendingCamKind = 2
            runCatching { askCameraPerm.launch(Manifest.permission.CAMERA) }
                .onFailure { pendingCamKind = 0; failCameraOpen(it) }
        }
    }

    // recording clock + waveform sampling + 3-minute hard cap (build 47)
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        recSecs = 0
        while (recording) {
            delay(200)
            recorder.sample()
            recSecs = ((System.currentTimeMillis() - recorder.startedAt) / 1000).toInt()
            if (recSecs >= 180) {
                val f = recorder.stop(true)
                recording = false
                if (f != null) {
                    AppState.repo.sendVoice(threadId, f, 180, recorder.normalizedAmps())
                    AppState.toast("Voice notes are capped at 3 minutes")
                } else AppState.toast("Recording failed — nothing was captured")
            }
        }
    }

    // Presence: freshness-aware (the profile listener pushes updates; the
    // 30s tick re-evaluates the freshness window so a peer whose app died
    // flips from "Active now" to their exact last seen).
    var presenceTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(threadId) {
        while (true) { delay(30_000); presenceTick++ }
    }
    val statusText = when {
        peerTyping -> "typing…"
        !Config.PREVIEW_UI_ONLY -> {
            @Suppress("UNUSED_VARIABLE") val tick = presenceTick // read: re-evaluate per tick
            val p = FirebaseEngine.presenceText(person)
            if (p == "online") "Active now" else p
        }
        else -> "Active now"
    }

    val visible = thread.messages.filter { !it.deletedForMe }

    // ROUND 19 — tail-glide machinery. ONE follow job at a time: every
    // glide request cancels the previous job, so a burst of arrivals (or
    // an own send racing its echo) coalesces into a single animation to
    // the final tail instead of per-message stutters.
    //
    // ROUND 21 — ONE SCROLL AUTHORITY: the follow became ONE
    // animateScrollBy of the exact pixel delta, and the IME glue was
    // muted while a glide was in flight or a send request unconsumed.
    //
    // ROUND 22 — WHY BUILD 65 STILL LURCHED (owner: "message pehle
    // niche jaata hai phir uper se jaata hai"), found by reading the
    // round-21 code as an event sequence instead of as intentions:
    //  (1) THE GUARD HAD A HOLE AT BOTH ENDS. The send effect CONSUMED
    //      the request — handledTailSeq + consumeTailScroll null
    //      pendingTail, so latestSendPending went false — BEFORE
    //      calling glideToTail(nearBottom), and glideInFlight was set INSIDE the
    //      launched coroutine: asynchronously, one dispatch later.
    //      Between the consume and the flag-set the glue was UNMUTED,
    //      while the composer was already shrinking (the input clears
    //      synchronously in the send tap and the reply strip starts
    //      collapsing in the same recomposition). Any viewport-height
    //      emission landing in that window ran the glue's instant
    //      scrollToItem — the DOWN snap. At the other end the flag
    //      dropped the instant the job finished, while the strip's
    //      shrinkVertically collapse (~300 ms) outlasts the 260 ms
    //      glide — late height steps hit an unmuted glue and pinned
    //      again, on top of the landed glide.
    //  (2) THE FALLBACK WAS AN INSTANT ANCHOR ON THE FOLLOW PATH. The
    //      350 ms wait tested visibleItemsInfo — a set containing only
    //      items INTERSECTING the viewport — and both its timeout
    //      branch and the measure loop's `last.index < target` branch
    //      answered with scrollToItem: a second instant jump racing
    //      the animation it was supposed to serve.
    //  (3) THE SECOND MEASURE PASS doubled the motion: pass 1 animated,
    //      then pass 2 measured the remainder — real, because the
    //      composer was still collapsing — and animated AGAIN, so the
    //      bubble visibly arrived twice.
    // ROUND 22 closes all three BY CONSTRUCTION:
    //  - glideToTail(nearBottom) raises glideInFlight SYNCHRONOUSLY, in the
    //    caller, before the job even exists — and the send effect now
    //    arms the glide BEFORE consuming the request, so from the send
    //    tap onward there is no unmuted instant in the whole sequence
    //    (see the timeline argument in the round-22 report).
    //  - The glue stays muted until 150 ms AFTER the glide job ends
    //    ([glueMutedUntil]), outliving the strip collapse — and the
    //    job itself ends with a settle pass (see glideToTail) that
    //    reconciles any viewport motion the IME made mid-glide, which
    //    is what keeps the tail glued exactly above the keyboard
    //    through send / receive / typing alike.
    //  - glideDeltaToTail awaits the tail's LAYOUT (totalItemsCount
    //    covering the index), never its visibility, then measures ONE
    //    delta from layoutInfo at animation start and runs ONE
    //    animateScrollBy. There is NO scrollToItem fallback on the
    //    at-bottom path: if the tail is somehow outside the visible
    //    window there, the glide does nothing — the list physically
    //    clamps at max scroll as the viewport grows, and the glue owns
    //    any residual once it un-mutes. The historic instant anchor
    //    survives ONLY for the not-at-bottom case (jump-to-latest pill,
    //    own send from deep history), whose behaviour is unchanged
    //    from rounds 19–21.
    val tailRequests by AppState.tailScrollRequests.collectAsState()
    val pendingTail = tailRequests[threadId]
    var handledTailSeq by remember(threadId) { mutableLongStateOf(0L) }
    var followJob by remember(threadId) { mutableStateOf<Job?>(null) }
    var glideInFlight by remember(threadId) { mutableStateOf(false) }
    /** Glue mute deadline (wall-clock ms): the IME glue is silent while
     *  a glide is in flight AND until this instant after it lands, so
     *  the composer's trailing height steps can never re-pin the tail
     *  on top of a finished glide. */
    var glueMutedUntil by remember(threadId) { mutableLongStateOf(0L) }
    val currentVisible by rememberUpdatedState(visible)
    val currentPeerTyping by rememberUpdatedState(peerTyping)

    /** The single tail-follow animation: scroll forward by exactly the
     *  distance between the tail item's end (plus bottom content
     *  padding) and the viewport's end, measured from layoutInfo AFTER
     *  the tail item is laid out (bounded wait on the layout total —
     *  the round-21 visibility test could starve on a tail starting
     *  below the fold and timed out into its instant fallback). At the
     *  bottom that distance is precisely the new bubble's height —
     *  IG's one clean shift. delta ≤ 0 means the tail is already fully
     *  in view: nothing to do, and doing nothing is what keeps an echo
     *  or a metadata-only snapshot from ever moving the list. */
    suspend fun glideDeltaToTail(atBottom: Boolean) {
        val target = currentVisible.lastIndex + if (currentPeerTyping) 1 else 0
        if (target < 0) return
        val laidOut = withTimeoutOrNull(600) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > target }
        } != null
        if (!laidOut) return
        if (!currentCoroutineContext().isActive) return
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return
        if (last.index < target) {
            // Tail outside the laid-out visible window. Reachable ONLY
            // when the user was not at the bottom (there the new item
            // always starts inside the window — it begins one spacing
            // below the old tail's end, above the viewport's end edge
            // by the bottom content padding). Keep the historic instant
            // anchor for that case alone; on the follow path there is
            // deliberately NO fallback — no scroll, of any kind.
            if (!atBottom) runCatching { listState.scrollToItem(target) }
            return
        }
        val delta = last.offset + last.size + info.afterContentPadding - info.viewportEndOffset
        if (delta <= 0) return
        try {
            listState.animateScrollBy(delta.toFloat(), tween(260, easing = EaseOutCubic))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
    }

    fun glideToTail(atBottom: Boolean) {
        // ROUND 22: the caller passes the anchored state it captured
        // (nearBottom in the screen scope) and the guard rises HERE,
        // synchronously — from this line onward the glue is muted no
        // matter when the dispatcher runs the job. (Round 21 set the
        // flag inside the coroutine body: one dispatch too late, and
        // the send effect had already consumed the request that was
        // the guard's other half.)
        glideInFlight = true
        followJob?.cancel()
        val job = scope.launch {
            try {
                delay(60) // burst coalescing: a newer glide replaces this one
                glideDeltaToTail(atBottom)
                // SETTLE PASS (keyboard invariant): the glide measured
                // its delta at animation start, but the IME may still
                // be animating — opening, closing, or resizing around
                // a send/receive — stepping the viewport AFTER the
                // measure. Before releasing the guard, wait out the
                // mute window and measure once more: in the normal
                // case delta ≤ 0 and this is a strict no-op; if the
                // viewport moved mid-glide it applies exactly the
                // remainder, so the tail ends glued above the IME —
                // never slipped under it, never pinned twice. (This is
                // NOT round 21's second pass: that one ran inline,
                // back-to-back, chasing the collapsing composer with a
                // second visible animation; this one runs after a
                // settle delay and only ever fires when the first
                // glide provably landed short.)
                if (atBottom && followJob === currentCoroutineContext()[Job]) {
                    delay(170)
                    if (followJob === currentCoroutineContext()[Job]) {
                        glideDeltaToTail(atBottom)
                    }
                }
            } finally {
                // only the CURRENT job may lower the guard — a cancelled
                // predecessor must not un-mute the glue mid-successor.
                // The mute outlives the job by 150 ms (see above).
                if (followJob === currentCoroutineContext()[Job]) {
                    glueMutedUntil = System.currentTimeMillis() + 150
                    glideInFlight = false
                }
            }
        }
        followJob = job
    }

    // ROUND 18 — UNREAD DIVIDER anchor. The unread set is, by the
    // engine's own definition, the newest [unreadAtOpen] incoming
    // non-call messages — the divider pins above the earliest of them,
    // identified by message ID: pagination prepends and new arrivals
    // can shift indices freely, the divider never moves or duplicates.
    // ROUND 20: the count comes from ChatThread.unreadAtOpen, recorded
    // by the engine inside openThread, synchronously, before markRead
    // can zero the badge (reading `thread.unread` here captured 0).
    //
    // ROUND 21 — two device holes in the round-20 capture, both from
    // freezing the values in remember() at FIRST composition:
    //  (a) entry paths that navigate with go(Screen.Chat) ALONE —
    //      the call screens' message button (Call.kt / Rtc.kt) and any
    //      deep entry — never ran openThread before this screen
    //      composed, so unreadAtOpen was stale (previous session) or 0;
    //  (b) the pin was computed once from whatever messages the thread
    //      object held at first composition — on a cold entry that list
    //      can still be empty / a beat behind the newest page, so the
    //      pin froze null (or above the wrong message) forever.
    // Fixes: this screen now runs the engine's open sequence itself as
    // an entry safety net (device only — PreviewRepo.openThread zeroes
    // the fixture's `unread`, which is the harness's divider source),
    // and the engine only captures the badge on a closed->open
    // transition, so the net can never clobber the row tap's capture.
    // The pin itself is reactive state: it lands the first moment the
    // loaded incoming set actually covers the count (the newest page
    // loads first, so that is the first snapshot in practice), then
    // freezes for the life of this composition.
    LaunchedEffect(threadId) {
        if (!Config.PREVIEW_UI_ONLY) AppState.repo.openThread(threadId)
    }
    var dividerPin by remember(threadId) { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(
        threadId, thread.messages.size, thread.unreadAtOpen, thread.unread, thread.hasOlder,
    ) {
        if (dividerPin != null) return@LaunchedEffect
        val count = if (Config.PREVIEW_UI_ONLY) thread.unread else thread.unreadAtOpen
        if (count <= 0) return@LaunchedEffect
        val incoming = thread.messages.filter {
            !it.deletedForMe && !it.me && it.kind != MsgKind.CALL
        }
        when {
            incoming.size >= count ->
                dividerPin = incoming[incoming.size - count].id to count
            // every page is loaded and the set still falls short (e.g.
            // some unread were deleted-for-me locally): pin above the
            // earliest loaded incoming rather than never.
            incoming.isNotEmpty() && !thread.hasOlder ->
                dividerPin = incoming.first().id to count
        }
    }

    // ROUND 18 — SEEN caption target: my LAST own non-call, non-tombstone
    // message. The data is the shared per-message seenBy marker the
    // partner's client writes when they read (decryptDoc -> seenByPeer);
    // the caption hides entirely while read receipts are off — globally
    // (Settings ▸ Read receipts) or for this chat (receiptsOffMine).
    val showReceipts = globalSettings.readReceipts && !thread.receiptsOffMine
    val lastOwnId = visible.lastOrNull {
        it.me && it.kind != MsgKind.CALL && !it.deletedForEveryone && !it.undecryptable
    }?.id

    /** ROUND 31: whose Save button the media viewers (and the message
     *  menu) may offer — my own media and plain media always; a
     *  partner's view-once message only under the owner's unlimited
     *  override. Everything else about saving is unchanged. */
    fun voSaveAllowed(vm: TikuMessage): Boolean =
        vm.me || vm.vo == null || VoStore.ownerBypass(ctx)

    /** ROUND 37: parse a Tika command at the very start of the field —
     *  "/tiku <question>" asks for a TEXT answer, "/tika <prompt>" for
     *  a generated IMAGE. Returns (isImage, prompt); null when the text
     *  is not a command or the prompt is empty (a bare "/tiku" simply
     *  sends as a plain message). Case-insensitive prefix; a word that
     *  merely starts alike ("/tikudu") never matches — the prefix must
     *  be the whole field or be followed by a space. */
    fun tikaCommand(v: String): Pair<Boolean, String>? {
        val lower = v.lowercase()
        val isImage = when {
            lower == "/tiku" || lower.startsWith("/tiku ") -> false
            lower == "/tika" || lower.startsWith("/tika ") -> true
            else -> return null
        }
        val prompt = v.substring(5).trim()
        return if (prompt.isEmpty()) null else isImage to prompt
    }

    /** ROUND 41: parse the game command — "/babeg <description>" asks
     *  Tika to BUILD a 2-player game for this chat. Same prefix
     *  discipline as [tikaCommand]: case-insensitive, the prefix must
     *  be the whole field or be followed by a space, and an empty
     *  description is not a command. Returns the game description. */
    fun gameCommand(v: String): String? {
        val lower = v.lowercase()
        if (lower != "/babeg" && !lower.startsWith("/babeg ")) return null
        val prompt = v.substring(6).trim()
        return prompt.ifEmpty { null }
    }

    /** ROUND 43: the replied-to photo's decrypted bitmap for a /tiku
     *  vision ask. Photos publish into thread state through the
     *  listener's decrypt path (TikuMessage.photo, an in-memory
     *  bitmap), so the bytes are normally already here — [snapshot] is
     *  the copy the user saw when they hit reply, and the poll covers
     *  a just-landed photo whose chunks finished a beat after its doc.
     *  Null when the message is gone, tombstoned, undecryptable on
     *  this phone, or still not loaded after the wait — the caller
     *  toasts "Couldn't load that photo" and does nothing else.
     *  NOTE: fetchMedia is deliberately NOT the loader for images —
     *  its success contract is a cache localPath, which image
     *  messages never carry (they decrypt straight to a Bitmap), so
     *  it cannot publish an image into state; the listener decrypt IS
     *  the image load path, and this poll observes it. */
    suspend fun loadReplyPhoto(msgId: String, snapshot: Bitmap?): Bitmap? {
        snapshot?.let { return it }
        repeat(40) {
            val m = AppState.thread(threadId)?.messages?.firstOrNull { it.id == msgId }
                ?: return null
            m.photo?.let { return it }
            if (m.undecryptable || m.deletedForEveryone) return null
            delay(300)
        }
        return AppState.thread(threadId)?.messages?.firstOrNull { it.id == msgId }?.photo
    }

    fun sendCurrent() {
        val v = input.trim()
        if (v.isEmpty()) return
        // ROUND 57 (Fix 2): EDIT MODE — Send replaces the message being
        // edited (the eCt/eIv write in the engine) instead of posting
        // a new one, and the composer returns to normal mode either
        // way. An unchanged text is a silent no-op; a target that was
        // deleted for everyone while the editor was open says so.
        val editTarget = editingMsg
        if (editTarget != null) {
            editingMsg = null
            input = ""
            scope.launch { AppState.repo.setTyping(threadId, false) }
            val stillThere = AppState.thread(threadId)?.messages
                ?.firstOrNull { it.id == editTarget.id }
            if (stillThere == null || stillThere.deletedForEveryone) {
                AppState.toast("That message is gone")
                return
            }
            if (v != stillThere.text) {
                AppState.repo.editText(threadId, editTarget.id, v)
            }
            return
        }
        // ROUND 37: a bare Tika command (no reply target, no armed
        // effect) takes the AI path: the command itself sends FIRST as
        // the user's normal message — both sides see what was asked,
        // exactly like Meta AI — then Gemini runs in the background
        // and its answer lands as a separate ai-flagged message. One
        // ask per chat at a time; a failure posts nothing, only a
        // toast says why. Keyboard invariants match the plain path:
        // the field just empties, focus and IME are never touched.
        // ROUND 41: "/babeg <description>" takes the game path — the
        // command sends FIRST as the user's normal message (both sides
        // see what was asked for, like the Tika commands), then the
        // build runs ASYNC in the background: the chat stays fully
        // usable while Tika writes the game (only the transient
        // indicator shows), and the finished game lands as its own
        // game message. The in-flight gate is SHARED with /tiku +
        // /tika (aiThinking) — one AI job per chat at a time, whichever
        // kind it is. A failed build posts NOTHING — just a toast.
        val gamePrompt = if (replyTarget == null && armedFx == null) gameCommand(v) else null
        if (gamePrompt != null) {
            if (aiThinking != null) {
                AppState.toast("Tika is busy with the previous task")
                return
            }
            AppState.repo.sendText(threadId, v, null)
            input = ""
            scope.launch { AppState.repo.setTyping(threadId, false) }
            aiThinking = "Tika is creating your game…"
            scope.launch {
                try {
                    when (val out = GeminiClient.generateGame(gamePrompt)) {
                        is GeminiClient.Outcome.Ok -> AppState.repo.sendGame(
                            threadId, out.value, gamePrompt.take(48),
                        )
                        GeminiClient.Outcome.Quota ->
                            AppState.toast("Tika: the quota is finished — try again in a little while")
                        GeminiClient.Outcome.Failed ->
                            AppState.toast("Tika couldn't create the game — try again")
                    }
                } finally {
                    aiThinking = null
                }
            }
            return
        }
        // ROUND 43: /tiku as a REPLY to a photo — Tika LOOKS at the
        // photo and answers about it. The command still sends FIRST,
        // as the user's reply message with its quote attached (both
        // sides see what was asked, exactly like a plain reply send);
        // then the replied photo's decrypted bytes are read from
        // thread state on THIS phone and go to Gemini vision with the
        // question, and the answer lands as a normal ai message. This
        // explicit reply is the ONLY door an image ever takes to the
        // AI (owner privacy law: nothing else from the chat — no
        // history, no other messages or images — is ever sent). A
        // bare "/tiku" reply asks the default describe-it question.
        val visionTarget = replyTarget?.takeIf { it.kind == MsgKind.IMAGE }
        val visionQuestion = if (visionTarget != null && armedFx == null) {
            val lower = v.lowercase()
            when {
                lower == "/tiku" -> ""
                lower.startsWith("/tiku ") -> v.substring(5).trim()
                else -> null
            }
        } else null
        if (visionTarget != null && visionQuestion != null) {
            if (aiThinking != null) {
                AppState.toast("Tika is still answering the previous one")
                return
            }
            val question = visionQuestion.ifEmpty { "What is in this photo? Describe it." }
            val targetId = visionTarget.id
            val snapshot = visionTarget.photo
            AppState.repo.sendTextFx(threadId, v, replyQuoteLabel(visionTarget), null)
            input = ""; replyTarget = null
            scope.launch { AppState.repo.setTyping(threadId, false) }
            aiThinking = "Tika is thinking…"
            scope.launch {
                try {
                    val bmp = loadReplyPhoto(targetId, snapshot)
                    if (bmp == null) {
                        AppState.toast("Couldn't load that photo")
                    } else {
                        // Vision payload: ≤1024 px on the long edge,
                        // JPEG — the question is about the content,
                        // not the pixels.
                        val jpeg = MediaStore.compressPhoto(bmp, maxSide = 1024, quality = 82)
                        when (val out = GeminiClient.answerImage(question, jpeg)) {
                            is GeminiClient.Outcome.Ok -> AppState.repo.sendTextAi(threadId, out.value)
                            GeminiClient.Outcome.Quota ->
                                AppState.toast("Tika: the quota is finished — try again in a little while")
                            GeminiClient.Outcome.Failed ->
                                AppState.toast("Tika couldn't answer — try again")
                        }
                    }
                } finally {
                    aiThinking = null
                }
            }
            return
        }
        val cmd = if (replyTarget == null && armedFx == null) tikaCommand(v) else null
        if (cmd != null) {
            if (aiThinking != null) {
                AppState.toast("Tika is still answering the previous one")
                return
            }
            val (isImage, prompt) = cmd
            AppState.repo.sendText(threadId, v, null)
            input = ""
            scope.launch { AppState.repo.setTyping(threadId, false) }
            aiThinking = if (isImage) "Tika is creating…" else "Tika is thinking…"
            scope.launch {
                try {
                    if (isImage) {
                        when (val out = GeminiClient.generateImage(prompt)) {
                            is GeminiClient.Outcome.Ok -> AppState.repo.sendPhotoAi(threadId, out.value)
                            GeminiClient.Outcome.Quota ->
                                AppState.toast("Tika: the image quota is finished — try again in a little while")
                            GeminiClient.Outcome.Failed ->
                                AppState.toast("Tika couldn't create the image — try again")
                        }
                    } else {
                        when (val out = GeminiClient.answerText(prompt)) {
                            is GeminiClient.Outcome.Ok -> AppState.repo.sendTextAi(threadId, out.value)
                            GeminiClient.Outcome.Quota ->
                                AppState.toast("Tika: the quota is finished — try again in a little while")
                            GeminiClient.Outcome.Failed ->
                                AppState.toast("Tika couldn't answer — try again")
                        }
                    }
                } finally {
                    aiThinking = null
                }
            }
            return
        }
        // ROUND 22: the armed effect (if any) rides this one send and is
        // consumed by it — the chip clears with the field. NOTE (keyboard
        // invariant): nothing on this path touches focus or the IME —
        // no hide(), no clearFocus, and the hoisted input state simply
        // empties — so the keyboard stays exactly where it was, open,
        // with the field still focused for the next message.
        val fx = armedFx
        AppState.repo.sendTextFx(threadId, v, replyTarget?.let { replyQuoteLabel(it) }, fx)
        armedFx = null
        input = ""; replyTarget = null
        scope.launch { AppState.repo.setTyping(threadId, false) }
    }

    fun react(m: TikuMessage, emoji: String) {
        val myKey = if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid()
        val cur = m.reactions[myKey] ?: m.reactions.values.firstOrNull()
        AppState.repo.react(threadId, m.id, if (cur == emoji) null else emoji)
    }

    /** Scroll to a message (the pinned strip's tap) — if it is beyond the
     *  loaded window, page older messages in until it lands (build 47). */
    fun jumpToMessage(msgId: String) {
        scope.launch {
            var tries = 0
            while (true) {
                val th = AppState.thread(threadId) ?: return@launch
                val msgs = th.messages.filter { !it.deletedForMe }
                val idx = msgs.indexOfFirst { it.id == msgId }
                if (idx >= 0) {
                    runCatching { listState.animateScrollToItem(idx) }
                    return@launch
                }
                if (!th.hasOlder || tries >= 10) {
                    AppState.toast("Couldn't find that message")
                    return@launch
                }
                AppState.repo.loadOlder(threadId)
                tries++
                delay(700)
            }
        }
    }

    // ---- list following (build 47's anti-jitter rule on the file's
    // forward list): settle at the latest message on open (instant —
    // opening a chat lands at the bottom; round 19 also initialises the
    // list state AT the tail, so there is no top flash to settle from).
    // ROUND 18 (owner: messages must GLIDE like IG, never jump): an
    // INCOMING tail glides the list ONLY while the user is already
    // at/near the bottom; scrolled up reading history, nothing ever
    // yanks them (the jump-to-latest pill is the way back, like IG).
    // ROUND 19: an OWN send is the one exception, and it no longer
    // rides the tail-change effect at all — the engine raises an
    // explicit tail-scroll request carrying the new local's id (see
    // AppState.tailScrollRequests), and the effect below fires it
    // unconditionally once that exact item is the composed tail.
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    var settled by remember(threadId) { mutableStateOf(false) }
    LaunchedEffect(threadId, visible.isNotEmpty()) {
        if (!settled && visible.isNotEmpty()) {
            runCatching { listState.scrollToItem(visible.lastIndex + if (peerTyping) 1 else 0) }
            settled = true
            // a send requested before this chat opened is satisfied by
            // the settle itself — never replay it as a second glide.
            pendingTail?.let {
                handledTailSeq = it.seq
                AppState.consumeTailScroll(threadId, it.seq)
            }
        }
    }
    // ROUND 19 — own-send glide (WhatsApp behaviour): ALWAYS glide on
    // send, wherever the user had scrolled to. Keyed on the request AND
    // on the composed tail's identity: if the optimistic item hasn't
    // landed in the list yet, the effect simply re-runs when it does.
    // The echo can never retrigger it — the request fires once, for the
    // local's id, and the echo carries that same id.
    LaunchedEffect(pendingTail?.seq, visible.lastOrNull()?.id, settled) {
        val req = pendingTail ?: return@LaunchedEffect
        if (!settled || req.seq <= handledTailSeq) return@LaunchedEffect
        if (visible.lastOrNull()?.id != req.tailId) return@LaunchedEffect
        handledTailSeq = req.seq
        // ROUND 22: arm the glide BEFORE consuming the request.
        // Consuming nulls pendingTail — half of the round-21 glue
        // guard — and round 21 called glideToTail only afterwards,
        // whose flag rose a dispatch later still. Armed first (and
        // raised synchronously inside glideToTail), the guard now
        // never dips between the send tap and the landed glide.
        glideToTail(nearBottom)
        AppState.consumeTailScroll(threadId, req.seq)
    }
    // ROUND 24 — effect auto-play lives in the list items now (see the
    // LaunchedEffect inside itemsIndexed): first DISPLAY on this
    // device is the trigger, for the sender at send and for the
    // recipient whenever the message first renders — the round-22
    // tail-only trigger + history priming that used to sit here are
    // replaced by the durable played logs. Gift has no overlay: its
    // wrap is the effect, opened by tap.
    // ROUND 22 (insta port) — entrance-pop marking, the same
    // prime-then-fresh discipline as the effect auto-play above: the
    // first tail this screen sees only primes the seen-set (opening a
    // chat never pops its history); a LATER tail whose id is outside
    // the set is fresh and pops exactly once. Keyed on the tail's
    // identity + the size, so loadOlder (head changes, tail stays)
    // and metadata-only snapshots (neither changes) can never mark
    // anything. Own sends are covered too — the optimistic bubble is
    // a fresh tail id, and its server echo carries the SAME id (the
    // id is client-chosen), so the echo never re-pops.
    LaunchedEffect(visible.lastOrNull()?.id, visible.size) {
        if (visible.isEmpty()) return@LaunchedEffect
        val tailId = visible.last().id
        if (!popPrimed) {
            popSeenIds += visible.map { it.id }
            popPrimed = true
            return@LaunchedEffect
        }
        val fresh = tailId.takeIf { it !in popSeenIds }
        popSeenIds += visible.map { it.id }
        if (fresh != null) {
            popFresh = popFresh + fresh
            // expiry rides the screen scope, not this effect's: a
            // restart (the next arrival) must not cancel the removal
            // and leave a stale id poppable forever.
            scope.launch { delay(1_500); popFresh = popFresh - fresh }
        }
    }
    // ROUND 15: the follow trigger is the TAIL item's identity, not
    // max(tsMs). The ts trigger misfired around sends twice over: an
    // optimistic bubble's device-clock ts could sit BELOW the running
    // max (no follow on send), then its server echo pushed the max up
    // and fired a LATE re-anchor scroll — the second half of the send
    // jump, chevron flash included. Identity never changes across
    // optimistic -> echo (same client-chosen doc id), so a send now
    // triggers exactly one gentle follow and the echo triggers none.
    var prevTailId by remember(threadId) { mutableStateOf<String?>(null) }
    LaunchedEffect(visible.lastOrNull()?.id, peerTyping) {
        val tail = visible.lastOrNull()
        if (settled && tail != null && tail.id != prevTailId) {
            // ROUND 18/19: incoming tails glide ONLY from the bottom —
            // no instant snaps, no yank from history. Own sends don't
            // rely on this path (they carry their own request above);
            // when one does land here, glideToTail coalesces both into
            // the same single animation. Metadata-only snapshot changes
            // (seen / reaction) never change the tail id, so they can
            // never re-trigger a glide or a re-sort jump.
            if (nearBottom) glideToTail(nearBottom)
        } else if (settled && peerTyping && nearBottom && tail != null) {
            // the typing bubble appended below the fold while following
            glideToTail(nearBottom)
        }
        if (tail != null) prevTailId = tail.id
    }
    // ROUND 20 — IME GLUE. The root applies imePadding, so opening or
    // closing the keyboard RESIZES this screen and the list's viewport
    // height changes step by step along the IME animation. LazyColumn
    // anchors at the top, so a shrink left the tail cut off behind the
    // composer until some later event scrolled — the owner's "newest
    // message hides under the keyboard". Now: whenever the viewport
    // height changes and the PREVIOUS layout had the user glued to the
    // tail, re-anchor to the tail instantly; repeated emissions during
    // the IME animation simply re-pin the same tail (no bounce, no
    // animation fighting the IME's own). A scrolled-up reader's previous
    // layout is never at the tail, so they are never yanked. Non-IME
    // height changes (pinned strip appearing) behave the same way.
    val latestVisible by rememberUpdatedState(visible)
    val latestPeerTyping by rememberUpdatedState(peerTyping)
    val latestGlideInFlight by rememberUpdatedState(glideInFlight)
    val latestGlueMutedUntil by rememberUpdatedState(glueMutedUntil)
    val latestSendPending by rememberUpdatedState(
        (pendingTail?.seq ?: 0L) > handledTailSeq,
    )
    LaunchedEffect(threadId) {
        var prevHeight = -1
        var wasAtTail = false
        snapshotFlow { listState.layoutInfo.viewportSize.height to nearBottom }.collect { (h, atTail) ->
            // ROUND 21: the glue NEVER runs while the delta glide owns
            // the list — a glide in flight, or a send whose tail
            // request is still unconsumed, mutes it entirely. A send
            // shrinks the composer (reply-strip collapse, multi-line
            // field returning to one line), which steps the viewport
            // height exactly like an IME resize; in rounds 19/20 those
            // steps made the glue instant-pin against the animated
            // glide — the pop/lurch in the owner's recording. Once the
            // glide has landed, the list is already at the true bottom,
            // so a late glue pin is a no-op anyway.
            // ROUND 22: the mute is now three-layered and hole-free —
            // an unconsumed send request (covers the tap → effect
            // window), glideInFlight (raised SYNCHRONOUSLY by
            // glideToTail, so it covers the coalesce delay, the layout
            // wait, the animation AND the settle pass), and the
            // [glueMutedUntil] deadline (150 ms past the job's end, so
            // the strip collapse's trailing steps land in silence).
            // Every height step OUTSIDE a glide still pins instantly —
            // that per-step pinning is the keyboard invariant: while
            // the user types, receives, or the IME opens/closes with
            // no glide running, the tail is re-anchored on EVERY
            // viewport step and can never slip under the keyboard.
            val glideBusy = latestGlideInFlight || latestSendPending ||
                System.currentTimeMillis() < latestGlueMutedUntil
            if (prevHeight >= 0 && h != prevHeight && wasAtTail && !glideBusy) {
                val msgs = latestVisible
                if (msgs.isNotEmpty()) {
                    runCatching {
                        listState.scrollToItem(msgs.lastIndex + if (latestPeerTyping) 1 else 0)
                    }
                }
            }
            prevHeight = h
            wasAtTail = atTail
        }
    }
    // pagination: approaching the oldest loaded message fetches the next
    // older page (the engine no-ops when there is none / one is in flight)
    LaunchedEffect(threadId, thread.hasOlder) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
            if (thread.hasOlder && idx <= 2) AppState.repo.loadOlder(threadId)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // ROUND 57 (Fix 3) — TOUCH SHIELD: the culprit behind "Memories
        // opens by itself from inside a chat". There is NO navigation
        // to Memories anywhere in the chat (the tree's only
        // `go(Screen.Moments)` call site is the Messages list's
        // "Memories" CardRow in Home.kt) — but HomeScreen stays
        // COMPOSED underneath this chat overlay in TikuRoot, and this
        // root was a plain background Box: a tap landing on a dead
        // zone of the chat (the gaps around the header pill, the
        // composer's margins, the list's top padding) was consumed by
        // NOTHING here and fell through to the Messages list beneath,
        // whose Memories row sits exactly in that upper region. This
        // first child consumes every pointer event that reaches it;
        // everything above it (list, header, composer) still gets its
        // events first, so chat interaction is unchanged — taps simply
        // can no longer escape the chat.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        )
        // ROUND 26: the theme wallpaper — full-bleed behind the message
        // list, under the header and composer (both draw later in this
        // Box). The asset is the owner's image exactly as supplied,
        // decoded once per theme. Default (chatTheme == null) draws
        // nothing: the list still sits on the same black as today.
        if (ChatThemes.isCustom(thread.themeId)) {
            // ROUND 35: the custom gallery wallpaper — the engine
            // publishes the decrypted bitmap here when the asset is
            // cached/fetched; until then no wallpaper (Default black).
            val customWallpapers by CustomThemeStore.bitmaps.collectAsState()
            val customWallpaper = customWallpapers[thread.id]
            if (customWallpaper != null) {
                Image(
                    customWallpaper.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            }
        } else if (chatTheme != null) {
            val wallpaper = remember(chatTheme.id) {
                runCatching {
                    ctx.assets.open("themes/${chatTheme.asset}")
                        .use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (wallpaper != null) {
                Image(
                    wallpaper.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            }
        }
        // ---- message list (the haze source the overlays blur) ----
        // ROUND 20: bounded above the composer by its MEASURED height
        // (see composerHeightPx) — the list's visible bottom IS the
        // composer's top edge, so the newest message is always fully
        // visible above the floating card, exactly like IG.
        Box(
            Modifier.fillMaxSize().padding(bottom = composerHeight)
                .then(if (haze != null) Modifier.hazeSource(haze) else Modifier),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("message_list"),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    // top stays generous: the glass header still floats
                    // over the list's first screenful (designed look).
                    top = if (thread.pinnedMsgIds.isNotEmpty()) 148.dp else 96.dp,
                    // bottom is now just breathing room — the composer's
                    // bound is the Box padding above, not a guess here.
                    bottom = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(visible, key = { _, m -> m.id }) { idx, m ->
                    // ROUND 18: no animateItem() wrapper — its placement
                    // animation fought the follow glide on every arrival
                    // (a visible pop riding on top of the scroll).
                    // ROUND 20: the bubbles' own spring entrance is gone
                    // too — a message simply appears, the ONE coalesced
                    // glide shifts the list, and the stable keys keep
                    // prepend/echo positions exact.
                    // ROUND 24 — first-display effect playback: the
                    // FIRST time this message renders for THIS ACCOUNT
                    // (ROUND 28: the played logs are per-account) its
                    // effect plays (fx kinds anchored to the
                    // bubble; lone-emoji effects screen-wide) and the
                    // durable log marks it, so scroll-backs,
                    // recompositions, the server echo (same
                    // client-chosen id) and later visits never replay
                    // it. A row composed while scrolling into view
                    // counts as displayed — that IS first sight.
                    LaunchedEffect(m.id) {
                        val k = m.fx
                        if (k == "fire" || k == "confetti" || k == "hearts") {
                            if (!fxPlayedLog.seen(m.id)) {
                                fxPlayedLog.mark(m.id)
                                playFx(k, m.id, anchored = true)
                            }
                        } else if (k == null) {
                            // ROUND 25: the owner's design emojis
                            // route to EmojiFxOverlay; the Round-24
                            // families keep RdFxOverlay. Both share
                            // this ONE durable played log, so a message
                            // auto-plays exactly once per account,
                            // whichever engine renders it.
                            // ROUND 66: the owner's Chat Emoji Effects
                            // file routes FIRST (EmojiFx2Overlay) —
                            // except 😘, which has no Fx2 entry by
                            // construction and falls through to the
                            // Round-25 design kind 1 exactly as before.
                            val fk = singleEmojiFx2Kind(m)
                            val dk = if (fk == null) singleEmojiDesignKind(m) else null
                            val ek = if (fk == null && dk == null) singleEmojiOverlayKind(m) else null
                            if ((fk != null || dk != null || ek != null) && !emojiFxPlayedLog.seen(m.id)) {
                                emojiFxPlayedLog.mark(m.id)
                                if (fk != null) playEmojiFx2(fk, m.id)
                                else if (dk != null) playEmojiFx(dk, m.id)
                                else playFx(ek!!, m.id)
                            }
                        }
                    }
                    Column {
                        val pin = dividerPin
                        if (pin != null && m.id == pin.first) {
                            RdUnreadDivider(pin.second)
                            Spacer(Modifier.height(4.dp))
                        }
                        when {
                            // ROUND 19: undecryptable history renders
                            // the honest placeholder — before every
                            // other branch, so no kind (not even CALL)
                            // can render it as an empty bubble/pill.
                            m.undecryptable -> RdUndecryptableRow(m, person) {
                                menuMsg = m
                            }
                            m.deletedForEveryone -> {
                                // ROUND 30 — OWNER VAULT: on the owner's
                                // phone, an incoming tombstone this device
                                // had loaded renders from the local vault
                                // (entry() self-gates on isOwner and on a
                                // copy actually existing). Everyone and
                                // everything else: the plain tombstone,
                                // exactly as before.
                                val ve = if (!m.me) OwnerVault.entry(threadId, m.id) else null
                                if (ve != null) {
                                    RdVaultDeletedRow(
                                        m = m, entry = ve, person = person,
                                        theme = chatTheme, textSp = textSp,
                                        playing = playingId == m.id,
                                        onMenu = { menuMsg = m },
                                        onPlayToggle = { vm ->
                                            val path = vm.localPath
                                            if (path != null && !File(path).exists()) {
                                                // ROUND 34: never feed the
                                                // player a dead path (its
                                                // prepare() throws on a
                                                // missing file and the tap
                                                // died silently). A vault
                                                // file can't be re-fetched
                                                // — say so plainly.
                                                AppState.toast("Couldn't play — the file is gone")
                                            } else if (path != null) {
                                                if (playingId == vm.id) {
                                                    player.stop(); playingId = null
                                                } else {
                                                    player.toggle(vm.id, File(path)) { playingId = null }
                                                    playingId = vm.id
                                                }
                                            }
                                        },
                                        onOpenImage = { vm -> viewerMsg = vm },
                                        onOpenVideo = { vm -> videoViewerMsg = vm },
                                    )
                                } else {
                                    RdTombstoneRow(m, person) {
                                        menuMsg = m
                                    }
                                }
                            }
                            m.kind == MsgKind.CALL -> CallEventPill(m)
                            // ROUND 41: a game message renders as its
                            // own cream game card (never the AI bubble,
                            // never code) — see RdGameCard.
                            m.game -> RdGameCard(
                                m = m, person = person,
                                onMenu = { menuMsg = m },
                                onLoad = {
                                    if (!Config.PREVIEW_UI_ONLY) {
                                        AppState.repo.fetchMedia(threadId, m.id)
                                        AppState.toast("Loading game…")
                                    }
                                },
                                onPlay = { gamePlayerMsg = m },
                                onRetry = { AppState.repo.retrySend(threadId, m.id) },
                            )
                            // ROUND 60: Nikah ceremony cards (invite /
                            // keepsake) render as centred ceremony cards,
                            // never as bubbles — the doc is a plain text
                            // message with a fallback line for old builds
                            // (see TikuMessage.nikah).
                            m.nikah == "invite" -> NikahInviteRow(
                                m = m, session = nikahSession,
                                peerName = person.name,
                                onOpen = { nikahOpen = true },
                            )
                            m.nikah == "keepsake" -> NikahKeepsakeRow(
                                m = m,
                                onView = { nikahCertMsg = m },
                            )
                            // ROUND 37: an AI (Tika) message renders
                            // as Tika's own left-aligned bubble with
                            // its label — whichever member's client
                            // posted it (see RdAiBubbleRow).
                            m.ai -> RdAiBubbleRow(
                                m = m, theme = chatTheme, textSp = textSp,
                                onMenu = { menuMsg = m },
                                onOpenImage = { vm -> viewerMsg = vm },
                            )
                            else -> RdBubbleRow(
                                i = idx,
                                m = m,
                                person = person,
                                acc = acc,
                                theme = chatTheme,
                                textSp = textSp,
                                playing = playingId == m.id,
                                onPlayToggle = {
                                    val path = m.localPath
                                    if (path != null && !File(path).exists()) {
                                        // ROUND 34: the held path outlived
                                        // its cache file (OS purge) — the
                                        // bubble still claims "loaded", so
                                        // reload the payload instead of
                                        // feeding the player a dead file.
                                        // fetchMedia covers voice too (its
                                        // forceLoad decrypt rewrites the
                                        // cache file and swaps it in).
                                        if (!Config.PREVIEW_UI_ONLY) {
                                            AppState.repo.fetchMedia(threadId, m.id)
                                            AppState.toast("Reloading…")
                                        }
                                    } else if (path != null) {
                                        if (playingId == m.id) { player.stop(); playingId = null } else {
                                            player.toggle(m.id, File(path)) { playingId = null }
                                            playingId = m.id
                                        }
                                    } else if (!Config.PREVIEW_UI_ONLY && m.kind == MsgKind.AUDIO) {
                                        // ROUND 15: an audio FILE with no
                                        // payload on this device yet — the
                                        // play tap IS the load tap.
                                        AppState.repo.fetchMedia(threadId, m.id)
                                        AppState.toast("Loading audio…")
                                    } else {
                                        // preview/design simulation: 4s "playing" state
                                        scope.launch {
                                            playingId = if (playingId == m.id) null else m.id
                                            delay(4000); if (playingId == m.id) playingId = null
                                        }
                                    }
                                },
                                onReply = { editingMsg = null; replyTarget = m },
                                onReact = { e -> react(m, e) },
                                onMenu = { menuMsg = m },
                                onMediaTap = {
                                    // ROUND 31 — a view-once tap is the
                                    // ONLY door to the media, so this is
                                    // where the allowance is spent: the
                                    // tap that OPENS the viewer consumes
                                    // one view (a video's load tap does
                                    // not — the viewing tap after it
                                    // does). Expired → a plain "Opened"
                                    // toast, the viewer never opens.
                                    // The owner's unlimited override
                                    // skips counting entirely. When the
                                    // last view is spent, voDone goes to
                                    // the doc so the sender sees "Opened"
                                    // (the current viewing still plays
                                    // out, like WhatsApp).
                                    val voGated = !m.me && m.vo != null && !VoStore.ownerBypass(ctx)
                                    fun voConsumeAndOpen(open: () -> Unit) {
                                        if (!voGated) { open(); return }
                                        if (VoStore.expired(ctx, m)) {
                                            AppState.toast("Opened")
                                            return
                                        }
                                        val used = VoStore.consumeOpen(ctx, m.id)
                                        if (used >= (m.vo ?: 0)) AppState.repo.markVoDone(threadId, m.id)
                                        open()
                                    }
                                    when (m.kind) {
                                        MsgKind.IMAGE -> voConsumeAndOpen { viewerMsg = m }
                                        MsgKind.VIDEO -> {
                                            if (m.localPath != null) voConsumeAndOpen { videoViewerMsg = m }
                                            else if (voGated && VoStore.expired(ctx, m)) {
                                                AppState.toast("Opened")
                                            } else {
                                                AppState.repo.fetchMedia(threadId, m.id)
                                                AppState.toast("Loading video…")
                                            }
                                        }
                                        else -> Unit
                                    }
                                },
                                onRetry = { AppState.repo.retrySend(threadId, m.id) },
                                // ROUND 22 (insta port): pop only while
                                // this tail arrival is inside its window
                                animateIn = m.id in popFresh,
                                // ROUND 22: gift seal state + effect taps
                                fxOpened = m.id in openedGifts,
                                onFxTap = { msg ->
                                    when (msg.fx) {
                                        "gift" -> openGift(msg.id)
                                        "fire", "confetti", "hearts" -> playFx(msg.fx!!, msg.id, anchored = true)
                                        // ROUND 24 + 25 + 66: a lone-emoji
                                        // message replays its text-derived
                                        // effect — the owner's Chat Emoji
                                        // Effects file first (😘 has no
                                        // entry there and falls through),
                                        // the Round-25 design kinds next,
                                        // Round-24 families last.
                                        null -> {
                                            val fk = singleEmojiFx2Kind(msg)
                                            if (fk != null) playEmojiFx2(fk, msg.id)
                                            else {
                                                val dk = singleEmojiDesignKind(msg)
                                                if (dk != null) playEmojiFx(dk, msg.id)
                                                else singleEmojiOverlayKind(msg)?.let { playFx(it, msg.id) }
                                            }
                                        }
                                    }
                                },
                                // ROUND 24: bubble anchor for fx playback
                                onBubblePositioned = { a -> fxAnchorMap[m.id] = a },
                            )
                        }
                        // ROUND 18 — "Seen" / "Sent" caption (IG-style)
                        // under my LAST own message only. Seen = the
                        // partner's client has marked it read (seenBy);
                        // Sent = delivered to the server, not yet read.
                        // ROUND 34 — a SENDING own message now shows a
                        // quiet "Sending…" in this SAME caption slot
                        // (IG-style, the owner's ask — on a slow network
                        // the wait is finally visible). Because the slot
                        // is occupied from the send's first frame, the
                        // echo's flip to "Sent"/"Seen" changes only the
                        // word, never the item's height — the caption
                        // used to POP IN under the bubble at echo time,
                        // one of the send-bounce sources. Seen/Sent keep
                        // their exact round-18 gating (last own message
                        // + receipts setting); Sending… is a send state,
                        // not a receipt, so it shows under ANY own
                        // message still in flight. FAILED keeps its own
                        // red tap-to-retry row inside the bubble column.
                        if (m.me && (m.status == SendStatus.SENDING || (showReceipts && m.id == lastOwnId))) {
                            val caption = when {
                                m.status == SendStatus.SENDING -> "Sending…"
                                m.seenByPeer -> "Seen"
                                m.status == SendStatus.SENT -> "Sent"
                                else -> null
                            }
                            if (caption != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    // ROUND 26: on a theme the caption sits
                                    // on the wallpaper — each theme carries
                                    // a shade picked for it (Themes.kt).
                                    Text(caption, fontSize = 11.5.sp, color = chatTheme?.caption ?: RdSub, fontFamily = PoppinsFamily)
                                }
                            }
                        }
                    }
                }
                if (peerTyping) {
                    item(key = "typing") { RdTypingBubble(person, theme = chatTheme) }
                }
                // ROUND 37: the transient Tika indicator — composed
                // only while THIS phone's ask is in flight (local
                // state, never a doc or a typing stamp).
                val aiLabel = aiThinking
                if (aiLabel != null) {
                    item(key = "ai_thinking") { RdAiThinkingBubble(aiLabel, theme = chatTheme) }
                }
            }
        }

        // ---- jump-to-latest pill (build 47 behaviour, redesign colours) ----
        AnimatedVisibility(
            visible = !nearBottom,
            enter = fadeIn(tween(200, easing = RdEase)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200, easing = RdEase)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = composerHeight + 14.dp),
        ) {
            Box(
                Modifier.testTag("jump_to_latest").size(40.dp).clip(CircleShape).background(RdCard)
                    .border(1.dp, Color.White.copy(0.12f), CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        // ROUND 19: the pill rides the same single
                        // coalesced glide as every other tail follow.
                        glideToTail(nearBottom)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp), tint = Color.White)
            }
        }

        // ---- top overlay: ChatTopBar + pinned strip ----
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            RdChatTopBar(
                h = haze,
                title = thread.displayName,
                status = statusText,
                person = person,
                onBack = { AppState.go(Screen.Home) },
                onInfo = { AppState.go(Screen.ChatDetails(threadId)) },
                onCall = { startCall(threadId, false) },
                onVideoCall = { startCall(threadId, true) },
                onPhotoClick = { showPhoto = true },
            )
            if (thread.pinnedMsgIds.isNotEmpty()) {
                val pinId = thread.pinnedMsgIds.last()
                val pinnedMsg = thread.messages.firstOrNull { it.id == pinId }
                val pinPreview = when {
                    pinnedMsg?.undecryptable == true -> "This message can't be opened on this phone"
                    // ROUND 22: a pinned gift previews as a gift — the
                    // strip must not leak the sealed text either.
                    pinnedMsg?.fx == "gift" -> "Gift message"
                    else -> when (pinnedMsg?.kind) {
                    MsgKind.IMAGE -> "Photo"
                    MsgKind.VOICE -> "Voice message"
                    MsgKind.VIDEO -> "Video"
                    MsgKind.AUDIO -> "Audio file"
                    MsgKind.CALL -> "Call"
                    else -> pinnedMsg?.text?.take(60) ?: "Pinned message"
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(999.dp)).background(RdCard)
                        .pointerInput(pinId) {
                            detectTapGestures(
                                onTap = { jumpToMessage(pinId) },
                                onLongPress = {
                                    AppState.repo.setPinnedMsg(threadId, pinId, false)
                                    AppState.toast("Message unpinned")
                                },
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PushPin, null, Modifier.size(13.dp), tint = RdRose)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Pinned · $pinPreview",
                        fontSize = 12.5.sp, color = Color.White, fontFamily = rememberChatFontFamily(),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (thread.pinnedMsgIds.size > 1) {
                        Text("${thread.pinnedMsgIds.size} pinned", fontSize = 11.5.sp, color = RdSub, fontFamily = PoppinsFamily)
                    }
                }
            }
        }

        // ---- bottom overlay: input, or the blocked bar ----
        // ROUND 20: measured on every size change (reply strip, recording
        // row, blocked bar all change its height) — the list bound above
        // tracks it exactly.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { composerHeightPx = it.height },
        ) {
            if (partnerBlocked) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
                        // ROUND 26: the blocked bar wears the composer
                        // colours under a theme (same pill language).
                        .then(
                            if (chatTheme != null) Modifier.clip(RoundedCornerShape(999.dp))
                                .background(chatTheme.composerBar)
                            else Modifier.chatGlass(haze, RoundedCornerShape(999.dp))
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "You blocked this person",
                        color = chatTheme?.composerText ?: Color.White, fontSize = 14.sp,
                        modifier = Modifier.weight(1f), fontFamily = PoppinsFamily,)
                    Text(
                        "Unblock", color = RdRose, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.rdPress { AppState.repo.unblockUser(person.uid) }.padding(6.dp), fontFamily = PoppinsFamily,)
                }
            } else {
                RdChatInput(
                    h = haze,
                    value = input,
                    onChange = {
                        input = it
                        // ROUND 22: typing presence follows the field
                        // BOTH ways — emptying the field clears my
                        // stamp right away (the old code only ever set
                        // it, and a 3 s timer raced to clear it). The
                        // engine throttles the true refreshes (≤ 1
                        // write / 3 s) and writes a clear only while a
                        // stamp is outstanding, so per-keystroke calls
                        // stay cheap.
                        AppState.repo.setTyping(threadId, it.isNotEmpty())
                    },
                    onSend = { sendCurrent() },
                    accent = chatTheme?.accent ?: acc.first,
                    theme = chatTheme,
                    replyText = replyTarget?.let { replyQuoteLabel(it) },
                    onClearReply = { replyTarget = null },
                    recording = recording,
                    recSecs = recSecs,
                    onPlus = { attachOpen = true },
                    onMic = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRdRecording(recorder) { recording = true }
                        } else {
                            // ROUND 20: the system permission sheet is an
                            // external activity — exactly like every picker
                            // and camera launch in this file, it must set
                            // expectExternalActivity first. Without it, the
                            // instant re-lock read the request as "app
                            // exit", locked the session mid-dialog and
                            // tore this composition (and the launcher's
                            // grant callback) down: the mic looked granted
                            // yet recording never started.
                            ChatSession.expectExternalActivity = true
                            askMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRecCancel = { recorder.stop(false); recording = false },
                    onRecSend = {
                        val f = recorder.stop(true)
                        recording = false
                        if (f != null) {
                            AppState.repo.sendVoice(threadId, f, recSecs.coerceAtLeast(1), recorder.normalizedAmps())
                        } else {
                            // ROUND 20: a failed capture says so — never
                            // a silent no-op "send".
                            AppState.toast("Recording failed — nothing was captured")
                        }
                    },
                    onCamera = { camChooserOpen = true },
                    // ROUND 57 (Fix 2): edit mode — the banner above
                    // the pill; ✕ cancels back to normal compose (the
                    // loaded text is discarded with the mode).
                    editing = editingMsg != null,
                    onCancelEdit = {
                        editingMsg = null
                        input = ""
                        scope.launch { AppState.repo.setTyping(threadId, false) }
                    },
                    // ROUND 22: message effects — arm state + picker
                    fxArmed = armedFx,
                    onFxClick = { fxPickerOpen = true },
                    onFxDisarm = { armedFx = null },
                )
            }
        }

        // ---- long-press menu: the file's centred glass menu ----
        AnimatedVisibility(
            visible = menuMsg != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            val mm = menuMsg
            if (mm != null) {
                Box(
                    Modifier.fillMaxSize().chatBackdrop(haze)
                        .clickable(remember { MutableInteractionSource() }, null) { menuMsg = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateEnterExit(
                            enter = slideInVertically(spring(0.8f, 380f)) { it / 10 } + scaleIn(spring(0.75f, 400f), initialScale = 0.88f) + fadeIn(tween(220)),
                            exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.95f),
                        ),
                        horizontalAlignment = if (mm.me) Alignment.End else Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // ROUND 18: a tombstone has nothing to react to —
                        // the emoji bar hides for deleted-for-everyone
                        // messages (their menu is Delete-for-me only).
                        // ROUND 19: same for undecryptable placeholders.
                        if (!mm.deletedForEveryone && !mm.undecryptable) Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(RdSheet)
                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RdBaseEmojis.forEachIndexed { k, e ->
                                Box(
                                    Modifier.rdPop(k).rdPress {
                                        react(mm, e); menuMsg = null
                                    }.padding(6.dp),
                                ) { EmojiGlyph(e, 28.dp) }
                            }
                            Box(
                                Modifier.padding(start = 4.dp).size(42.dp).clip(CircleShape)
                                    .background(Color.White.copy(0.12f))
                                    .rdPress {
                                        // ROUND 23 (owner order): "+" opens the FULL
                                        // emoji picker in react mode for this message
                                        // (the old expanded second quick row is gone —
                                        // the picker replaces it). Picking applies the
                                        // reaction through react() and closes
                                        // everything; dismissing returns to the chat.
                                        val target = mm
                                        menuMsg = null
                                        AppState.sheet {
                                            EmojiPickerSheetContent(
                                                initialTab = 0,
                                                recentEmojis = emptyList(),
                                                onClose = { AppState.closeSheet() },
                                                onSelectEmoji = { e ->
                                                    react(target, e)
                                                    AppState.closeSheet()
                                                },
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+", color = Color.White, fontSize = 24.sp)
                            }
                        }
                        if (mm.undecryptable) {
                            // ROUND 19: preview = the placeholder itself,
                            // exactly as it renders in the list.
                            Row(
                                Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(0.04f))
                                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(22.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "This message can't be opened on this phone",
                                    color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic, fontFamily = InterFamily,)
                            }
                        } else if (mm.deletedForEveryone) {
                            // Preview = the tombstone itself, exactly as
                            // it renders in the list.
                            Row(
                                Modifier.clip(RoundedCornerShape(22.dp)).background(RdCard)
                                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(22.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Block, null, Modifier.size(15.dp), tint = RdSub)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    "This message was deleted",
                                    color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic, fontFamily = InterFamily,)
                            }
                        } else if (mm.kind == MsgKind.IMAGE && mm.photo != null) {
                            Image(
                                mm.photo.asImageBitmap(), null,
                                Modifier.width(160.dp).height(190.dp).clip(RoundedCornerShape(22.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (mm.fx == "gift" && mm.id !in openedGifts) {
                            // ROUND 22 + 24: an unopened gift previews
                            // as its wrap — the same pink pill the
                            // bubble wears; the menu never shows the
                            // sealed text.
                            Box(Modifier.widthIn(max = 280.dp)) { RdGiftWrapPill() }
                        } else {
                            Text(
                                if (mm.kind == MsgKind.TEXT) mm.text else replyQuoteLabel(mm),
                                // ROUND 26: the menu preview IS the bubble —
                                // it wears the theme colours too.
                                color = if (chatTheme == null) Color.White
                                    else if (mm.me) chatTheme.ownText else chatTheme.peerText,
                                fontSize = textSp.sp, fontFamily = rememberChatFontFamily(),
                                modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                                    .background(
                                        if (chatTheme != null) SolidColor(if (mm.me) chatTheme.ownBubble else chatTheme.peerBubble)
                                        else if (mm.me) Brush.linearGradient(listOf(acc.first, acc.second)) else SolidColor(RdCard)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                            )
                        }
                        Column(
                            Modifier.width(250.dp).clip(RoundedCornerShape(26.dp)).background(RdSheet)
                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(26.dp)),
                        ) {
                          if (mm.deletedForEveryone || mm.undecryptable) {
                            // ROUND 18: a tombstone's only action —
                            // hiding it for me as well. ROUND 19: an
                            // undecryptable placeholder's only action too
                            // (no reply / copy / star / pin on content
                            // this phone cannot open).
                            RdMenuItem("Delete for me", danger = true, onClick = {
                                AppState.repo.deleteForMe(threadId, mm.id); menuMsg = null
                            }) {
                                Icon(Icons.Filled.Delete, null, tint = RdDanger, modifier = Modifier.size(22.dp))
                            }
                          } else {
                            RdMenuItem("Reply", onClick = { editingMsg = null; replyTarget = mm; menuMsg = null }) {
                                Text("↩", color = Color.White, fontSize = 20.sp)
                            }
                            // ROUND 57 (Fix 2): Edit — MY OWN text
                            // messages only (fx text included; media,
                            // games and AI messages never), at ANY age
                            // (no time cutoff, owner's order), and only
                            // once the send has landed (a SENDING doc
                            // may not exist yet; a FAILED one never
                            // will). Tapping it loads the message's
                            // current text into the composer under the
                            // "Editing message" banner; the edit itself
                            // writes only the additive eCt/eIv doc
                            // fields and shows no "edited" mark anywhere.
                            if (mm.me && mm.kind == MsgKind.TEXT && !mm.ai &&
                                mm.status == SendStatus.SENT
                            ) {
                                RdDivider1()
                                RdMenuItem("Edit", onClick = {
                                    replyTarget = null
                                    armedFx = null
                                    editingMsg = mm
                                    input = mm.text
                                    menuMsg = null
                                }) {
                                    Text("✎", color = Color.White, fontSize = 20.sp)
                                }
                            }
                            // ROUND 22: Copy is withheld for an
                            // unopened gift — copying would hand out
                            // the sealed text without opening it.
                            if (mm.kind == MsgKind.TEXT && !(mm.fx == "gift" && mm.id !in openedGifts)) {
                                RdDivider1()
                                RdMenuItem("Copy", onClick = {
                                    clipboard.setText(AnnotatedString(mm.text))
                                    AppState.toast("Copied"); menuMsg = null
                                }) { Text("⧉", color = Color.White, fontSize = 20.sp) }
                            }
                            RdDivider1()
                            RdMenuItem(if (mm.starredByMe) "Unstar" else "Star", onClick = {
                                AppState.repo.toggleStar(threadId, mm.id)
                                AppState.toast(if (mm.starredByMe) "Unstarred" else "Starred"); menuMsg = null
                            }) {
                                Icon(Icons.Filled.Star, null, tint = if (mm.starredByMe) Color(0xFFFFC857) else Color.White, modifier = Modifier.size(22.dp))
                            }
                            RdDivider1()
                            RdMenuItem(if (mm.id in thread.pinnedMsgIds) "Unpin" else "Pin", onClick = {
                                AppState.repo.setPinnedMsg(threadId, mm.id, mm.id !in thread.pinnedMsgIds)
                                menuMsg = null
                            }) {
                                Icon(Icons.Filled.PushPin, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            // Save is offered for every media kind that
                            // actually holds its payload on this device
                            // (an unloaded video/audio loads via its
                            // bubble tap first — round 15). ROUND 31: a
                            // partner's view-once message is never
                            // savable here either (the viewer rule, in
                            // the menu too) — except for the owner
                            // under his unlimited override.
                            val savable = when (mm.kind) {
                                MsgKind.IMAGE -> mm.photo != null
                                MsgKind.VOICE, MsgKind.VIDEO, MsgKind.AUDIO -> mm.localPath != null
                                else -> false
                            } && (mm.me || mm.vo == null || VoStore.ownerBypass(ctx))
                            if (savable) {
                                RdDivider1()
                                RdMenuItem("Save to device", onClick = {
                                    if (Config.PREVIEW_UI_ONLY) {
                                        AppState.toast("Saved to device")
                                    } else {
                                        val ok = MediaSaver.saveToDevice(AppState.appCtx, mm)
                                        AppState.toast(if (ok) "Saved to device" else "Couldn't save")
                                    }
                                    menuMsg = null
                                }) {
                                    Icon(Icons.Filled.SaveAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            // ROUND 18 — delete is two-option, IG-style:
                            // "Delete for me" (local hide; offered for
                            // EVERY message, mine or theirs) and, for my
                            // own messages only, "Delete for everyone"
                            // (server tombstone — both sides see it).
                            RdDivider1()
                            RdMenuItem("Delete for me", onClick = {
                                AppState.repo.deleteForMe(threadId, mm.id); menuMsg = null
                            }) {
                                Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            if (mm.me) {
                                RdDivider1()
                                RdMenuItem("Delete for everyone", danger = true, onClick = {
                                    AppState.repo.deleteForEveryone(threadId, mm.id); menuMsg = null
                                }) {
                                    Icon(Icons.Filled.Delete, null, tint = RdDanger, modifier = Modifier.size(22.dp))
                                }
                            }
                          }
                        }
                    }
                }
            }
        }

        // ---- ROUND 60: Nikah rejoin banner — floats directly above the
        // composer (whose measured height this screen already tracks)
        // while a ceremony is live and not currently open. ----
        if (nikahSession?.active == true && !nikahOpen) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = composerHeight),
            ) {
                NikahProgressBanner { nikahOpen = true }
            }
        }

        // ---- attachment sheet: the file's glass sheet ----
        // ROUND 15: Photo / Video / Audio file rows. The keyboard is
        // dismissed the moment this opens (see the attachOpen effect), so
        // the sheet always anchors to the true bottom edge.
        AnimatedVisibility(
            visible = attachOpen,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
                    .clickable(remember { MutableInteractionSource() }, null) { attachOpen = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.padding(14.dp).fillMaxWidth().animateEnterExit(
                        enter = slideInVertically(spring(0.85f, 360f)) { it } + fadeIn(),
                        exit = slideOutVertically(tween(220, easing = RdEase)) { it } + fadeOut(),
                    ).chatGlass(haze, RoundedCornerShape(28.dp), 0.1f).padding(8.dp),
                ) {
                    // ROUND 31: the view-once arm for the next photo/video
                    // picked from this sheet (see RdVoArmRow).
                    RdVoArmRow(armedVo) { armedVo = it }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            attachOpen = false
                            ChatSession.expectExternalActivity = true
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Image, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Photo", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            attachOpen = false
                            ChatSession.expectExternalActivity = true
                            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Video", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            attachOpen = false
                            ChatSession.expectExternalActivity = true
                            pickAudio.launch("audio/*")
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Audiotrack, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Audio file", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                    }
                    // ROUND 60: the Nikah entry — inside a 1:1 chat only
                    // (this sheet exists only on the 1:1 chat screen).
                    // A live ceremony re-opens; otherwise the role pick
                    // creates one and lands the invite card in the chat.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            attachOpen = false
                            if (nikahSession?.active == true) nikahOpen = true
                            else nikahRolePick = true
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE7C46A), modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Nikah", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (nikahSession?.active == true) "In progress — tap to rejoin" else "Live ceremony, together",
                            color = RdSub, fontSize = 12.sp, fontFamily = PoppinsFamily,
                        )
                    }
                }
            }
        }

        // ---- ROUND 15: camera chooser (composer camera button) ----
        // Same bottom glass sheet as attach; the field is unfocused
        // whenever the camera button exists, so the keyboard is already
        // down and this presents cleanly at the bottom edge.
        AnimatedVisibility(
            visible = camChooserOpen,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
                    .clickable(remember { MutableInteractionSource() }, null) { camChooserOpen = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.padding(14.dp).fillMaxWidth().animateEnterExit(
                        enter = slideInVertically(spring(0.85f, 360f)) { it } + fadeIn(),
                        exit = slideOutVertically(tween(220, easing = RdEase)) { it } + fadeOut(),
                    ).chatGlass(haze, RoundedCornerShape(28.dp), 0.1f).padding(8.dp),
                ) {
                    // ROUND 31: the view-once arm for the next capture
                    // from this chooser (see RdVoArmRow).
                    RdVoArmRow(armedVo) { armedVo = it }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            camChooserOpen = false
                            launchCameraPhoto()
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Take photo", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            camChooserOpen = false
                            launchCameraVideo()
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Record video", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                    }
                }
            }
        }

        // ---- ROUND 22 + 24: message-effects picker ----
        // The attach sheet's glass language: four effect rows (a live
        // mini preview of the typed text inside the effect + name +
        // one-line blurb, a rose tick on the armed one) over a
        // "No effect" row. Choosing arms the effect for the next send
        // and closes; the composer chip is the visible armed state.
        AnimatedVisibility(
            visible = fxPickerOpen,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
                    .clickable(remember { MutableInteractionSource() }, null) { fxPickerOpen = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.padding(14.dp).fillMaxWidth().animateEnterExit(
                        enter = slideInVertically(spring(0.85f, 360f)) { it } + fadeIn(),
                        exit = slideOutVertically(tween(220, easing = RdEase)) { it } + fadeOut(),
                    ).chatGlass(haze, RoundedCornerShape(28.dp), 0.1f).padding(8.dp),
                ) {
                    Text(
                        "Send with effect", color = RdSub, fontSize = 12.5.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), fontFamily = InterTight,)
                    // ROUND 24: every row previews the CURRENT typed
                    // text rendered inside the effect — a mini dressed
                    // bubble (gift: the sealed mini wrap, text hidden),
                    // the way Instagram's tray shows your own words in
                    // each effect before you choose.
                    val fxPreviewText = input.trim().ifEmpty { "Hello" }
                        .let { if (it.length > 14) it.take(14) + "…" else it }
                    RdMessageFx.forEach { def ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().rdPress {
                                armedFx = def.kind
                                fxPickerOpen = false
                            }.padding(16.dp),
                        ) {
                            Box(
                                Modifier.width(128.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // ROUND 26: the tray previews MY bubble —
                                // under a theme it previews in the theme's
                                // own-bubble colour (solid pair).
                                RdFxMiniPreview(
                                    def.kind, fxPreviewText,
                                    if (chatTheme != null) chatTheme.ownBubble to chatTheme.ownBubble else acc,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(def.name, color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                                Text(def.blurb, color = RdSub, fontSize = 12.sp, fontFamily = PoppinsFamily)
                            }
                            if (armedFx == def.kind) {
                                Text("✓", color = RdRose, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            armedFx = null
                            fxPickerOpen = false
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Block, null, tint = RdSub, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("No effect", color = Color.White, fontSize = 15.sp, fontFamily = PoppinsFamily)
                            Text("Send it plain", color = RdSub, fontSize = 12.sp, fontFamily = PoppinsFamily)
                        }
                        if (armedFx == null) {
                            Text("✓", color = RdRose, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ---- full-screen image viewer ----
        viewerMsg?.let { vm ->
            ImageViewerOverlay(vm, saveAllowed = voSaveAllowed(vm)) { viewerMsg = null }
        }

        // ---- full-screen video player ----
        videoViewerMsg?.let { vm ->
            VideoViewerOverlay(
                vm, saveAllowed = voSaveAllowed(vm),
                onUnavailable = {
                    // ROUND 34: the bubble believed the video was loaded
                    // but its cache file is gone — re-fetch it (the new
                    // tap then plays). A VAULT copy is device-only: its
                    // message is tombstoned server-side, so there is
                    // nothing to re-fetch — just say so.
                    if (vm.localPath?.contains("owner_vault") == true) {
                        AppState.toast("Couldn't play this video")
                    } else if (!Config.PREVIEW_UI_ONLY) {
                        AppState.repo.fetchMedia(threadId, vm.id)
                        AppState.toast("Reloading video…")
                    }
                },
            ) { videoViewerMsg = null }
        }

        // ---- ROUND 22 + 24: effect playback overlay ----
        // Topmost in the chat root. RdFxOverlay has no pointer input of
        // its own, so taps fall through to the list beneath while it
        // runs. The clear is key-checked: a replay started mid-flight
        // replaces the key, and the old playback's finally must not
        // clear the new one. fxPlayAnchor anchors fx hearts / fire to
        // their bubble; everything else plays screen-wide (null).
        fxPlay?.let { (kind, playKey) ->
            RdFxOverlay(kind, playKey, fxPlayAnchor) {
                if (fxPlay?.second == playKey) fxPlay = null
            }
        }

        // ---- ROUND 25: the owner's EmojiFx design overlay ----
        // Same host position and key-checked clear as RdFxOverlay
        // above. key(playKey) gives every playback a FRESH overlay
        // (fresh Fx engine), so a tap replay mid-flight restarts the
        // design cleanly instead of resuming a half-run show. The
        // overlay is transparent and carries no pointer input.
        emojiFxPlay?.let { (kindIdx, playKey) ->
            key(playKey) {
                EmojiFxOverlay(kindIdx) {
                    if (emojiFxPlay?.second == playKey) emojiFxPlay = null
                }
            }
        }

        // ---- ROUND 66: the owner's Chat Emoji Effects overlay ----
        // Same host position and key-checked clear as the Round-25
        // overlay above. key(playKey) gives every playback a FRESH
        // overlay (fresh Fx2 engine), so a tap replay mid-flight
        // restarts the show cleanly. The overlay is transparent and
        // carries no pointer input.
        emojiFx2Play?.let { (specIdx, playKey) ->
            key(playKey) {
                EmojiFx2Overlay(specIdx) {
                    if (emojiFx2Play?.second == playKey) emojiFx2Play = null
                }
            }
        }

        // ---- ROUND 68 (WS9): full profile picture (topmost) ----
        if (showPhoto) RdIProfilePhotoViewer(person) { showPhoto = false }

        // ---- ROUND 41: full-screen game player (topmost) ----
        gamePlayerMsg?.let { gm ->
            GamePlayerOverlay(
                chatId = threadId, m = gm,
                myUid = if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid(),
                myName = if (Config.PREVIEW_UI_ONLY) "You"
                    else FirebaseEngine.myPerson.name.takeIf { it.isNotBlank() && it != "You" }
                        ?: AppState.myUsername.ifEmpty { "You" },
                peerName = thread.displayName,
                onClose = { gamePlayerMsg = null },
            )
        }

        // ---- ROUND 60: Nikah overlays (topmost) ----
        if (nikahRolePick) {
            NikahStarterSheet(
                myName = myDisplayName, peerName = thread.displayName,
                onPick = { role ->
                    nikahRolePick = false
                    NikahStore.create(threadId, person.uid, role, myDisplayName)
                    nikahOpen = true
                },
                onDismiss = { nikahRolePick = false },
            )
        }
        if (nikahOpen) {
            NikahCeremonyOverlay(
                chatId = threadId, peerUid = person.uid,
                myName = myDisplayName, peerName = thread.displayName,
                onClose = { nikahOpen = false },
            )
        }
        nikahCertMsg?.nikahKeepsake?.let { k ->
            NikahCertificateOverlay(k) { nikahCertMsg = null }
        }
    }
}
