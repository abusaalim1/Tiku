package com.tiku.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ROUND 12 — the Tik-section design system, converted 1:1 from the owner's
 * HTML design (Tikuu_11): dark tokens, component metrics and icon path data
 * are the file's own values. The study facade / gate / onboarding keep the
 * light-peach TikuTokens in TikuTheme.kt — nothing here is used by them.
 */
object Tik {
    // :root — --bg --s1 --s2 --ln --tx --sub --ac
    val Bg = Color(0xFF000000)
    val S1 = Color(0xFF121214)
    val S2 = Color(0xFF1C1C1F)
    val Ln = Color(0xFF242428)
    val Tx = Color(0xFFF5F5F7)
    val Sub = Color(0xFF8B8B93)
    val AccentDefault = Color(0xFFE5677F)

    // states / misc from the stylesheet
    val Danger = Color(0xFFFF6B6B)      // .er, .rd2
    val RecDot = Color(0xFFFF5A6A)      // .rd recording dot
    val TrackOff = Color(0xFF34343A)    // .tg off track
    val OutlineBtn = Color(0xFF3A3A40)  // .ob / .pb.o border
    val Grabber = Color(0xFF48484E)     // .gr
    val CheckBorder = Color(0xFF55555B) // .ck border
    val Scrim = Color(0xAA000000)       // .shw background #000a
    val CtxScrim = Color(0xBD000000)    // #cx .cxb rgba(0,0,0,.74)
    val RowActive = Color(0x0AFFFFFF)   // .row:active #ffffff0a
    val InsetRing = Color(0x26FFFFFF)   // .sw i box-shadow inset #fff2

    /** WALL swatches by WallpaperMode ordinal (design order maps: plain, peach, dots, couple). */
    val WallColors = listOf(
        Color(0xFF000000), // DEFAULT
        Color(0xFF0F0E18), // PLAIN
        Color(0xFF120C0F), // PEACH
        Color(0xFF0B1311), // DOTS
        Color(0xFF1A1214), // COUPLE
    )

    /** AVC — the design's letter-avatar palette. */
    val AvatarColors = listOf(
        Color(0xFFB95B72), Color(0xFFB07A5C), Color(0xFF7D6FA8),
        Color(0xFF4F8F7E), Color(0xFF5B86B8), Color(0xFF9A6B8A),
    )
    val AvatarArgb = listOf(
        0xFFB95B72, 0xFFB07A5C, 0xFF7D6FA8, 0xFF4F8F7E, 0xFF5B86B8, 0xFFC9B13A,
    )

    /** Context-menu reactions, design order (EM). */
    val Reactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍")
}

/** --e: cubic-bezier(.22,.8,.22,1) — the design's screen/sheet easing. */
val TikEase = CubicBezierEasing(0.22f, 0.8f, 0.22f, 1f)

/** rp keyframes' curve: cubic-bezier(.3,1.6,.5,1) — reaction pop overshoot. */
val TikPopEase = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)

/** .couple bubble gradient end: color-mix(ac 70%, #5A2A8A). */
fun coupleGradientEnd(ac: Color): Color {
    val p = Color(0xFF5A2A8A)
    return Color(
        red = ac.red * 0.7f + p.red * 0.3f,
        green = ac.green * 0.7f + p.green * 0.3f,
        blue = ac.blue * 0.7f + p.blue * 0.3f,
    )
}

// ---------------------------------------------------------------------------
// Icons — the design's own SVG path data (24-grid, stroke 1.6, round caps).
// ---------------------------------------------------------------------------

private val TikPathCache = HashMap<String, Path>()
private fun tikPath(data: String): Path =
    TikPathCache.getOrPut(data) { PathParser().parsePathString(data).toPath() }

/**
 * Draws one of the design's icons. [name] is the design's own key:
 * msg send call vid play set back edit chev chevR srch user bell bellOff
 * dots cam mic img plus music pin heart trash reply copy check x.
 */
@Composable
fun TikIcon(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = Tik.Tx,
    strokeWidth: Float = 1.6f,
) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        fun sx(v: Float) = v * u
        val stroke = Stroke(width = strokeWidth * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(data: String) = drawPath(tikPath(data), tint, style = stroke)
        fun circle(cx: Float, cy: Float, r: Float) =
            drawCircle(tint, radius = r * u, center = androidx.compose.ui.geometry.Offset(sx(cx), sx(cy)), style = stroke)
        fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(
            tint,
            topLeft = androidx.compose.ui.geometry.Offset(sx(x), sx(y)),
            size = androidx.compose.ui.geometry.Size(sx(w), sx(h)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(sx(r), sx(r)),
            style = stroke,
        )
        when (name) {
            "msg" -> p("M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z")
            "send" -> p("M21 3 10 14M21 3l-7 18-4-7-7-4z")
            "call" -> p("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z")
            "vid" -> { rrect(2f, 6f, 14f, 12f, 3f); p("m16 11 6-3v8l-6-3") }
            "play" -> { rrect(3f, 3f, 18f, 18f, 5f); p("m10 8 6 4-6 4z") }
            "set" -> {
                circle(12f, 12f, 3f)
                p("M19 12a7 7 0 0 0-.1-1.3l2-1.5-2-3.4-2.3 1a7 7 0 0 0-2.2-1.3L14 3h-4l-.4 2.5a7 7 0 0 0-2.2 1.3l-2.3-1-2 3.4 2 1.5a7 7 0 0 0 0 2.6l-2 1.5 2 3.4 2.3-1a7 7 0 0 0 2.2 1.3L10 21h4l.4-2.5a7 7 0 0 0 2.2-1.3l2.3 1 2-3.4-2-1.5c.1-.4.1-.9.1-1.3z")
            }
            "back" -> p("m15 5-7 7 7 7")
            "edit" -> p("M4 20h4L19 9l-4-4L4 16zM14 6l4 4")
            "chev" -> p("m6 9 6 6 6-6")
            "chevR" -> p("m9 6 6 6-6 6")
            "srch" -> { circle(11f, 11f, 7f); p("m21 21-4.3-4.3") }
            "user" -> { circle(12f, 8f, 4f); p("M4 21a8 8 0 0 1 16 0") }
            "bell" -> p("M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4")
            "bellOff" -> p("M6 16v-5a6 6 0 0 1 9-5.2M18 11v5l2 2H4M10 21h4M3 3l18 18")
            "dots" -> listOf(5f, 12f, 19f).forEach { cx ->
                drawCircle(tint, radius = 1.5f * u, center = androidx.compose.ui.geometry.Offset(sx(cx), sx(12f)))
            }
            "cam" -> { p("M4 8h3l2-3h6l2 3h3v12H4z"); circle(12f, 13f, 4f) }
            "mic" -> { rrect(9f, 3f, 6f, 11f, 3f); p("M5 11a7 7 0 0 0 14 0M12 18v3") }
            "img" -> { rrect(3f, 4f, 18f, 16f, 4f); circle(9f, 10f, 1.6f); p("m21 16-5-5-8 8") }
            "plus" -> p("M12 5v14M5 12h14")
            "music" -> { p("M9 18V5l11-2v13"); circle(6f, 18f, 3f); circle(17f, 16f, 3f) }
            "pin" -> { p("M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11z"); circle(12f, 10f, 2.5f) }
            "heart" -> p("M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.5A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z")
            "trash" -> p("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
            "reply" -> p("M9 14 4 9l5-5M4 9h10a6 6 0 0 1 6 6v3")
            "copy" -> { rrect(9f, 9f, 11f, 11f, 3f); p("M5 15V6a2 2 0 0 1 2-2h9") }
            "check" -> p("m5 12 5 5 9-10")
            "x" -> p("M6 6l12 12M18 6 6 18")
            "playFill" -> drawPath(tikPath("M8 5v14l11-7z"), tint)
            "pauseFill" -> {
                drawLine(tint, androidx.compose.ui.geometry.Offset(sx(9f), sx(5f)),
                    androidx.compose.ui.geometry.Offset(sx(9f), sx(19f)), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
                drawLine(tint, androidx.compose.ui.geometry.Offset(sx(15f), sx(5f)),
                    androidx.compose.ui.geometry.Offset(sx(15f), sx(19f)), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
            }
        }
    }
}

/** The design's filled heart (TH path — typing hearts, heart sticker). */
@Composable
fun TikHeart(modifier: Modifier = Modifier, tint: Color = Tik.AccentDefault) {
    Canvas(modifier) {
        drawPath(
            tikPath("M12 21s-8-5.2-8-11.2A4.6 4.6 0 0 1 12 6.6a4.6 4.6 0 0 1 8 3.2C20 15.8 12 21 12 21z"),
            tint,
        )
    }
}

// ---------------------------------------------------------------------------
// Components — metrics copied from the stylesheet.
// ---------------------------------------------------------------------------

/** .btn — bare icon button: padding 6, icon 24, press scale(.88) + opacity .7 (.16s). */
@Composable
fun TikBtn(
    icon: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = Tik.Tx,
    strokeWidth: Float = 1.6f,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.88f else 1f, tween(160, easing = TikEase), label = "btnScale")
    Box(
        modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = if (pressed) 0.7f else 1f
            }
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        TikIcon(icon, Modifier.size(iconSize), tint = tint, strokeWidth = strokeWidth)
    }
}

/** .top — padding 12/18/6, min-height 52; h1 22px w600 ls −.03em. */
@Composable
fun TikTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onTitleLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.offset(x = (-6).dp)) { leading() }
            Spacer(Modifier.width(2.dp))
        }
        Box(
            Modifier.weight(1f).then(
                if (onTitleLongPress != null)
                    Modifier.pointerInput(onTitleLongPress) {
                        detectTapGestures(
                            onLongPress = { onTitleLongPress() },
                        )
                    }
                else Modifier,
            ),
        ) {
            Text22(title)
        }
        trailing?.invoke()
    }
}

@Composable
private fun Text22(title: String) {
    androidx.compose.material3.Text(
        title,
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.66).sp,
        color = Tik.Tx,
    )
}

/** .tg — toggle 44×26 r13, knob 22, on = accent, off = #34343A. */
@Composable
fun TikToggle(checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    val track by animateColorAsState(
        if (checked) accent else Tik.TrackOff, tween(250, easing = TikEase), label = "tgTrack")
    val knobX by animateDpAsState(
        if (checked) 20.dp else 2.dp, tween(300, easing = TikEase), label = "tgKnob")
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** .sw — 24px swatches, gap 9; selected: 2px tx outline offset 2 + scale 1.05. */
@Composable
fun TikSwatches(
    colors: List<Color>,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp)) {
        colors.forEachIndexed { i, c ->
            val isSel = i == selected
            val scale by androidx.compose.animation.core.animateFloatAsState(
                if (isSel) 1.05f else 1f, tween(200, easing = TikEase), label = "swScale")
            Box(
                Modifier
                    .size(28.dp)
                    .then(if (isSel) Modifier.border(2.dp, Tik.Tx, CircleShape) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(c)
                        .border(1.dp, Tik.InsetRing, CircleShape),
                )
            }
        }
    }
}

/** .gt — sheet title: centred, w600, padding 6/0/12. */
@Composable
fun TikSheetTitle(text: String) {
    androidx.compose.material3.Text(
        text,
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = Tik.Tx,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** .pb — primary button: accent bg, #fff, r12, padding 13, w600; .pb.o = outline. */
@Composable
fun TikPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, tween(160, easing = TikEase), label = "pbScale")
    val settings = SettingsStore.state.value
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.5f }
            .clip(RoundedCornerShape(12.dp))
            .background(if (outline || danger) Color.Transparent else settings.accent)
            .then(
                if (outline) Modifier.border(1.dp, Tik.OutlineBtn, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = when {
                danger -> Tik.Danger
                outline -> Tik.Tx
                else -> Color.White
            },
        )
    }
}

/** .fld — field: bg var(--bg), 1px ln border (focus: accent), r12, padding 12/14, margins 0/18/10. */
@Composable
fun TikField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (focused) SettingsStore.state.value.accent else Tik.Ln,
        tween(200, easing = TikEase), label = "fldBorder")
    Box(
        modifier
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tik.Bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(
                fontFamily = Manrope, fontSize = 15.sp, color = Tik.Tx),
            cursorBrush = SolidColor(SettingsStore.state.value.accent),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    androidx.compose.material3.Text(
                        placeholder, fontFamily = Manrope, fontSize = 15.sp, color = Tik.Sub)
                }
                inner()
            },
        )
    }
}

/** .sh — section header: 12.5px w500 sub, margin 22/34/6. */
@Composable
fun TikSectionHeader(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text,
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        color = Tik.Sub,
        modifier = modifier.padding(start = 34.dp, end = 18.dp, top = 22.dp, bottom = 6.dp),
    )
}

/** .grp — settings group card: margin 8/18, bg s1, r14. */
@Composable
fun TikGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Tik.S1),
        content = content,
    )
}

/**
 * .si / .lrow — group row: padding 13/16, border-top ln (except first),
 * title w500 with optional sub (12.5 sub, margin-top 1), trailing slot.
 */
@Composable
fun TikInfoRow(
    title: String,
    sub: String? = null,
    danger: Boolean = false,
    first: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (!first) Modifier.borderTopLn() else Modifier)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                ) { onClick?.invoke() } else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                title,
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = if (danger) Tik.Danger else Tik.Tx,
            )
            if (sub != null) {
                androidx.compose.material3.Text(
                    sub,
                    fontFamily = Manrope,
                    fontSize = 12.5.sp,
                    color = Tik.Sub,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

private fun Modifier.borderTopLn(): Modifier = this.drawWithContent {
        drawContent()
        drawLine(
            Tik.Ln,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
        )
    }


/** .emp — empty state: centred sub text, b = 16 w600 tx block. */
@Composable
fun TikEmpty(title: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 30.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            title,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Tik.Tx,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.material3.Text(
            sub,
            fontFamily = Manrope,
            fontSize = 13.5.sp,
            color = Tik.Sub,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
