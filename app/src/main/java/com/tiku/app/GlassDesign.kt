package com.tiku.app

// ============================================================================
// ROUND 13 — the owner-supplied liquid-glass design system (TikuGlass.kt +
// Tikuapp.kt), ported onto the real app. Palette / themes / icons / pattern /
// glass components are the owner's code, adapted only where the real app
// differs: Inter Tight comes from the bundled fonts, persistence lives in
// SettingsStore (TikuTheme.kt) instead of the demo's TikuPrefs, and a few
// mode-aware extension colours (s1 / rowActive / outlineBtn / grabber /
// trackOff / danger / recDot) were added to the palette because the real
// screens need solid-surface tokens the demo never used.
// Demo DATA (people, messages, notes, requests) was NOT ported — every screen
// renders the real engine's state only.
// ============================================================================

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Screen/sheet easing carried over from the build-44 Tik system. */
val TikEase = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.8f, 0.22f, 1f)

/** Staggered-pop easing (reaction bar), carried over from build 44. */
val TikPopEase = androidx.compose.animation.core.CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)

// ---- glass core (owner TikuGlass.kt) ----
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Real backdrop blur (falls back to no-blur if no HazeState is provided). */
@Composable
fun Modifier.glassBlur(radius: Dp = 24.dp): Modifier {
    val hz = LocalHazeState.current ?: return this
    return this.hazeEffect(
        state = hz,
        style = HazeStyle(
            backgroundColor = Color.Transparent,
            tint = HazeTint(Color.Transparent),
            blurRadius = radius,
            noiseFactor = 0f
        )
    )
}

/** Sheet colour: lighter when real blur is available (CSS .84 / .88), denser otherwise. */
@Composable
fun sheetColor(): Color {
    val p = LocalPalette.current
    return if (LocalHazeState.current != null) p.sheet.copy(alpha = if (p.isDark) 0.84f else 0.88f) else p.sheet
}

@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(26.dp),
    useLargeGlass: Boolean = false,
    blur: Boolean = false,
    blurRadius: Dp = 24.dp
): Modifier {
    val p = LocalPalette.current
    val brush = if (useLargeGlass) p.lgBrush else p.glBrush
    val base = this.clip(shape).let { if (blur) it.glassBlur(blurRadius) else it }
    return base
        .background(brush, shape)
        .drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            // DrawScope.drawOutline needs UI 1.8+; this app is on 1.7, so the
            // same sheen/edge is drawn as a Path built from the outline.
            val outlinePath = when (outline) {
                is Outline.Generic -> outline.path
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            }
            // soft specular sheen from the top-left
            drawPath(
                path = outlinePath,
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = if (p.isDark) 0.10f else 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.maxDimension * 0.9f
                )
            )
            // edge light: bright on top, soft border below
            drawPath(
                path = outlinePath,
                brush = Brush.verticalGradient(
                    colors = listOf(p.hi, p.gb, p.gb.copy(alpha = p.gb.alpha * 0.55f))
                ),
                style = Stroke(width = 2.dp.toPx())
            )
        }
}

val InterTight = FontFamily(
    Font(R.font.inter_tight_light, FontWeight.Light),
    Font(R.font.inter_tight_regular, FontWeight.Normal),
    Font(R.font.inter_tight_medium, FontWeight.Medium),
    Font(R.font.inter_tight_semibold, FontWeight.SemiBold),
    Font(R.font.inter_tight_bold, FontWeight.Bold))

// ---- couple themes + palette (owner Tikuapp.kt) ----
data class CoupleTheme(
    val name: String,
    val subtitle: String,
    val ac: Color,
    val ac2: Color,
    val pat: String
)

val COUPLE_THEMES = listOf(
    CoupleTheme("Rose Petal", "Blush pink with floating hearts", Color(0xFFFF4F81), Color(0xFFFF93AD), "heart"),
    CoupleTheme("Cotton Candy", "Pink and lilac with dreamy sparkles", Color(0xFFFF63B0), Color(0xFF9D84FF), "spark"),
    CoupleTheme("Golden Hour", "Warm sunset with tiny daisies", Color(0xFFF0603A), Color(0xFFFF9A3D), "daisy"),
    CoupleTheme("Lavender Dream", "Moonlit stars in soft lilac", Color(0xFF7D6BFF), Color(0xFFC882FF), "star"),
    CoupleTheme("Cherry Kiss", "Deep cherry with outline hearts", Color(0xFFD81B5B), Color(0xFFFF6290), "heartO")
)

val AVATAR_COLORS = listOf(
    Color(0xFFB95B72),
    Color(0xFFB07A5C),
    Color(0xFF7D6FA8),
    Color(0xFF4F8F7E),
    Color(0xFF5B86B8)
)

val DEFAULT_REACTIONS = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

data class TikuPalette(
    val isDark: Boolean,
    val bg: Color,
    val tx: Color,
    val sub: Color,
    val ln: Color,
    val s2: Color,
    val glStart: Color,
    val glEnd: Color,
    val lgStart: Color,
    val lgEnd: Color,
    val gb: Color,
    val hi: Color,
    val sheet: Color,
    val lens: Color,
    val appBg: Color,
    val ac: Color,
    val ac2: Color,
    val pat: String
) {
    val glBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(glStart, glEnd),
            start = Offset.Zero,
            end = Offset(400f, 400f)
        )

    val lgBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(lgStart, lgEnd),
            start = Offset.Zero,
            end = Offset(400f, 400f)
        )

    val acGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(ac, ac2),
            start = Offset.Zero,
            end = Offset(200f, 500f)
        )

    val buttonGradient: Brush
        get() = Brush.verticalGradient(
            colors = listOf(lerp(Color.White, ac, 0.82f), ac)
        )
    // -- Round-13 extension tokens (real-app surfaces the demo never had) --
    /** Solid card fill for chips/rows that sit on the page background. */
    val s1: Color get() = if (isDark) Color(0xFF121214) else Color.White.copy(alpha = 0.55f)
    /** Pressed/selected row fill. */
    val rowActive: Color get() = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFF141428).copy(alpha = 0.05f)
    /** Outline-style button border/fill. */
    val outlineBtn: Color get() = if (isDark) Color(0xFF3A3A40) else Color(0xFF141428).copy(alpha = 0.28f)
    /** Bottom-sheet grabber. */
    val grabber: Color get() = if (isDark) Color(0xFF48484E) else Color(0xFFC6C5D0)
    /** Switch/slider track when off. */
    val trackOff: Color get() = if (isDark) Color(0xFF34343A) else Color(0xFFD8D7E0)
    /** Destructive red (design context-menu red). */
    val danger: Color get() = Color(0xFFFF5A6A)
    /** Recording dot red (design composer). */
    val recDot: Color get() = Color(0xFFFF5A6A)
}

fun buildPalette(mode: String, themeIdx: Int): TikuPalette {
    val th = COUPLE_THEMES.getOrElse(themeIdx) { COUPLE_THEMES[0] }
    return if (mode == "dark") {
        TikuPalette(
            isDark = true,
            bg = Color(0xFF0A0A0D),
            tx = Color(0xFFF6F5FA),
            sub = Color(0xFF9A99A6),
            ln = Color.White.copy(alpha = 0.09f),
            s2 = Color.White.copy(alpha = 0.10f),
            glStart = Color.White.copy(alpha = 0.14f),
            glEnd = Color.White.copy(alpha = 0.05f),
            lgStart = Color.White.copy(alpha = 0.20f),
            lgEnd = Color.White.copy(alpha = 0.06f),
            gb = Color.White.copy(alpha = 0.13f),
            hi = Color.White.copy(alpha = 0.26f),
            sheet = Color(0xFF1C1C22).copy(alpha = 0.94f),
            lens = Color.White.copy(alpha = 0.20f),
            appBg = Color.White.copy(alpha = 0.025f),
            ac = th.ac,
            ac2 = th.ac2,
            pat = th.pat
        )
    } else {
        TikuPalette(
            isDark = false,
            bg = Color(0xFFEEEDF3),
            tx = Color(0xFF17161D),
            sub = Color(0xFF6C6B78),
            ln = Color(0xFF141428).copy(alpha = 0.09f),
            s2 = Color(0xFF6E6E87).copy(alpha = 0.14f),
            glStart = Color.White.copy(alpha = 0.86f),
            glEnd = Color.White.copy(alpha = 0.52f),
            lgStart = Color.White.copy(alpha = 0.72f),
            lgEnd = Color.White.copy(alpha = 0.36f),
            gb = Color.White.copy(alpha = 0.90f),
            hi = Color.White.copy(alpha = 0.95f),
            sheet = Color(0xFFFAF9FD).copy(alpha = 0.96f),
            lens = Color.White.copy(alpha = 0.96f),
            appBg = Color.White.copy(alpha = 0.50f),
            ac = th.ac,
            ac2 = th.ac2,
            pat = th.pat
        )
    }
}

val LocalPalette = staticCompositionLocalOf { buildPalette("dark", 0) }

// ---- emoji catalogue (owner Tikuapp.kt) ----
data class EmojiCategory(
    val name: String,
    val icon: String,
    val emojis: List<String>,
    val keywords: List<String>
)

val EMOJI_CATEGORIES: List<EmojiCategory> by lazy {
    listOf(
        EmojiCategory(
            "Smileys", "😀",
            "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 🤫 🤔 🫡 🤐 🤨 😐 😑 😶 🫥 😶‍🌫️ 😏 😒 🙄 😬 😮‍💨 🤥 🫨 🙂‍↔️ 🙂‍↕️ 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 😵‍💫 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 🫤 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ 💔 ❤️‍🔥 ❤️‍🩹 ❤️ 🩷 🧡 💛 💚 💙 🩵 💜 🤎 🖤 🩶 🤍 💋 💯 💢 💥 💫 💦 💨 🕳️ 💬 👁️‍🗨️ 🗨️ 🗯️ 💭 💤".split(" "),
            "grinning face|smiling face with open mouth|smiling face with open mouth and smiling eyes|grinning face with smiling eyes|smiling face with open mouth and tightly-closed eyes|smiling face with open mouth and cold sweat|rolling on the floor laughing|face with tears of joy|slightly smiling face|upside-down face|melting face|winking face|smiling face with smiling eyes|smiling face with halo|smiling face with smiling eyes and three hearts|smiling face with heart-shaped eyes|grinning face with star eyes|face throwing a kiss|kissing face|white smiling face|kissing face with closed eyes|kissing face with smiling eyes|smiling face with tear|face savouring delicious food|face with stuck-out tongue|face with stuck-out tongue and winking eye|grinning face with one large and one small eye|face with stuck-out tongue and tightly-closed eyes|money-mouth face|hugging face|smiling face with smiling eyes and hand covering mouth|face with open eyes and hand over mouth|face with peeking eye|face with finger covering closed lips|thinking face|saluting face|zipper-mouth face|face with one eyebrow raised|neutral face|expressionless face|face without mouth|dotted line face|face in clouds|smirking face|unamused face|face with rolling eyes|grimacing face|face exhaling|lying face|shaking face|head shaking horizontally|head shaking vertically|relieved face|pensive face|sleepy face|drooling face|sleeping face|face with medical mask|face with thermometer|face with head-bandage|nauseated face|face with open mouth vomiting|sneezing face|overheated face|freezing face|face with uneven eyes and wavy mouth|dizzy face|face with spiral eyes|shocked face with exploding head|face with cowboy hat|face with party horn and party hat|disguised face|smiling face with sunglasses|nerd face|face with monocle|confused face|face with diagonal mouth|worried face|slightly frowning face|frowning face|face with open mouth|hushed face|astonished face|flushed face|face with pleading eyes|face holding back tears|frowning face with open mouth|anguished face|fearful face|face with open mouth and cold sweat|disappointed but relieved face|crying face|loudly crying face|face screaming in fear|confounded face|persevering face|disappointed face|face with cold sweat|weary face|tired face|yawning face|face with look of triumph|pouting face|angry face|serious face with symbols covering mouth|smiling face with horns|imp|skull|skull and crossbones|pile of poo|clown face|japanese ogre|japanese goblin|ghost|extraterrestrial alien|alien monster|robot face|smiling cat face with open mouth|grinning cat face with smiling eyes|cat face with tears of joy|smiling cat face with heart-shaped eyes|cat face with wry smile|kissing cat face with closed eyes|weary cat face|crying cat face|pouting cat face|see-no-evil monkey|hear-no-evil monkey|speak-no-evil monkey|love letter|heart with arrow|heart with ribbon|sparkling heart|growing heart|beating heart|revolving hearts|two hearts|heart decoration|heart exclamation|broken heart|heart on fire|mending heart|heavy black heart|pink heart|orange heart|yellow heart|green heart|blue heart|light blue heart|purple heart|brown heart|black heart|grey heart|white heart|kiss mark|hundred points symbol|anger symbol|collision symbol|dizzy symbol|splashing sweat symbol|dash symbol|hole|speech balloon|eye in speech bubble|left speech bubble|right anger bubble|thought balloon|sleeping symbol".split("|")
        ),
        EmojiCategory(
            "People", "👋",
            "👋 🤚 🖐️ ✋ 🖖 🫱 🫲 🫳 🫴 🫷 🫸 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 🖕 👇 ☝️ 🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦿 🦵 🦶 👂 🦻 👃 🧠 🫀 🫁 🦷 🦴 👀 👁️ 👅 👄 🫦 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 🧑‍⚕️ 🧑‍🎓 🧑‍🏫 🧑‍⚖️ 🧑‍🌾 🧑‍🍳 🧑‍🔧 🧑‍🏭 🧑‍💼 🧑‍🔬 🧑‍💻 🧑‍🎤 🧑‍🎨 🧑‍✈️ 🧑‍🚀 🧑‍🚒 👮 🕵️ 💂 🥷 👷 🫅 🤴 👸 👳 👲 🧕 🤵 👰 🤰 🫃 🫄 🤱 👼 🎅 🤶 🧑‍🎄 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 🧌 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 🕴️ 👯 🧖 🧗 🤺 🏇 ⛷️ 🏂 🏌️ 🏄 🚣 🏊 ⛹️ 🏋️ 🚴 🚵 🤸 🤼 🤽 🤾 🤹 🧘 🛀 🛌 🧑‍🤝‍🧑 👭 👫 👬 💏 💑 👨‍👩‍👦 🗣️ 👤 👥 🫂 👪 👣".split(" "),
            "waving hand|raised back of hand|hand|raised hand|vulcan|rightwards|leftwards|palm down|palm up|pushing|pushing|ok hand|pinched|pinching|victory|crossed fingers|love|love you|horns|call me|point left|point right|point up|middle finger|point down|index|point|thumbs up|thumbs down|fist|punch|left fist|right fist|clap|celebration|heart hands|open hands|palms up|handshake|pray|writing|nail polish|selfie|biceps|mechanical arm|mechanical leg|leg|foot|ear|hearing aid|nose|brain|heart|lungs|tooth|bone|eyes|eye|tongue|mouth|biting lip|baby|child|boy|girl|adult|blond|man|beard|woman|older adult|older man|older woman|frowning|pouting|no good|ok gesture|tipping hand|raising hand|deaf|bowing|facepalm|shrug|health worker|student|teacher|judge|farmer|cook|mechanic|factory|office|scientist|technologist|singer|artist|pilot|astronaut|firefighter|police|detective|guard|ninja|construction|crown|prince|princess|turban|cap|headscarf|tuxedo|bride|pregnant|pregnant man|pregnant person|breast-feeding|angel|santa|mrs claus|claus|superhero|supervillain|mage|fairy|vampire|merperson|elf|genie|zombie|troll|massage|haircut|walking|standing|kneeling|runner|dancer|dancing|levitating|bunny ears|steamy room|climbing|fencer|horse racing|skier|snowboarder|golfing|surfer|rowboat|swimmer|bouncing ball|lifting weights|bicyclist|mountain bike|cartwheel|wrestlers|water polo|handball|juggling|lotus|bath|sleeping|holding hands|women holding hands|man and woman|men holding hands|kiss|couple with heart|family|speaking|bust|busts|hugging|family|footprints".split("|")
        ),
        EmojiCategory(
            "Animals", "🐻",
            "🐵 🐒 🦍 🦧 🐶 🐕 🦮 🐕‍🦺 🐩 🐺 🦊 🦝 🐱 🐈 🐈‍⬛ 🦁 🐯 🐅 🐆 🐴 🫎 🫏 🐎 🦄 🦓 🦌 🦬 🐮 🐂 🐃 🐄 🐷 🐖 🐗 🐽 🐏 🐑 🐐 🐪 🐫 🦙 🦒 🐘 🦣 🦏 🦛 🐭 🐁 🐀 🐹 🐰 🐇 🐿️ 🦫 🦔 🦇 🐻 🐻‍❄️ 🐨 🐼 🦥 🦦 🦨 🦘 🦡 🐾 🦃 🐔 🐓 🐣 🐤 🐥 🐦 🐧 🕊️ 🦅 🦆 🦢 🦉 🦤 🪶 🦩 🦚 🦜 🪽 🐦‍⬛ 🪿 🐦‍🔥 🐸 🐊 🐢 🦎 🐍 🐲 🐉 🦕 🦖 🐳 🐋 🐬 🦭 🐟 🐠 🐡 🦈 🐙 🐚 🪸 🪼 🦀 🦞 🦐 🦑 🦪 🐌 🦋 🐛 🐜 🐝 🪲 🐞 🦗 🪳 🕷️ 🕸️ 🦂 🦟 🪰 🪱 🦠 💐 🌸 💮 🪷 🏵️ 🌹 🥀 🌺 🌻 🌼 🌷 🪻 🌱 🪴 🌲 🌳 🌴 🌵 🌾 🌿 ☘️ 🍀 🍁 🍂 🍃 🪹 🪺 🍄".split(" "),
            "monkey face|monkey|gorilla|orangutan|dog face|dog|guide dog|service dog|poodle|wolf face|fox face|raccoon|cat face|cat|black cat|lion face|tiger face|tiger|leopard|horse face|moose|donkey|horse|unicorn face|zebra face|deer|bison|cow face|ox|water buffalo|cow|pig face|pig|boar|pig nose|ram|sheep|goat|dromedary camel|bactrian camel|llama|giraffe face|elephant|mammoth|rhinoceros|hippopotamus|mouse face|mouse|rat|hamster face|rabbit face|rabbit|chipmunk|beaver|hedgehog|bat|bear face|polar bear|koala|panda face|sloth|otter|skunk|kangaroo|badger|paw prints|turkey|chicken|rooster|hatching chick|baby chick|front-facing baby chick|bird|penguin|dove|eagle|duck|swan|owl|dodo|feather|flamingo|peacock|parrot|wing|black bird|goose|phoenix|frog face|crocodile|turtle|lizard|snake|dragon face|dragon|sauropod|t-rex|spouting whale|whale|dolphin|seal|fish|tropical fish|blowfish|shark|octopus|spiral shell|coral|jellyfish|crab|lobster|shrimp|squid|oyster|snail|butterfly|bug|ant|honeybee|beetle|lady beetle|cricket|cockroach|spider|spider web|scorpion|mosquito|fly|worm|microbe|bouquet|cherry blossom|white flower|lotus|rosette|rose|wilted flower|hibiscus|sunflower|blossom|tulip|hyacinth|seedling|potted plant|evergreen tree|deciduous tree|palm tree|cactus|ear of rice|herb|shamrock|four leaf clover|maple leaf|fallen leaf|leaf fluttering in wind|empty nest|nest with eggs|mushroom".split("|")
        ),
        EmojiCategory(
            "Food", "🍔",
            "🍇 🍈 🍉 🍊 🍋 🍋‍🟩 🍌 🍍 🥭 🍎 🍏 🍐 🍑 🍒 🍓 🫐 🥝 🍅 🫒 🥥 🥑 🍆 🥔 🥕 🌽 🌶️ 🫑 🥒 🥬 🥦 🧄 🧅 🥜 🫘 🌰 🫚 🫛 🍄‍🟫 🍞 🥐 🥖 🫓 🥨 🥯 🥞 🧇 🧀 🍖 🍗 🥩 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🫔 🥙 🧆 🥚 🍳 🥘 🍲 🫕 🥣 🥗 🍿 🧈 🧂 🥫 🍱 🍘 🍙 🍚 🍛 🍜 🍝 🍠 🍢 🍣 🍤 🍥 🥮 🍡 🥟 🥠 🥡 🍦 🍧 🍨 🍩 🍪 🎂 🍰 🧁 🥧 🍫 🍬 🍭 🍮 🍯 🍼 🥛 ☕ 🫖 🍵 🍶 🍾 🍷 🍸 🍹 🍺 🍻 🥂 🥃 🫗 🥤 🧋 🧃 🧉 🧊 🥢 🍽️ 🍴 🥄 🔪 🫙 🏺".split(" "),
            "grapes|melon|watermelon|tangerine|lemon|lime|banana|pineapple|mango|red apple|green apple|pear|peach|cherries|strawberry|blueberries|kiwifruit|tomato|olive|coconut|avocado|aubergine|potato|carrot|ear of maize|hot pepper|bell pepper|cucumber|leafy green|broccoli|garlic|onion|peanuts|beans|chestnut|ginger root|pea pod|brown mushroom|bread|croissant|baguette bread|flatbread|pretzel|bagel|pancakes|waffle|cheese wedge|meat on bone|poultry leg|cut of meat|bacon|hamburger|french fries|slice of pizza|hot dog|sandwich|taco|burrito|tamale|stuffed flatbread|falafel|egg|cooking|shallow pan of food|pot of food|fondue|bowl with spoon|green salad|popcorn|butter|salt shaker|canned food|bento box|rice cracker|rice ball|cooked rice|curry and rice|steaming bowl|spaghetti|roasted sweet potato|oden|sushi|fried shrimp|fish cake with swirl design|moon cake|dango|dumpling|fortune cookie|takeout box|soft ice cream|shaved ice|ice cream|doughnut|cookie|birthday cake|shortcake|cupcake|pie|chocolate bar|candy|lollipop|custard|honey pot|baby bottle|glass of milk|hot beverage|teapot|teacup without handle|sake bottle and cup|bottle with popping cork|wine glass|cocktail glass|tropical drink|beer mug|clinking beer mugs|clinking glasses|tumbler glass|pouring liquid|cup with straw|bubble tea|beverage box|mate drink|ice cube|chopsticks|fork and knife with plate|fork and knife|spoon|hocho|jar|amphora".split("|")
        ),
        EmojiCategory(
            "Travel", "🚗",
            "🌍 🌎 🌏 🌐 🗺️ 🗾 🧭 🏔️ ⛰️ 🌋 🗻 🏕️ 🏖️ 🏜️ 🏝️ 🏞️ 🏟️ 🏛️ 🏗️ 🧱 🪨 🪵 🛖 🏘️ 🏚️ 🏠 🏡 🏢 🏣 🏤 🏥 🏦 🏨 🏩 🏪 🏫 🏬 🏭 🏯 🏰 💒 🗼 🗽 ⛪ 🕌 🛕 🕍 ⛩️ 🕋 ⛲ ⛺ 🌁 🌃 🏙️ 🌄 🌅 🌆 🌇 🌉 ♨️ 🎠 🛝 🎡 🎢 💈 🎪 🚂 🚃 🚄 🚅 🚆 🚇 🚈 🚉 🚊 🚝 🚞 🚋 🚌 🚍 🚎 🚐 🚑 🚒 🚓 🚔 🚕 🚖 🚗 🚘 🚙 🛻 🚚 🚛 🚜 🏎️ 🏍️ 🛵 🦽 🦼 🛺 🚲 🛴 🛹 🛼 🚏 🛣️ 🛤️ 🛢️ ⛽ 🛞 🚨 🚥 🚦 🛑 🚧 ⚓ 🛟 ⛵ 🛶 🚤 🛳️ ⛴️ 🛥️ 🚢 ✈️ 🛩️ 🛫 🛬 🪂 💺 🚁 🚟 🚠 🚡 🛰️ 🚀 🛸 🛎️ 🧳 ⌛ ⏳ ⌚ ⏰ ⏱️ ⏲️ 🕰️ 🌙 ☀️ ⭐ 🌟 🌠 🌌 ☁️ ⛅ ⛈️ 🌈 ☂️ ☔ ⚡ ❄️ ☃️ 🔥 💧 🌊".split(" "),
            "earth|americas|asia|globe|world map|japan|compass|mountain|mountain|volcano|mount fuji|camping|beach|desert|island|park|stadium|classical|construction|brick|rock|wood|hut|houses|derelict|house|garden|office|post office|post office|hospital|bank|hotel|love hotel|store|school|department|factory|castle|castle|wedding|tokyo tower|statue of liberty|church|mosque|temple|synagogue|shrine|kaaba|fountain|tent|foggy|night|cityscape|sunrise|sunrise|dusk|sunset|bridge|hot springs|carousel|slide|ferris wheel|roller coaster|barber|circus|locomotive|railway|train|bullet train|train|metro|light rail|station|tram|monorail|mountain railway|tram|bus|bus|trolleybus|minibus|ambulance|fire engine|police|police|taxi|taxi|car|automobile|rv|pickup|truck|lorry|tractor|racing car|motorcycle|scooter|wheelchair|wheelchair|rickshaw|bicycle|scooter|skateboard|roller skate|bus stop|motorway|railway|oil|fuel|wheel|siren|traffic light|traffic light|stop|construction|anchor|buoy|sailboat|canoe|speedboat|ship|ferry|motor boat|ship|airplane|small airplane|departure|arriving|parachute|seat|helicopter|railway|cableway|tramway|satellite|rocket|ufo|bell|luggage|hourglass|sand|watch|alarm|stopwatch|timer|clock|moon|sun|star|glowing star|shooting star|milky way|cloud|partly cloudy|storm|rainbow|umbrella|rain|lightning|snowflake|snowman|fire|droplet|wave".split("|")
        ),
        EmojiCategory(
            "Activity", "⚽",
            "🎃 🎄 🎆 🎇 🧨 ✨ 🎈 🎉 🎊 🎋 🎍 🎎 🎏 🎐 🎑 🧧 🎀 🎁 🎗️ 🎟️ 🎫 🎖️ 🏆 🏅 🥇 🥈 🥉 ⚽ ⚾ 🥎 🏀 🏐 🏈 🏉 🎾 🥏 🎳 🏏 🏑 🏒 🥍 🏓 🏸 🥊 🥋 🥅 ⛳ ⛸️ 🎣 🤿 🎽 🎿 🛷 🥌 🎯 🪀 🪁 🔫 🎱 🔮 🪄 🎮 🕹️ 🎰 🎲 🧩 🧸 🪅 🪩 🪆 ♠️ ♥️ ♦️ ♣️ ♟️ 🃏 🀄 🎴 🎭 🖼️ 🎨 🧵 🪡 🧶 🪢".split(" "),
            "jack-o-lantern|christmas tree|fireworks|sparkler|firecracker|sparkles|balloon|party popper|confetti|tanabata|pine|dolls|carp|wind chime|moon|red envelope|ribbon|gift|reminder|tickets|ticket|medal|trophy|sports medal|first place|second place|third place|soccer|baseball|softball|basketball|volleyball|football|rugby|tennis|flying disc|bowling|cricket|hockey|ice hockey|lacrosse|table tennis|badminton|boxing|martial arts|goal|golf|ice skate|fishing|diving|running|ski|sled|curling|bullseye|yo-yo|kite|water pistol|billiards|crystal ball|magic wand|video game|joystick|slot machine|dice|puzzle|teddy bear|pinata|mirror ball|nesting dolls|spade|heart|diamond|club|chess|joker|mahjong|cards|arts|picture|palette|thread|needle|yarn|knot".split("|")
        ),
        EmojiCategory(
            "Objects", "💡",
            "👓 🕶️ 🥽 🥼 🦺 👔 👕 👖 🧣 🧤 🧥 🧦 👗 👘 🥻 🩱 🩲 🩳 👙 👚 🪭 👛 👜 👝 🛍️ 🎒 🩴 👞 👟 🥾 🥿 👠 👡 🩰 👢 🪮 👑 👒 🎩 🎓 🧢 🪖 ⛑️ 📿 💄 💍 💎 🔇 🔈 🔉 🔊 📢 📣 📯 🔔 🔕 🎼 🎵 🎶 🎙️ 🎚️ 🎛️ 🎤 🎧 📻 🎷 🪗 🎸 🎹 🎺 🎻 🪕 🥁 🪘 🪇 🪈 📱 📲 ☎️ 📞 📟 📠 🔋 🪫 🔌 💻 🖥️ 🖨️ ⌨️ 🖱️ 🖲️ 💽 💾 💿 📀 🧮 🎥 🎞️ 📽️ 🎬 📺 📷 📸 📹 📼 🔍 🔎 🕯️ 💡 🔦 🏮 🪔 📔 📕 📖 📗 📘 📙 📚 📓 📒 📃 📜 📄 📰 🗞️ 📑 🔖 🏷️ 💰 🪙 💴 💵 💶 💷 💸 💳 🧾 💹 ✉️ 📧 📨 📩 📤 📥 📦 📫 📪 📬 📭 📮 🗳️ ✏️ ✒️ 🖋️ 🖊️ 🖌️ 🖍️ 📝 💼 📁 📂 🗂️ 📅 📆 🗒️ 🗓️ 📇 📈 📉 📊 📋 📌 📍 📎 🖇️ 📏 📐 ✂️ 🗃️ 🗄️ 🗑️ 🔒 🔓 🔏 🔐 🔑 🗝️ 🔨 🪓 ⛏️ ⚒️ 🛠️ 🗡️ ⚔️ 💣 🪃 🏹 🛡️ 🪚 🔧 🪛 🔩 ⚙️ 🗜️ ⚖️ 🦯 🔗 ⛓️‍💥 ⛓️ 🪝 🧰 🧲 🪜 ⚗️ 🧪 🧫 🧬 🔬 🔭 📡 💉 🩸 💊 🩹 🩼 🩺 🩻 🚪 🛗 🪞 🪟 🛏️ 🛋️ 🪑 🚽 🪠 🚿 🛁 🪤 🪒 🧴 🧷 🧹 🧺 🧻 🪣 🧼 🫧 🪥 🧽 🧯 🛒 🚬 ⚰️ 🪦 ⚱️ 🧿 🪬 🗿 🪧 🪪".split(" "),
            "eyeglasses|sunglasses|goggles|lab coat|safety vest|necktie|t-shirt|jeans|scarf|gloves|coat|socks|dress|kimono|sari|swimsuit|briefs|shorts|bikini|clothes|fan|purse|handbag|pouch|shopping bags|backpack|sandal|shoe|sneaker|boot|flat shoe|high heel|sandal|ballet|boots|hair pick|crown|hat|top hat|graduation|cap|helmet|rescue helmet|beads|lipstick|ring|gem|mute|speaker|speaker|loudspeaker|pa|megaphone|horn|bell|mute bell|score|note|music notes|studio mic|slider|knobs|microphone|headphone|radio|saxophone|accordion|guitar|keyboard|trumpet|violin|banjo|drum|long drum|maracas|flute|phone|mobile|telephone|receiver|pager|fax|battery|low battery|plug|laptop|desktop|printer|keyboard|mouse|trackball|minidisc|floppy|cd|dvd|abacus|movie camera|film|projector|clapper|tv|camera|flash|video camera|vhs|search|magnify|candle|bulb|flashlight|lantern|diya|notebook|red book|open book|green book|blue book|orange book|books|notebook|ledger|page|scroll|document|newspaper|rolled newspaper|tabs|bookmark|label|money bag|coin|yen|dollar|euro|pound|flying money|credit card|receipt|chart|envelope|email|incoming|envelope|outbox|inbox|package|mailbox|mailbox|mailbox|mailbox|postbox|ballot|pencil|nib|fountain pen|pen|paintbrush|crayon|memo|briefcase|folder|open folder|dividers|calendar|calendar|notepad|calendar|index|chart up|chart down|bar chart|clipboard|pushpin|pin|paperclip|paperclips|ruler|triangle ruler|scissors|file box|cabinet|trash|lock|unlock|lock pen|lock key|key|old key|hammer|axe|pick|hammer pick|tools|dagger|swords|bomb|boomerang|bow|shield|saw|wrench|screwdriver|bolt|gear|clamp|scale|cane|link|broken chain|chains|hook|toolbox|magnet|ladder|alembic|test tube|petri dish|dna|microscope|telescope|dish|syringe|blood|pill|bandage|crutch|stethoscope|x-ray|door|elevator|mirror|window|bed|couch|chair|toilet|plunger|shower|bathtub|trap|razor|lotion|safety pin|broom|basket|toilet paper|bucket|soap|bubbles|toothbrush|sponge|extinguisher|cart|cigarette|coffin|headstone|urn|nazar|hamsa|moai|placard|id card".split("|")
        ),
        EmojiCategory(
            "Symbols", "❤️",
            "🏧 🚮 🚰 ♿ 🚹 🚺 🚻 🚼 🚾 🛂 🛃 🛄 🛅 ⚠️ 🚸 ⛔ 🚫 🚳 🚭 🚯 🚱 🚷 📵 🔞 ☢️ ☣️ ⬆️ ↗️ ➡️ ↘️ ⬇️ ↙️ ⬅️ ↖️ ↕️ ↔️ ↩️ ↪️ ⤴️ ⤵️ 🔃 🔄 🔙 🔚 🔛 🔜 🔝 🛐 ⚛️ 🕉️ ✡️ ☸️ ☯️ ✝️ ☦️ ☪️ ☮️ 🕎 🔯 🪯 ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ ⛎ 🔀 🔁 🔂 ▶️ ⏩ ⏭️ ⏯️ ◀️ ⏪ ⏮️ 🔼 ⏫ 🔽 ⏬ ⏸️ ⏹️ ⏺️ ⏏️ 🎦 🔅 🔆 📶 🛜 📳 📴 ♀️ ♂️ ⚧️ ✖️ ➕ ➖ ➗ 🟰 ♾️ ‼️ ⁉️ ❓ ❔ ❕ ❗ 〰️ 💱 💲 ⚕️ ♻️ ⚜️ 🔱 📛 🔰 ⭕ ✅ ☑️ ✔️ ❌ ❎ ➰ ➿ 〽️ ✳️ ✴️ ❇️ ™️ 🔟 🔠 🔡 🔢 🔣 🔤 🅰️ 🆎 🅱️ 🆑 🆒 🆓 ℹ️ 🆔 Ⓜ️ 🆕 🆖 🅾️ 🆗 🅿️ 🆘 🆙 🆚 🔴 🟠 🟡 🟢 🔵 🟣 🟤 ⚫ ⚪ 🟥 🟧 🟨 🟩 🟦 🟪 🟫 ⬛ ⬜ 🔶 🔷 🔸 🔹 🔺 🔻 💠 🔘 🔳 🔲".split(" "),
            "atm|litter|water|wheelchair|mens|womens|restroom|baby|wc|passport|customs|baggage|luggage|warning|children|no entry|prohibited|no bikes|no smoking|no litter|no water|no pedestrians|no phones|18+|radioactive|biohazard|up|up right|right|down right|down|down left|left|up left|up down|left right|hook left|hook right|curve up|curve down|clockwise|refresh|back|end|on|soon|top|worship|atom|om|star of david|dharma|yin yang|cross|orthodox|crescent|peace|menorah|star|khanda|aries|taurus|gemini|cancer|leo|virgo|libra|scorpio|sagittarius|capricorn|aquarius|pisces|ophiuchus|shuffle|repeat|repeat one|play|fast forward|next|play pause|reverse|rewind|previous|up|fast up|down|fast down|pause|stop|record|eject|cinema|dim|bright|signal|wifi|vibration|phone off|female|male|transgender|multiply|plus|minus|divide|equals|infinity|bangbang|interrobang|question|question|exclamation|exclamation|wavy|currency|dollar|medical|recycle|fleur-de-lis|trident|badge|beginner|circle|check|ballot check|check mark|cross|cross mark|loop|double loop|part|asterisk|star|sparkle|trademark|ten|uppercase|lowercase|numbers|symbols|letters|a|ab|b|cl|cool|free|info|id|m|new|ng|o|ok|p|sos|up|vs|red circle|orange circle|yellow circle|green circle|blue circle|purple circle|brown circle|black circle|white circle|red square|orange square|yellow square|green square|blue square|purple square|brown square|black square|white square|orange diamond|blue diamond|small orange diamond|small blue diamond|red triangle up|red triangle down|diamond dot|radio|white button|black button".split("|")
        ),
        EmojiCategory(
            "Flags", "🏳️",
            "🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🏳️‍⚧️ 🏴‍☠️ 🇮🇳 🇺🇸 🇬🇧 🇨🇦 🇦🇺 🇯🇵 🇰🇷 🇩🇪 🇫🇷 🇮🇹 🇪🇸 🇧🇷 🇷🇺 🇨🇳 🇦🇪 🇸🇦 🇹🇷 🇵🇰 🇧🇩 🇳🇵 🇱🇰 🇲🇾 🇸🇬 🇮🇩 🇹🇭 🇻🇳 🇵🇭 🇿🇦 🇳🇬 🇪🇬 🇲🇽 🇦🇷 🇨🇭 🇸🇪 🇳🇴 🇳🇱 🇧🇪 🇵🇹 🇬🇷 🇵🇱 🇺🇦 🇳🇿 🇮🇪".split(" "),
            "chequered flag|triangular flag|crossed flags|black flag|white flag|rainbow flag|transgender flag|pirate flag|india|united states|united kingdom|canada|australia|japan|south korea|germany|france|italy|spain|brazil|russia|china|uae|saudi arabia|turkey|pakistan|bangladesh|nepal|sri lanka|malaysia|singapore|indonesia|thailand|vietnam|philippines|south africa|nigeria|egypt|mexico|argentina|switzerland|sweden|norway|netherlands|belgium|portugal|greece|poland|ukraine|new zealand|ireland".split("|")
        )
    )
}

// ---- icon set (owner Tikuapp.kt) ----
@Composable
fun TikuIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    strokeWidthDp: Float = 1.6f,
    fillColor: Color = Color.Unspecified
) {
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.minDimension / 24f
        val strokePx = strokeWidthDp * scale
        val stroke = Stroke(
            width = strokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        fun drawSvgPath(d: String, fill: Boolean = false) {
            val path = PathParser().parsePathString(d).toPath()
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                if (fill || fillColor != Color.Unspecified) {
                    drawPath(path, color = if (fillColor != Color.Unspecified) fillColor else tint, style = Fill)
                }
                if (!fill) {
                    drawPath(path, color = tint, style = stroke)
                }
            }
        }

        fun drawSvgCircle(cx: Float, cy: Float, r: Float, fill: Boolean = false) {
            if (fill || fillColor != Color.Unspecified) {
                drawCircle(
                    color = if (fillColor != Color.Unspecified) fillColor else tint,
                    radius = r * scale,
                    center = Offset(cx * scale, cy * scale)
                )
            }
            if (!fill) {
                drawCircle(
                    color = tint,
                    radius = r * scale,
                    center = Offset(cx * scale, cy * scale),
                    style = stroke
                )
            }
        }

        fun drawSvgRect(x: Float, y: Float, w: Float, h: Float, rx: Float) {
            if (fillColor != Color.Unspecified) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(x * scale, y * scale),
                    size = Size(w * scale, h * scale),
                    cornerRadius = CornerRadius(rx * scale, rx * scale),
                    style = Fill
                )
            }
            drawRoundRect(
                color = tint,
                topLeft = Offset(x * scale, y * scale),
                size = Size(w * scale, h * scale),
                cornerRadius = CornerRadius(rx * scale, rx * scale),
                style = stroke
            )
        }

        when (name) {
            "msg" -> drawSvgPath("M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z")
            "send" -> drawSvgPath("M21 3 10 14M21 3l-7 18-4-7-7-4z")
            "call" -> drawSvgPath("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z")
            "vid" -> {
                drawSvgRect(2f, 6f, 14f, 12f, 3f)
                drawSvgPath("m16 11 6-3v8l-6-3")
            }
            "play" -> {
                drawSvgRect(2.5f, 2.5f, 19f, 19f, 5.5f)
                drawSvgPath("m10.5 8 7 4-7 4z")
            }
            "set" -> {
                drawSvgCircle(12f, 12f, 3f)
                drawSvgPath("M19 12a7 7 0 0 0-.1-1.3l2-1.5-2-3.4-2.3 1a7 7 0 0 0-2.2-1.3L14 3h-4l-.4 2.5a7 7 0 0 0-2.2 1.3l-2.3-1-2 3.4 2 1.5a7 7 0 0 0 0 2.6l-2 1.5 2 3.4 2.3-1a7 7 0 0 0 2.2 1.3L10 21h4l.4-2.5a7 7 0 0 0 2.2-1.3l2.3 1 2-3.4-2-1.5c.1-.4.1-.9.1-1.3z")
            }
            "back" -> drawSvgPath("m15 5-7 7 7 7")
            "edit" -> drawSvgPath("M4 20h4L19 9l-4-4L4 16zM14 6l4 4")
            "book" -> drawSvgPath("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2zM22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z")
            "chev" -> drawSvgPath("m6 9 6 6 6-6")
            "chevR" -> drawSvgPath("m9 6 6 6-6 6")
            "srch" -> {
                drawSvgCircle(11f, 11f, 7f)
                drawSvgPath("m21 21-4.3-4.3")
            }
            "user" -> {
                drawSvgCircle(12f, 8f, 4f)
                drawSvgPath("M4 21a8 8 0 0 1 16 0")
            }
            "bell" -> drawSvgPath("M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4")
            "bellOff" -> drawSvgPath("M6 16v-5a6 6 0 0 1 9-5.2M18 11v5l2 2H4M10 21h4M3 3l18 18")
            "dots" -> {
                drawSvgCircle(5f, 12f, 1.2f, fill = true)
                drawSvgCircle(12f, 12f, 1.2f, fill = true)
                drawSvgCircle(19f, 12f, 1.2f, fill = true)
            }
            "cam" -> {
                drawSvgPath("M4 8h3l2-3h6l2 3h3v12H4z")
                drawSvgCircle(12f, 13f, 4f)
            }
            "mic" -> {
                drawSvgRect(9f, 3f, 6f, 11f, 3f)
                drawSvgPath("M5 11a7 7 0 0 0 14 0M12 18v3")
            }
            "img" -> {
                drawSvgRect(3f, 3f, 18f, 18f, 4f)
                drawSvgCircle(9f, 9f, 1.8f)
                drawSvgPath("m4 18 5.5-5.5 3 3L16 12l4 4")
            }
            "plus" -> drawSvgPath("M12 5v14M5 12h14")
            "music" -> {
                drawSvgPath("M9 18V5l11-2v13")
                drawSvgCircle(6f, 18f, 3f)
                drawSvgCircle(17f, 16f, 3f)
            }
            "pin" -> {
                drawSvgPath("M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11z")
                drawSvgCircle(12f, 10f, 2.5f)
            }
            "heart" -> drawSvgPath("M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.5A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z")
            "heartFill" -> drawSvgPath("M12 21s-8-5.2-8-11.2A4.6 4.6 0 0 1 12 6.6a4.6 4.6 0 0 1 8 3.2C20 15.8 12 21 12 21z", fill = true)
            "trash" -> drawSvgPath("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
            "reply" -> {
                drawSvgPath("M9 14 4 9l5-5")
                drawSvgPath("M4 9h10a6 6 0 0 1 6 6v3")
            }
            "copy" -> {
                drawSvgRect(9f, 9f, 11f, 11f, 3f)
                drawSvgPath("M5 15V6a2 2 0 0 1 2-2h9")
            }
            "check" -> drawSvgPath("m5 12 5 5 9-10")
            "x" -> drawSvgPath("M6 6l12 12M18 6 6 18")
            "star" -> drawSvgPath("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")
            "sun" -> {
                drawSvgCircle(12f, 12f, 4f)
                drawSvgPath("M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
            }
            "moon" -> drawSvgPath("M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z")
            "micOff" -> {
                drawSvgRect(9f, 3f, 6f, 11f, 3f)
                drawSvgPath("M5 11a7 7 0 0 0 11.5 5.4M19 11a7 7 0 0 1-.4 2.3M12 18v3M3 3l18 18")
            }
            "spk" -> {
                drawSvgPath("M4 9v6h4l5 4V5L8 9z")
                drawSvgPath("M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12")
            }
            "vidOff" -> drawSvgPath("M10 6h3a3 3 0 0 1 3 3v2.5l6-3.5v8l-1.5-.9M16 16v.5a3 3 0 0 1-3 2H5a3 3 0 0 1-3-3V9a3 3 0 0 1 2-2.8M3 3l18 18")
            "flip" -> drawSvgPath("M4 12a8 8 0 0 1 13.7-5.6L20 8M20 4v4h-4M20 12a8 8 0 0 1-13.7 5.6L4 16M4 20v-4h4")
            // ROUND 32: share-screen glyph — a monitor with an up arrow
            // rising out of it and a stand line.
            "shareScr" -> {
                drawSvgRect(2.5f, 4f, 19f, 13f, 3f)
                drawSvgPath("M12 13.8V9M9.4 11.4 12 8.8l2.6 2.6M8.5 21h7")
            }
            "PL" -> drawSvgPath("M8 5v14l11-7z", fill = true)
            "PA" -> drawSvgPath("M9 5v14M15 5v14")
        }
    }
}

// ---- chat wallpaper pattern (owner Tikuapp.kt) ----
private val PATTERN_SPOTS = listOf(
    floatArrayOf(22f, 26f, -14f, 1f),
    floatArrayOf(108f, 16f, 14f, 0.62f),
    floatArrayOf(70f, 86f, -8f, 1.2f),
    floatArrayOf(18f, 122f, 16f, 0.66f),
    floatArrayOf(128f, 118f, -12f, 0.85f)
)

private val HEART_SVG_PATH by lazy {
    PathParser().parsePathString("M12 21s-8-5.2-8-11.2A4.6 4.6 0 0 1 12 6.6a4.6 4.6 0 0 1 8 3.2C20 15.8 12 21 12 21z").toPath()
}
private val SPARK_SVG_PATH by lazy {
    PathParser().parsePathString("M12 2c.8 5.2 4.8 9.2 10 10-5.2.8-9.2 4.8-10 10-.8-5.2-4.8-9.2-10-10 5.2-.8 9.2-4.8 10-10z").toPath()
}
private val STAR_SVG_PATH by lazy {
    PathParser().parsePathString("M12 2.5l2.8 6 6.5.8-4.8 4.5 1.3 6.5L12 17l-5.8 3.3 1.3-6.5L2.7 9.3l6.5-.8z").toPath()
}

fun DrawScope.drawThemePattern(
    pat: String,
    ac: Color,
    isDark: Boolean,
    tilePx: Float
) {
    val baseOpacity = if (isDark) 0.16f else 0.20f
    val isOutline = pat == "heartO"
    val unitScale = tilePx / 160f

    val cols = (size.width / tilePx).toInt() + 1
    val rows = (size.height / tilePx).toInt() + 1

    for (r in 0..rows) {
        for (c in 0..cols) {
            val ox = c * tilePx
            val oy = r * tilePx
            PATTERN_SPOTS.forEachIndexed { idx, spot ->
                val sx = spot[0] * unitScale
                val sy = spot[1] * unitScale
                val rot = spot[2]
                val sc = spot[3] * unitScale
                val alpha = baseOpacity * (if (idx % 2 == 1) 0.7f else 1f)
                val color = ac.copy(alpha = alpha)

                withTransform({
                    translate(ox + sx, oy + sy)
                    rotate(rot, Offset.Zero)
                    scale(sc, sc, Offset.Zero)
                    translate(-12f, -12f)
                }) {
                    when (pat) {
                        "heart", "heartO" -> {
                            if (isOutline) {
                                drawPath(HEART_SVG_PATH, color = color, style = Stroke(width = 1.7f, join = StrokeJoin.Round))
                            } else {
                                drawPath(HEART_SVG_PATH, color = color, style = Fill)
                            }
                        }
                        "spark" -> drawPath(SPARK_SVG_PATH, color = color, style = Fill)
                        "star" -> drawPath(STAR_SVG_PATH, color = color, style = Fill)
                        "daisy" -> {
                            listOf(0f, 72f, 144f, 216f, 288f).forEach { angle ->
                                withTransform({ rotate(angle, Offset(12f, 12f)) }) {
                                    drawOval(
                                        color = color,
                                        topLeft = Offset(12f - 2.7f, 6.6f - 4.2f),
                                        size = Size(5.4f, 8.4f)
                                    )
                                }
                            }
                            drawCircle(color = color, radius = 2.2f, center = Offset(12f, 12f))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. REUSABLE GLASSMORPHISM SURFACES, AVATARS & BUTTONS
// ============================================================================

// ---- avatar, glass buttons, switch (owner Tikuapp.kt) ----
@Composable
fun TikuAvatar(
    text: String,
    sizeDp: Dp,
    colorIdx: Int = 0,
    hasRing: Boolean = false,
    ringBgColor: Color = LocalPalette.current.bg,
    ringAccentColor: Color = LocalPalette.current.ac,
    modifier: Modifier = Modifier
) {
    val bgColor = AVATAR_COLORS.getOrElse(colorIdx) { AVATAR_COLORS[0] }
    Box(
        modifier = modifier
            .then(
                if (hasRing) {
                    Modifier
                        .border(2.dp, ringAccentColor, CircleShape)
                        .padding(3.dp)
                        .background(ringBgColor, CircleShape)
                        .padding(2.dp)
                } else Modifier
            )
            .size(sizeDp)
            .clip(CircleShape)
            .background(bgColor)
            .drawWithContent {
                drawContent()
                // Subtle top highlight & bottom inner shadow matching .av CSS
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.12f))
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1).uppercase(),
            color = Color.White,
            fontSize = (sizeDp.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GlassCircleIconButton(
    iconName: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 22.dp,
    tint: Color = LocalPalette.current.tx,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "btnScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .glassSurface(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        TikuIcon(name = iconName, size = iconSize, tint = tint)
    }
}

@Composable
fun TikuToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalPalette.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "toggleThumb"
    )
    val bgColor by animateColorAsState(
        targetValue = if (checked) p.ac else p.s2,
        label = "toggleBg"
    )
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, if (checked) Color.Transparent else p.ln, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// Deterministic waveform bar heights matching `bars(id, n)` in Tiku.html
fun generateWaveformBars(seedId: Int, count: Int = 26): List<Float> {
    val out = ArrayList<Float>(count)
    var x = seedId * 7L + 3L
    for (k in 0 until count) {
        x = (x * 9301L + 49297L) % 233280L
        out.add((30f + (x % 70L).toFloat()) / 100f)
    }
    return out
}

fun formatSeconds(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// ---- typing indicator (owner Tikuapp.kt) ----
@Composable
fun TypingHeartsBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typingHearts")
    Row(
        modifier = Modifier
            .glassSurface(RoundedCornerShape(23.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val p = LocalPalette.current
        for (i in 0..2) {
            val anim by infiniteTransition.animateFloat(
                initialValue = 0.72f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 160),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "th_$i"
            )
            TikuIcon(
                name = "heartFill",
                size = 11.dp,
                tint = p.ac,
                modifier = Modifier
                    .scale(anim)
                    .alpha(0.45f + (anim - 0.72f) * 1.9f)
            )
        }
    }
}

// ---- fields + pill buttons (owner Tikuapp.kt) ----
@Composable
fun CheckCircleIndicator(selected: Boolean) {
    val p = LocalPalette.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) p.ac else Color.Transparent)
            .border(1.5.dp, if (selected) p.ac else p.sub, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            TikuIcon("check", size = 14.dp, tint = Color.White, strokeWidthDp = 2.4f)
        }
    }
}

@Composable
fun TikuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false
) {
    val p = LocalPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.s2)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = p.sub, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = p.tx, fontSize = 15.sp),
            cursorBrush = SolidColor(p.ac),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PillActionButton(
    text: String,
    isOutlineGlass: Boolean = false,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (isOutlineGlass) Modifier.glassSurface(RoundedCornerShape(999.dp))
                else Modifier.background(p.buttonGradient)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isOutlineGlass) p.tx else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

// ============================================================================
// 12. SCREEN 5: CALLS SCREEN (`#call`) & ACTIVE CALL OVERLAY (`#cl`)
// ============================================================================

// ---- appearance controls (owner Tikuapp.kt) ----
@Composable
fun ModeSegmentedToggle(
    mode: String,
    onModeChange: (String) -> Unit
) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(p.s2)
            .border(1.dp, p.ln, RoundedCornerShape(999.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("light" to ("sun" to "Light"), "dark" to ("moon" to "Dark")).forEach { (m, pair) ->
            val (icon, label) = pair
            val isSelected = mode == m
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) p.lens else Color.Transparent)
                    .clickable { onModeChange(m) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TikuIcon(icon, size = 16.dp, tint = if (isSelected) p.tx else p.sub)
                Text(
                    text = label,
                    color = if (isSelected) p.tx else p.sub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FontSizeSliderRow(
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit
) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Text size", color = p.tx, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Message font size", color = p.sub, fontSize = 12.5.sp)
        }
        Slider(
            value = fontSizeSp,
            onValueChange = onFontSizeChange,
            valueRange = 13f..20f,
            steps = 6,
            colors = SliderDefaults.colors(
                thumbColor = p.ac,
                activeTrackColor = p.ac,
                inactiveTrackColor = p.s2
            ),
            modifier = Modifier.width(125.dp)
        )
    }
}

// ============================================================================
// 15. MESSAGE CONTEXT MENU OVERLAY (`#cx`) & BOTTOM SHEETS (`#shw`)
// ============================================================================

// ---- emoji picker sheet (owner Tikuapp.kt) ----
@Composable
fun EmojiPickerSheetContent(
    initialTab: Int,
    recentEmojis: List<String>,
    onClose: () -> Unit,
    onSelectEmoji: (String) -> Unit
) {
    val p = LocalPalette.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    val tabs = remember { listOf("Recent" to "🕘") + EMOJI_CATEGORIES.map { it.name to it.icon } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp, max = 520.dp)
    ) {
        // Header (`.esh`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("React with any emoji", color = p.tx, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(p.s2)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                TikuIcon("x", size = 17.dp, tint = p.tx)
            }
        }

        // Search Input (`#eq`)
        TikuTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search emoji"
        )

        // Category Tabs (`.etabs`)
        if (searchQuery.trim().isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tabs.size) { k ->
                    val (label, icon) = tabs[k]
                    val isSel = selectedTab == k
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSel) p.ac.copy(alpha = 0.26f) else p.s2)
                            .clickable { selectedTab = k }
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // ROUND 23: category tab icons are emoji — they go
                        // through the iOS emoji font like every emoji surface.
                        Text(icon, fontSize = 15.sp, fontFamily = rememberEmojiFontFamily())
                        Text(
                            text = label,
                            color = if (isSel) p.tx else p.sub,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 8-Column Emoji Grid (`.eg`)
        val q = searchQuery.trim().lowercase()
        val searchResults = remember(q) {
            if (q.isEmpty()) emptyList()
            else {
                val out = mutableListOf<String>()
                EMOJI_CATEGORIES.forEach { cat ->
                    for (i in cat.emojis.indices) {
                        val kw = cat.keywords.getOrNull(i) ?: ""
                        if (kw.contains(q) && out.size < 280) {
                            out.add(cat.emojis[i])
                        }
                    }
                }
                out
            }
        }

        if (q.isNotEmpty() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No emoji found", color = p.sub, fontSize = 13.5.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                if (q.isNotEmpty()) {
                    items(searchResults) { em ->
                        EmojiPickerCell(em, onClick = { onSelectEmoji(em) })
                    }
                } else if (selectedTab == 0) {
                    if (recentEmojis.isNotEmpty()) {
                        item(span = { GridItemSpan(8) }) {
                            EmojiGridCategoryHeader("Recent")
                        }
                        items(recentEmojis) { em ->
                            EmojiPickerCell(em, onClick = { onSelectEmoji(em) })
                        }
                    }
                    EMOJI_CATEGORIES.forEach { cat ->
                        item(span = { GridItemSpan(8) }) {
                            EmojiGridCategoryHeader("${cat.icon} ${cat.name}")
                        }
                        items(cat.emojis) { em ->
                            EmojiPickerCell(em, onClick = { onSelectEmoji(em) })
                        }
                    }
                } else {
                    val cat = EMOJI_CATEGORIES.getOrElse(selectedTab - 1) { EMOJI_CATEGORIES[0] }
                    items(cat.emojis) { em ->
                        EmojiPickerCell(em, onClick = { onSelectEmoji(em) })
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiGridCategoryHeader(title: String) {
    val p = LocalPalette.current
    Text(
        text = title,
        color = p.sub,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        // ROUND 23: headers lead with a category emoji ("😀 Smileys") —
        // the chained iOS family draws the emoji, Latin falls back.
        fontFamily = rememberEmojiFontFamily(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp, start = 4.dp)
    )
}


// ============================================================================
// Round-13 glue: small helpers + the shared Tik building blocks, re-skinned
// onto the glass system. Names/signatures match the build-44 components they
// replace so the screens keep their shape; only the rendering changed.
// ============================================================================

/** Wallpaper tint per mode of [WallpaperMode]: dark values are the build-44
 *  Tik.WallColors; light values are the matching pale tints for light mode.
 *  DEFAULT returns [Color.Unspecified] — callers use the palette bg. */
fun wallTint(wallpaper: WallpaperMode, isDark: Boolean): Color = when (wallpaper) {
    WallpaperMode.DEFAULT -> Color.Unspecified
    WallpaperMode.PLAIN -> if (isDark) Color(0xFF101014) else Color(0xFFF3F1F8)
    WallpaperMode.PEACH -> if (isDark) Color(0xFF1A1216) else Color(0xFFFBF0EC)
    WallpaperMode.DOTS -> if (isDark) Color(0xFF0D1512) else Color(0xFFEFF5F2)
    WallpaperMode.COUPLE -> if (isDark) Color(0xFF170E13) else Color(0xFFFBEEF2)
}

/** An emoji rendered through the bundled Fluent 3D asset when one exists for
 *  it, otherwise as plain text — the spec's sanctioned fallback for surfaces
 *  like the full emoji picker, whose catalogue is larger than the asset set. */
@Composable
fun EmojiGlyph(emoji: String, size: Dp) {
    if (EmojiArt.assetName(emoji) != null) {
        EmojiArt(emoji, size)
    } else {
        Text(emoji, fontSize = (size.value * 0.92f).sp)
    }
}

/** One emoji-picker grid cell (owner's metrics; asset-or-text rendering). */
@Composable
fun EmojiPickerCell(emoji: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { EmojiGlyph(emoji, 26.dp) }
}

/** Build-44 call-site adapter: the old TikIcon(name, modifier, tint) shape,
 *  rendered by the owner's TikuIcon. The two extra build-44 names map onto
 *  the design's filled glyphs (playFill -> PL, pauseFill -> PA). */
@Composable
fun TikIcon(name: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, strokeWidth: Float = 1.6f) {
    val pal = LocalPalette.current
    val mapped = when (name) {
        "playFill" -> "PL"
        "pauseFill" -> "PA"
        else -> name
    }
    TikuIcon(
        name = mapped,
        modifier = modifier,
        size = 24.dp,
        tint = if (tint == Color.Unspecified) pal.tx else tint,
        strokeWidthDp = strokeWidth,
    )
}

/** Bare icon button (build-44 TikBtn metrics: 36dp box, press scale .88). */
@Composable
fun TikBtn(
    icon: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    strokeWidth: Float = 1.6f,
    onClick: () -> Unit,
) {
    val pal = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f, tween(160, easing = TikEase), label = "btnScale")
    Box(
        modifier.size(36.dp)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = if (pressed) 0.7f else 1f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TikIcon(icon, Modifier.size(iconSize), tint = if (tint == Color.Unspecified) pal.tx else tint, strokeWidth = strokeWidth)
    }
}

/** Top bar (build-44 TikTopBar signature): 22sp SemiBold title, optional
 *  leading/trailing slots, optional discreet title long-press. */
@Composable
fun TikTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onTitleLongPress: (() -> Unit)? = null,
) {
    val pal = LocalPalette.current
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
                        detectTapGestures(onLongPress = { onTitleLongPress() })
                    }
                else Modifier,
            ),
        ) {
            Text(
                title, fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp, letterSpacing = (-0.66).sp, color = pal.tx,
            )
        }
        trailing?.invoke()
    }
}

/** Sheet heading (design sheet titles: 17sp SemiBold, centred). */
@Composable
fun TikSheetTitle(text: String) {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = pal.tx)
    }
}

/** Full-width pill action (design PillActionButton). [outline] renders the
 *  ghost variant; [danger] the quiet red text variant (sheet actions). */
@Composable
fun TikPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val pal = LocalPalette.current
    when {
        danger -> Box(
            modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (enabled) pal.danger else pal.sub)
        }
        outline -> Box(
            modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
                .glassSurface(RoundedCornerShape(999.dp))
                .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (enabled) pal.tx else pal.sub)
        }
        else -> Box(
            modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(999.dp)).background(pal.buttonGradient)
                .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.55f))
        }
    }
}

/** Text field in the design's shape (solid s2 fill, r16). Password fields keep
 *  their dedicated component in Onboarding.kt. */
@Composable
fun TikField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    val pal = LocalPalette.current
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = InterTight, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = pal.tx,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(pal.ac),
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(16.dp)).background(pal.s2)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = InterTight, fontSize = 15.sp, color = pal.sub)
                }
                inner()
            }
        },
    )
}

/** Group section header (design: 12.5sp Medium, muted, indented to the card). */
@Composable
fun TikSectionHeader(text: String, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Text(
        text,
        modifier = modifier.padding(start = 34.dp, end = 18.dp, top = 22.dp, bottom = 6.dp),
        fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = pal.sub,
    )
}

/** A settings group: one floating glass card (design r28). */
@Composable
fun TikGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .glassSurface(RoundedCornerShape(28.dp)),
        content = content,
    )
}

/** One row inside a [TikGroup] — build-44 TikInfoRow signature, owner
 *  design metrics (padding 18/15, hairline above all but the first row). */
@Composable
fun TikInfoRow(
    title: String,
    sub: String? = null,
    danger: Boolean = false,
    first: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        if (!first) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(pal.ln))
        }
        Row(
            (if (onClick != null) {
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            } else Modifier)
                .fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = if (danger) pal.danger else pal.tx)
                if (sub != null) {
                    Text(sub, fontFamily = InterTight, fontSize = 12.5.sp, color = pal.sub,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            trailing?.invoke()
        }
    }
}

/** Centred empty state (build-44 TikEmpty signature). */
@Composable
fun TikEmpty(title: String, sub: String, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Column(
        modifier.padding(horizontal = 30.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontFamily = InterTight, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            color = pal.tx, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(sub, fontFamily = InterTight, fontSize = 13.5.sp, color = pal.sub,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** Swatch row (wallpaper picks): flat colour discs, ring on the selected one. */
@Composable
fun TikSwatches(colors: List<Color>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        colors.forEachIndexed { i, c ->
            val isSel = i == selected
            val scale by animateFloatAsState(
                if (isSel) 1.05f else 1f, tween(200, easing = TikEase), label = "swScale")
            Box(
                Modifier.size(28.dp)
                    .then(if (isSel) Modifier.border(2.dp, pal.tx, CircleShape) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(24.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape).background(c)
                        .border(1.dp, pal.ln, CircleShape),
                )
            }
        }
    }
}
