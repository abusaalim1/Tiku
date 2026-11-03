package com.tiku.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * CHAT — ROUND 14: the owner's TikuRedesign ChatList + overlays, ported
 * onto the real thread. Black background, theme-gradient own bubbles,
 * CardC peer bubbles with a 28dp avatar, the file's gestures (swipe-reply
 * spring, double-tap heart burst, long-press centred glass menu), the
 * file's ChatTopBar / ChatInput overlays, pinned strip, real typing
 * bubble, real voice recording + playback. Everything the file forbids is
 * gone: no timestamps, no seen ticks, no E2EE banner, no quick chips.
 * Engine behaviour is unchanged (E2EE, reply/star/pin/react/save,
 * delete-for-me = local only, explicit Save only for media).
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
private val RdMoreEmojis = listOf("🔥", "🙏", "😍", "🎉", "💯", "😭")

/** Avatar fallback hues: the redesign file's people colours + extraColors. */
private val RdAvatarHues = listOf(
    Color(0xFFFF5C8A), Color(0xFF8E7CFF), Color(0xFFFFB347), Color(0xFF4FD1C5),
    Color(0xFF6AA8FF), Color(0xFFB07A5A), Color(0xFF5A82B0), Color(0xFF4F9A7E),
)

// ================= the file's animation modifiers (verbatim) =================
/** Chat bubble entrance: springs up from its own corner. */
private fun Modifier.rdBubbleIn(mine: Boolean, i: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(if (i < 8) i * 30L else 0L); a.animateTo(1f, spring(0.62f, 380f)) }
    this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f); val s = 0.82f + 0.18f * a.value; scaleX = s; scaleY = s
        translationY = (1f - a.value) * 40f
        transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
    }
}

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
fun replyQuoteLabel(m: TikuMessage): String = when (m.kind) {
    MsgKind.IMAGE -> "Photo"
    MsgKind.VOICE -> "Voice message"
    else -> m.text
}.take(60)

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

// ================= message bubbles (the file's BubbleRow) =================
private fun Modifier.rdDrawLeftBar(): Modifier = this.drawBehind {
    drawRect(Color.White.copy(0.55f), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
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
    onImageClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val off = remember { Animatable(0f) }
    val heart = remember { Animatable(0f) }
    var heartKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(heartKey) { if (heartKey > 0) { heart.snapTo(0f); heart.animateTo(1f, tween(800, easing = LinearEasing)) } }
    val media = m.kind == MsgKind.IMAGE
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().rdBubbleIn(m.me, i),
            horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!m.me) { RdAv(person, 28); Spacer(Modifier.width(8.dp)) }
            Box(contentAlignment = Alignment.Center) {
                Column(
                    Modifier.offset { IntOffset(off.value.roundToInt(), 0) }
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
                                    val cur = m.reactions.values.firstOrNull()
                                    if (cur != "❤️") onReact("❤️")
                                    heartKey++
                                },
                                onLongPress = { onMenu() },
                                onTap = { if (m.kind == MsgKind.IMAGE && m.photo != null) onImageClick() },
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
                            MsgKind.VOICE -> RdVoiceBubble(m, playing, onPlayToggle)
                            else -> {
                                if (m.replyQuote != null) {
                                    Text(
                                        m.replyQuote, color = Color.White.copy(0.75f), fontSize = 12.sp, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 6.dp).rdDrawLeftBar().padding(start = 8.dp),
                                    )
                                }
                                Text(m.text, color = Color.White, fontSize = textSp.sp)
                            }
                        }
                    }
                    if (m.starredByMe) {
                        Text("★", color = Color(0xFFFFC857), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                    val reactions = m.reactions.values.distinct()
                    if (reactions.isNotEmpty()) {
                        Row(
                            Modifier.rdPop(0).offset(x = if (m.me) (-8).dp else 8.dp, y = (-8).dp)
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

/** The file's TypingBubble (3 bouncing dots + avatar) on real peer typing. */
@Composable
private fun RdTypingBubble(person: Person) {
    Row(Modifier.fillMaxWidth().rdBubbleIn(false, 99), verticalAlignment = Alignment.Bottom) {
        RdAv(person, 28); Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(RdCard).padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val t = rememberInfiniteTransition(label = "typing")
            repeat(3) { k ->
                val y by t.animateFloat(
                    0f, -5f,
                    infiniteRepeatable(tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = androidx.compose.animation.core.StartOffset(k * 140)),
                    label = "d$k",
                )
                Box(Modifier.size(7.dp).graphicsLayer { translationY = y * density }.clip(CircleShape).background(RdSub))
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
) {
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
                    Text(replyText ?: "", color = RdSub, fontSize = 13.sp, maxLines = 1)
                }
                Text("✕", color = Color.White, fontSize = 16.sp, modifier = Modifier.rdPress(onClearReply).padding(6.dp))
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
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.12f)).rdPress(onPlus), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 24.sp)
                }
                Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    if (value.isEmpty()) Text("Message…", color = RdSub, fontSize = 15.sp)
                    BasicTextField(
                        value, onChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val has = value.isNotBlank()
                val bg by animateColorAsState(if (has) accent else Color.White.copy(0.12f), label = "sendBg")
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(bg).rdPress { if (has) onSend() else onMic() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (has) Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    else Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
        .onFailure { AppState.toast("Couldn't start recording") }
}

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
    val peerTyping = typingMap[threadId] == true

    var input by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<TikuMessage?>(null) }
    var menuMsg by remember { mutableStateOf<TikuMessage?>(null) }
    var moreEmoji by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var viewerMsg by remember { mutableStateOf<TikuMessage?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableIntStateOf(0) }
    val recorder = remember { VoiceRecorder(ctx) }
    val player = remember { VoicePlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val startCall = rememberCallStarter()
    val clipboard = LocalClipboardManager.current

    DisposableEffect(threadId) { onDispose { player.stop(); AppState.repo.closeThread(threadId) } }

    // Photo ONLY (the engine has no video messages): the real Photo
    // Picker -> decode -> the real encrypted image send.
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
                if (f != null) AppState.repo.sendVoice(threadId, f, 180, recorder.normalizedAmps())
                AppState.toast("Voice notes are capped at 3 minutes")
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

    fun sendCurrent() {
        val v = input.trim()
        if (v.isEmpty()) return
        AppState.repo.sendText(threadId, v, replyTarget?.let { replyQuoteLabel(it) })
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
    // forward list): settle at the latest message on open; afterwards
    // follow ONLY when already at the bottom or the arrival is mine.
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
        }
    }
    var prevNewestTs by remember(threadId) { mutableStateOf(0L) }
    LaunchedEffect(visible.size, peerTyping) {
        val newest = visible.maxOfOrNull { it.tsMs } ?: 0L
        if (settled && newest > prevNewestTs && visible.isNotEmpty()) {
            if (nearBottom || visible.lastOrNull()?.me == true) {
                runCatching { listState.animateScrollToItem(visible.lastIndex + if (peerTyping) 1 else 0) }
            }
        }
        if (newest > prevNewestTs) prevNewestTs = newest
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
        Box(
            Modifier.fillMaxSize()
                .then(if (haze != null) Modifier.hazeSource(haze) else Modifier),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("message_list"),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = if (thread.pinnedMsgIds.isNotEmpty()) 148.dp else 96.dp,
                    bottom = if (partnerBlocked) 96.dp else 130.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { m ->
                    Box(Modifier.animateItem()) {
                        if (m.kind == MsgKind.CALL) {
                            CallEventPill(m)
                        } else {
                            RdBubbleRow(
                                i = visible.indexOf(m),
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
                                onMenu = { menuMsg = m; moreEmoji = false },
                                onImageClick = { viewerMsg = m },
                                onRetry = { AppState.repo.retrySend(threadId, m.id) },
                            )
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 120.dp),
        ) {
            Box(
                Modifier.testTag("jump_to_latest").size(40.dp).clip(CircleShape).background(RdCard)
                    .border(1.dp, Color.White.copy(0.12f), CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch {
                            runCatching { listState.animateScrollToItem(visible.lastIndex + if (peerTyping) 1 else 0) }
                        }
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
            )
            if (thread.pinnedMsgIds.isNotEmpty()) {
                val pinId = thread.pinnedMsgIds.last()
                val pinnedMsg = thread.messages.firstOrNull { it.id == pinId }
                val pinPreview = when (pinnedMsg?.kind) {
                    MsgKind.IMAGE -> "Photo"
                    MsgKind.VOICE -> "Voice message"
                    MsgKind.CALL -> "Call"
                    else -> pinnedMsg?.text?.take(60) ?: "Pinned message"
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
                        fontSize = 12.5.sp, color = Color.White,
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
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
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
                        if (it.isNotEmpty()) AppState.repo.setTyping(threadId, true)
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
                            askMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRecCancel = { recorder.stop(false); recording = false },
                    onRecSend = {
                        val f = recorder.stop(true)
                        recording = false
                        if (f != null) {
                            AppState.repo.sendVoice(threadId, f, recSecs.coerceAtLeast(1), recorder.normalizedAmps())
                        }
                    },
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
                        .clickable(remember { MutableInteractionSource() }, null) { menuMsg = null; moreEmoji = false },
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
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(RdSheet)
                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            (if (moreEmoji) RdMoreEmojis else RdBaseEmojis).forEachIndexed { k, e ->
                                Box(
                                    Modifier.rdPop(k).rdPress {
                                        react(mm, e); menuMsg = null; moreEmoji = false
                                    }.padding(6.dp),
                                ) { EmojiGlyph(e, 28.dp) }
                            }
                            Box(
                                Modifier.padding(start = 4.dp).size(42.dp).clip(CircleShape)
                                    .background(Color.White.copy(0.12f))
                                    .rdPress { moreEmoji = !moreEmoji },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (moreEmoji) "−" else "+", color = Color.White, fontSize = 24.sp)
                            }
                        }
                        if (mm.kind == MsgKind.IMAGE && mm.photo != null) {
                            Image(
                                mm.photo.asImageBitmap(), null,
                                Modifier.width(160.dp).height(190.dp).clip(RoundedCornerShape(22.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                if (mm.kind == MsgKind.TEXT) mm.text else replyQuoteLabel(mm),
                                color = Color.White, fontSize = textSp.sp,
                                modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                                    .background(if (mm.me) Brush.linearGradient(listOf(acc.first, acc.second)) else SolidColor(RdCard))
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                            )
                        }
                        Column(
                            Modifier.width(250.dp).clip(RoundedCornerShape(26.dp)).background(RdSheet)
                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(26.dp)),
                        ) {
                            RdMenuItem("Reply", onClick = { replyTarget = mm; menuMsg = null }) {
                                Text("↩", color = Color.White, fontSize = 20.sp)
                            }
                            if (mm.kind == MsgKind.TEXT) {
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
                            if (mm.kind == MsgKind.IMAGE || mm.kind == MsgKind.VOICE) {
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
                            if (mm.me) {
                                RdDivider1()
                                RdMenuItem("Delete", danger = true, onClick = {
                                    AppState.repo.deleteForMe(threadId, mm.id); menuMsg = null
                                }) {
                                    Icon(Icons.Filled.Delete, null, tint = RdDanger, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- attachment sheet: the file's glass sheet, Photo ONLY ----
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
                }
            }
        }

        // ---- full-screen image viewer ----
        viewerMsg?.let { vm ->
            ImageViewerOverlay(vm) { viewerMsg = null }
        }
    }
}
