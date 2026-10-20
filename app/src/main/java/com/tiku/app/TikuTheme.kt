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

    /** Accent swatch pairs (s1, s2) — exactly the 4 from the prototype. */
    val Accents = listOf(
        Color(0xFFEC8159) to Color(0xFFCC5832),
        Color(0xFFE27A93) to Color(0xFFB53F5C),
        Color(0xFFDDA866) to Color(0xFFA8742F),
        Color(0xFFB0708A) to Color(0xFF7A3A52),
    )

    val ToastBg = Color(0xFF3B1519); val ToastText = Color(0xFFFFF4EE)
    val Red = Color(0xFFE5484D)
    val MeterColors = listOf(Color(0xFFE5484D), Color(0xFFF2A33A), Color(0xFF9BC53D), Color(0xFF7BB661))
}

enum class WallpaperMode { DEFAULT, PLAIN, PEACH, DOTS }

data class TikuUiSettings(
    val accentIdx: Int = 0,
    val wallpaper: WallpaperMode = WallpaperMode.DEFAULT,
    val textSizeSp: Float = 15f,
    val toggles: Map<String, Boolean> = SettingsStore.defaultToggles(),
) {
    val accent: Color get() = TikuTokens.Accents[accentIdx.coerceIn(0, 3)].second
    val gradTop: Color get() = TikuTokens.Accents[accentIdx.coerceIn(0, 3)].first
    val gradBottom: Color get() = TikuTokens.Accents[accentIdx.coerceIn(0, 3)].second
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
        _state.value = TikuUiSettings(
            accentIdx = prefs.getInt("accent", 0),
            wallpaper = WallpaperMode.entries[prefs.getInt("wallpaper", 0).coerceIn(0, 3)],
            textSizeSp = prefs.getFloat("textSize", 15f),
            toggles = keys.associateWith { prefs.getBoolean("tg_$it", defaultToggles()[it] == true) },
        )
    }

    fun setAccent(i: Int) { _state.value = _state.value.copy(accentIdx = i); prefs.edit().putInt("accent", i).apply() }
    fun setWallpaper(i: Int) { _state.value = _state.value.copy(wallpaper = WallpaperMode.entries[i]); prefs.edit().putInt("wallpaper", i).apply() }
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

val TikuEase = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@Composable
fun tikuSettings(): State<TikuUiSettings> = SettingsStore.state.collectAsState()

@Composable
fun TextUnit.Companion.tikuSp(): TextUnit = SettingsStore.state.collectAsState().value.textSizeSp.sp
