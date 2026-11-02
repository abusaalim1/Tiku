package com.tiku.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TabsIcons = mapOf(
    HomeTab.CHATS to Icons.Outlined.ChatBubbleOutline,
    HomeTab.CALLS to Icons.Filled.Call,
    HomeTab.ME to Icons.Outlined.PersonOutline,
    // WATCH has no entry: its tab renders the drawn WatchGlyph instead.
)

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    // QUICK EXIT (lock rule): Back at ChatRoot home re-locks instantly to the facade.
    androidx.activity.compose.BackHandler { ChatSession.quickExit(ctx) }
    val settings = tikuSettings().value
    val tab = AppState.homeTab
    val title = when (tab) {
        HomeTab.CHATS -> "Chats"; HomeTab.CALLS -> "Calls"
        HomeTab.WATCH -> "Watch"; HomeTab.ME -> "Me"
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // .top header
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // discreet quick-exit control: long-press the home title re-locks instantly
                Box(
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { ChatSession.quickExit(ctx) })
                    },
                ) { SerifTitle(title, 30.sp) }
                Row {
                    if (tab == HomeTab.WATCH) {
                        // ROUND 11 (owner's Tikuu_11 design): the Watch
                        // header is the serif title + ONE round plus button
                        // — no moon/theme dummy chrome (the theme is
                        // light-locked). The plus is real: YouTube home.
                        GlassIconButton(Icons.Filled.Add, label = "YouTube home") {
                            WatchHolder.goHome(ctx)
                        }
                    } else {
                        GlassIconButton(Icons.Filled.DarkMode, label = "Theme") {
                            AppState.toast("Light mode is Tikuu’s home")
                        }
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(Icons.Filled.Add, label = "New chat") { NewChatSheet.open() }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                // ROUND 8 (animations pass): panes cross-fade with a whisper
                // of horizontal drift instead of snapping (transform/alpha
                // only, 240ms — calm).
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(240, easing = TikuEase)) +
                            slideInHorizontally(tween(240, easing = TikuEase)) { it / 24 })
                            .togetherWith(fadeOut(tween(160, easing = TikuEase)))
                    },
                    label = "home-tabs",
                ) { t ->
                    when (t) {
                        HomeTab.CHATS -> ChatsPane()
                        HomeTab.CALLS -> CallsPane()
                        HomeTab.WATCH -> WatchPane()
                        HomeTab.ME -> MePane()
                    }
                }
            }
        }
        // glass floating tab bar
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .align(Alignment.BottomCenter)
                .height(64.dp)
                .clip(RoundedCornerShape(26.dp))
                // NOTE: no .blur() here — blurring this box blurred its OWN icons/labels
                // (the "Tik bottom stays blurred" bug); the translucent Glass color is
                // what gives the frosted-glass look.
                .background(TikuTokens.Glass)
                .border(1.dp, TikuTokens.Line, RoundedCornerShape(26.dp))
                .shadow(20.dp, RoundedCornerShape(26.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                HomeTab.CHATS to "Chats", HomeTab.CALLS to "Calls",
                HomeTab.WATCH to "Watch", HomeTab.ME to "Me",
            ).forEach { (t, label) ->
                val on = t == tab
                val tint = if (on) settings.accent else TikuTokens.Muted
                Column(
                    Modifier.weight(1f).height(52.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) TikuTokens.Mint else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AppState.homeTab = t },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // ROUND 10: Watch's tab mark is the drawn glyph (rounded
                    // rect + play triangle), never the official logo.
                    if (t == HomeTab.WATCH) WatchGlyph(tint, Modifier.size(22.dp))
                    else Icon(TabsIcons.getValue(t), null, Modifier.size(22.dp), tint = tint)
                    Text(label, fontSize = 12.sp, fontFamily = Manrope, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint)
                }
            }
        }
    }
}

// ================= Chats pane =================
@Composable
fun ChatsPane() {
    var query by remember { mutableStateOf("") }
    val threads by AppState.threads.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
        item {
            // search field
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp)).background(TikuTokens.Card)
                    .border(1.dp, TikuTokens.Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(16.dp), tint = TikuTokens.Muted)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it },
                    textStyle = TextStyle(fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink),
                    cursorBrush = SolidColor(TikuTokens.Ink), singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search chats", fontSize = 15.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                        inner()
                    },
                )
            }
            MomentsRow()
        }
        // round 6: the list filter matches the DISPLAY name (nickname when
        // set) and the @username, not just the profile name.
        // round 8: pinned chats float to the top (stable sort — recency
        // order is otherwise untouched).
        val filtered = threads.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.person.username.contains(query, ignoreCase = true)
        }.sortedByDescending { it.pinned }
        if (filtered.isEmpty() && !Config.PREVIEW_UI_ONLY) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Search a username to start a chat", fontSize = 14.sp, color = TikuTokens.Muted,
                        fontFamily = Manrope, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 60.dp))
                }
            }
        }
        items(filtered, key = { it.id }) { th -> ChatRow(th) }
    }
}

@Composable
fun MomentsRow() {
    val scope = rememberCoroutineScope()
    val threads by AppState.threads.collectAsState()
    val settings = tikuSettings().value
    val people = if (Config.PREVIEW_UI_ONLY) PreviewStore.people else threads.map { it.person }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        people.forEachIndexed { i, p ->
            Column(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    scope.launch { StoryHelper.open(p, i) }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(60.dp)
                        .border(2.dp, settings.accent, RoundedCornerShape(22.dp))
                        .padding(3.dp),
                ) {
                    AvatarBox(p, 54.dp, 19.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(p.name.split(" ").first(), fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
        }
    }
}

@Composable
fun ChatRow(th: ChatThread) {
    val settings = tikuSettings().value
    val last = th.messages.lastOrNull { !it.deletedForMe }
    val preview = when {
        last == null -> ""
        last.kind == MsgKind.VOICE -> "Voice message"
        last.kind == MsgKind.IMAGE -> "Photo"
        // round 4: call events preview as their pill label, never raw text
        last.kind == MsgKind.CALL -> callEventLabel(last, last.callMine)
        else -> last.text
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                AppState.repo.openThread(th.id)
                AppState.go(Screen.Chat(th.id))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(th.person, 52.dp, 19.dp, 22f)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // round 6: nickname replaces the profile name in the list too
                Text(th.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                if (th.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.PushPin, null, Modifier.size(13.dp), tint = TikuTokens.Muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ROUND 8 (item 7b): the row tick is gone — a subtle "Seen"
                // stands in its place when my latest message was seen.
                // ROUND 10: hidden while MY receipts are off in this chat.
                if (last?.me == true && last.kind != MsgKind.CALL && last.seenByPeer && !th.receiptsOffMine) {
                    Text("Seen", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TikuTokens.Muted, fontFamily = Manrope)
                    Spacer(Modifier.width(5.dp))
                }
                val pvIcon = when {
                    last == null -> null
                    last.kind == MsgKind.VOICE -> Icons.Filled.Mic
                    last.kind == MsgKind.IMAGE -> Icons.Filled.PhotoCamera
                    else -> null
                }
                if (pvIcon != null) {
                    Icon(pvIcon, null, Modifier.size(14.dp), tint = TikuTokens.Muted)
                    Spacer(Modifier.width(3.dp))
                }
                Text(preview, fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(th.time, fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            if (th.unread > 0) {
                Spacer(Modifier.height(4.dp))
                // ROUND 10: the unread badge tints with the chat's OWN theme
                // accent when it has one (else the global accent, as before).
                val badgeAccent = th.themeAccent?.let {
                    TikuTokens.Accents[it.coerceIn(0, TikuTokens.Accents.lastIndex)].second
                } ?: settings.accent
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(badgeAccent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(th.unread.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = TikuTokens.BgTop, fontFamily = Manrope)
                }
            }
        }
    }
}

// ================= Calls pane =================
// ROUND 6 SUPERSESSION (owner's new rule, 2026-10-20): this pane used to be a
// PERMANENT EMPTY STATE ("calls leave no trace here"). The owner ordered call
// history here instead — so the Calls tab now aggregates the encrypted
// call-EVENT messages already stored inside the chats (round 4), newest
// first; tapping a row opens that chat. What did NOT change: WebRTC
// signaling docs + ICE are still deleted in full the moment a call resolves,
// there are still zero notifications, and NO new server data exists for this
// surface — it renders only the in-chat records. History depth = the loaded
// message windows (each chat's newest messages, plus full history of any
// chat opened this session).
@Composable
fun CallsPane() {
    val threads by AppState.threads.collectAsState()
    val events = remember(threads) {
        threads.flatMap { th -> th.messages.filter { it.kind == MsgKind.CALL }.map { th to it } }
            .sortedByDescending { it.second.tsMs }
    }
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No calls yet", fontFamily = SerifItalic,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontSize = 24.sp, color = TikuTokens.Ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("When you make, miss or decline a call, a note of it appears here — "
                    + "the same encrypted note that lives inside the chat. "
                    + "The call's connection record itself is always deleted.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 21.sp)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp, top = 6.dp)) {
        items(events, key = { (th, m) -> th.id + "_" + m.id }) { (th, m) -> CallHistoryRow(th, m) }
    }
}

@Composable
fun CallHistoryRow(th: ChatThread, m: TikuMessage) {
    // missed = an incoming call that never got answered (my perspective)
    val missed = m.callOutcome == "noanswer" && !m.callMine
    val icon = when {
        missed -> Icons.Filled.CallMissed
        m.callMine -> Icons.Filled.CallMade
        else -> Icons.Filled.CallReceived
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                AppState.repo.openThread(th.id)
                AppState.go(Screen.Chat(th.id))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(th.person, 52.dp, 19.dp, 22f)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(th.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = if (missed) TikuTokens.Red else TikuTokens.Muted)
                Spacer(Modifier.width(5.dp))
                // callEventLabel is already perspective-correct and carries
                // the duration for ended calls ("Call ended · 12:34").
                Text(callEventLabel(m, m.callMine), fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.callVideo) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Filled.Videocam, null, Modifier.size(14.dp), tint = TikuTokens.Muted)
                }
            }
        }
        Text(m.time, fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
    }
}

// ================= Me pane =================
@Composable
fun MePane() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(24.dp)).background(TikuTokens.Card)
                    .border(1.dp, TikuTokens.Line, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // .av profile avatar
                Box(
                    Modifier.size(116.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(TikuTokens.Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = AppState.myAvatar
                    if (bmp != null) {
                        androidx.compose.foundation.Image(bmp.asBmp(), null, Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        EmojiArt("🙂", 40.dp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("@" + AppState.myUsername.ifEmpty { "you" }, fontFamily = Bricolage, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, color = TikuTokens.Ink)
                Text("Encrypted · Verified", fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                // ROUND 8 (item 6): Edit profile — photo + username.
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(TikuTokens.Mint)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            EditProfileSheet.open()
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text("Edit profile", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = Manrope, color = TikuTokens.Ink)
                }
            }
        }
        // ROUND 9: the Owner section exists ONLY for the owner account —
        // for every other account this item is never composed at all.
        if (OwnerState.isOwner) {
            item { OwnerSection() }
        }
        item { SettingsGroups() }
    }
}

/**
 * ROUND 9 — Me ▸ Owner (owner account only; [OwnerState] is the single
 * check, and MePane composes this only when it passes). Two device-local
 * privileges for the owner's own phone, both stored in [OwnerPrefs]:
 * opt-in notifications and an optional app lock — plus "Lock now", the
 * explicit full lock that overrides the relaxed mode once. Nothing here
 * exists, renders, or can be reached for any other account.
 */
@Composable
fun OwnerSection() {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SerifTitle("Owner", 22.sp, TikuTokens.Muted, Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp))
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp)
            .clip(RoundedCornerShape(24.dp)).background(TikuTokens.Card)
            .border(1.dp, TikuTokens.Line, RoundedCornerShape(24.dp)),
    ) {
        // --- Notifications (opt-in; default OFF even for the owner) ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Notifications", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                Text("A quiet “New message” alert on this phone when something arrives — only for your account, and only while the app is running",
                    fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
            ToggleSwitch(OwnerPrefs.notifyOn) { on ->
                OwnerPrefs.setNotify(on)
                if (on) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                    ) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    AppState.toast("Notifications on — this phone only")
                } else {
                    OwnerNotify.cancelAll(ctx)
                    AppState.toast("Notifications off")
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 18.dp).background(TikuTokens.Line))
        // --- App lock (ON = the standing law; OFF = his phone skips the gate) ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("App lock", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                Text("Ask for your password every time you open your journal on this phone",
                    fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
            ToggleSwitch(OwnerPrefs.appLockOn) { on ->
                OwnerPrefs.setAppLock(on)
                AppState.toast(
                    if (on) "App lock on — password on every open"
                    else "App lock off — this phone opens without the password",
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 18.dp).background(TikuTokens.Line))
        // --- Lock now (explicit one-shot full lock) ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // One-shot: even with App lock OFF, the NEXT entry must
                    // pass the password gate (unlockFlow consumes the flag).
                    OwnerPrefs.requestGateOnce()
                    ChatSession.lock(ctx)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Lock now", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                Text("Lock right away — the password will be asked on the next open, even while App lock is off",
                    fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
            Text("›", fontSize = 20.sp, color = TikuTokens.Muted)
        }
    }
}

// ================= Settings groups (exactly the prototype's G data) =================
@Composable
fun SettingsGroups() {
    val settings by SettingsStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    data class RowDef(val type: String, val title: String, val sub: String, val key: String, val on: Boolean = false)
    val groups = listOf(
        "Appearance" to listOf(
            RowDef("tg", "Dark mode", "Easy on the eyes", "dk"),
            RowDef("sw", "Accent", "Pick your glow", "ac"),
            RowDef("sw", "Chat wallpaper", "Behind your messages", "wp"),
            // ROUND 7: the couple preset gets ONE clearly labelled row of its
            // own — round 6 shipped it as anonymous swatches and the owner
            // never found it ("theme add nhi kia"). Its toggle applies /
            // restores the full preset (see SettingsStore.setCoupleTheme).
            RowDef("tg", "Couple theme", "Blush hearts + rose glow", "ct"),
            RowDef("rg", "Text size", "Message font size", "fs"),
        ),
        "Privacy" to listOf(
            RowDef("tg", "App lock", "Ask for password on open", "al"),
            RowDef("tg", "Disappearing messages", "Vanish after 24 hours", "dm"),
            RowDef("tg", "Read receipts", "Show “Seen” when read", "rr", true),
            RowDef("tg", "Last seen", "Let contacts see when you were online", "ls", true),
            RowDef("tg", "Block screenshots", "Block screenshots & app-switcher previews of chats", "bs", true),
            RowDef("tg", "Hide typing status", "Don’t show “typing…”", "ht"),
        ),
        "Notifications" to listOf(
            RowDef("tg", "Sounds", "Soft pop on new message", "sn", true),
            RowDef("tg", "Vibration", "Gentle haptics", "vb", true),
            RowDef("tg", "Message preview", "Show text in notifications", "mp", true),
            RowDef("tg", "Quiet hours", "Mute from 11 pm to 7 am", "qh"),
        ),
        "Data and storage" to listOf(
            RowDef("tg", "Data saver", "Lower image quality", "ds"),
            RowDef("tg", "Auto-download photos", "On Wi‑Fi only", "ad", true),
            RowDef("bt", "Clear cache", "Frees up 128 MB", "cc"),
        ),
        "Account" to listOf(
            RowDef("bt", "Linked devices", "Manage where Tikuu is open", "ld"),
            RowDef("bt", "Change password", "Update your lock password", "cp"),
            RowDef("bt", "Log out", "", "lo"),
        ),
        // NEW (round 2): the very last group, styled exactly like the others.
        // Round 3: the duplicate "Allow screenshots" switch was REMOVED from here —
        // Privacy ▸ "Block screenshots" is the single screenshot control.
        "Additional settings" to listOf(
            RowDef("tg", "Camera on for video calls", "Front camera starts enabled on outgoing calls", "vcd", true),
            RowDef("tg", "Speaker for video calls", "Audio through the speaker on video calls", "vsp", true),
            RowDef("tg", "Enter to send", "Pressing Enter sends the message", "ets"),
        ),
    )

    groups.forEach { (header, rows) ->
        SerifTitle(header, 22.sp, TikuTokens.Muted, Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp))
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(24.dp)).background(TikuTokens.Card)
                .border(1.dp, TikuTokens.Line, RoundedCornerShape(24.dp)),
        ) {
            rows.forEachIndexed { idx, r ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSettingTap(r.key, r.title, settings, scope, ctx)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                        if (r.sub.isNotEmpty()) Text(r.sub, fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                    }
                    when (r.type) {
                        "tg" -> {
                            if (r.key == "dk") {
                                // dark mode: locked to light — visual stays off, toast only
                                ToggleSwitch(false) { AppState.toast("Light mode is Tikuu’s home") }
                            } else if (r.key == "ct") {
                                // ROUND 7: derived state — ON exactly when the
                                // rose accent AND the couple wallpaper are both
                                // active, however they were set, so this toggle
                                // and the swatch rows never disagree.
                                val coupleOn = settings.accentIdx == TikuTokens.Accents.lastIndex &&
                                    settings.wallpaper == WallpaperMode.COUPLE
                                ToggleSwitch(coupleOn) { on ->
                                    SettingsStore.setCoupleTheme(on)
                                    AppState.toast("Couple theme " + if (on) "on" else "off")
                                }
                            } else {
                                ToggleSwitch(settings.toggles[r.key] == true) { on ->
                                    SettingsStore.setToggle(r.key, on)
                                    AppState.toast(r.title + if (on) " on" else " off")
                                }
                            }
                        }
                        "bt" -> Text("›", fontSize = 20.sp, color = TikuTokens.Muted)
                        "rg" -> Slider(
                            value = settings.textSizeSp, onValueChange = { SettingsStore.setTextSize(it) },
                            valueRange = 13f..19f, modifier = Modifier.width(110.dp),
                        )
                        "sw" -> Row {
                            val items = if (r.key == "ac") TikuTokens.Accents.map { (a, b) ->
                                Brush.linearGradient(listOf(a, b))
                            } else listOf(
                                Brush.linearGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom)),
                                Brush.verticalGradient(listOf(TikuTokens.PlainPlain, TikuTokens.PlainPlain)),
                                Brush.linearGradient(listOf(TikuTokens.PeachTop, TikuTokens.PeachBottom)),
                                Brush.radialGradient(listOf(TikuTokens.Line, Color.Transparent)),
                                // ROUND 6: Couple blush (WallpaperMode.COUPLE,
                                // index-aligned with the enum order)
                                Brush.linearGradient(listOf(Color(0xFFFFF1EA), Color(0xFFF9DAD0))),
                            )
                            items.forEachIndexed { i, brush ->
                                Box(
                                    Modifier.size(26.dp).padding(start = 8.dp)
                                        .clip(CircleShape).background(brush)
                                        .border(2.dp, TikuTokens.Card, CircleShape)
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            if (r.key == "ac") SettingsStore.setAccent(i) else SettingsStore.setWallpaper(i)
                                            AppState.toast("Updated")
                                        }
                                )
                            }
                        }
                    }
                }
                if (idx < rows.size - 1) {
                    Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 18.dp).background(TikuTokens.Line))
                }
            }
        }
    }
}

fun onSettingTap(key: String, title: String, settings: TikuUiSettings, scope: kotlinx.coroutines.CoroutineScope, ctx: android.content.Context) {
    when (key) {
        "lo" -> {
            if (Config.PREVIEW_UI_ONLY) AppState.go(Screen.Welcome) else FirebaseEngine.signOut(ctx)
        }
        "cc" -> AppState.toast("Cache cleared")
        "ld" -> AppState.toast("1 device linked: this phone")
        "cp" -> {
            if (Config.PREVIEW_UI_ONLY) AppState.toast("Password change opens here")
            else ChangePasswordSheet.open(ctx, scope)
        }
        "fs", "ac", "wp", "dk", "ct" -> { /* handled by their own controls */ }
    }
}

// ================= New chat sheet =================
object NewChatSheet {
    /**
     * The suggestion source, behind a seam so the screenshot tests can drive
     * the REAL shared matcher (SearchNorm) against the preview people — the
     * Robolectric process has no Firebase backend to query. Production uses
     * the default: preview people in a preview build, Firestore otherwise.
     */
    var searcher: suspend (String) -> List<Person> = { q ->
        if (Config.PREVIEW_UI_ONLY) {
            PreviewStore.people.filter { SearchNorm.matches(it.username, it.name, q) }
        } else {
            runCatching { FirebaseEngine.searchPeople(q) }.getOrDefault(emptyList())
        }
    }

    fun open() {
        AppState.sheet {
            var q by remember { mutableStateOf("") }
            var results by remember { mutableStateOf<List<Person>>(emptyList()) }
            var searching by remember { mutableStateOf(false) }
            // LIVE SUGGESTIONS (round 4, item 4) — YouTube-style: every
            // keystroke, debounced ~200 ms, refreshes the results from the
            // forgiving matcher (prefix on the normalized username + name
            // tokens — see FirebaseEngine.searchPeople / SearchNorm). No
            // Search button, no exact-full-username requirement. An empty
            // query shows NOTHING — never a dump of all users.
            LaunchedEffect(q) {
                if (q.isBlank()) { results = emptyList(); searching = false; return@LaunchedEffect }
                searching = true
                delay(200)
                results = searcher(q)
                searching = false
            }
            SerifTitle("New chat", 28.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Box(
                Modifier.fillMaxWidth().height(56.dp)
                    .clip(RoundedCornerShape(18.dp)).background(TikuTokens.Glass)
                    .border(1.dp, TikuTokens.Line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("@", color = TikuTokens.Muted, fontSize = 16.sp, fontFamily = Manrope,
                    modifier = Modifier.padding(start = 2.dp))
                BasicTextField(
                    value = q,
                    onValueChange = { q = it.take(30) },
                    textStyle = TextStyle(fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink),
                    cursorBrush = SolidColor(TikuTokens.Ink), singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text("name or username", fontSize = 16.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                q.isBlank() ->
                    Text("Start typing — matches appear as you type. You don't need the exact username.",
                        fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                searching && results.isEmpty() ->
                    Text("Searching…", fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                results.isEmpty() ->
                    Text("No one found for “${q.trim()}”", fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
            results.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            val id = AppState.repo.startThreadWith(p)
                            AppState.closeSheet()
                            AppState.repo.openThread(id)
                            AppState.go(Screen.Chat(id))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(p, 52.dp, 19.dp, 22f)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                        Text("@" + p.username, fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                    }
                }
            }
        }
    }
}

/**
 * ROUND 8 — EDIT PROFILE (item 6): change the profile photo (the photoData
 * pipeline) and change the username. Username validation mirrors the signup
 * screen exactly (same input filter, same 3-char minimum, same debounced
 * availability check); the rename itself is ONE transaction in the engine
 * (claim new + free old + rewrite user doc/search fields), so a taken name
 * fails cleanly and changes nothing. Chats key on uid — a rename touches
 * no conversation.
 */
object EditProfileSheet {
    fun open() {
        AppState.sheet {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            var username by remember { mutableStateOf(AppState.myUsername) }
            var hint by remember { mutableStateOf("Letters, numbers, dots and underscores.") }
            var available by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var err by remember { mutableStateOf("") }
            val changed = username != AppState.myUsername

            val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    ctx.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bmp ->
                            if (Config.PREVIEW_UI_ONLY) {
                                AppState.myAvatar = bmp
                                AppState.toast("Profile photo updated")
                            } else {
                                scope.launch {
                                    val r = FirebaseEngine.updateProfilePhoto(bmp)
                                    AppState.toast(if (r.isSuccess) "Profile photo updated" else "Couldn't update the photo")
                                }
                            }
                        }
                    }
                }
            }

            // debounced availability — the signup screen's exact behaviour
            LaunchedEffect(username) {
                available = false
                if (!changed) { hint = "This is your current username."; return@LaunchedEffect }
                if (username.isEmpty()) { hint = "Letters, numbers, dots and underscores."; return@LaunchedEffect }
                delay(450)
                when {
                    username.length < 3 -> hint = "At least 3 characters."
                    else -> {
                        val taken = if (Config.PREVIEW_UI_ONLY) username in listOf("admin", "tikuu")
                            else runCatching { FirebaseEngine.usernameTaken(username) }.getOrDefault(false)
                        if (taken) hint = "@$username is taken. Try another."
                        else { hint = "✓ @$username is available"; available = true }
                    }
                }
            }

            SerifTitle("Edit profile", 28.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            // photo row
            Row(
                Modifier.fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        // picker backgrounds the app — flag it so the instant
                        // re-lock doesn't fire mid-pick (same rule as chat)
                        ChatSession.expectExternalActivity = true
                        pick.launch("image/*")
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(TikuTokens.Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = AppState.myAvatar
                    if (bmp != null) {
                        androidx.compose.foundation.Image(bmp.asBmp(), null, Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        EmojiArt("🙂", 24.dp)
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text("Change photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                    Text("Shown on your profile", fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                }
            }
            Spacer(Modifier.height(16.dp))
            // username field — same shape + input filter as signup
            Box(
                Modifier.fillMaxWidth().height(56.dp)
                    .clip(RoundedCornerShape(18.dp)).background(TikuTokens.Card)
                    .border(1.dp, TikuTokens.Line, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("@", color = TikuTokens.Muted, fontSize = 16.sp, fontFamily = Manrope,
                    modifier = Modifier.padding(start = 18.dp))
                BasicTextField(
                    value = username,
                    onValueChange = { raw ->
                        username = raw.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20)
                    },
                    textStyle = TextStyle(fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink),
                    cursorBrush = SolidColor(TikuTokens.Ink),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 18.dp),
                    decorationBox = { inner ->
                        if (username.isEmpty()) Text("username", fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(hint, fontSize = 13.sp, color = if (available) tikuSettings().value.accent else TikuTokens.Muted, fontFamily = Manrope)
            Text("Friends find you by your username — chats and history are not affected.",
                fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            if (err.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(err, fontSize = 13.sp, color = TikuTokens.Red, fontFamily = Manrope)
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (busy) "Saving…" else "Save username",
                enabled = changed && available && !busy, modifier = Modifier.fillMaxWidth()) {
                if (Config.PREVIEW_UI_ONLY) {
                    AppState.myUsername = username
                    AppState.closeSheet()
                    AppState.toast("Username updated")
                } else {
                    busy = true
                    err = ""
                    scope.launch {
                        val r = FirebaseEngine.changeUsername(username)
                        busy = false
                        if (r.isSuccess) {
                            AppState.closeSheet()
                            AppState.toast("Username updated")
                        } else {
                            err = r.exceptionOrNull()?.message ?: "Couldn't change username. Try again."
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

object ChangePasswordSheet {
    fun open(ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
        var cur by mutableStateOf("")
        var p1 by mutableStateOf("")
        var p2 by mutableStateOf("")
        var busy by mutableStateOf(false)
        var err by mutableStateOf("")
        AppState.sheet {
            SerifTitle("Change password", 28.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            PasswordField(cur, { cur = it }, "Current password")
            Spacer(Modifier.height(10.dp))
            PasswordField(p1, { p1 = it }, "New password")
            Spacer(Modifier.height(10.dp))
            PasswordField(p2, { p2 = it }, "Confirm new password")
            Spacer(Modifier.height(6.dp))
            if (err.isNotEmpty()) Text(err, fontSize = 13.sp, color = TikuTokens.Red, fontFamily = Manrope)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (busy) "Updating…" else "Update password",
                enabled = cur.isNotEmpty() && p1.length >= 8 && p1 == p2 && !busy,
                modifier = Modifier.fillMaxWidth()) {
                busy = true
                scope.launch {
                    val ok = runCatching {
                        val hash = SettingsStore.passwordHash(ctx)
                        hash != null && Crypto.passwordVerify(cur, hash)
                    }.getOrDefault(false)
                    if (!ok) { busy = false; err = "Current password is wrong"; return@launch }
                    // re-wrap the identity backup under the new password
                    val r = runCatching {
                        val blob = FirebaseEngine.backupBlob() ?: return@runCatching false
                        val priv = Crypto.backupUnwrap(blob, cur) // validates current password against the backup
                        val newBlob = Crypto.backupWrap(priv, p1)
                        FirebaseEngine.updateBackupBlob(newBlob)
                        SettingsStore.savePasswordHash(ctx, Crypto.passwordHash(p1))
                        true
                    }.getOrDefault(false)
                    busy = false
                    if (r) { AppState.closeSheet(); AppState.toast("Password updated") }
                    else err = "Couldn’t update. Try again."
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

object StoryHelper {
    suspend fun open(person: Person, index: Int) {
        if (Config.PREVIEW_UI_ONLY) {
            AppState.story = StoryContent(person, PreviewStore.storyQuotes, PreviewStore.storyGrads, index)
            AppState.go(Screen.Story)
        } else {
            val quotes = FirebaseEngine.loadMoments(person)
            if (quotes.isEmpty()) {
                val id = AppState.repo.startThreadWith(person)
                AppState.repo.openThread(id)
                AppState.go(Screen.Chat(id))
            } else {
                AppState.story = StoryContent(person, quotes, PreviewStore.storyGrads, 0)
                AppState.go(Screen.Story)
            }
        }
    }
}

data class StoryContent(
    val person: Person,
    val quotes: List<String>,
    val grads: List<Pair<Color, Color>>,
    val personIndex: Int,
)
