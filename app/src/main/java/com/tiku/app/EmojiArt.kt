package com.tiku.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * NO-EMOJI-GLYPH RULE (owner hard rule): no unicode emoji may be rendered anywhere
 * in the app chrome. Every emoji the UI needs is a Microsoft Fluent Emoji 3D PNG
 * (MIT-licensed, github.com/microsoft/fluentui-emoji) in app/src/main/assets/emoji3d/.
 *
 * DATA stays standard: Firestore/local stores keep the real unicode chars
 * (reactions, sheet selections, avatar markers). ONLY the DISPLAY swaps glyph -> 3D image.
 * User-typed emoji inside message TEXT stay text (user content, exempt).
 */
object EmojiArt {
    private val map = mapOf(
        "❤️" to "red_heart",
        "😂" to "face_with_tears_of_joy",
        "😮" to "face_with_open_mouth",
        "😢" to "crying_face",
        "👍" to "thumbs_up",
        "🔥" to "fire",
        "👋" to "waving_hand",
        "📍" to "round_pushpin",
        "😊" to "smiling_face_with_smiling_eyes",
        "🥹" to "face_holding_back_tears",
        "😍" to "smiling_face_with_heart-eyes",
        "🤍" to "white_heart",
        "✨" to "sparkles",
        "🌿" to "herb",
        "🌸" to "cherry_blossom",
        "☕" to "hot_beverage",
        "🎧" to "headphone",
        "🎨" to "artist_palette",
        "🙌" to "raising_hands",
        "👀" to "eyes",
        "💫" to "dizzy",
        "🍋" to "lemon",
        "☁️" to "cloud",
        "☁" to "cloud",
        "🫶" to "heart_hands",
        "🥳" to "partying_face",
        "😌" to "relieved_face",
        "🤭" to "face_with_hand_over_mouth",
        "💬" to "speech_balloon",
        "🌙" to "crescent_moon",
        "🙂" to "slightly_smiling_face",
        "📘" to "blue_book",
        "📝" to "memo",
        "📓" to "notebook",
        "⏱" to "stopwatch",
    )

    fun assetName(char: String): String? = map[char]

    /** Subject icons map BY NAME — the JSON "icon" field is deliberately ignored (owner rule). */
    fun subjectAsset(subjectName: String): String = when (subjectName.trim().lowercase()) {
        "physics" -> "atom_symbol"
        "chemistry" -> "test_tube"
        "mathematics", "maths" -> "triangular_ruler"
        "biology" -> "dna"
        else -> "blue_book"
    }

    private val cache = mutableMapOf<String, ImageBitmap?>()

    private fun loadAsset(ctx: Context, asset: String): ImageBitmap? {
        synchronized(cache) { if (cache.containsKey(asset)) return cache[asset] }
        val bmp = try {
            ctx.assets.open("emoji3d/$asset.png").use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }?.asImageBitmap()
        synchronized(cache) { cache[asset] = bmp }
        return bmp
    }

    internal fun load(ctx: Context, char: String): ImageBitmap? {
        val asset = map[char] ?: return null
        return loadAsset(ctx, asset)
    }

    internal fun loadSubject(ctx: Context, subjectName: String): ImageBitmap? =
        loadAsset(ctx, subjectAsset(subjectName))
}

/**
 * Renders the Fluent 3D image for a mapped emoji char. Renders nothing (never a glyph)
 * when the char has no asset — call sites fall back to initials for unmapped data.
 */
@Composable
fun EmojiArt(
    char: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val ctx = LocalContext.current
    val bmp = remember(char) { EmojiArt.load(ctx, char) }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier.size(size))
    }
}

/** Facade subject icon (Fluent 3D atom / test tube / triangular ruler / DNA), ~32dp, no backplate. */
@Composable
fun SubjectArt(
    subjectName: String,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val ctx = LocalContext.current
    val bmp = remember(subjectName) { EmojiArt.loadSubject(ctx, subjectName) }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier.size(size))
    }
}
