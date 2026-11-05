package com.tiku.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** ROUND 26 — the owner's six chat THEMES.
 *
 *  Each theme re-skins ONE chat (Instagram-style, shared): the chat
 *  background becomes the owner's supplied wallpaper (assets/themes/,
 *  used exactly as delivered — never re-encoded), and ONLY the message
 *  bubble colours + the composer ("message box") colours follow the
 *  table below. UI, layout and structure are untouched. The colours
 *  were colour-matched to the owner's wallpapers and approved by him
 *  on preview mockups (2026-11-04) — do not "improve" them.
 *
 *  There is deliberately NO entry for Default here: [byId] returns
 *  null for "" / "default" / any unknown id, and every render site
 *  guards on `theme != null`, so the Default chat renders through the
 *  exact pre-round code path with the exact pre-round constants.
 *
 *  [caption] is the one colour NOT in the owner's table: the Seen/Sent
 *  caption is drawn directly ON the wallpaper (there are no in-bubble
 *  time/tick texts in this design), so each theme carries a caption
 *  shade picked for ≥ ~5.5:1 contrast against its own wallpaper —
 *  dark shades on the three light wallpapers, light shades on the
 *  three dark ones (t3's is RdSub nudged lighter, #9A99A6 → #B8B7C2;
 *  t6 keeps RdSub's value verbatim). */
data class ChatTheme(
    val id: String,
    val name: String,
    /** File name under assets/themes/ (the wallpaper, exactly as supplied). */
    val asset: String,
    val ownBubble: Color,
    val ownText: Color,
    val peerBubble: Color,
    val peerText: Color,
    /** The composer pill's background. */
    val composerBar: Color,
    /** Composer text + icon tint (send keeps the accent disc). */
    val composerText: Color,
    /** Send/camera discs, reply bar, cursor, picker tick. */
    val accent: Color,
    /** Seen/Sent caption colour, drawn on the wallpaper itself. */
    val caption: Color,
)

object ChatThemes {
    private val WHITE = Color.White
    private val INK = Color(0xFF2B2B2B)
    private val CREAM = Color(0xFFF5E9F0)

    val all: List<ChatTheme> = listOf(
        ChatTheme(
            id = "t1", name = "Blush Kisses", asset = "theme_1.webp",
            ownBubble = Color(0xFFC14A68), ownText = WHITE,
            peerBubble = WHITE, peerText = INK,
            composerBar = WHITE, composerText = INK,
            accent = Color(0xFFC14A68),
            caption = Color(0xFF7E3E4F),
        ),
        ChatTheme(
            id = "t2", name = "First Hold", asset = "theme_2.webp",
            ownBubble = Color(0xFF4E9A51), ownText = WHITE,
            peerBubble = WHITE, peerText = INK,
            composerBar = WHITE, composerText = INK,
            accent = Color(0xFF4E9A51),
            caption = Color(0xFF3E5C38),
        ),
        ChatTheme(
            id = "t3", name = "Magenta Hearts", asset = "theme_3.webp",
            ownBubble = Color(0xFFE84393), ownText = WHITE,
            peerBubble = Color(0xFF3D2440), peerText = WHITE,
            composerBar = Color(0xFF171016), composerText = CREAM,
            accent = Color(0xFFE84393),
            caption = Color(0xFFB8B7C2),
        ),
        ChatTheme(
            id = "t4", name = "Wine Kisses", asset = "theme_4.webp",
            ownBubble = Color(0xFF7E2233), ownText = WHITE,
            peerBubble = Color(0xFFF3E4D8), peerText = Color(0xFF3A2020),
            composerBar = Color(0xFF1D1114), composerText = Color(0xFFF3E4D8),
            accent = Color(0xFFA63A4B),
            caption = Color(0xFFD9C4BC),
        ),
        ChatTheme(
            id = "t5", name = "First Touch", asset = "theme_5.webp",
            ownBubble = Color(0xFFC97E63), ownText = WHITE,
            peerBubble = WHITE, peerText = INK,
            composerBar = WHITE, composerText = INK,
            accent = Color(0xFFC97E63),
            caption = Color(0xFF7D4E38),
        ),
        ChatTheme(
            id = "t6", name = "Midnight Kiss", asset = "theme_6.webp",
            ownBubble = Color(0xFFC2437F), ownText = WHITE,
            peerBubble = Color(0xFF2B2328), peerText = WHITE,
            composerBar = Color(0xFF120D10), composerText = CREAM,
            accent = Color(0xFFD65C94),
            caption = Color(0xFF9A99A6), // = RdSub verbatim (near-black wallpaper)
        ),
    )

    /** The theme for a stored id, or null = Default (absent / blank /
     *  unknown ids all fall back to Default, so a theme set by a newer
     *  build can never break an older one). */
    fun byId(id: String): ChatTheme? = all.firstOrNull { it.id == id }

    // ---- ROUND 35: custom gallery themes ----
    // A custom theme stores its value in the SAME chats/{id}.theme
    // field, encoded as "custom" or "custom:<paletteId>", where
    // <paletteId> is one of the built-in ids above (t1..t6) whose
    // bubble/composer colours the custom photo borrows; "" after the
    // colon (or a bare "custom") means the Default palette. The photo
    // itself is NOT in the field: it rides as an encrypted asset at
    // chats/{id}/themeasset/main (see FirebaseEngine), versioned by
    // the chat doc's themeAssetAt timestamp, and the decoded bitmap
    // reaches the UI through [CustomThemeStore].

    /** True when [id] selects a custom gallery theme. */
    fun isCustom(id: String): Boolean = id == "custom" || id.startsWith("custom:")

    /** The palette half of a custom value: "custom:t3" -> "t3",
     *  "custom" -> "". Non-custom ids are returned unchanged. */
    fun customPaletteId(id: String): String =
        if (isCustom(id)) id.removePrefix("custom").removePrefix(":") else id

    /** The stored theme value for a custom photo with palette
     *  [paletteId] ("" = Default colours). */
    fun customValue(paletteId: String): String =
        if (paletteId.isBlank()) "custom" else "custom:$paletteId"

    /** The COLOUR source for any stored theme value: a built-in id
     *  resolves to itself; a custom value resolves to its palette
     *  theme (null = Default colours, the photo still applies). Every
     *  colour consumer should read through here; the wallpaper source
     *  is chosen separately (asset vs [CustomThemeStore]). */
    fun paletteFor(id: String): ChatTheme? =
        if (isCustom(id)) byId(customPaletteId(id)) else byId(id)
}

/** ROUND 35 — decoded custom-theme wallpapers, keyed by chat id.
 *
 *  The engine owns the bytes (cache file in app files + the encrypted
 *  Firestore asset) and publishes the decoded bitmap here; the chat
 *  render side simply observes [bitmaps] and draws the entry for a
 *  chat whose theme is custom. An absent entry means "not downloaded
 *  yet" — the render side draws no wallpaper until it lands, exactly
 *  like a built-in theme whose asset failed to decode. */
object CustomThemeStore {
    private val _bitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val bitmaps: StateFlow<Map<String, Bitmap>> = _bitmaps

    fun bitmap(chatId: String): Bitmap? = _bitmaps.value[chatId]

    fun publish(chatId: String, bmp: Bitmap) {
        _bitmaps.value = _bitmaps.value + (chatId to bmp)
    }

    fun evict(chatId: String) {
        _bitmaps.value = _bitmaps.value - chatId
    }
}
