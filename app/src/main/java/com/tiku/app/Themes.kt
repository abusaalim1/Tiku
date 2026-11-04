package com.tiku.app

import androidx.compose.ui.graphics.Color

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
}
