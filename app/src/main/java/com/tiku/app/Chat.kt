package com.tiku.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
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

/** One deterministic overlay particle. The whole set is built ONCE per
 *  playback from a fixed seed (remembered on the play key) and every
 *  frame is a pure function of the progress value — the animation
 *  allocates nothing per frame beyond the fixed set (~40 particles). */
private data class FxParticle(
    val xFrac: Float, val delay: Float, val span: Float,
    val sizeDp: Float, val swayAmp: Float, val swayFreq: Float,
    val spin: Float, val variant: Int,
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
        )
    }
}

/** The one-shot full-chat effect overlay (fire / confetti / hearts —
 *  gift has no overlay; its wrap IS the effect). [playKey] identifies
 *  one playback: the progress Animatable runs 0→1 over ~2.6 s exactly
 *  once per key, then [onDone] fires (the caller clears the playback
 *  only if the key is still current, so a replay started mid-flight is
 *  never cancelled by its predecessor's finally). The overlay carries
 *  NO pointer input — taps fall through to the chat beneath it. */
@Composable
private fun RdFxOverlay(kind: String, playKey: String, onDone: () -> Unit) {
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
        fxParticles(kind, when (kind) { "confetti" -> 42; "hearts" -> 24; else -> 30 })
    }
    val palette = remember {
        listOf(RdRose, Color(0xFFFFC857), Color.White, Color(0xFFFF8A5C), Color(0xFF8E7CFF), Color(0xFF4FD1C5))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val p = progress.value
        if (kind == "fire") {
            // a warm glow pooled at the bottom while the flames run
            Box(
                Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = (sin(p * 3.14159f).coerceIn(0f, 1f)) * 0.30f }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFFFF6A00).copy(0.55f), Color(0xFFFF3D00).copy(0.8f)),
                        ),
                    ),
            )
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
                "hearts" -> {
                    val x = pt.xFrac * w + sway
                    val y = (h + 30f) - local * (h + 70f)
                    Box(
                        Modifier.offset(x.dp, y.dp)
                            .graphicsLayer {
                                alpha = fadeIn * (1f - ((local - 0.6f) / 0.4f).coerceIn(0f, 1f))
                                val sc = 0.7f + 0.4f * local
                                scaleX = sc; scaleY = sc
                            },
                    ) { EmojiGlyph(if (pt.variant % 3 == 0) "❤️" else "💖", pt.sizeDp.dp) }
                }
                else -> { // fire — flames rise through the lower chat, flickering
                    val x = pt.xFrac * w + sway * 0.5f
                    val y = (h - pt.sizeDp) - local * (h * 0.72f)
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
                fontSize = (size * 0.38f).sp, fontWeight = FontWeight.SemiBold,
            )
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
                fontSize = 12.sp, color = RdSub,
            )
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
                color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic,
            )
        }
    }
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
                color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic,
            )
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
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RdRose,
            )
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
fun ImageViewerOverlay(m: TikuMessage, onClose: () -> Unit) {
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
                Text("Save to device", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
fun VideoViewerOverlay(m: TikuMessage, onClose: () -> Unit) {
    val path = m.localPath
    if (path == null) {
        LaunchedEffect(Unit) { onClose() }
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
                Text("Save to device", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ================= message bubbles (the file's BubbleRow) =================
private fun Modifier.rdDrawLeftBar(): Modifier = this.drawBehind {
    drawRect(Color.White.copy(0.55f), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
}

/** ROUND 22 — the sealed gift, rendered in place of the bubble's
 *  content: one big gift glyph + the tap caption, centred in the same
 *  padded footprint a short text bubble takes. Shared by the bubble and
 *  the long-press menu's preview, so the wrap looks identical on both. */
@Composable
private fun RdGiftWrap() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        EmojiGlyph("🎁", 46.dp)
        Spacer(Modifier.height(6.dp))
        Text("Tap to open", color = Color.White.copy(0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    /** ROUND 22 (insta port): true only while this row is a FRESH tail
     *  arrival inside its pop window (the caller tracks first-seen
     *  ids — history and loadOlder rows always pass false). Drives the
     *  graphicsLayer entrance pop; see the layer itself for why it can
     *  never disturb layout or the follow glide. */
    animateIn: Boolean = false,
) {
    val scope = rememberCoroutineScope()
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
    val emojiFont = rememberEmojiFontFamily()
    var heartKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(heartKey) { if (heartKey > 0) { heart.snapTo(0f); heart.animateTo(1f, tween(800, easing = LinearEasing)) } }
    val media = m.kind == MsgKind.IMAGE || m.kind == MsgKind.VIDEO
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
                                    // ROUND 22: an effect message's tap
                                    // belongs to the effect — gift
                                    // unwraps, fire / confetti / hearts
                                    // replay (the caller decides; an
                                    // already-open gift falls through to
                                    // nothing). Effect kinds only exist
                                    // on TEXT, so media routing below is
                                    // unreachable for them.
                                    if (m.fx != null) onFxTap(m)
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
                    Column(
                        Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                            .background(if (media) SolidColor(RdCard) else if (m.me) Brush.linearGradient(listOf(acc.first, acc.second)) else SolidColor(RdCard))
                            .then(if (media) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 11.dp)),
                    ) {
                        when (m.kind) {
                            MsgKind.IMAGE -> RdImageBubble(m)
                            MsgKind.VIDEO -> RdVideoBubble(m)
                            MsgKind.VOICE, MsgKind.AUDIO -> RdVoiceBubble(m, playing, onPlayToggle)
                            else -> {
                                if (m.fx == "gift" && !fxOpened) {
                                    // ROUND 22: the sealed gift — the
                                    // text is NOT composed at all in
                                    // this branch, so nothing on screen
                                    // (or in an accessibility read of
                                    // the bubble) can leak it.
                                    RdGiftWrap()
                                } else {
                                    if (m.fx == "gift") {
                                        // opened gift: a normal bubble,
                                        // keeping a tiny gift marker.
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 5.dp),
                                        ) {
                                            EmojiGlyph("🎁", 13.dp)
                                            Spacer(Modifier.width(5.dp))
                                            Text("Gift", color = Color.White.copy(0.7f), fontSize = 10.5.sp)
                                        }
                                    }
                                    if (m.replyQuote != null) {
                                        Text(
                                            m.replyQuote, color = Color.White.copy(0.75f), fontSize = 12.sp, maxLines = 1,
                                            fontFamily = emojiFont,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(bottom = 6.dp).rdDrawLeftBar().padding(start = 8.dp),
                                        )
                                    }
                                    Text(m.text, color = Color.White, fontSize = textSp.sp, fontFamily = emojiFont)
                                }
                            }
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
                    ) { onRetry() },
                )
            }
        }
    }
}

/** The file's fixed 220×260 image box, fed by the real decrypted bitmap. */
@Composable
private fun RdImageBubble(m: TikuMessage) {
    val bmp = m.photo
    Box(Modifier.size(220.dp, 260.dp).background(RdCard)) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (m.text.isNotEmpty()) {
            Text(
                m.text, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
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
            Text(fmtDurSec(m.voiceDurSec), color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    !loaded && m.mediaBytes > 0 -> "Tap to load · ${fmtMediaSize(m.mediaBytes)}"
                    !loaded -> "Tap to load"
                    m.mediaBytes > 0 -> fmtMediaSize(m.mediaBytes)
                    else -> ""
                },
                color = Color.White.copy(0.85f), fontSize = 11.5.sp,
            )
        }
    }
}

/** The file's voice bubble, driven by REAL playback: the play disc
 *  toggles the decrypted file through VoicePlayer (wired by the caller);
 *  the waveform bars are the message's real recorded amplitudes and the
 *  played portion lights up over the real duration. */
@Composable
private fun RdVoiceBubble(m: TikuMessage, playing: Boolean, onToggle: () -> Unit) {
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
            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(0.25f)).rdPress(onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = 12.sp)
        }
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            bars.forEachIndexed { k, h ->
                Box(
                    Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(if (k < prog.value * bars.size) 1f else 0.45f)),
                )
            }
        }
        Text(
            "${m.voiceDurSec / 60}:${(m.voiceDurSec % 60).toString().padStart(2, '0')}",
            color = Color.White, fontSize = 12.sp,
        )
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
private fun RdTypingBubble(person: Person) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        RdAv(person, 28); Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(RdCard).padding(horizontal = 16.dp, vertical = 15.dp)
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
                        .clip(CircleShape).background(RdSub),
                )
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
) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth().chatGlass(h, RoundedCornerShape(999.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).rdPress(onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).rdPress(onInfo),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(horizontal = 6.dp)) { RdAv(person, 38) }
            Column {
                Text(
                    title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(status, color = RdSub, fontSize = 12.sp)
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
) {
    // ROUND 15: the camera button lives only while the field is EMPTY and
    // UNFOCUSED — tapping into the field to type takes focus and the
    // button animates away (owner's exact ask).
    var fieldFocused by remember { mutableStateOf(false) }
    // ROUND 22: typed emoji render as iOS glyphs while typing (the
    // same font the bubbles use — EmojiArt.kt, worker A §5).
    val emojiFont = rememberEmojiFontFamily()
    val showCamera = value.isEmpty() && !fieldFocused
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            visible = replyText != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                Modifier.fillMaxWidth().chatGlass(h, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Replying", color = accent, fontSize = 11.sp)
                    Text(replyText ?: "", color = RdSub, fontSize = 13.sp, maxLines = 1, fontFamily = emojiFont)
                }
                Text("✕", color = Color.White, fontSize = 16.sp, modifier = Modifier.rdPress(onClearReply).padding(6.dp))
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
                    Modifier.chatGlass(h, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EmojiGlyph(def.glyph, 16.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(def.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "✕", color = RdSub, fontSize = 13.sp,
                        modifier = Modifier.rdPress(onFxDisarm).padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().chatGlass(h, RoundedCornerShape(999.dp)).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) {
                val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
                    1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot",
                )
                Box(Modifier.padding(start = 14.dp).size(10.dp).clip(CircleShape).background(RdDanger.copy(alpha = pulse)))
                Text(
                    "${recSecs / 60}:${(recSecs % 60).toString().padStart(2, '0')}",
                    color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Text("Cancel", color = RdSub, modifier = Modifier.rdPress(onRecCancel).padding(10.dp))
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
                            .background(if (fxArmed != null) accent else Color.White.copy(0.12f))
                            .rdPress(onFxClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        TikuSparklesIcon(Modifier.size(20.dp), tint = Color.White)
                    }
                }
                AnimatedVisibility(
                    visible = showCamera,
                    enter = fadeIn(tween(160)) + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    Box(
                        Modifier.padding(end = 6.dp).size(40.dp).clip(CircleShape).background(accent).rdPress(onCamera),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.12f)).rdPress(onPlus), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 24.sp)
                }
                Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    if (value.isEmpty()) Text("Message…", color = RdSub, fontSize = 15.sp)
                    BasicTextField(
                        value, onChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = emojiFont),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { fieldFocused = it.isFocused },
                    )
                }
                val has = value.isNotBlank()
                val bg by animateColorAsState(if (has) accent else Color.White.copy(0.12f), label = "sendBg")
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
                        else Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
        Text(label, color = if (danger) RdDanger else Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
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
            Text("Chat unavailable", color = RdSub, fontSize = 14.sp)
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
    var menuMsg by remember { mutableStateOf<TikuMessage?>(null) }
    var attachOpen by remember { mutableStateOf(false) }
    var viewerMsg by remember { mutableStateOf<TikuMessage?>(null) }
    var videoViewerMsg by remember { mutableStateOf<TikuMessage?>(null) }
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
    var fxPickerOpen by remember(threadId) { mutableStateOf(false) }
    /** The running overlay playback: fx kind to play key ("<msgId>#<n>").
     *  The counter makes every playback — first play or tap replay — a
     *  fresh key, so the overlay's Animatable restarts cleanly. */
    var fxPlay by remember(threadId) { mutableStateOf<Pair<String, String>?>(null) }
    var fxPlaySeq by remember(threadId) { mutableIntStateOf(0) }
    /** Ids whose overlay already auto-played on this screen instance —
     *  the one-shot discipline: scroll-back, recomposition and the
     *  server echo (same id) can never replay; only an explicit bubble
     *  tap replays, and it bypasses this set on purpose. */
    val fxPlayed = remember(threadId) { mutableSetOf<String>() }
    /** False until this screen has seen its first tail: history is
     *  primed into [fxPlayed] silently, so opening a chat never erupts
     *  with the effects of messages that were already there. */
    var fxPrimed by remember(threadId) { mutableStateOf(false) }
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
    /** Opened gift ids — per-device and DURABLE (SharedPreferences):
     *  a gift unwraps once on this phone and stays a normal bubble
     *  across restarts, exactly like IG. Local-only by design: nothing
     *  about opening is written to the server. */
    val giftPrefs = remember { ctx.getSharedPreferences("tiku_fx_gifts", Context.MODE_PRIVATE) }
    var openedGifts by remember(threadId) {
        mutableStateOf(giftPrefs.getStringSet("opened", emptySet())?.toSet() ?: emptySet())
    }
    fun playFx(kind: String, msgId: String) {
        fxPlaySeq += 1
        fxPlay = kind to "$msgId#$fxPlaySeq"
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
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                BitmapFactory.decodeStream(s)?.let { AppState.repo.sendPhoto(threadId, it) }
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
                AppState.repo.sendVideo(threadId, f, ctx.contentResolver.getType(uri) ?: "video/mp4", mediaDurationSec(f))
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
    var camPhotoFile by remember { mutableStateOf<File?>(null) }
    var camVideoFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camPhotoFile
        camPhotoFile = null
        if (ok && f != null && f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { AppState.repo.sendPhoto(threadId, it) }
            runCatching { f.delete() }
        }
    }
    val recordVideoCapture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = camVideoFile
        camVideoFile = null
        if (ok && f != null && f.exists() && f.length() > 0) {
            AppState.repo.sendVideo(threadId, f, "video/mp4", mediaDurationSec(f))
        } else if (f != null) {
            runCatching { f.delete() }
        }
    }
    fun launchCameraPhoto() {
        val f = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        camPhotoFile = f
        ChatSession.expectExternalActivity = true
        takePhoto.launch(FileProvider.getUriForFile(ctx, "tiku.app.fileprovider", f))
    }
    fun launchCameraVideo() {
        val f = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.mp4")
        camVideoFile = f
        ChatSession.expectExternalActivity = true
        recordVideoCapture.launch(FileProvider.getUriForFile(ctx, "tiku.app.fileprovider", f))
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

    fun sendCurrent() {
        val v = input.trim()
        if (v.isEmpty()) return
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
    // ROUND 22 — effect auto-play: a fire / confetti / hearts message
    // that BECOMES the tail — my send landing, or an arrival while this
    // chat is open — plays its overlay exactly once. Keyed on the
    // tail's identity, so scrolling and recomposition can never
    // re-fire it; the first tail this screen sees only primes the
    // played-set (history never erupts on open). Gift has no overlay:
    // its wrap is the effect, opened by tap.
    LaunchedEffect(visible.lastOrNull()?.id) {
        val tail = visible.lastOrNull() ?: return@LaunchedEffect
        if (!fxPrimed) {
            fxPrimed = true
            visible.forEach { m -> if (m.fx != null && m.fx != "gift") fxPlayed += m.id }
            return@LaunchedEffect
        }
        if (!settled) return@LaunchedEffect
        val k = tail.fx
        if (k != null && k != "gift" && tail.id !in fxPlayed) {
            fxPlayed += tail.id
            playFx(k, tail.id)
        }
    }
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
                            m.deletedForEveryone -> RdTombstoneRow(m, person) {
                                menuMsg = m
                            }
                            m.kind == MsgKind.CALL -> CallEventPill(m)
                            else -> RdBubbleRow(
                                i = idx,
                                m = m,
                                person = person,
                                acc = acc,
                                textSp = textSp,
                                playing = playingId == m.id,
                                onPlayToggle = {
                                    val path = m.localPath
                                    if (path != null) {
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
                                onReply = { replyTarget = m },
                                onReact = { e -> react(m, e) },
                                onMenu = { menuMsg = m },
                                onMediaTap = {
                                    when (m.kind) {
                                        MsgKind.IMAGE -> viewerMsg = m
                                        MsgKind.VIDEO -> {
                                            if (m.localPath != null) videoViewerMsg = m
                                            else {
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
                                        "fire", "confetti", "hearts" -> playFx(msg.fx!!, msg.id)
                                    }
                                },
                            )
                        }
                        // ROUND 18 — "Seen" / "Sent" caption (IG-style)
                        // under my LAST own message only. Seen = the
                        // partner's client has marked it read (seenBy);
                        // Sent = delivered to the server, not yet read.
                        // Sending/failed states render nothing here (the
                        // bubble already shows "Sending…"/"Tap to retry").
                        if (showReceipts && m.id == lastOwnId) {
                            val caption = when {
                                m.seenByPeer -> "Seen"
                                m.status == SendStatus.SENT -> "Sent"
                                else -> null
                            }
                            if (caption != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Text(caption, fontSize = 11.5.sp, color = RdSub)
                                }
                            }
                        }
                    }
                }
                if (peerTyping) {
                    item(key = "typing") { RdTypingBubble(person) }
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
                        fontSize = 12.5.sp, color = Color.White, fontFamily = rememberEmojiFontFamily(),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (thread.pinnedMsgIds.size > 1) {
                        Text("${thread.pinnedMsgIds.size} pinned", fontSize = 11.5.sp, color = RdSub)
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
                        .chatGlass(haze, RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "You blocked this person", color = Color.White, fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Unblock", color = RdRose, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.rdPress { AppState.repo.unblockUser(person.uid) }.padding(6.dp),
                    )
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
                    accent = acc.first,
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
                                    color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic,
                                )
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
                                    color = RdSub, fontSize = 13.5.sp, fontStyle = FontStyle.Italic,
                                )
                            }
                        } else if (mm.kind == MsgKind.IMAGE && mm.photo != null) {
                            Image(
                                mm.photo.asImageBitmap(), null,
                                Modifier.width(160.dp).height(190.dp).clip(RoundedCornerShape(22.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (mm.fx == "gift" && mm.id !in openedGifts) {
                            // ROUND 22: an unopened gift previews as
                            // its wrap — the menu never shows the
                            // sealed text.
                            Box(
                                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                                    .background(if (mm.me) Brush.linearGradient(listOf(acc.first, acc.second)) else SolidColor(RdCard))
                                    .padding(horizontal = 26.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { RdGiftWrap() }
                        } else {
                            Text(
                                if (mm.kind == MsgKind.TEXT) mm.text else replyQuoteLabel(mm),
                                color = Color.White, fontSize = textSp.sp, fontFamily = rememberEmojiFontFamily(),
                                modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                                    .background(if (mm.me) Brush.linearGradient(listOf(acc.first, acc.second)) else SolidColor(RdCard))
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
                            RdMenuItem("Reply", onClick = { replyTarget = mm; menuMsg = null }) {
                                Text("↩", color = Color.White, fontSize = 20.sp)
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
                            // bubble tap first — round 15).
                            val savable = when (mm.kind) {
                                MsgKind.IMAGE -> mm.photo != null
                                MsgKind.VOICE, MsgKind.VIDEO, MsgKind.AUDIO -> mm.localPath != null
                                else -> false
                            }
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
                        Text("Photo", color = Color.White, fontSize = 16.sp)
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
                        Text("Video", color = Color.White, fontSize = 16.sp)
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
                        Text("Audio file", color = Color.White, fontSize = 16.sp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().rdPress {
                            camChooserOpen = false
                            launchCameraPhoto()
                        }.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Take photo", color = Color.White, fontSize = 16.sp)
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
                        Text("Record video", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }

        // ---- ROUND 22: message-effects picker ----
        // The attach sheet's glass language: four effect rows (glyph +
        // name + one-line blurb, a rose tick on the armed one) over a
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
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                    RdMessageFx.forEach { def ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().rdPress {
                                armedFx = def.kind
                                fxPickerOpen = false
                            }.padding(16.dp),
                        ) {
                            EmojiGlyph(def.glyph, 26.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(def.name, color = Color.White, fontSize = 16.sp)
                                Text(def.blurb, color = RdSub, fontSize = 12.sp)
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
                            Text("No effect", color = Color.White, fontSize = 16.sp)
                            Text("Send it plain", color = RdSub, fontSize = 12.sp)
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
            ImageViewerOverlay(vm) { viewerMsg = null }
        }

        // ---- full-screen video player ----
        videoViewerMsg?.let { vm ->
            VideoViewerOverlay(vm) { videoViewerMsg = null }
        }

        // ---- ROUND 22: effect playback overlay ----
        // Topmost in the chat root. RdFxOverlay has no pointer input of
        // its own, so taps fall through to the list beneath while it
        // runs. The clear is key-checked: a replay started mid-flight
        // replaces the key, and the old playback's finally must not
        // clear the new one.
        fxPlay?.let { (kind, playKey) ->
            RdFxOverlay(kind, playKey) {
                if (fxPlay?.second == playKey) fxPlay = null
            }
        }
    }
}
