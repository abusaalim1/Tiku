package com.tiku.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * INFO — ROUND 14: the owner's TikuRedesign InfoScreen, ported onto the
 * real thread (this file replaces the build-47 ChatDetailsScreen — the
 * old glass details UI is gone). Glowing avatar header, Day-N-together
 * pill from the thread's real creation time, RoundActions
 * Profile/Search/Mute/Options with real subpages, Customize (per-chat
 * theme swatches on the EXISTING theme storage + the real text-size
 * store), Starred (real list), Privacy & safety (Block via the engine's
 * block API + Clear chat), Nicknames (the existing nicknames engine),
 * Shared media (the thread's real image messages).
 *
 * Hard-NO honoured: no follower stats, no bio, no follow button, no
 * Restrict, no Report, no group creation, no fake devices.
 */

// ================= per-thread local mute (Round 14 spec) =================
/**
 * Local per-thread mute flag. There is no server mute and (outside the
 * owner account) no notifications at all in Tik, so muting is a purely
 * local preference: a muted thread's rows lose their unread-dot emphasis
 * in Messages (Worker A's shell reads [muted]). Persisted in
 * SharedPreferences; every access is guarded because AppState.appCtx is
 * a lateinit that is unset in the screenshot harness.
 */
object ThreadMuteStore {
    private val _muted = MutableStateFlow<Set<String>>(emptySet())
    val muted: StateFlow<Set<String>> = _muted

    @Volatile
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val prefs = AppState.appCtx.getSharedPreferences("tiku_thread_mute", Context.MODE_PRIVATE)
            _muted.value = prefs.getStringSet("muted", emptySet())?.toSet() ?: emptySet()
        }
    }

    fun isMuted(threadId: String): Boolean {
        ensureLoaded()
        return threadId in _muted.value
    }

    fun setMuted(threadId: String, on: Boolean) {
        ensureLoaded()
        _muted.value = if (on) _muted.value + threadId else _muted.value - threadId
        runCatching {
            AppState.appCtx.getSharedPreferences("tiku_thread_mute", Context.MODE_PRIVATE)
                .edit().putStringSet("muted", _muted.value).apply()
        }
    }
}

// ================= redesign tokens (verbatim from TikuRedesign) =================
private val RdICard = Color(0xFF1B1B1F)
private val RdIRose = Color(0xFFFF5C8A)
private val RdIRoseDeep = Color(0xFFE23D74)
private val RdISub = Color(0xFF9A99A6)
private val RdIDanger = Color(0xFFFF453A)
private val RdIEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val RdIThemes = listOf(
    Color(0xFFFF5C8A) to Color(0xFFE23D74),
    Color(0xFF6AA8FF) to Color(0xFF3D7BE0),
    Color(0xFF8E7CFF) to Color(0xFF6A55E0),
    Color(0xFFFFB347) to Color(0xFFE08A1E),
    Color(0xFF4FD1C5) to Color(0xFF22A99D),
)
private val RdIAvatarHues = listOf(
    Color(0xFFFF5C8A), Color(0xFF8E7CFF), Color(0xFFFFB347), Color(0xFF4FD1C5),
    Color(0xFF6AA8FF), Color(0xFFB07A5A), Color(0xFF5A82B0), Color(0xFF4F9A7E),
)

/** The file's .rise entrance. */
private fun Modifier.rdIRise(i: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(i * 70L); a.animateTo(1f, spring(0.6f, 360f)) }
    this.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 28f }
}

/** The file's .press feedback. */
private fun Modifier.rdIPress(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) 0.95f else 1f, spring(0.6f, 500f), label = "rdIPress")
    this.scale(s).clickable(src, null, onClick = onClick)
}

/** Redesign avatar on real data (same chain as Chat's RdAv). */
@Composable
private fun RdInfoAv(person: Person, size: Int) {
    val seed = person.uid.ifEmpty { person.name }
    val hue = RdIAvatarHues[((seed.hashCode() % RdIAvatarHues.size) + RdIAvatarHues.size) % RdIAvatarHues.size]
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(hue),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = person.photoBitmap
        when {
            bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            person.photoUrl.isNotEmpty() -> coil.compose.AsyncImage(
                person.photoUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            EmojiArt.assetName(person.emoji) != null -> EmojiArt(person.emoji, (size * 0.62f).dp)
            else -> Text(
                person.name.take(1).uppercase(), color = Color.White,
                fontSize = (size * 0.38f).sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ================= the file's Info atoms =================
@Composable
private fun RdIBackBtn(onBack: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(RdICard)
            .border(1.dp, Color.White.copy(0.08f), CircleShape)
            .rdIPress(onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RdICardRow(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(RdICard).padding(6.dp),
    ) { content() }
}

@Composable
private fun RdITwoLine(
    t: String,
    s: String? = null,
    chevron: Boolean = false,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) Modifier.fillMaxWidth().rdIPress(onClick) else Modifier.fillMaxWidth()
    Row(mod.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t, color = if (danger) RdIDanger else Color.White, fontSize = 16.sp)
            if (s != null) Text(s, color = RdISub, fontSize = 13.sp)
        }
        if (chevron) Icon(Icons.Filled.KeyboardArrowRight, null, tint = RdISub, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RdINavRow(
    t: String,
    s: String? = null,
    danger: Boolean = false,
    chevron: Boolean = true,
    onClick: () -> Unit,
) = RdITwoLine(t, s, chevron, danger, onClick)

@Composable
private fun RdIToggle(on: Boolean) {
    val x = remember { Animatable(if (on) 25f else 3f) }
    LaunchedEffect(on) { x.animateTo(if (on) 25f else 3f, spring(0.55f, 420f)) }
    Box(
        Modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(999.dp))
            .background(if (on) RdIRose else Color.White.copy(0.15f)),
    ) {
        Box(
            Modifier.offset { IntOffset(x.value.roundToInt(), 0) }.padding(top = 3.dp)
                .size(24.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White),
        )
    }
}

@Composable
private fun RdIToggleRow(t: String, s: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().rdIPress { onChange(!on) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t, color = Color.White, fontSize = 16.sp)
            if (s != null) Text(s, color = RdISub, fontSize = 13.sp)
        }
        RdIToggle(on)
    }
}

@Composable
private fun RdIField(
    value: String,
    onChange: (String) -> Unit,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    Column {
        if (label != null) {
            Text(label, color = RdISub, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RdICard)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = RdISub, fontSize = 16.sp)
            }
            BasicTextField(
                value, onChange,
                enabled = enabled,
                textStyle = TextStyle(color = if (enabled) Color.White else RdISub, fontSize = 16.sp),
                cursorBrush = SolidColor(RdIRose),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RdIPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (filled) Brush.linearGradient(listOf(RdIRose, RdIRoseDeep)) else SolidColor(RdICard))
            .rdIPress(onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RdISection(t: String) {
    Text(t, color = RdISub, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun RdIDivider() = Box(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color.White.copy(0.08f)),
)

@Composable
private fun RdIRoundAction(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(if (active) RdIRose else RdICard)
                .border(1.dp, Color.White.copy(0.08f), CircleShape)
                .rdIPress(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(label, color = RdISub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The file's SubPage shell. */
@Composable
private fun RdISubPage(top: Dp = 8.dp, bottom: Dp, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RdISubHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RdIBackBtn(onBack)
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ================= InfoScreen =================
@Composable
fun InfoScreen(threadId: String) {
    val threads by AppState.threads.collectAsState()
    val thread = threads.firstOrNull { it.id == threadId }
    var sub by remember { mutableStateOf<String?>(null) }
    BackHandler {
        if (sub != null) sub = null else AppState.go(Screen.Chat(threadId))
    }
    if (thread == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Chat unavailable", color = RdISub, fontSize = 14.sp)
        }
        return
    }
    val mutedSet by ThreadMuteStore.muted.collectAsState()
    val isMuted = thread.id in mutedSet
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AnimatedContent(
            targetState = sub,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(380, easing = RdIEase)) { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = RdIEase)) { -it / 4 } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally(tween(380, easing = RdIEase)) { -it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = RdIEase)) { it / 4 } + fadeOut(tween(220)))
                }
            },
            label = "infoSub",
        ) { target ->
            when (target) {
                null -> RdIInfoMain(thread, isMuted, onSub = { sub = it })
                "profile" -> RdIProfilePage(thread) { sub = null }
                "search" -> RdISearchPage(thread) { sub = null }
                "customize" -> RdICustomizePage(thread) { sub = null }
                "starred" -> RdIStarredPage(thread) { sub = null }
                "privacy" -> RdIPrivacyPage(thread) { sub = null }
                "nicknames" -> RdINicknamesPage(thread) { sub = null }
                "options" -> RdIOptionsPage(thread) { sub = null }
                else -> RdIInfoMain(thread, isMuted, onSub = { sub = it })
            }
        }
    }
}

/** MainActivity routes Screen.ChatDetails here — the redesign's
 *  InfoScreen IS the chat-details surface now; the old glass
 *  ChatDetailsScreen implementation is deleted. */
@Composable
fun ChatDetailsScreen(threadId: String) = InfoScreen(threadId)

@Composable
private fun RdIInfoMain(thread: ChatThread, muted: Boolean, onSub: (String) -> Unit) {
    val ctx = LocalContext.current
    val person = thread.person
    val starred = thread.messages.filter { it.starredByMe && !it.deletedForMe && !it.deletedForEveryone }
    val images = thread.messages.filter { it.kind == MsgKind.IMAGE && !it.deletedForMe }
    val days = if (thread.createdAtMs > 0) {
        ((System.currentTimeMillis() - thread.createdAtMs) / 86_400_000L).toInt()
    } else {
        0
    }
    RdISubPage(bottom = 40.dp) {
        item {
            Row { RdIBackBtn { AppState.go(Screen.Chat(thread.id)) } }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val glowT = rememberInfiniteTransition(label = "glow")
                val glow by glowT.animateFloat(
                    18f, 38f,
                    infiniteRepeatable(tween(1600, easing = RdIEase), RepeatMode.Reverse),
                    label = "g",
                )
                Box(
                    Modifier.size(116.dp)
                        .shadow(glow.dp, CircleShape, ambientColor = RdIRose, spotColor = RdIRose)
                        .clip(CircleShape)
                        .border(3.dp, RdIRose, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { RdInfoAv(person, 104) }
                Spacer(Modifier.height(14.dp))
                Text(
                    thread.displayName, color = Color.White, fontSize = 32.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                if (person.username.isNotEmpty()) {
                    Text("@${person.username}", color = RdISub, fontSize = 18.sp)
                }
                if (days >= 1) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(RdICard)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Favorite, null, tint = RdIRose, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Day $days together", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.rdIRise(0)) {
                    RdIRoundAction(Icons.Filled.Person, "Profile") { onSub("profile") }
                }
                Box(Modifier.rdIRise(1)) {
                    RdIRoundAction(Icons.Filled.Search, "Search") { onSub("search") }
                }
                Box(Modifier.rdIRise(2)) {
                    RdIRoundAction(
                        Icons.Filled.Notifications,
                        if (muted) "Unmute" else "Mute",
                        active = muted,
                    ) {
                        ThreadMuteStore.setMuted(thread.id, !muted)
                        AppState.toast(if (!muted) "Chat muted" else "Chat unmuted")
                    }
                }
                Box(Modifier.rdIRise(3)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(RdICard)
                                .border(1.dp, Color.White.copy(0.08f), CircleShape)
                                .rdIPress { onSub("options") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MoreVert, null, tint = Color.White,
                                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = 90f },
                            )
                        }
                        Text("Options", color = RdISub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        item {
            RdICardRow {
                RdINavRow("Customize chat", "Couple themes and text size") { onSub("customize") }
                RdIDivider()
                RdINavRow("Starred messages", "${starred.size} saved") { onSub("starred") }
                RdIDivider()
                RdINavRow("Privacy & safety") { onSub("privacy") }
                RdIDivider()
                RdINavRow("Nicknames", "Set nicknames for each other") { onSub("nicknames") }
                // ROUND 33 — OWNER-ONLY chat export: a plain-text
                // transcript of this chat's loaded messages, shared as
                // a .txt via the share sheet (see ChatExport). The row
                // composes only for the owner account; nobody else's
                // details screen changes.
                if (OwnerState.isOwner) {
                    RdIDivider()
                    RdINavRow(
                        "Export chat (text)",
                        "Share this chat as a text file — only for your account",
                    ) { ChatExport.share(ctx, thread) }
                }
            }
        }
        item { RdISection("Shared media") }
        item {
            RdICardRow {
                if (images.isEmpty()) {
                    Text(
                        "Photos and videos shared in this chat will appear here.",
                        color = RdISub, fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyRow(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(images, key = { it.id }) { m ->
                            Box(
                                Modifier.size(110.dp, 130.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                            ) {
                                m.photo?.let {
                                    Image(
                                        it.asImageBitmap(), null, Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= subpages =================
@Composable
private fun RdIProfilePage(thread: ChatThread, onBack: () -> Unit) {
    val startCall = rememberCallStarter()
    RdISubPage(bottom = 40.dp) {
        item { RdISubHeader("Profile", onBack) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RdInfoAv(thread.person, 96)
                Spacer(Modifier.height(12.dp))
                Text(
                    thread.displayName, color = Color.White, fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
                if (thread.person.username.isNotEmpty()) {
                    Text("@${thread.person.username}", color = RdISub, fontSize = 15.sp)
                }
            }
        }
        item {
            RdICardRow {
                RdINavRow("Voice call", "Start a voice call") { startCall(thread.id, false) }
            }
        }
    }
}

@Composable
private fun RdISearchPage(thread: ChatThread, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    // ROUND 22 (parent): an UNOPENED gift message is sealed on this
    // surface too — it can neither match a search (its text stays
    // unguessable) nor render. Opened gifts behave as normal text.
    val giftCtx = LocalContext.current
    // ROUND 28b: same per-account namespace as Chat.kt's gift store —
    // this reader must look in the signed-in account's file, or a gift
    // opened in chat would still count as sealed here.
    val giftUid = (if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid())
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .take(64)
        .ifEmpty { "anon" }
    val giftOpened = remember(giftUid) {
        giftCtx.getSharedPreferences("tiku_fx_gifts_$giftUid", Context.MODE_PRIVATE)
            .getStringSet("opened", emptySet())?.toSet() ?: emptySet()
    }
    val results = if (q.isBlank()) {
        emptyList()
    } else {
        thread.messages.filter {
            !it.deletedForMe && it.kind == MsgKind.TEXT &&
                !(it.fx == "gift" && it.id !in giftOpened) &&
                it.text.contains(q, ignoreCase = true)
        }
    }
    RdISubPage(bottom = 40.dp) {
        item { RdISubHeader("Search", onBack) }
        item { RdIField(q, { q = it }, placeholder = "Search messages") }
        if (q.isNotBlank()) {
            if (results.isEmpty()) {
                item {
                    Text(
                        "No messages found", color = RdISub, fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            } else {
                item {
                    RdICardRow {
                        results.forEachIndexed { idx, m ->
                            if (idx > 0) RdIDivider()
                            RdITwoLine(if (m.me) "You" else thread.displayName, m.text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RdICustomizePage(thread: ChatThread, onBack: () -> Unit) {
    val globalSettings by SettingsStore.state.collectAsState()
    val themeIdx = thread.effectiveSettings(globalSettings).accentIdx
    RdISubPage(bottom = 40.dp) {
        item { RdISubHeader("Customize chat", onBack) }
        // ROUND 26 — the shared wallpaper themes (the owner's six):
        // picking one re-skins BOTH phones (chats/{id}.theme).
        item { RdISection("Theme") }
        item {
            RdICardRow {
                val current = ChatThemes.byId(thread.themeId)
                RdINavRow("Theme", current?.name ?: "Default") {
                    openRdThemePicker(thread)
                }
            }
        }
        item { RdISection("Chat theme") }
        item {
            RdICardRow {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RdIThemes.forEachIndexed { i, pair ->
                        val sel = themeIdx == i
                        val sc by animateFloatAsState(
                            if (sel) 1.1f else 1f, spring(0.5f, 400f), label = "sw$i",
                        )
                        Box(
                            Modifier.size(52.dp).scale(sc).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(pair.first, pair.second)))
                                .then(
                                    if (sel) Modifier.border(2.5.dp, Color.White, CircleShape)
                                    else Modifier,
                                )
                                .rdIPress {
                                    AppState.repo.setTheme(thread.id, ThemeCodec.toStored(i), null)
                                },
                        )
                    }
                }
            }
        }
        item { RdISection("Text size") }
        item {
            RdICardRow {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Text size", color = Color.White, fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${globalSettings.textSizeSp.toInt()} sp", color = RdISub, fontSize = 13.sp)
                    }
                    Slider(
                        value = globalSettings.textSizeSp,
                        onValueChange = { SettingsStore.setTextSize(it) },
                        valueRange = 12f..20f,
                        colors = SliderDefaults.colors(
                            thumbColor = RdIRose,
                            activeTrackColor = RdIRose,
                            inactiveTrackColor = Color.White.copy(0.15f),
                        ),
                    )
                }
            }
        }
    }
}

/** ROUND 26 — the theme picker, opened from Customize chat ▸ Theme.
 *  AppState.sheet is the house pattern; the sheet host renders it in
 *  the Tik sheet skin over this screen. A Default tile + the six
 *  wallpaper tiles (the owner's images, decoded from assets exactly
 *  as supplied). Tapping one writes the SHARED chats/{id}.theme via
 *  the repo and closes — both phones re-skin live through the
 *  threads listener, the open chat included. */
private fun openRdThemePicker(thread: ChatThread) {
    AppState.sheet {
        Column(Modifier.padding(horizontal = 6.dp)) {
            Text(
                "Theme", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "Applies to both of you", color = RdISub, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            val tiles: List<ChatTheme?> = listOf(null) + ChatThemes.all
            tiles.chunked(3).forEach { rowTiles ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowTiles.forEach { t ->
                        RdThemeTile(
                            theme = t,
                            selected = (t?.id ?: "") == thread.themeId,
                            modifier = Modifier.weight(1f),
                        ) {
                            AppState.repo.setTheme(thread.id, t?.id ?: "")
                            AppState.closeSheet()
                        }
                    }
                    repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** One picker tile: the wallpaper itself (Default = the plain black
 *  chat of today), a rose ring + ✓ on the live theme, name beneath. */
@Composable
private fun RdThemeTile(
    theme: ChatTheme?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.66f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .then(
                    if (selected) Modifier.border(2.5.dp, RdIRose, RoundedCornerShape(14.dp))
                    else Modifier.border(1.dp, Color.White.copy(0.14f), RoundedCornerShape(14.dp)),
                )
                .rdIPress(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (theme != null) {
                val bmp = remember(theme.id) {
                    runCatching {
                        ctx.assets.open("themes/${theme.asset}")
                            .use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), null, Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                        .clip(CircleShape).background(RdIRose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            theme?.name ?: "Default",
            color = if (selected) Color.White else RdISub,
            fontSize = 12.5.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RdIStarredPage(thread: ChatThread, onBack: () -> Unit) {
    val starred = thread.messages.filter { it.starredByMe && !it.deletedForMe && !it.deletedForEveryone }
    RdISubPage(bottom = 40.dp) {
        item { RdISubHeader("Starred messages", onBack) }
        if (starred.isEmpty()) {
            item {
                Text(
                    "No starred messages yet. Hold a message and tap Star.",
                    color = RdISub, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        } else {
            item {
                RdICardRow {
                    starred.forEachIndexed { idx, m ->
                        if (idx > 0) RdIDivider()
                        RdITwoLine(if (m.me) "You" else thread.displayName, replyQuoteLabel(m))
                    }
                }
            }
        }
    }
}

@Composable
private fun RdIPrivacyPage(thread: ChatThread, onBack: () -> Unit) {
    val blockedUids by AppState.blockedUids.collectAsState()
    val blocked = thread.person.uid in blockedUids
    RdISubPage(bottom = 40.dp) {
        item { RdISubHeader("Privacy & safety", onBack) }
        item {
            RdICardRow {
                RdINavRow(if (blocked) "Unblock" else "Block", danger = true, chevron = false) {
                    if (blocked) {
                        AppState.repo.unblockUser(thread.person.uid)
                        AppState.toast("Unblocked")
                    } else {
                        AppState.repo.blockUser(thread.person.uid)
                        AppState.toast("Blocked")
                    }
                }
                RdIDivider()
                RdINavRow("Clear chat", "Removes messages from this device", danger = true, chevron = false) {
                    AppState.repo.clearChat(thread.id)
                    AppState.toast("Chat cleared")
                }
            }
        }
    }
}

@Composable
private fun RdINicknamesPage(thread: ChatThread, onBack: () -> Unit) {
    var peerNick by remember(thread.id) { mutableStateOf(thread.nickname ?: "") }
    RdISubPage(bottom = 40.dp) {
        item { RdISubHeader("Nicknames", onBack) }
        item { RdISection("Nickname for ${thread.person.name}") }
        item {
            RdIField(peerNick, { peerNick = it }, placeholder = thread.person.name)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RdIPill("Save", filled = true) {
                    AppState.repo.setNickname(thread.id, peerNick)
                    AppState.toast("Nickname saved")
                }
            }
        }
        item { RdISection("Your nickname") }
        item {
            RdIField(thread.myNickname ?: "", {}, placeholder = "Not set", enabled = false)
        }
        item {
            Text(
                "Only ${thread.person.name} can set your nickname.",
                color = RdISub, fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun RdIOptionsPage(thread: ChatThread, onBack: () -> Unit) {
    val startCall = rememberCallStarter()
    val mutedSet by ThreadMuteStore.muted.collectAsState()
    val muted = thread.id in mutedSet
    val blockedUids by AppState.blockedUids.collectAsState()
    val blocked = thread.person.uid in blockedUids
    RdISubPage(bottom = 40.dp) {
        item { RdISubHeader("Options", onBack) }
        item {
            RdICardRow {
                RdINavRow("Voice call") { startCall(thread.id, false) }
                RdIDivider()
                RdIToggleRow("Mute", "Silences the unread dot for this chat", muted) {
                    ThreadMuteStore.setMuted(thread.id, it)
                }
                RdIDivider()
                RdINavRow("Clear chat", danger = true, chevron = false) {
                    AppState.repo.clearChat(thread.id)
                    AppState.toast("Chat cleared")
                }
                RdIDivider()
                RdINavRow(if (blocked) "Unblock" else "Block", danger = true, chevron = false) {
                    if (blocked) {
                        AppState.repo.unblockUser(thread.person.uid)
                        AppState.toast("Unblocked")
                    } else {
                        AppState.repo.blockUser(thread.person.uid)
                        AppState.toast("Blocked")
                    }
                }
            }
        }
    }
}
