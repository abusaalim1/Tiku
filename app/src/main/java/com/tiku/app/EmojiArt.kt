package com.tiku.app

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS EMOJI FONT (owner order, Round 22 — supersedes the old no-glyph rule and
 * the Microsoft Fluent 3D PNG set, which is fully removed):
 *
 * Every emoji surface in the app renders through the owner-supplied iOS 17.4
 * emoji font (his own uploaded asset, a CBDT/CBLC colour-bitmap font) at
 * app/src/main/assets/fonts/ios_emoji_{a,b}.ttf — picker cells, reaction bar/chips,
 * the heart burst, avatar markers, subject icons, and (via [rememberEmojiFontFamily])
 * message text + the composer. Android renders CBDT fonts natively, so these
 * are the real iOS glyphs, not pictures and not generated art.
 *
 * FONT FILE DECISION: the font ships as TWO fonttools SUBSET files of the
 * original 47,175,752-byte upload — ios_emoji_a.ttf (codepoints < U+1F500,
 * 19,514,920 B, 685 cmap entries, 1,880 glyphs) and ios_emoji_b.ttf
 * (codepoints >= U+1F500, 14,324,572 B, 706 cmap entries, 1,534 glyphs) —
 * because a single 45 MB file exceeds the GitHub API blob-upload limit and
 * could not be pushed to the repo at all. Both parts keep exactly the
 * codepoints the app uses (the full EMOJI_CATEGORIES catalogue + reaction
 * sets + avatar/subject markers, plus regional indicators, skin-tone
 * modifiers, ZWJ, VS16 and keycap parts in both) with all GSUB layout
 * features retained so ZWJ sequences and skin-tone variants still shape;
 * [rememberEmojiFontFamily] chains both files into one FontFamily and the
 * platform falls back per glyph, so callers see a single emoji font.
 * (ROUND 68 sizes: A grew +103,420 B and B +1,415,184 B over the R23
 * files — the WS5 ligature fix; see the R23/WS5 note below.)
 * Validated after subsetting: CBDT + CBLC + GSUB present in both parts.
 *
 * ROUND 23 — WHY SOME EMOJIS WERE NOT iOS ON DEVICE, AND THE FIX:
 * Both subset files inherited the SAME internal family name from the source
 * font ("Apple MFFM Emoji", name IDs 1/4/6 identical in both). Inside one
 * Compose FontFamily chain, Android's font matcher collapsed the two
 * same-named fonts into one — the second part was shadowed, so every glyph
 * that lives only in part B fell back to the DEVICE emoji font (exactly the
 * mix the owner photographed: part A's ❤️/👍 rendered iOS, part B's faces
 * rendered as flat system emoji). Fixes, by construction:
 *  1. Part B's internal family is renamed ("Apple MFFM Emoji B",
 *     postscript "AppleMFFMEmojiB"; name IDs 1/4/6 set, 16/17 added) — the
 *     two parts are now distinct families and the mixed-text chain in
 *     [rememberEmojiFontFamily] falls back per glyph as intended.
 *  2. SINGLE-emoji rendering (the [EmojiArt] composable, hence EmojiGlyph,
 *     the picker cells, the reaction bar, chips, bursts, avatars) no longer
 *     relies on fallback at all: it routes DETERMINISTICALLY by the emoji's
 *     first codepoint — < U+1F500 uses ONLY part A's file, >= U+1F500 uses
 *     ONLY part B's file. The subsets were partitioned on exactly that
 *     boundary, so the routed file always holds the glyph (and, for ZWJ /
 *     skin-tone / flag sequences, the ligature: sequences route by their
 *     first codepoint, whose part holds the whole sequence by construction).
 *  3. Both typefaces are preloaded on a background thread at app start
 *     ([preload], called from TikuApp.onCreate), so a transient surface
 *     (the reaction-bar popup) never races a cold font load.
 *
 * ROUND 68 — WS5: THE ZWJ LIGATURE HOLE IN THAT "BY CONSTRUCTION" CLAIM,
 * AND THE FIX. The R23 claim in point 2 above was WRONG for one class of
 * sequences: ZWJ sequences whose components STRADDLE the U+1F500 split
 * (25 of them in the picker catalogue — 😶‍🌫️ face-in-clouds, 🙂‍↔️/🙂‍↕️
 * head-shakes, ❤️‍🔥/❤️‍🩹, 👁️‍🗨️, 🐕‍🦺, 🐦‍🔥, 🍋‍🟩, 🍄‍🟫, 😵‍💫, 😮‍💨 and
 * all twelve 🧑‍-profession emoji). fonttools' subsetter keeps a GSUB
 * ligature only when EVERY component glyph survives in that subset, so
 * a cross-boundary ligature was dropped from BOTH parts: neither file
 * holds it, and the single-part render falls back per-component to the
 * device font — the missing component (cloud / arrow / star / laptop…)
 * draws as a system glyph stacked OVER the iOS base. That is exactly
 * the owner's WS5 screenshot (cloud/arrow/star overlays on faces).
 * Fix (font files only, no rendering-code change needed): both parts
 * were re-subset from the original 47 MB font (source zip in
 * ~/workspace/user/files/iOS17.4-ANSHULDIXIT_0_xip3.zip) with their
 * shipped cmap as the base PLUS the missing component codepoint of
 * every cross-boundary sequence starting in that part (A +6:
 * U+1F525, U+1FA79, U+1F5E8, U+1F9BA, U+1F7E9, U+1F7EB; B +18:
 * U+1F32B, U+1F4A8, U+2194, U+2195, U+1F4AB, U+2695, U+1F393,
 * U+1F3EB, U+2696, U+1F33E, U+1F373, U+1F3ED, U+1F4BC, U+1F4BB,
 * U+1F3A4, U+1F3A8, U+2708, U+1F384). Verified with fontTools after
 * the rebuild: each part's cmap is a strict superset of the shipped
 * one (A 679→685, B 688→706, nothing lost), zero ligatures lost
 * (A 1816→1830, B 848→1014), all 25 cross-boundary ligatures now
 * present in their routed part, shared glyphs' CBDT bitmaps
 * byte-identical, and part B's distinct family name re-applied.
 * Cost: A +103,420 B, B +1,415,184 B (the B closure also retains the
 * skin-tone variants of the twelve profession ligatures) — see the
 * worker-B report for the APK-budget consequence. With the ligatures
 * restored, point 2's routing rule is now TRUE as written, and no
 * picker-cell code had to change: one EmojiArt per cell, centred,
 * was always the correct layout — the font was the bug.
 *
 * DATA stays standard unicode everywhere (Firestore/local stores keep the real
 * chars for reactions, selections, avatar markers). Only the DISPLAY changed.
 */
object EmojiArt {
    /** Asset paths of the bundled iOS emoji font (two chained parts —
     *  see the header note). Part A covers codepoints < U+1F500. */
    const val FONT_ASSET_PATH = "fonts/ios_emoji_a.ttf"
    const val FONT_ASSET_PATH_B = "fonts/ios_emoji_b.ttf"
    val FONT_ASSET_PATHS = listOf(FONT_ASSET_PATH, FONT_ASSET_PATH_B)

    /** The split point between the two font parts: codepoints below this
     *  live in part A, the rest in part B. */
    const val PART_B_START = 0x1F500

    /** Which part serves [char]: 0 = A, 1 = B, decided by the FIRST
     *  codepoint (regional-indicator flags start at U+1F1E6 → A; keycaps
     *  start at ASCII/#/\* → A; ❤️ U+2764 → A; ZWJ sequences follow their
     *  first codepoint, whose part holds the ligature). */
    fun partFor(char: String): Int {
        if (char.isEmpty()) return 0
        return if (Character.codePointAt(char, 0) < PART_B_START) 0 else 1
    }

    /** The asset file that serves [char] (see [partFor]). */
    fun pathFor(char: String): String =
        if (partFor(char) == 0) FONT_ASSET_PATH else FONT_ASSET_PATH_B

    private val loadLock = Any()

    @Volatile
    private var cachedA: Typeface? = null

    @Volatile
    private var cachedB: Typeface? = null

    private fun load(context: Context, path: String): Typeface {
        val hit = if (path == FONT_ASSET_PATH) cachedA else cachedB
        hit?.let { return it }
        return synchronized(loadLock) {
            val again = if (path == FONT_ASSET_PATH) cachedA else cachedB
            again ?: Typeface.createFromAsset(context.applicationContext.assets, path).also {
                if (path == FONT_ASSET_PATH) cachedA = it else cachedB = it
            }
        }
    }

    /** Part A's [Typeface], loaded once from assets and cached. */
    fun typefaceA(context: Context): Typeface = load(context, FONT_ASSET_PATH)

    /** Part B's [Typeface], loaded once from assets and cached. */
    fun typefaceB(context: Context): Typeface = load(context, FONT_ASSET_PATH_B)

    /** The [Typeface] that serves [char] (see [partFor]). */
    fun typefaceFor(context: Context, char: String): Typeface =
        if (partFor(char) == 0) typefaceA(context) else typefaceB(context)

    /** Legacy accessor kept for existing callers: part A's typeface.
     *  Prefer [typefaceFor] / the [EmojiArt] composable, which route. */
    fun typeface(context: Context): Typeface = typefaceA(context)

    /**
     * ROUND 23: warm BOTH typefaces off the main thread at app start so no
     * surface — least of all the transient reaction-bar popup — can paint
     * with the device fallback while a 13–19 MB CBDT font is still parsing.
     * Fire-and-forget: failures are silent (surfaces load on demand anyway).
     */
    fun preload(context: Context) {
        val app = context.applicationContext
        Thread {
            runCatching { typefaceA(app) }
            runCatching { typefaceB(app) }
        }.start()
    }

    /**
     * Source-compatibility shim for the old PNG map: call sites ask
     * `assetName(x) != null` to decide "render the emoji art". With the font,
     * EVERY emoji renders as art, so any non-blank string qualifies.
     */
    fun assetName(char: String): String? = char.takeIf { it.isNotBlank() }

    /** Facade subject icon, BY NAME (the JSON "icon" field is deliberately
     *  ignored — owner rule). Returns the emoji char the font renders. */
    fun subjectAsset(subjectName: String): String = when (subjectName.trim().lowercase()) {
        "physics" -> "⚛️"
        "chemistry" -> "🧪"
        "mathematics", "maths" -> "📐"
        "biology" -> "🧬"
        else -> "📘"
    }
}

/**
 * The iOS emoji font as a Compose [FontFamily] — THE API for rendering text
 * with real iOS emoji glyphs. Apply it to any Text/BasicTextField whose emoji
 * must look iOS: glyphs present in the font (all emoji) draw from it, and any
 * other characters fall back to the platform fonts as usual.
 *
 * This is the MIXED-TEXT family (bubbles, composer): both parts chained.
 * Since Round 23 the parts carry distinct internal family names, so the
 * platform's per-glyph fallback across the chain actually reaches part B.
 * For a SINGLE emoji, prefer the [EmojiArt] composable, which routes to
 * exactly one file deterministically and never involves fallback.
 */
@Composable
fun rememberEmojiFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        FontFamily(EmojiArt.FONT_ASSET_PATHS.map { Font(it, ctx.assets) })
    }
}

/**
 * ROUND 48: THE CHAT FAMILY — the iOS emoji subsets chained FIRST, Inter
 * (Regular + Medium) behind them. This is the family chat message text
 * (bubbles, composer, reply quotes, thread previews, vault copies) renders
 * in, per the owner's typography order: message text in Inter, emoji as
 * iOS emoji.
 *
 * Why the emoji parts stay first: the subsets contain ZERO ASCII glyphs
 * (verified against their cmaps — no letters, digits or punctuation), so
 * they can never steal ordinary text; every letter, digit and mark falls
 * through to Inter. And every codepoint the subsets DO cover (hearts,
 * arrows, symbols, all emoji) keeps resolving from the subsets exactly
 * as it did with [rememberEmojiFontFamily] — including sequences, whose
 * clusters the platform itemiser keeps inside one font. Inter itself has
 * no emoji glyphs, so nothing emoji-shaped can be pulled out of the
 * subsets by the Inter entries behind them.
 */
@Composable
fun rememberChatFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        FontFamily(
            EmojiArt.FONT_ASSET_PATHS.map { Font(it, ctx.assets) } + listOf(
                Font(R.font.inter_regular, FontWeight.Normal),
                Font(R.font.inter_medium, FontWeight.Medium),
            )
        )
    }
}

/**
 * ROUND 48: the same chain trick for Inter Tight — for the rare header
 * that LEADS with an emoji (the emoji picker's category titles): glyphs
 * resolve from the iOS subsets, the Latin from Inter Tight, exactly the
 * fallback the surface had before, with a named face behind it now.
 */
@Composable
fun rememberTightEmojiFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        FontFamily(
            EmojiArt.FONT_ASSET_PATHS.map { Font(it, ctx.assets) } + listOf(
                Font(R.font.inter_tight_regular, FontWeight.Normal),
                Font(R.font.inter_tight_medium, FontWeight.Medium),
                Font(R.font.inter_tight_semibold, FontWeight.SemiBold),
            )
        )
    }
}

/**
 * Renders one emoji with the bundled iOS emoji font at roughly [size] square
 * (fontSize == size, lineHeight pinned to size so the layout box matches the
 * old image cell). Used by the picker cells, reaction bar/chips, heart burst
 * and avatar markers (via EmojiGlyph / direct call sites).
 *
 * ROUND 23: the font is chosen DETERMINISTICALLY by the emoji itself
 * ([EmojiArt.pathFor]) — a single-font family holding exactly the part that
 * serves this emoji, so no cross-font fallback can substitute the device
 * emoji for any part of the string.
 */
@Composable
fun EmojiArt(
    char: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val desc = contentDescription
    val mod = if (desc != null) {
        modifier.semantics { this.contentDescription = desc }
    } else {
        modifier
    }
    val ctx = LocalContext.current
    val family = remember(char) { FontFamily(Font(EmojiArt.pathFor(char), ctx.assets)) }
    Text(
        text = char,
        fontFamily = family,
        fontSize = size.value.sp,
        lineHeight = size.value.sp,
        textAlign = TextAlign.Center,
        modifier = mod,
    )
}

/** Facade subject icon (atom / test tube / triangular ruler / DNA), ~32dp, no backplate. */
@Composable
fun SubjectArt(
    subjectName: String,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    EmojiArt(
        char = EmojiArt.subjectAsset(subjectName),
        size = size,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}
