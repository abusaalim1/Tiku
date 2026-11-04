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
 * 19,411,500 B, 679 cmap entries, 1,867 glyphs) and ios_emoji_b.ttf
 * (codepoints >= U+1F500, 12,909,332 B, 688 cmap entries, 1,433 glyphs) —
 * because a single 45 MB file exceeds the GitHub API blob-upload limit and
 * could not be pushed to the repo at all. Both parts keep exactly the
 * codepoints the app uses (the full EMOJI_CATEGORIES catalogue + reaction
 * sets + avatar/subject markers, plus regional indicators, skin-tone
 * modifiers, ZWJ, VS16 and keycap parts in both) with all GSUB layout
 * features retained so ZWJ sequences and skin-tone variants still shape;
 * [rememberEmojiFontFamily] chains both files into one FontFamily and the
 * platform falls back per glyph, so callers see a single emoji font.
 * Validated after subsetting: CBDT + CBLC + GSUB present in both parts.
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

    @Volatile
    private var cachedTypeface: Typeface? = null

    /** Part A as a raw [Typeface] (legacy/direct use). For full emoji
     *  coverage in Compose text, use [rememberEmojiFontFamily], which
     *  chains both parts with per-glyph fallback. */
    fun typeface(context: Context): Typeface {
        cachedTypeface?.let { return it }
        return synchronized(this) {
            cachedTypeface ?: Typeface.createFromAsset(context.applicationContext.assets, FONT_ASSET_PATH)
                .also { cachedTypeface = it }
        }
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
 */
@Composable
fun rememberEmojiFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        FontFamily(EmojiArt.FONT_ASSET_PATHS.map { Font(it, ctx.assets) })
    }
}

/**
 * Renders one emoji with the bundled iOS emoji font at roughly [size] square
 * (fontSize == size, lineHeight pinned to size so the layout box matches the
 * old image cell). Used by the picker cells, reaction bar/chips, heart burst
 * and avatar markers (via EmojiGlyph / direct call sites).
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
    Text(
        text = char,
        fontFamily = rememberEmojiFontFamily(),
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
