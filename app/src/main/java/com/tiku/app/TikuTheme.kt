package com.tiku.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Locked light tokens — ported 1:1 from the prototype CSS (:root light). */
object TikuTokens {
    val BgTop = Color(0xFFFFF4EE); val BgBottom = Color(0xFFFCD9C6)
    val Card = Color(0xFFFFFBF8); val Ink = Color(0xFF3B1519); val Muted = Color(0xFFA07F73)
    val Mint = Color(0xFFFBE2D4)
    val Line = Color(0x175A281E)           // rgba(90,40,30,.09)
    val Bubble = Color(0xFFFFFBF8); val BubbleBorder = Color(0x175A281E)
    val Glass = Color(0xB8FFFAF6)          // rgba(255,250,246,.72)
    val Button = Color(0xFF3B1519); val ButtonText = Color(0xFFFFF4EE)
    val PlainPlain = Color(0xFFFFF4EE)     // wallpaper "plain" = var(--bg)
    val PeachTop = Color(0xFFF6D2C4); val PeachBottom = Color(0xFFD9A5A0)

    /** Accent pairs — ROUND 12: the Tik design's flat ACC swatches
     *  (#E5677F #EF8062 #D6A653 #A587E0 #5B9CF5), flat pairs so [accent]
     *  and [gradBrush] resolve to the design colour itself, plus the
     *  couple pair kept LAST (setCoupleTheme targets lastIndex). The
     *  facade never reads Accents — it keeps the peach tokens above. */
    val Accents = listOf(
        Color(0xFFE5677F) to Color(0xFFE5677F),
        Color(0xFFEF8062) to Color(0xFFEF8062),
        Color(0xFFD6A653) to Color(0xFFD6A653),
        Color(0xFFA587E0) to Color(0xFFA587E0),
        Color(0xFF5B9CF5) to Color(0xFF5B9CF5),
        Color(0xFFF2A0B5) to Color(0xFFE5677F), // couple rose
    )

    val ToastBg = Color(0xFF3B1519); val ToastText = Color(0xFFFFF4EE)
    val Red = Color(0xFFE5484D)
    val MeterColors = listOf(Color(0xFFE5484D), Color(0xFFF2A33A), Color(0xFF9BC53D), Color(0xFF7BB661))
}

/** COUPLE (round 6): soft blush wallpaper with a subtle heart motif drawn
 *  over it on the chat surface — appended LAST so existing indices never
 *  shift for users who already picked a wallpaper. */
enum class WallpaperMode { DEFAULT, PLAIN, PEACH, DOTS, COUPLE }

data class TikuUiSettings(
    /** ROUND 13: this is now the COUPLE-THEME index (0..4 into
     *  [COUPLE_THEMES]) — the owner-supplied design replaced the six legacy
     *  accent swatches with five couple themes. Legacy stored values are
     *  migrated once in [SettingsStore.init]; per-chat stored theme ints go
     *  through [ThemeCodec] (Models.kt). */
    val accentIdx: Int = 0,
    val wallpaper: WallpaperMode = WallpaperMode.DEFAULT,
    val textSizeSp: Float = 15f,
    /** ROUND 13: Tik appearance mode. Default DARK (the design's default);
     *  persisted by [SettingsStore.setDarkMode]. */
    val darkMode: Boolean = true,
    val toggles: Map<String, Boolean> = SettingsStore.defaultToggles(),
) {
    val coupleTheme: CoupleTheme get() = COUPLE_THEMES[accentIdx.coerceIn(0, COUPLE_THEMES.lastIndex)]
    val accent: Color get() = coupleTheme.ac
    val gradTop: Color get() = coupleTheme.ac
    val gradBottom: Color get() = coupleTheme.ac2
    val gradBrush: Brush get() = Brush.linearGradient(listOf(gradTop, gradBottom))
    val readReceipts get() = toggles["rr"] == true
    val lastSeen get() = toggles["ls"] == true
    val hideTyping get() = toggles["ht"] == true
    val messagePreview get() = toggles["mp"] == true
    val sounds get() = toggles["sn"] == true
    val vibration get() = toggles["vb"] == true
    val appLock get() = toggles["al"] == true
    // ---- Privacy (ChatRoot) ----
    /** "Block screenshots" (key bs): THE single screenshot switch, owned by the
     *  Privacy group. Default ON = FLAG_SECURE applied inside ChatRoot (chats
     *  blocked from screenshots + app-switcher previews); OFF lifts it. */
    val blockScreenshots get() = toggles["bs"] != false
    /** Video calls: front camera on by default (key vcd, default ON). */
    val cameraDefaultOn get() = toggles["vcd"] != false
    /** Video calls: speaker on by default (key vsp, default ON). */
    val speakerDefaultOn get() = toggles["vsp"] != false
    /** Messages: Enter key sends (key ets, default OFF). */
    val enterToSend get() = toggles["ets"] == true
}

object SettingsStore {
    fun defaultToggles(): Map<String, Boolean> = mapOf(
        "dk" to false, // dark mode (locked to light — toasts instead)
        "al" to false, // app lock
        "dm" to false, // disappearing messages (stored; engine skips in v1)
        "rr" to true,  // read receipts
        "ls" to true,  // last seen
        "bs" to true,  // block screenshots (default ON — the one screenshot switch)
        "ht" to false, // hide typing status
        "sn" to true,  // sounds
        "vb" to true,  // vibration
        "mp" to true,  // message preview
        "qh" to false, // quiet hours
        "ds" to false, // data saver
        "ad" to true,  // auto-download photos
        "vcd" to true, // additional: video calls start with camera on
        "vsp" to true, // additional: video calls start on speaker
        "ets" to false, // additional: Enter key sends the message
    )

    private const val PREF = "tiku_settings"
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(TikuUiSettings())
    val state: StateFlow<TikuUiSettings> = _state

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val keys = defaultToggles().keys
        // ROUND 13 one-time migration: the legacy "accent" pref held a
        // build-44 swatch index (0..5); the new "theme" pref holds a couple
        // theme index (0..4). The remap is NOT idempotent (legacy 4 -> 1),
        // so it runs only while "theme" is absent, then writes "theme".
        val themeIdx = if (prefs.contains("theme")) {
            prefs.getInt("theme", 0)
        } else {
            val legacy = prefs.getInt("accent", 0)
            val mapped = when (legacy) {
                in 0..3 -> legacy
                4 -> 1
                5 -> 0
                else -> 0
            }
            prefs.edit().putInt("theme", mapped).apply()
            mapped
        }
        _state.value = TikuUiSettings(
            accentIdx = themeIdx.coerceIn(0, COUPLE_THEMES.lastIndex),
            wallpaper = WallpaperMode.entries[prefs.getInt("wallpaper", 0).coerceIn(0, WallpaperMode.entries.lastIndex)],
            textSizeSp = prefs.getFloat("textSize", 15f),
            darkMode = prefs.getBoolean("darkMode", true),
            toggles = keys.associateWith { prefs.getBoolean("tg_$it", defaultToggles()[it] == true) },
        )
    }

    /** ROUND 13: [i] is a COUPLE-THEME index (0..4), persisted as "theme". */
    fun setAccent(i: Int) {
        val v = i.coerceIn(0, COUPLE_THEMES.lastIndex)
        _state.value = _state.value.copy(accentIdx = v)
        prefs.edit().putInt("theme", v).apply()
    }
    fun setWallpaper(i: Int) { _state.value = _state.value.copy(wallpaper = WallpaperMode.entries[i]); prefs.edit().putInt("wallpaper", i).apply() }

    /** ROUND 13: Tik appearance mode (design Appearance ▸ Mode). */
    fun setDarkMode(on: Boolean) {
        _state.value = _state.value.copy(darkMode = on)
        prefs.edit().putBoolean("darkMode", on).apply()
    }

    /** The labelled "Couple theme" preset (Me ▸ Appearance). ROUND 13: ON
     *  applies Rose Petal (theme 0 — the successor of the old rose couple
     *  pair) + COUPLE wallpaper; OFF restores the default wallpaper. The
     *  toggle's on/off state in the UI is DERIVED (theme == 0 && wallpaper ==
     *  COUPLE), so the individual controls stay consistent with it. */
    fun setCoupleTheme(on: Boolean) {
        if (on) {
            setAccent(0)
            setWallpaper(WallpaperMode.COUPLE.ordinal)
        } else {
            setWallpaper(WallpaperMode.DEFAULT.ordinal)
        }
    }
    fun setTextSize(sp: Float) { _state.value = _state.value.copy(textSizeSp = sp); prefs.edit().putFloat("textSize", sp).apply() }
    fun setToggle(key: String, on: Boolean) {
        _state.value = _state.value.copy(toggles = _state.value.toggles + (key to on))
        prefs.edit().putBoolean("tg_$key", on).apply()
    }

    /** PBKDF2 app-lock password hash (verification only). */
    private const val SECURE_PREF = "tiku_secure"
    fun passwordHash(ctx: Context): String? =
        ctx.getSharedPreferences(SECURE_PREF, Context.MODE_PRIVATE).getString("pw_hash", null)

    fun savePasswordHash(ctx: Context, hash: String) {
        ctx.getSharedPreferences(SECURE_PREF, Context.MODE_PRIVATE).edit().putString("pw_hash", hash).apply()
    }
}

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)
val Bricolage = FontFamily(
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_bold, FontWeight.Bold),
)
val SerifRegular = FontFamily(Font(R.font.instrument_serif_regular))
val SerifItalic = FontFamily(Font(R.font.instrument_serif_italic))

/** ROUND 48: the chat face — Inter (the Apple-Music-like minimal sans the
 *  owner picked). Plain family, no emoji chain: for app-generated strings
 *  in the message area (tombstones, placeholders). Surfaces that can carry
 *  USER text use the chained family instead
 *  ([rememberChatFontFamily] in EmojiArt.kt) so emoji stay iOS. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

/** ROUND 48: the normal-text face everywhere outside chat — Poppins Bold.
 *  A single weight ON PURPOSE: the owner's rule is that ordinary labels,
 *  rows and body text render IN Poppins Bold, so the family holds exactly
 *  that one font and every weight request resolves to it. */
val PoppinsFamily = FontFamily(
    Font(R.font.poppins_bold, FontWeight.Bold),
)

val TikuEase = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** ROUND 8: the gentle spring ease of the owner's reference chat UI
 *  (cubic-bezier(.34, 1.56, .64, 1) in the HTML, toned down a touch) — used
 *  for bubble entrances and other small settle motions. Calm, not bouncy. */
val TikuSpringEase = androidx.compose.animation.core.CubicBezierEasing(0.34f, 1.30f, 0.64f, 1f)

@Composable
fun tikuSettings(): State<TikuUiSettings> = SettingsStore.state.collectAsState()

@Composable
fun TextUnit.Companion.tikuSp(): TextUnit = SettingsStore.state.collectAsState().value.textSizeSp.sp
