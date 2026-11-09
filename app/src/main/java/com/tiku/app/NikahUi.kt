package com.tiku.app

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import android.icu.util.Calendar as IcuCalendar
import java.util.Date
import java.util.Locale

/**
 * ROUND 60 — Nikah screens (see Nikah.kt for state/crypto).
 * ROUND 65 — restyled to the owner-approved Prototype v4: near-black
 * bg #050507, two blurred aurora glows (rose top-right, gold
 * bottom-left) instead of drifting hearts, a single progress-FILL
 * bar, sentence-case step labels, h2-scale Instrument Serif headings
 * (SerifRegular — the tree bundles v4's exact display face), Inter
 * (InterFamily — v4's exact UI face) for UI text, rose
 * #FF4F7B / deep #E02A5C / soft #FF9DB5, gold #E8CE95, green #30D158.
 * Arabic renders through the platform's Arabic-capable fallback (no
 * Amiri file exists in the tree; nothing was downloaded).
 * COUPLE RULE (owner): wherever the groom/bride are REPRESENTED —
 * role-pick cards, lobby rings, turn/waiting indicators — they are
 * the iOS emoji glyphs 🤵 / 👰 via [EmojiArt] (the bundled iOS emoji
 * font), never crops of the mascot couple portrait. The mascot poses
 * that are pure decoration (signing, celebrate) stay.
 */
val NkGold = Color(0xFFE8CE95)
val NkGoldDeep = Color(0xFFB98A2F)
val NkRose = Color(0xFFFF4F7B)
val NkRoseDeepV4 = Color(0xFFE02A5C)
val NkRoseSoft = Color(0xFFFF9DB5)
val NkPetal = Color(0xFFFF4F7B)
val NkGreen = Color(0xFF30D158)
val NkSubC = Color(0xFF8E8E99)
val NkBg = Color(0xFF050507)
val NkLine = Color(0x17FFFFFF)
val NkPaperInk = Color(0xFF3B1519)
val NkCertGreen = Color(0xFF0A4636)
// ROUND 65 — the Nikah Nama's own paper palette (certificate only;
// the ceremony around it keeps the dark palette above): warm cream
// paper, deep maroon, antique gold.
val NkMaroon = Color(0xFF6E1423)
val NkMaroonDeep = Color(0xFF4A0D18)
val NkAntiqueGold = Color(0xFFB8862F)
val NkCream = Color(0xFFF7EAD2)
val NkCreamLight = Color(0xFFFCF3DF)
val NkPaperText = Color(0xFF4A2E33)

private val NkRoseBrush = Brush.verticalGradient(listOf(Color(0xFFFF6D90), Color(0xFFFF3F6E)))
private val NkGoldBrush = Brush.linearGradient(listOf(Color(0xFFF5DFA9), Color(0xFFC9A35A)))

fun nikahDateStr(ms: Long): String =
    SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(Date(ms))

/** The certificate's Gregorian date (prototype: "9 November 2026"). */
fun nikahCertDateStr(ms: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.US).format(Date(ms))

/** ROUND 65: the Hijri date for the certificate, computed on-device
 *  with ICU's Islamic calendar (Umalqura on Android). Any failure —
 *  calendar unavailable on the device — returns null and the
 *  certificate OMITS the Hijri cell; a date is never invented. */
fun nikahHijriStr(ms: Long): String? = runCatching {
    val cal = android.icu.util.IslamicCalendar()
    cal.timeInMillis = ms
    val fmt = android.icu.text.SimpleDateFormat("d MMMM yyyy AH", Locale.US)
    @Suppress("UNCHECKED_CAST")
    fmt.calendar = cal as IcuCalendar
    fmt.format(cal.time)
}.getOrNull()

/** ROUND 65: "NN-{year}-{####}" — a pure function of keepsake payload
 *  fields that are byte-identical on both phones and in every
 *  re-render (the couple's names, the ceremony date, the mahr), so
 *  both phones and every keepsake re-open print the SAME number.
 *  No randomness, no server state. */
fun nikahCertNo(k: NikahKeepsake): String {
    val year = SimpleDateFormat("yyyy", Locale.US).format(Date(k.dateMs))
    val seed = "${k.groomName}|${k.brideName}|${k.dateMs}|${k.mahr}"
    var acc = 0L
    seed.forEachIndexed { i, ch -> acc += (i + 1).toLong() * ch.code }
    val num = ((acc % 10000) + 10000) % 10000
    return "NN-$year-${num.toString().padStart(4, '0')}"
}

/** First name of a full real name (the prototype's turn/waiting lines
 *  address each partner by first name). */
fun nkFirst(full: String): String = full.trim().split(Regex("\s+")).firstOrNull() ?: ""


/** Callbacks the ceremony content renders against — the overlay wires
 *  them to [NikahStore]; the screenshot harness passes the defaults
 *  (inert) with a fake state, exactly like the harness's other seams. */
data class NikahCallbacks(
    /** ROUND 65: my real-name entry (full name, father's name). */
    val onSubmitNames: (String, String) -> Unit = { _, _ -> },
    val onReady: () -> Unit = {},
    val onKhutbahAmeen: () -> Unit = {},
    val onPickMahr: (String) -> Unit = {},
    val onProposeMahr: () -> Unit = {},
    val onAcceptMahr: () -> Unit = {},
    val onRequestMahrChange: () -> Unit = {},
    val onQabool: () -> Unit = {},
    val onSubmitWitness: (String) -> Unit = {},
    val onSubmitSignature: (ByteArray) -> Unit = {},
    val onDuaAmeen: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onClose: () -> Unit = {},
)

// ---------------- shared pieces ----------------

/** ROUND 65 (v4): a mascot pose as pure decoration — a dark panel
 *  (#17171B) with a rose glow shadow + hairline inset ring, NO
 *  gradient border frame; a slow float (scale/translate) instead of
 *  the old bob. [motion] is off in the screenshot harness so captures
 *  are deterministic. NEVER used as the couple's avatars — couple
 *  representation is the emoji pair (see [NkStoryRing]). */
@Composable
private fun NikahMascot(asset: String, small: Boolean = false, xs: Boolean = false, motion: Boolean = true) {
    val ctx = LocalContext.current
    val bmp = remember(asset) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            ctx.assets.open("nikah/$asset").use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }
    val radius = if (xs) 26.dp else 34.dp
    val trans = rememberInfiniteTransition(label = "nkFloat")
    val fy by trans.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "nkFloatY",
    )
    val fs by trans.animateFloat(
        initialValue = 1.02f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "nkFloatS",
    )
    Box(
        Modifier.shadow(28.dp, RoundedCornerShape(radius), spotColor = NkRose.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(radius))
            .background(Color(0xFF17171B))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(radius)),
    ) {
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), null,
                Modifier.width(if (xs) 118.dp else if (small) 216.dp else 296.dp)
                    .then(
                        if (motion) Modifier.graphicsLayer {
                            translationY = fy; scaleX = fs; scaleY = fs
                        } else Modifier,
                    )
                    .clip(RoundedCornerShape(radius)),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Box(Modifier.size(if (small) 180.dp else 240.dp)
                .clip(RoundedCornerShape(radius)).background(Color(0xFF17171B)))
        }
    }
}

/** ROUND 65 (owner emoji rule): ONE partner as an iOS emoji glyph
 *  inside a v4 gradient story-ring (roseSoft → gold), with the green
 *  presence dot when [showDot]. Used for the lobby avatars. */
@Composable
private fun NkStoryRing(
    emoji: String, present: Boolean, showDot: Boolean = true,
    ring: androidx.compose.ui.unit.Dp = 110.dp,
    glyph: androidx.compose.ui.unit.Dp = 62.dp,
) {
    Box(Modifier.size(ring)) {
        Box(
            Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(NkRoseSoft, NkGold))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                    .background(Color(0xFF17171B)),
                contentAlignment = Alignment.Center,
            ) {
                EmojiArt(emoji, glyph)
            }
        }
        if (showDot) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp)
                    .clip(CircleShape).background(NkBg)
                    .padding(3.dp).clip(CircleShape)
                    .background(if (present) NkGreen else Color(0xFF4A4A52)),
            )
        }
    }
}

/** ROUND 65 (owner emoji rule): the couple as a PAIR of emoji rings —
 *  the header treatment wherever v4 showed the couple mascot as the
 *  couple's representation (role pick, name step, waiting sides). */
@Composable
private fun NkCouplePair(small: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (small) 14.dp else 20.dp)) {
        NkStoryRing("🤵", present = true, showDot = false,
            ring = if (small) 76.dp else 96.dp, glyph = if (small) 42.dp else 54.dp)
        NkStoryRing("👰", present = true, showDot = false,
            ring = if (small) 76.dp else 96.dp, glyph = if (small) 42.dp else 54.dp)
    }
}

/** v4's rose-rule: a short 28dp rose bar (no ✦, no hairlines). */
@Composable
private fun NkRoseRule() {
    Box(
        Modifier.padding(vertical = 2.dp).width(28.dp).height(3.dp)
            .clip(CircleShape).background(NkRose),
    )
}

/** v4's live status line: a pulsing-colour dot + calm Inter text.
 *  [waiting] paints the dot amber; otherwise green. */
@Composable
private fun NkStatusLine(text: String, waiting: Boolean = true, check: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (check) {
            Text("✓", color = Color(0xFF7BE79B), fontSize = 13.5.sp,
                fontFamily = InterFamily, fontWeight = FontWeight.Medium)
        } else {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (waiting) Color(0xFFFFB340) else NkGreen),
            )
        }
        Text(text, color = NkSubC, fontSize = 13.5.sp, fontFamily = InterFamily,
            textAlign = TextAlign.Center, lineHeight = 19.sp)
    }
}

/** v4's ameen-row: one box per partner — name + Ameen ✓ / …waiting. */
@Composable
private fun NkAmeenBoxes(
    state: NikahUiState, myUid: String, flags: Map<String, Boolean>,
) {
    val partnerUid = if (myUid == state.groomUid) state.brideUid else state.groomUid
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NkAmeenBox(
            "You · ${nkFirst(state.certNameOf(myUid))}",
            flags[myUid] == true, Modifier.weight(1f),
        )
        NkAmeenBox(
            nkFirst(state.certNameOf(partnerUid)),
            flags[partnerUid] == true, Modifier.weight(1f),
            waiting = flags[partnerUid] != true,
        )
    }
}

@Composable
private fun NkAmeenBox(
    label: String, done: Boolean, modifier: Modifier = Modifier, waiting: Boolean = false,
) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp))
            .background(
                if (done) NkGreen.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.055f),
            )
            .border(
                1.dp,
                if (done) NkGreen.copy(alpha = 0.40f) else NkLine,
                RoundedCornerShape(22.dp),
            )
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = NkSubC, fontSize = 12.5.sp, fontFamily = InterFamily,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            if (done) "Ameen ✓" else if (waiting) "…waiting" else "",
            color = if (done) Color(0xFF7BE79B) else Color.White,
            fontSize = 15.sp, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NkCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, NkLine, RoundedCornerShape(26.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun NkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(NkRoseBrush) else Modifier.background(Color.White.copy(0.08f)))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = InterFamily)
    }
}

@Composable
private fun NkGhostButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(CircleShape)
            .border(1.dp, NkLine, CircleShape)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFFF5F5F7), fontSize = 14.sp, fontFamily = InterFamily)
    }
}

/** v4's h2: 36sp Instrument Serif; *segments* render italic (the
 *  prototype's <em> accents). */
@Composable
private fun NkHeading(marked: String) {
    val annotated = buildAnnotatedString {
        var i = 0
        val parts = marked.split("*")
        parts.forEachIndexed { idx, part ->
            if (part.isEmpty()) return@forEachIndexed
            if (idx % 2 == 1) {
                withStyle(SpanStyle(fontFamily = SerifItalic, fontStyle = FontStyle.Italic)) {
                    append(part)
                }
            } else append(part)
            i++
        }
    }
    Text(annotated, color = Color.White, fontSize = 36.sp, fontFamily = SerifRegular,
        textAlign = TextAlign.Center, lineHeight = 40.sp)
}

@Composable
private fun NkSub(text: String) {
    Text(text, color = NkSubC, fontSize = 14.5.sp, fontFamily = InterFamily,
        textAlign = TextAlign.Center, lineHeight = 22.sp)
}

@Composable
private fun NkWaiting(text: String) {
    NkStatusLine(text, waiting = true)
}

/** v4's wait-line: the turn lock as a large gold serif italic line,
 *  addressed to the ACTIVE partner by first name. */
@Composable
private fun NkTurnLock(firstName: String) {
    Text(
        "$firstName's turn — please wait",
        color = NkGold, fontSize = 22.sp, fontFamily = SerifItalic,
        fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
    )
}

/** v4's field: a calm sentence-case label above a soft glass input. */
@Composable
private fun NkField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = NkSubC, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, fontFamily = InterFamily,
            modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = InterFamily, color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(NkRose),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(0.065f))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color(0xFF5A5A64), fontSize = 16.sp,
                            fontFamily = InterFamily)
                    }
                    inner()
                }
            },
        )
    }
}

/** v4's aurora backdrop: near-black with two large blurred glows —
 *  rose top-right, gold bottom-left (radial gradients stand in for
 *  the prototype's blur(80px) divs). */
@Composable
private fun NkBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(NkBg)) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 110.dp, y = (-150).dp).size(340.dp)
                .background(
                    Brush.radialGradient(listOf(NkRose.copy(alpha = 0.26f), Color.Transparent)),
                ),
        )
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-130).dp, y = 170.dp).size(320.dp)
                .background(
                    Brush.radialGradient(listOf(NkGold.copy(alpha = 0.11f), Color.Transparent)),
                ),
        )
        content()
    }
}

// ---------------- ceremony shell ----------------

/**
 * The live ceremony surface: watches the session doc, runs MY presence
 * heartbeat for exactly as long as it is open, and renders
 * [NikahCeremonyContent] from the decoded doc state.
 */
@Composable
fun NikahCeremonyOverlay(
    chatId: String, peerUid: String, myName: String, peerName: String,
    onClose: () -> Unit,
) {
    val state by NikahStore.flow(chatId).collectAsState()
    val myUid = remember {
        if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid()
    }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(chatId) {
        NikahStore.watch(chatId, peerUid, myName, peerName)
        while (true) {
            delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }
    DisposableEffect(chatId) {
        NikahStore.startPresence(chatId)
        onDispose { NikahStore.stopPresence() }
    }
    BackHandler { onClose() }
    val st = state
    if (st == null || !st.exists) {
        Box(
            Modifier.fillMaxSize().background(NkBg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NkSub(if (st == null) "Loading…" else "This ceremony is no longer available.")
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 40.dp)) { NkGhostButton("Close", onClose) }
            }
        }
        return
    }
    val iAmGroom = myUid == st.groomUid
    NikahCeremonyContent(
        state = st, myUid = myUid, nowMs = nowMs,
        cb = NikahCallbacks(
            onSubmitNames = { full, father ->
                NikahStore.submitNames(chatId, peerUid, iAmGroom, full, father)
            },
            onReady = { NikahStore.setReady(chatId) },
            onKhutbahAmeen = { NikahStore.khutbahAmeen(chatId) },
            onPickMahr = { NikahStore.streamMahr(chatId, peerUid, it) },
            onProposeMahr = { NikahStore.proposeMahr(chatId) },
            onAcceptMahr = { NikahStore.acceptMahr(chatId) },
            onRequestMahrChange = { NikahStore.requestMahrChange(chatId) },
            onQabool = {
                NikahStore.qaboolTap(chatId, NikahStore.current(chatId)?.step == NikahSteps.QABOOL_GROOM)
            },
            onSubmitWitness = { NikahStore.submitWitness(chatId, peerUid, iAmGroom, it) },
            onSubmitSignature = { NikahStore.submitSignature(chatId, peerUid, iAmGroom, it) },
            onDuaAmeen = { NikahStore.duaAmeen(chatId) },
            onCancel = { NikahStore.cancel(chatId) },
            onClose = onClose,
        ),
    )
}

/**
 * The ceremony itself, pure on (state, myUid, nowMs): both phones render
 * the same step from the session doc; the ACTIVE partner's controls are
 * live, the other's are visibly locked behind the turn line. The
 * presence gate is enforced here — while the partner is absent or
 * stale, no advance control is enabled (Cancel always is).
 */
@Composable
fun NikahCeremonyContent(
    state: NikahUiState, myUid: String, nowMs: Long, cb: NikahCallbacks,
    motion: Boolean = true,
) {
    val stepIdx = NikahSteps.ORDER.indexOf(state.step).coerceAtLeast(0)
    val iAmGroom = myUid == state.groomUid
    val partnerUid = if (iAmGroom) state.brideUid else state.groomUid
    // ROUND 65: ceremony surfaces address the partner by REAL name
    // once entered; before that the line stays generic ("your
    // partner") — app display names appear nowhere in the ceremony.
    val partnerReal = if (partnerUid == state.groomUid) state.groomRealName else state.brideRealName
    val partnerName = partnerReal.ifBlank { "your partner" }
    // I am present by construction (this screen is open and the overlay
    // heartbeats); the gate turns on the PARTNER's doc stamp.
    val partnerPresent = state.present(partnerUid, nowMs)
    val gateOpen = partnerPresent && state.status == "active"
    var menuOpen by remember { mutableStateOf(false) }
    // v4's staggered rise-in: the step's content rises + fades in on
    // every step change (a single block rise in Compose; the harness
    // passes motion=false for deterministic captures).
    val rise = remember(state.step) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(state.step) {
        if (motion) rise.animateTo(1f, tween(700))
    }

    NkBackdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "✕", color = Color.White, fontSize = 18.sp,
                    modifier = Modifier.clickable { cb.onClose() }.padding(6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                    color = NkGold.copy(alpha = 0.92f), fontSize = 20.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                if (state.status == "active") {
                    Box {
                        Text(
                            "⋮", color = Color.White, fontSize = 20.sp,
                            modifier = Modifier.clickable { menuOpen = true }.padding(6.dp),
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Cancel Nikah", fontFamily = PoppinsFamily) },
                                onClick = { menuOpen = false; cb.onCancel() },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(30.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            // v4: ONE progress-fill bar (rose gradient), width by step.
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(3.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
            ) {
                val frac = (stepIdx + 1).toFloat() / NikahSteps.ORDER.size.toFloat()
                Box(
                    Modifier.fillMaxWidth(frac).height(3.dp).clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(listOf(NkRoseSoft, NkRose)),
                        ),
                )
            }
            Spacer(Modifier.height(11.dp))
            Text(
                "Step ${stepIdx + 1} of ${NikahSteps.ORDER.size} · " +
                    (NikahSteps.LABELS[state.step] ?: ""),
                color = NkSubC, fontSize = 12.5.sp, fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(14.dp))

            if (state.status == "cancelled") {
                NkCard {
                    Text("💔", fontSize = 26.sp)
                    Spacer(Modifier.height(10.dp))
                    NkHeading("Nikah Cancelled")
                    Spacer(Modifier.height(6.dp))
                    NkSub("This ceremony was cancelled. The invitation in your chat shows the same.")
                }
                Spacer(Modifier.height(18.dp))
                NkGhostButton("Close") { cb.onClose() }
                return@Column
            }

            Column(
                Modifier.graphicsLayer {
                    alpha = rise.value
                    translationY = (1f - rise.value) * 42f
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            if (state.status == "active" && !partnerPresent && state.step != NikahSteps.NAMES) {
                NkWaiting("Waiting for $partnerName to join…")
                Spacer(Modifier.height(14.dp))
            }

            when (state.step) {
                NikahSteps.NAMES -> NkStepNames(state, myUid, partnerUid, cb)
                NikahSteps.LOBBY -> NkStepLobby(state, myUid, partnerUid, gateOpen, cb)
                NikahSteps.KHUTBAH -> NkStepKhutbah(state, myUid, gateOpen, cb)
                NikahSteps.MAHR -> NkStepMahr(state, myUid, iAmGroom, gateOpen, cb)
                NikahSteps.QABOOL_GROOM -> NkStepQabool(state, groomTurn = true, myUid, gateOpen, cb, motion)
                NikahSteps.QABOOL_BRIDE -> NkStepQabool(state, groomTurn = false, myUid, gateOpen, cb, motion)
                NikahSteps.WITNESSES -> NkStepWitnesses(state, myUid, partnerUid, gateOpen, cb, motion)
                NikahSteps.SIGNATURES -> NkStepSignatures(state, myUid, partnerUid, gateOpen, cb, motion)
                NikahSteps.DUA -> NkStepDua(state, myUid, gateOpen, cb, motion)
                else -> NkStepNama(state, cb, motion)
            }
            }
            Spacer(Modifier.height(10.dp))
            if (state.status == "active" &&
                (state.step == NikahSteps.LOBBY || state.step == NikahSteps.NAMES)
            ) {
                NkGhostButton("Cancel Nikah") { cb.onCancel() }
            }
        }
        if (state.step == NikahSteps.DUA && state.status == "active") {
            NikahConfetti()
        }
    }
}

// ---------------- steps ----------------

/** ROUND 65 — the real-name step (first, before the lobby), styled
 *  to v4's "Your real name" screen: each participant enters their
 *  real full name + father's name on their OWN phone. The entries
 *  ride the encrypted session channel (see Nikah.kt); app usernames
 *  never reach the ceremony or the certificate. My entry is
 *  intentionally NOT presence-gated — it is personal entry on my own
 *  phone; the step advances in the submit transaction once BOTH
 *  sides are in. The partner status line follows v4: before their
 *  entry lands it stays generic ("your partner"); once it lands we
 *  KNOW their real first name (decrypted) and the line uses it. */
@Composable
private fun NkStepNames(
    state: NikahUiState, myUid: String, partnerUid: String, cb: NikahCallbacks,
) {
    // field state hoisted above every conditional branch (hook order)
    var fullText by remember { mutableStateOf("") }
    var fatherText by remember { mutableStateOf("") }
    val iAmGroom = myUid == state.groomUid
    val myDone = state.nameDoneBySide[myUid] == true
    val partnerDone = state.nameDoneBySide[partnerUid] == true
    val partnerReal = if (partnerUid == state.groomUid) state.groomRealName else state.brideRealName
    val partnerFirst = nkFirst(partnerReal)
    val partnerPronoun = if (partnerUid == state.groomUid) "his" else "her"
    NkCouplePair(small = true)
    Spacer(Modifier.height(14.dp))
    NkHeading("Your *real name*")
    Spacer(Modifier.height(9.dp))
    NkSub("A Nikah Nama carries the names you were given — not nicknames. Write yours exactly as it should appear on the certificate.")
    Spacer(Modifier.height(8.dp))
    if (myDone) {
        NkCard {
            Text("✓ Your name is on the Nikah Nama", color = NkGold, fontSize = 14.sp,
                fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(state.certNameOf(myUid), color = Color.White, fontSize = 19.sp,
                fontFamily = SerifRegular, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        if (partnerDone) {
            NkStatusLine("Both names added — beginning…", waiting = false, check = true)
        } else {
            NkStatusLine("Waiting for your partner to add $partnerPronoun name…", waiting = true)
        }
    } else {
        NkField(
            "Your full name · ${if (iAmGroom) "Groom" else "Bride"}",
            fullText, "e.g. ${if (iAmGroom) "Danish Iqbal" else "Maryam Siddiqui"}",
        ) { fullText = it }
        Spacer(Modifier.height(16.dp))
        NkField("Father's name", fatherText, "e.g. ${if (iAmGroom) "Mohammad Iqbal" else "Abdul Siddiqui"}") {
            fatherText = it
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "These names appear on your Nikah Nama — app usernames are never used.",
            color = NkGold, fontSize = 12.5.sp, fontFamily = InterFamily,
            textAlign = TextAlign.Center, lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))
        if (partnerDone) {
            NkStatusLine(
                "$partnerFirst added $partnerPronoun name on $partnerPronoun phone",
                waiting = false, check = true,
            )
        } else {
            NkStatusLine("Waiting for your partner to add $partnerPronoun name…", waiting = true)
        }
        Spacer(Modifier.height(20.dp))
        NkButton(
            "My name is set",
            enabled = fullText.isNotBlank() && fatherText.isNotBlank(),
        ) { cb.onSubmitNames(fullText, fatherText) }
    }
}

/** ROUND 65 (v4 lobby): the couple as emoji story-rings with
 *  presence dots, REAL names, a live status line, and the Ready
 *  button (disabled "✓ You are ready" once mine lands). NAME GUARD
 *  (round spec): if a session ever reaches the lobby with a side's
 *  real name still missing (legacy/rejoin edge — the NAMES step
 *  normally gates this), Ready stays locked and the status line
 *  names exactly what is missing. */
@Composable
private fun NkStepLobby(
    state: NikahUiState, myUid: String, partnerUid: String,
    gateOpen: Boolean, cb: NikahCallbacks,
) {
    NkHeading("The *Lobby*")
    Spacer(Modifier.height(9.dp))
    NkSub("The ceremony begins when both hearts are present. Take a breath — this is your moment.")
    Spacer(Modifier.height(22.dp))
    val now = System.currentTimeMillis()
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        NkLobbyAvatar(
            emoji = "🤵", name = state.groomCertName, role = "Groom",
            present = state.present(state.groomUid, now),
            ready = state.ready[state.groomUid] == true,
            isMe = myUid == state.groomUid,
        )
        NkLobbyAvatar(
            emoji = "👰", name = state.brideCertName, role = "Bride",
            present = state.present(state.brideUid, now),
            ready = state.ready[state.brideUid] == true,
            isMe = myUid == state.brideUid,
        )
    }
    Spacer(Modifier.height(20.dp))
    val myReady = state.ready[myUid] == true
    val partnerReady = state.ready[partnerUid] == true
    val partnerPresent = state.present(partnerUid, now)
    val myNameMissing = state.nameDoneBySide[myUid] != true
    val partnerNameMissing = state.nameDoneBySide[partnerUid] != true
    val partnerFirst = nkFirst(state.certNameOf(partnerUid))
    when {
        myNameMissing ->
            NkStatusLine("Your real name is still missing — Ready unlocks once both names are in", waiting = true)
        partnerNameMissing ->
            NkStatusLine("Waiting for a name still missing on your partner's phone — Ready unlocks once both names are in", waiting = true)
        !partnerPresent ->
            NkStatusLine("Waiting for $partnerFirst to join…", waiting = true)
        myReady && !partnerReady ->
            NkStatusLine("You are ready — waiting for $partnerFirst…", waiting = true)
        else ->
            NkStatusLine("$partnerFirst is here with you", waiting = false)
    }
    Spacer(Modifier.height(20.dp))
    NkButton(
        if (myReady) "✓ You are ready" else "I'm Ready",
        enabled = gateOpen && !myReady && !myNameMissing && !partnerNameMissing,
    ) { cb.onReady() }
    Spacer(Modifier.height(14.dp))
    Text(
        "A symbolic celebration of your bond, made with love. Both of you take part live, from your own phones.",
        color = NkSubC, fontSize = 12.sp, fontFamily = InterFamily,
        textAlign = TextAlign.Center, lineHeight = 18.sp,
    )
}

@Composable
private fun NkLobbyAvatar(
    emoji: String, name: String, role: String,
    present: Boolean, ready: Boolean, isMe: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(112.dp),
    ) {
        NkStoryRing(emoji, present = present, ring = 110.dp, glyph = 62.dp)
        Spacer(Modifier.height(11.dp))
        Text(
            if (isMe) "$name · You" else name,
            color = Color.White, fontSize = 14.sp, fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
        Text(role, color = NkSubC, fontSize = 12.sp, fontFamily = InterFamily,
            textAlign = TextAlign.Center)
        Text(
            if (ready) "✓ Ready" else "",
            color = NkGold, fontSize = 12.5.sp, fontFamily = InterFamily,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NkStepKhutbah(state: NikahUiState, myUid: String, gateOpen: Boolean, cb: NikahCallbacks) {
    NkHeading("Khutbah-tun-*Nikah*")
    Spacer(Modifier.height(9.dp))
    NkSub("Every Nikah begins with the blessed sermon — praise of Allah, and a reminder of the sacred promise you are about to make. Both of you say Ameen.")
    Spacer(Modifier.height(14.dp))
    NkCard {
        Text(
            "الْحَمْدُ لِلَّهِ نَسْتَعِينُهُ وَنَسْتَغْفِرُهُ، وَنَعُوذُ بِاللَّهِ مِنْ شُرُورِ أَنْفُسِنَا",
            color = Color(0xFFEFDDB0), fontSize = 25.sp, lineHeight = 50.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Alhamdu lillahi nasta'eenuhu wa nastaghfiruh, wa na'oodhu billahi min shuroori anfusina…",
            color = NkRoseSoft, fontSize = 17.sp, fontFamily = SerifItalic,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center, lineHeight = 25.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "\"All praise belongs to Allah. We seek His help and His forgiveness, and we seek refuge in Him from the evils within ourselves.\"",
            color = Color(0xFFC9C9D1), fontSize = 13.5.sp, fontFamily = InterFamily,
            textAlign = TextAlign.Center, lineHeight = 21.sp,
        )
    }
    Spacer(Modifier.height(14.dp))
    NkCard {
        Text(
            "Marriage in Islam is a mithaqan ghaleeza — a solemn, weighty covenant of peace (sakina), love (mawaddah) and mercy (rahmah).",
            color = Color(0xFFC9C9D1), fontSize = 13.5.sp, fontFamily = InterFamily, lineHeight = 21.sp,
        )
    }
    Spacer(Modifier.height(16.dp))
    NkAmeenBoxes(state, myUid, state.khutbah)
    Spacer(Modifier.height(20.dp))
    if (state.khutbah[myUid] == true) {
        val partnerUid = if (myUid == state.groomUid) state.brideUid else state.groomUid
        NkButton("Waiting for ${nkFirst(state.certNameOf(partnerUid))}'s Ameen…", enabled = false) {}
    } else {
        NkButton("Ameen", enabled = gateOpen) { cb.onKhutbahAmeen() }
    }
}

@Composable
private fun NkStepMahr(
    state: NikahUiState, myUid: String, iAmGroom: Boolean,
    gateOpen: Boolean, cb: NikahCallbacks,
) {
    // Mahr local UI state — hoisted ABOVE every conditional branch so
    // hook order never changes when the phase flips mid-composition.
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var customTouched by remember { mutableStateOf(false) }
    LaunchedEffect(customText) {
        if (iAmGroom && customMode && customTouched) {
            delay(600)
            cb.onPickMahr(customText.trim())
        }
    }
    NkHeading("The *Mahr*")
    Spacer(Modifier.height(9.dp))
    NkSub(
        if (iAmGroom) "The Mahr is given by the groom, to the bride — it is her right and her honour, a symbol of his commitment and respect."
        else "The Mahr is given by the groom, to the bride — your right and your honour, a symbol of his commitment and respect.",
    )
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        NkPill("🤵", "${nkFirst(state.groomCertName)} · the groom gives")
        Text("  →  ", color = NkGold, fontSize = 15.sp)
        NkPill("👰", "${nkFirst(state.brideCertName)} · the bride receives")
    }
    Spacer(Modifier.height(18.dp))
    if (iAmGroom) {
        if (state.mahrPhase == "proposed") {
            NkWaiting("You offered ${state.mahrText} — waiting for ${nkFirst(state.brideCertName)} to accept…")
            return
        }
        // chips (the groom's pick streams to the bride on every tap)
        val opts = NikahSteps.MAHR_OPTIONS
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                opts.take(3).forEach { o ->
                    NkChip(o, selected = !customMode && state.mahrText == o) {
                        customMode = false
                        cb.onPickMahr(o)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                opts.drop(3).forEach { o ->
                    NkChip(o, selected = !customMode && state.mahrText == o) {
                        customMode = false
                        cb.onPickMahr(o)
                    }
                }
                NkChip("A promise from the heart…", selected = customMode) { customMode = true }
            }
        }
        if (customMode) {
            Spacer(Modifier.height(14.dp))
            NkField("Write the promise", customText, "e.g. A trip to Makkah together, one day") {
                customText = it
                customTouched = true
            }
        }
        Spacer(Modifier.height(20.dp))
        NkButton(
            "Offer the Mahr",
            enabled = gateOpen && state.mahrText.isNotBlank(),
        ) { cb.onProposeMahr() }
    } else {
        // Bride: watch the groom's pick stream in, then accept or ask again.
        NkCouplePair(small = true)
        Spacer(Modifier.height(16.dp))
        when (state.mahrPhase) {
            "proposed" -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0x26FF4F7B), Color.White.copy(0.025f)),
                            ),
                        )
                        .border(1.dp, Color(0x38FF9DB5), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${nkFirst(state.groomCertName)} · the groom offers", color = NkGold,
                        fontSize = 13.sp, fontFamily = InterFamily, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "“For you, ${nkFirst(state.brideCertName)} — a Mahr of ${state.mahrText}, from my heart.”",
                        color = Color.White, fontSize = 22.sp, fontFamily = SerifRegular,
                        textAlign = TextAlign.Center, lineHeight = 31.sp,
                    )
                }
                Spacer(Modifier.height(18.dp))
                NkButton("Accept the Mahr", enabled = gateOpen) { cb.onAcceptMahr() }
                Spacer(Modifier.height(10.dp))
                NkGhostButton("Request a change") { if (gateOpen) cb.onRequestMahrChange() }
            }
            else -> {
                NkStatusLine(
                    if (state.mahrText.isBlank()) "${nkFirst(state.groomCertName)} is choosing your Mahr…"
                    else "${nkFirst(state.groomCertName)} is choosing your Mahr… so far: ${state.mahrText}",
                    waiting = true,
                )
            }
        }
    }
}

/** v4's flow pill; the side's iOS emoji glyph leads (owner rule). */
@Composable
private fun NkPill(emoji: String, text: String) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(0.055f))
            .border(1.dp, NkLine, CircleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        EmojiArt(emoji, 15.dp)
        Text(text, color = Color(0xFFF5F5F7), fontSize = 13.sp, fontFamily = InterFamily,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NkChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .then(
                if (selected) Modifier.background(NkRose)
                else Modifier.background(Color.White.copy(0.055f))
                    .border(1.dp, NkLine, CircleShape),
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontFamily = InterFamily,
            fontWeight = FontWeight.Medium)
    }
}

/** ROUND 65 (v4 Qabool): script card in sentence case, a turn tag,
 *  the great rose orb — "Qabool hai" in Instrument Serif with TWO
 *  thin gold breathe rings (staggered) while it is live; the dim
 *  waiting state drops the rings entirely and shows the emoji pair
 *  (owner rule: the couple's representation), the serif wait-line
 *  with the ACTIVE partner's REAL first name, and a live status
 *  line counting the acceptances. */
@Composable
private fun NkStepQabool(
    state: NikahUiState, groomTurn: Boolean, myUid: String,
    gateOpen: Boolean, cb: NikahCallbacks, motion: Boolean = true,
) {
    val activeUid = if (groomTurn) state.groomUid else state.brideUid
    val activeFirst = nkFirst(if (groomTurn) state.groomCertName else state.brideCertName)
    val count = if (groomTurn) state.groomQabool else state.brideQabool
    val mine = myUid == activeUid
    NkHeading("Ijab-o-*Qabool*")
    Spacer(Modifier.height(9.dp))
    NkSub("The heart of the Nikah — the offer and the acceptance, spoken in one gathering, before the witnesses.")
    Spacer(Modifier.height(16.dp))
    // the offer card (the prototype's script card)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0x26FF4F7B), Color.White.copy(0.025f)),
                ),
            )
            .border(1.dp, Color(0x38FF9DB5), RoundedCornerShape(28.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (groomTurn) "The Wali offers" else "The bride is asked",
            color = NkGold, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            fontFamily = InterFamily,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (groomTurn) {
                "“I give ${state.brideCertName} to you, ${state.groomCertName}, in Nikah, " +
                    "with a Mahr of ${state.mahrText}. Do you accept?”"
            } else {
                "“${state.brideCertName}, ${state.groomCertName} has accepted you in Nikah " +
                    "with a Mahr of ${state.mahrText}. Do you accept him as your husband?”"
            },
            color = Color.White, fontSize = 23.sp, fontFamily = SerifRegular,
            textAlign = TextAlign.Center, lineHeight = 32.sp,
        )
    }
    Spacer(Modifier.height(22.dp))
    if (mine) {
        Text(
            "$activeFirst · ${if (groomTurn) "the groom" else "the bride"} — your turn",
            color = NkSubC, fontSize = 13.sp, fontFamily = InterFamily,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(18.dp))
    } else {
        NkCouplePair(small = true)
        Spacer(Modifier.height(20.dp))
    }
    // the orb — breathe rings only while live (never in the dim state)
    Box(contentAlignment = Alignment.Center) {
        if (mine && motion) {
            val trans = rememberInfiniteTransition(label = "nkBreathe")
            val bp by trans.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(3000)),
                label = "nkBreatheP",
            )
            listOf(bp, (bp + 0.5f) % 1f).forEach { ph ->
                Box(
                    Modifier.size(180.dp).graphicsLayer {
                        val sc = 0.96f + 0.32f * ph
                        scaleX = sc; scaleY = sc
                        alpha = 0.7f * (1f - ph)
                    }.clip(CircleShape)
                        .border(1.dp, NkGold.copy(alpha = 0.55f), CircleShape),
                )
            }
        }
        Box(
            Modifier.size(180.dp).alpha(if (mine) 1f else 0.35f)
                .shadow(if (mine) 34.dp else 0.dp, CircleShape, spotColor = NkRose.copy(alpha = 0.6f))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFA3BA), NkRose, NkRoseDeepV4),
                        center = Offset(110f, 80f), radius = 330f,
                    ),
                )
                .clickable(enabled = mine && gateOpen && count < 3) { cb.onQabool() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Qabool\nhai", color = Color.White, fontSize = 32.sp,
                fontFamily = SerifRegular,
                textAlign = TextAlign.Center, lineHeight = 36.sp,
            )
        }
    }
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        (0 until 3).forEach { i ->
            val filled = count > i
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .then(
                        if (filled) Modifier.background(NkGoldBrush)
                        else Modifier.border(1.5.dp, Color.White.copy(0.14f), CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (filled) {
                    Text("✓", color = Color(0xFF3A2A04), fontSize = 14.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (mine) {
        NkSub(
            when (count) {
                0 -> "Tap and say it — three times, as is the beautiful custom."
                else -> "$count of 3 — say it again, with your whole heart…"
            },
        )
    } else {
        NkTurnLock(activeFirst)
        Spacer(Modifier.height(14.dp))
        NkStatusLine(
            if (count == 0) "Waiting for $activeFirst to begin…"
            else "$activeFirst said Qabool Hai · $count of 3",
            waiting = count == 0,
        )
    }
}

/** ROUND 65 (v4 witnesses): the signing mascot (pure decoration)
 *  above the heading, both witness fields side by side in the flow —
 *  mine editable, my partner's read-only (their name arrives from
 *  THEIR phone through the encrypted session doc; the step advances
 *  in the second submit's transaction, exactly as shipped). */
@Composable
private fun NkStepWitnesses(
    state: NikahUiState, myUid: String, partnerUid: String,
    gateOpen: Boolean, cb: NikahCallbacks, motion: Boolean = true,
) {
    // hoisted above the conditional branches (hook-order discipline)
    var witText by remember { mutableStateOf("") }
    NikahMascot("mascot_signing.webp", small = true, motion = motion)
    Spacer(Modifier.height(12.dp))
    NkHeading("The *Witnesses*")
    Spacer(Modifier.height(9.dp))
    NkSub("A Nikah is never hidden — it is witnessed. Two upright witnesses stand guard over this promise.")
    Spacer(Modifier.height(14.dp))
    NkCard {
        Text(
            "“There is no Nikah except with a guardian and two upright witnesses.” Each of you names one witness — their names live on the Nikah Nama forever.",
            color = Color(0xFFC9C9D1), fontSize = 13.5.sp, fontFamily = InterFamily, lineHeight = 21.sp,
        )
    }
    Spacer(Modifier.height(18.dp))
    val iAmGroom = myUid == state.groomUid
    val myWitness = state.witnessNameBySide[myUid] ?: ""
    val partnerWitness = state.witnessNameBySide[partnerUid] ?: ""
    val partnerFirst = nkFirst(state.certNameOf(partnerUid))
    if (myWitness.isNotBlank()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Your witness · ${if (iAmGroom) "Groom's side" else "Bride's side"}",
                color = NkSubC, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                fontFamily = InterFamily, modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(0.065f))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(myWitness, color = Color.White, fontSize = 16.sp, fontFamily = InterFamily)
            }
        }
    } else {
        NkField(
            "Your witness · ${if (iAmGroom) "Groom's side" else "Bride's side"}",
            witText, "Full name",
        ) { witText = it }
        Spacer(Modifier.height(16.dp))
        NkButton("This witness is present", enabled = gateOpen && witText.isNotBlank()) {
            cb.onSubmitWitness(witText)
        }
    }
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth()) {
        Text(
            "$partnerFirst's witness · ${if (iAmGroom) "Bride's side" else "Groom's side"}",
            color = NkSubC, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            fontFamily = InterFamily, modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(0.04f))
                .border(1.dp, NkLine, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                partnerWitness.ifBlank { "Arriving from $partnerFirst's phone…" },
                color = if (partnerWitness.isNotBlank()) Color.White else Color(0xFF5A5A64),
                fontSize = 16.sp, fontFamily = InterFamily,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    if (partnerWitness.isNotBlank()) {
        NkStatusLine("$partnerFirst's witness has arrived", waiting = false, check = true)
    } else {
        NkStatusLine("Waiting for $partnerFirst's witness…", waiting = true)
    }
}

/** ROUND 65 (v4 signatures): my pad is v4's cream canvas with dark
 *  ink (the pad view's stroke colour changed to match — Nikah.kt);
 *  my partner's box shows their REAL signature image once it arrives
 *  through the encrypted doc (cream, "✓ received"), otherwise the
 *  dark waiting box. The step advances in the second submit's
 *  transaction, as shipped. */
@Composable
private fun NkStepSignatures(
    state: NikahUiState, myUid: String, partnerUid: String,
    gateOpen: Boolean, cb: NikahCallbacks, motion: Boolean = true,
) {
    // pad + ink state hoisted above the conditional branches (hook order)
    val ctx = LocalContext.current
    val pad = remember { NikahSignatureView(ctx) }
    var hasInk by remember { mutableStateOf(false) }
    DisposableEffect(pad) {
        pad.onInkChanged = { hasInk = it }
        onDispose { pad.onInkChanged = null }
    }
    NikahMascot("mascot_signing.webp", small = true, motion = motion)
    Spacer(Modifier.height(12.dp))
    NkHeading("Sign the *Nikah Nama*")
    Spacer(Modifier.height(9.dp))
    NkSub("With your own hand — the signature that seals the covenant.")
    Spacer(Modifier.height(16.dp))
    val partnerFirst = nkFirst(state.certNameOf(partnerUid))
    val partnerSig = state.sigPngB64BySide[partnerUid] ?: ""
    val mySigned = state.sigDoneBySide[myUid] == true
    val partnerIsBride = partnerUid == state.brideUid
    if (mySigned) {
        NkCard {
            Text("✓ You have signed", color = NkGold, fontSize = 14.sp,
                fontFamily = InterFamily, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your signature · ${state.certNameOf(myUid)}",
                color = NkSubC, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(
                "Clear", color = NkGold, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                modifier = Modifier.clickable { pad.clear() }.padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        AndroidView(
            factory = { pad },
            modifier = Modifier.fillMaxWidth().height(118.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFFBF5E8))
                .border(1.dp, NkAntiqueGold.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(16.dp))
        NkButton("Sign", enabled = gateOpen && hasInk) {
            pad.exportPng()?.let { cb.onSubmitSignature(it) }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(
        "${state.certNameOf(partnerUid)} · ${if (partnerIsBride) "Bride" else "Groom"}'s signature",
        color = NkSubC, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        fontFamily = InterFamily, modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
    )
    Spacer(Modifier.height(8.dp))
    val partnerBmp = remember(partnerSig) {
        if (partnerSig.isBlank()) null
        else runCatching {
            val bytes = Crypto.unb64(partnerSig)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (partnerBmp != null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFFBF5E8))
                .border(1.dp, NkAntiqueGold.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                partnerBmp.asImageBitmap(), null,
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color(0xFF5A2333)),
            )
            Text("✓ received", color = Color(0xFF2E7D4F), fontSize = 12.sp,
                fontFamily = InterFamily, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Box(
            Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(0.055f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$partnerFirst is signing on ${if (partnerIsBride) "her" else "his"} phone",
                color = NkSubC, fontSize = 13.sp, fontFamily = InterFamily,
            )
        }
    }
}

@Composable
private fun NkStepDua(state: NikahUiState, myUid: String, gateOpen: Boolean, cb: NikahCallbacks, motion: Boolean = true) {
    NikahMascot("mascot_celebrate.webp", motion = motion)
    Spacer(Modifier.height(12.dp))
    NkHeading("*Mubarak!*")
    Spacer(Modifier.height(12.dp))
    NkCard {
        Text(
            "بَارَكَ اللهُ لَكُمَا وَبَارَكَ عَلَيْكُمَا وَجَمَعَ بَيْنَكُمَا فِي خَيْرٍ",
            color = Color(0xFFEFDDB0), fontSize = 28.sp, lineHeight = 54.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Barakallahu lakuma, wa baraka alaikuma, wa jama'a bainakuma fi khair.",
            color = NkRoseSoft, fontSize = 17.sp, fontFamily = SerifItalic,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center, lineHeight = 25.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "\"May Allah bless you both, shower His blessings upon you, and unite you both in goodness.\"",
            color = Color(0xFFC9C9D1), fontSize = 13.5.sp, fontFamily = InterFamily,
            textAlign = TextAlign.Center, lineHeight = 21.sp,
        )
    }
    Spacer(Modifier.height(14.dp))
    NkSub("By the mercy of Allah, ${state.groomCertName} and ${state.brideCertName} are now husband and wife. May your days be filled with sakina, mawaddah and rahmah. Ameen.")
    Spacer(Modifier.height(16.dp))
    NkAmeenBoxes(state, myUid, state.dua)
    Spacer(Modifier.height(20.dp))
    if (state.dua[myUid] == true) {
        val partnerUid = if (myUid == state.groomUid) state.brideUid else state.groomUid
        NkButton("Waiting for ${nkFirst(state.certNameOf(partnerUid))}'s Ameen…", enabled = false) {}
    } else {
        NkButton("Ameen", enabled = gateOpen) { cb.onDuaAmeen() }
    }
}

@Composable
private fun NkStepNama(state: NikahUiState, cb: NikahCallbacks, motion: Boolean = true) {
    NikahMascot("mascot_celebrate.webp", xs = true, motion = motion)
    Spacer(Modifier.height(12.dp))
    NkHeading("Your *Nikah Nama*")
    Spacer(Modifier.height(4.dp))
    NikahCertificateCard(state.keepsake())
    Spacer(Modifier.height(16.dp))
    Text(
        "A symbolic keepsake of love, created inside Tiku. This certificate is rendered on both phones and saved as an encrypted keepsake in your chat — open it any time to see it again.",
        color = NkSubC, fontSize = 12.sp, fontFamily = InterFamily,
        textAlign = TextAlign.Center, lineHeight = 18.sp,
    )
    Spacer(Modifier.height(18.dp))
    NkButton("Done") { cb.onClose() }
}

/** Confetti in the prototype's colours — a single deterministic burst
 *  over the Dua screen (drawn, never interactive). */
@Composable
private fun NikahConfetti() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3200)) }
    val colors = remember {
        listOf(NkRose, NkRoseSoft, NkGold, Color.White, Color(0xFFFFD9E2))
    }
    val parts = remember {
        kotlin.random.Random(60).let { r ->
            List(110) {
                floatArrayOf(
                    r.nextFloat(), r.nextFloat() * 0.45f,
                    5f + r.nextFloat() * 6f, 8f + r.nextFloat() * 8f,
                    r.nextFloat(),
                ) to colors[it % colors.size]
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        parts.forEach { (f, color) ->
            val y = ((p * 1.25f - f[1]) * size.height)
            if (y < -20f || y > size.height + 20f) return@forEach
            val x = f[0] * size.width + (f[4] - 0.5f) * 60f * p
            drawRect(
                color = color.copy(alpha = 0.9f),
                topLeft = Offset(x, y),
                size = Size(f[2] * 2f, f[3] * 2f),
            )
        }
    }
}

// ---------------- certificate ----------------

/** The Nikah Nama — v4's classic cream paper document floating on
 *  the dark app, rendered on-device from the decrypted
 *  state/payload. ONE renderer for the ceremony's final step AND
 *  the keepsake re-render in the chat. Cream paper (#F7EAD2 family),
 *  maroon #6E1423 + antique gold #B8862F, double-rule border with
 *  gold eight-point-star corner ornaments, an arched header, the
 *  couple's REAL names + fathers, a ruled two-column grid (Mahr /
 *  Gregorian date / Hijri date / venue / witnesses / officiated
 *  by), the couple's REAL drawn signatures over their rules, the
 *  round maroon seal, and a deterministic certificate number.
 *  Keepsakes saved by older builds carry no father names: the
 *  SON OF / DAUGHTER OF line then falls back to "THE GROOM" /
 *  "THE BRIDE" under the (display) names already in the payload. */
@Composable
fun NikahCertificateCard(k: NikahKeepsake, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFCF4DF), NkCream, Color(0xFFF2E1BE)),
                ),
            )
            .border(2.5.dp, NkAntiqueGold, RoundedCornerShape(8.dp)),
    ) {
        // double-rule border (v4 ::before / ::after)
        Box(
            Modifier.matchParentSize().padding(7.dp)
                .border(1.dp, NkMaroon.copy(alpha = 0.55f), RoundedCornerShape(5.dp)),
        )
        Box(
            Modifier.matchParentSize().padding(10.5.dp)
                .border(1.dp, NkAntiqueGold.copy(alpha = 0.75f), RoundedCornerShape(4.dp)),
        )
        // gold eight-point-star corner ornaments
        listOf(
            Alignment.TopStart, Alignment.TopEnd,
            Alignment.BottomStart, Alignment.BottomEnd,
        ).forEach { align ->
            Box(
                Modifier.align(align).padding(2.dp).size(37.dp).clip(CircleShape)
                    .background(Color(0xFFF8EDD3)),
                contentAlignment = Alignment.Center,
            ) { NkStar8(Modifier.size(31.dp)) }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- arched header ----
            Box(
                Modifier.fillMaxWidth().clip(
                    RoundedCornerShape(topStart = 150.dp, topEnd = 150.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                ).border(
                    1.6.dp, Color(0xFF96712A),
                    RoundedCornerShape(topStart = 150.dp, topEnd = 150.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                ).padding(3.5.dp)
                    .clip(
                        RoundedCornerShape(topStart = 146.dp, topEnd = 146.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                    ).border(
                        1.3.dp, NkMaroon.copy(alpha = 0.5f),
                        RoundedCornerShape(topStart = 146.dp, topEnd = 146.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 18.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                        color = NkMaroon, fontSize = 19.5.sp, lineHeight = 31.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "عقد نكاح",
                        color = NkMaroon, fontSize = 40.sp, lineHeight = 54.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                    Text(
                        "NIKAH NAMA", color = NkMaroon, fontSize = 29.sp,
                        fontFamily = SerifRegular, letterSpacing = 3.8.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CERTIFICATE OF ISLAMIC MARRIAGE", color = Color(0xFF96712A),
                        fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                        fontFamily = InterFamily, letterSpacing = 2.8.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "وَمِنْ آيَاتِهِ أَنْ خَلَقَ لَكُم مِّنْ أَنفُسِكُمْ أَزْوَاجًا لِّتَسْكُنُوا إِلَيْهَا وَجَعَلَ بَيْنَكُم مَّوَدَّةً وَرَحْمَةً",
                color = Color(0xFF5A2A20), fontSize = 19.5.sp, lineHeight = 40.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "SURAH AR-RUM · 30:21", color = Color(0xFF96712A), fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = InterFamily,
                letterSpacing = 1.4.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(13.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(NkAntiqueGold.copy(alpha = 0.85f)))
                Text("  ✦  ", color = Color(0xFF96712A), fontSize = 11.sp)
                Box(Modifier.weight(1f).height(1.dp).background(NkAntiqueGold.copy(alpha = 0.85f)))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "This is to certify that the Nikah of",
                color = Color(0xFF5A3A35), fontSize = 16.sp, fontFamily = SerifItalic,
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                k.groomName.uppercase(), color = NkMaroon, fontSize = 28.sp,
                fontFamily = SerifRegular, letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center, lineHeight = 35.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (k.groomFather.isNotBlank()) "SON OF ${k.groomFather.uppercase()} · GROOM" else "THE GROOM",
                color = Color(0xFF7A5A33), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily, letterSpacing = 2.2.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "was solemnised with",
                color = Color(0xFF5A3A35), fontSize = 15.5.sp, fontFamily = SerifItalic,
                fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                k.brideName.uppercase(), color = NkMaroon, fontSize = 28.sp,
                fontFamily = SerifRegular, letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center, lineHeight = 35.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (k.brideFather.isNotBlank()) "DAUGHTER OF ${k.brideFather.uppercase()} · BRIDE" else "THE BRIDE",
                color = Color(0xFF7A5A33), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily, letterSpacing = 2.2.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // ---- ruled two-column grid ----
            val hijri = if (k.dateMs > 0L) nikahHijriStr(k.dateMs) else null
            val cells = mutableListOf<Pair<String, String>>()
            cells += "Mahr (Dower)" to k.mahr
            cells += "Date of Nikah" to (if (k.dateMs > 0L) nikahCertDateStr(k.dateMs) else "")
            if (hijri != null) cells += "Hijri Date" to hijri
            cells += "Venue" to "Online ceremony, conducted on Tiku"
            cells += "Witnesses" to listOf(k.witnessGroom, k.witnessBride)
                .filter { it.isNotBlank() }.joinToString(" · ")
            cells += "Officiated By" to "Tiku — in-app ceremony"
            cells.chunked(2).forEach { rowCells ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    rowCells.forEach { (label, value) ->
                        Column(
                            Modifier.weight(1f)
                                .border(0.dp, Color.Transparent)
                                .padding(vertical = 7.dp),
                        ) {
                            Text(
                                label.uppercase(), color = Color(0xFF96712A), fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold, fontFamily = InterFamily,
                                letterSpacing = 1.7.sp,
                            )
                            Spacer(Modifier.height(2.5.dp))
                            Text(
                                value, color = Color(0xFF46202A), fontSize = 15.sp,
                                fontFamily = SerifRegular, lineHeight = 21.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp)
                                .background(NkAntiqueGold.copy(alpha = 0.5f)))
                        }
                    }
                    if (rowCells.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(22.dp))
            // ---- signatures + seal ----
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                NkCertSignature(k.sigGroomPngB64, "Signature of the Groom", Modifier.weight(1f))
                Box(
                    Modifier.padding(horizontal = 2.dp).size(86.dp).rotate(-8f)
                        .shadow(10.dp, CircleShape, spotColor = NkMaroonDeep.copy(alpha = 0.45f))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF8E2236), NkMaroon, NkMaroonDeep),
                                center = Offset(60f, 50f), radius = 150f,
                            ),
                        )
                        .border(2.dp, NkAntiqueGold, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NkCrescent(Modifier.size(25.dp))
                        Text(
                            "TIKU", color = Color(0xFFF0D48A), fontSize = 8.sp,
                            fontWeight = FontWeight.Bold, fontFamily = InterFamily,
                            letterSpacing = 1.4.sp, textAlign = TextAlign.Center,
                        )
                        Text(
                            "NIKAH", color = Color(0xFFF0D48A).copy(alpha = 0.85f),
                            fontSize = 6.5.sp, fontFamily = InterFamily,
                            letterSpacing = 1.6.sp, textAlign = TextAlign.Center,
                        )
                    }
                }
                NkCertSignature(k.sigBridePngB64, "Signature of the Bride", Modifier.weight(1f))
            }
            Spacer(Modifier.height(15.dp))
            Text(
                "CERTIFICATE NO. ${nikahCertNo(k)}",
                color = Color(0xFF96712A), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily, letterSpacing = 2.5.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/** The gold eight-point star of the certificate's corner ornaments:
 *  two squares, one rotated 45°, around a filled centre dot. */
@Composable
private fun NkStar8(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val inset = w * 0.25f
        val rect = androidx.compose.ui.geometry.Rect(inset, inset, w - inset, h - inset)
        val stroke = Stroke(width = w * 0.045f)
        drawRect(NkAntiqueGold, topLeft = rect.topLeft,
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height), style = stroke)
        with(drawContext.canvas) {
            save()
            rotate(45f, rect.center)
            drawRect(NkAntiqueGold, topLeft = rect.topLeft,
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height), style = stroke)
            restore()
        }
        drawCircle(NkAntiqueGold, radius = w * 0.08f, center = center)
    }
}

/** The seal's gold crescent + star. */
@Composable
private fun NkCrescent(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gold = Color(0xFFF0D48A)
        val r = size.minDimension * 0.42f
        val c = Offset(size.width * 0.46f, size.height * 0.54f)
        val moon = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
        }
        val bite = androidx.compose.ui.graphics.Path().apply {
            val br = r * 0.82f
            val bc = Offset(c.x + r * 0.42f, c.y - r * 0.34f)
            addOval(androidx.compose.ui.geometry.Rect(bc.x - br, bc.y - br, bc.x + br, bc.y + br))
        }
        val crescent = androidx.compose.ui.graphics.Path().apply {
            op(moon, bite, PathOperation.Difference, this)
        }
        drawPath(crescent, gold)
        // small four-point star at the crescent's upper right
        val sc = Offset(size.width * 0.74f, size.height * 0.24f)
        val sr = size.minDimension * 0.16f
        val star = androidx.compose.ui.graphics.Path().apply {
            moveTo(sc.x, sc.y - sr)
            quadraticBezierTo(sc.x, sc.y, sc.x + sr, sc.y)
            quadraticBezierTo(sc.x, sc.y, sc.x, sc.y + sr)
            quadraticBezierTo(sc.x, sc.y, sc.x - sr, sc.y)
            quadraticBezierTo(sc.x, sc.y, sc.x, sc.y - sr)
            close()
        }
        drawPath(star, gold)
    }
}

@Composable
private fun NkCertSignature(pngB64: String, label: String, modifier: Modifier = Modifier) {
    val bmp = remember(pngB64) {
        if (pngB64.isBlank()) null
        else runCatching {
            val bytes = Crypto.unb64(pngB64)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.BottomCenter) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 4.dp),
                    contentScale = ContentScale.Fit,
                    // signature ink on paper is the deep maroon-brown,
                    // whatever colour the pad strokes were drawn in
                    colorFilter = ColorFilter.tint(Color(0xFF5A2333)),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(1.2.dp).background(NkMaroon.copy(alpha = 0.65f)))
        Spacer(Modifier.height(4.5.dp))
        Text(
            label.uppercase(), color = Color(0xFF7A5A33), fontSize = 8.sp,
            fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp,
        )
    }
}

/** Full-screen keepsake view (from the chat's keepsake card) — the
 *  same v4 certificate screen as the ceremony's final step. */
@Composable
fun NikahCertificateOverlay(k: NikahKeepsake, onClose: () -> Unit) {
    BackHandler { onClose() }
    Box(
        Modifier.fillMaxSize().background(NkBg)
            .clickable { onClose() },
    ) {
        NkBackdrop {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NikahMascot("mascot_celebrate.webp", xs = true, motion = false)
                Spacer(Modifier.height(12.dp))
                NkHeading("Your *Nikah Nama*")
                Spacer(Modifier.height(4.dp))
                NikahCertificateCard(k)
                Spacer(Modifier.height(16.dp))
                Text(
                    "A symbolic keepsake of love, created inside Tiku — saved privately in this chat.",
                    color = NkSubC, fontSize = 12.sp, fontFamily = InterFamily,
                    textAlign = TextAlign.Center, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(18.dp))
                NkGhostButton("Close", onClose)
            }
        }
    }
}

// ---------------- chat cards / banner / starter ----------------

/** The invite CARD rendered in the thread in place of the fallback text
 *  bubble (old builds see the fallback line — see TikuMessage.nikah). */
@Composable
fun NikahInviteRow(
    m: TikuMessage, session: NikahUiState?, peerName: String, onOpen: () -> Unit,
) {
    val stateText = when {
        m.nikahState == "cancelled" || session?.status == "cancelled" -> "cancelled"
        m.nikahState == "done" || session?.status == "done" -> "done"
        else -> "active"
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(292.dp).clip(RoundedCornerShape(22.dp))
                .background(CardC).border(1.dp, NkGold.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("💍", fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text("Nikah Invitation", color = Color.White, fontSize = 16.sp,
                fontFamily = SerifRegular)
            Spacer(Modifier.height(4.dp))
            Text(
                if (m.me) "You invited $peerName to a Nikah ceremony"
                else "$peerName invited you to a Nikah ceremony",
                color = Sub, fontSize = 12.5.sp, fontFamily = PoppinsFamily,
                textAlign = TextAlign.Center, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(13.dp))
            when (stateText) {
                "cancelled" -> Text("Cancelled", color = Danger, fontSize = 13.sp,
                    fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold)
                "done" -> Text("Nikah complete 💛", color = NkGold, fontSize = 13.sp,
                    fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold)
                else -> Box(
                    Modifier.clip(CircleShape).background(NkRoseBrush)
                        .clickable { onOpen() }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(
                        if (m.me) "Open the Nikah" else "Join the Nikah",
                        color = Color.White, fontSize = 13.5.sp,
                        fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** The keepsake CARD: the forever copy of the certificate, carried
 *  inside the message itself (see NikahKeepsake). */
@Composable
fun NikahKeepsakeRow(m: TikuMessage, onView: () -> Unit) {
    val k = m.nikahKeepsake
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(292.dp).clip(RoundedCornerShape(22.dp))
                .background(CardC).border(1.dp, NkGold.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("💍", fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (k != null) "Nikah complete — ${
                    SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(k.dateMs))
                }" else m.text,
                color = Color.White, fontSize = 15.sp, fontFamily = SerifRegular,
                textAlign = TextAlign.Center,
            )
            if (k != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${k.groomName}  ✦  ${k.brideName}",
                    color = NkGold, fontSize = 12.5.sp, fontFamily = PoppinsFamily,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(13.dp))
                Box(
                    Modifier.clip(CircleShape).background(NkGoldBrush)
                        .clickable { onView() }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(
                        "View certificate", color = Color(0xFF3A2A04), fontSize = 13.5.sp,
                        fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** The rejoin banner shown over the composer while a ceremony is live. */
@Composable
fun NikahProgressBanner(onTap: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardC).border(1.dp, NkGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💍", fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Nikah in progress — tap to rejoin",
            color = Color.White, fontSize = 13.5.sp, fontFamily = PoppinsFamily,
            modifier = Modifier.weight(1f),
        )
        Text("→", color = NkGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Role pick shown when "Nikah" is tapped with no live ceremony:
 *  v4's role screen compressed into the sheet — the couple as an
 *  emoji pair in story-rings (owner rule; v4's header there is the
 *  couple portrait, which represents the couple), the great serif
 *  title, and the two role cards (glyph + serif name + Dulha/Dulhan)
 *  with a Continue that fires only once a role is chosen. The
 *  creator declares their own role; the partner is the other role. */
@Composable
fun NikahStarterSheet(
    myName: String, peerName: String,
    onPick: (String) -> Unit, onDismiss: () -> Unit,
) {
    var role by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF101014)).border(1.dp, NkLine, RoundedCornerShape(28.dp))
                .clickable { }
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NkCouplePair()
            Spacer(Modifier.height(10.dp))
            Text(
                "Nikah",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFC9D6))),
                    fontFamily = SerifRegular, fontSize = 54.sp,
                ),
                textAlign = TextAlign.Center,
            )
            NkRoseRule()
            NkSub("Two hearts, one beautiful promise — a joyful Islamic wedding ceremony, right here inside Tiku, wherever in the world you both are.")
            Spacer(Modifier.height(8.dp))
            NkSub("A live ceremony with $peerName, on both phones together. First — who are you in this Nikah?")
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NkRoleCard("🤵", "I am the Groom", "Dulha", role == "groom", Modifier.weight(1f)) {
                    role = "groom"
                }
                NkRoleCard("👰", "I am the Bride", "Dulhan", role == "bride", Modifier.weight(1f)) {
                    role = "bride"
                }
            }
            Spacer(Modifier.height(22.dp))
            NkButton("Continue", enabled = role.isNotEmpty()) { onPick(role) }
            Spacer(Modifier.height(6.dp))
            Text(
                "Cancel", color = NkSubC, fontSize = 13.sp, fontFamily = InterFamily,
                modifier = Modifier.clickable { onDismiss() }.padding(10.dp),
            )
        }
    }
}

/** v4's role card: iOS emoji glyph (owner rule), serif name, quiet
 *  sub; the selected card warms rose with a filled check dot. */
@Composable
private fun NkRoleCard(
    emoji: String, name: String, sub: String,
    selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(26.dp))
            .background(
                if (selected) Brush.linearGradient(
                    listOf(Color(0x33FF4F7B), Color.White.copy(0.03f)),
                ) else Brush.linearGradient(
                    listOf(Color.White.copy(0.055f), Color.White.copy(0.055f)),
                ),
            )
            .border(
                1.dp,
                if (selected) NkRose.copy(alpha = 0.7f) else NkLine,
                RoundedCornerShape(26.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 24.dp, horizontal = 12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmojiArt(emoji, 36.dp)
            Spacer(Modifier.height(10.dp))
            Text(name, color = Color.White, fontSize = 21.sp, fontFamily = SerifRegular,
                textAlign = TextAlign.Center, lineHeight = 25.sp)
            Spacer(Modifier.height(4.dp))
            Text(sub, color = NkSubC, fontSize = 13.sp, fontFamily = InterFamily,
                textAlign = TextAlign.Center)
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape)
                .then(
                    if (selected) Modifier.background(NkRose)
                    else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

