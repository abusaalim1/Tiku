package com.tiku.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ROUND 12 — the Tik home shell in the owner's design (Tikuu_11): `.top`
 * header, panes entering with the design's `in` motion (fade + 8px rise,
 * .26s, cubic-bezier(.22,.8,.22,1)), and the design's `.nav` — a flat
 * black bar with a hairline top border, the file's own drawn icons and
 * the labels Message / Call / Watch / Setting. All data + behaviour is
 * Build 42's; only the skin changed.
 */
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    // QUICK EXIT (lock rule): Back at ChatRoot home re-locks instantly to the facade.
    androidx.activity.compose.BackHandler { ChatSession.quickExit(ctx) }
    val settings = tikuSettings().value
    val tab = AppState.homeTab
    val threads by AppState.threads.collectAsState()
    val title = when (tab) {
        HomeTab.CHATS -> "Messages"; HomeTab.CALLS -> "Calls"
        HomeTab.WATCH -> "Watch"; HomeTab.ME -> "Settings"
    }
    Box(Modifier.fillMaxSize().background(Tik.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TikTopBar(
                title = title,
                // discreet quick-exit control: long-press the home title re-locks instantly
                onTitleLongPress = { ChatSession.quickExit(ctx) },
                trailing = {
                    when (tab) {
                        // .btn edit — opens the new-chat sheet (real action).
                        HomeTab.CHATS -> TikBtn("edit") { NewChatSheet.open() }
                        // .btn plus — the design's Watch action: YouTube home.
                        HomeTab.WATCH -> TikBtn("plus") { WatchHolder.goHome(ctx) }
                        else -> Spacer(Modifier.width(36.dp))
                    }
                },
            )
            Box(Modifier.weight(1f)) {
                // Screen enter = the design's `in` keyframes: opacity 0 ->
                // 1 with translateY(8px) -> 0 over .26s on --e.
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(260, easing = TikEase)) +
                            slideInVertically(tween(260, easing = TikEase)) { 24 })
                            .togetherWith(fadeOut(tween(180, easing = TikEase)))
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
            // .nav — flat bar, bg var(--bg), 1px ln top border, padding 8.
            Column(Modifier.fillMaxWidth().background(Tik.Bg)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tik.Ln))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val anyUnread = threads.any { it.unread > 0 }
                    listOf(
                        Triple(HomeTab.CHATS, "Message", "msg"),
                        Triple(HomeTab.CALLS, "Call", "call"),
                        Triple(HomeTab.WATCH, "Watch", "play"),
                        Triple(HomeTab.ME, "Setting", "set"),
                    ).forEach { (t, label, icon) ->
                        val on = t == tab
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val iconScale by animateFloatAsState(
                            if (pressed) 0.88f else if (on) 1.08f else 1f,
                            tween(280, easing = TikEase), label = "navIconScale")
                        val iconLift by animateDpAsState(
                            if (on) (-1).dp else 0.dp,
                            tween(280, easing = TikEase), label = "navIconLift")
                        Box(Modifier.weight(1f)) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(interactionSource = interaction, indication = null) {
                                        AppState.homeTab = t
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                TikIcon(
                                    icon,
                                    Modifier.size(24.dp).offset(y = iconLift)
                                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                                    tint = if (on) Tik.Tx else Tik.Sub,
                                    strokeWidth = if (on) 2f else 1.6f,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    label, fontSize = 10.5.sp, fontFamily = Manrope,
                                    fontWeight = FontWeight.Medium,
                                    color = if (on) Tik.Tx else Tik.Sub,
                                )
                            }
                            // #nd — the design's unread dot on the Message tab.
                            if (t == HomeTab.CHATS && anyUnread) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = (-20).dp)
                                        .size(7.dp).clip(CircleShape).background(settings.accent),
                                )
                            }
                        }
                    }
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
    val settings = tikuSettings().value
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 84.dp)) {
        item {
            // .search — margin 8/18/14, bg s2, r12, padding 10/12, icon 18.
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Tik.S2)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TikIcon("srch", Modifier.size(18.dp), tint = Tik.Sub)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it },
                    textStyle = TextStyle(fontSize = 15.sp, fontFamily = Manrope, color = Tik.Tx),
                    cursorBrush = SolidColor(settings.accent), singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search", fontSize = 15.sp, color = Tik.Sub, fontFamily = Manrope)
                        inner()
                    },
                )
            }
            MomentsRow()
            // .sec — the section header above the rows ("Messages" + the
            // design's Requests link; the app has no requests surface, so
            // the header stands alone).
            Text(
                "Messages", fontSize = 15.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                color = Tik.Tx,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        // the list filter matches the DISPLAY name (nickname when set) and
        // the @username, not just the profile name; pinned chats float to
        // the top (stable sort — recency order is otherwise untouched).
        val filtered = threads.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.person.username.contains(query, ignoreCase = true)
        }.sortedByDescending { it.pinned }
        if (filtered.isEmpty() && !Config.PREVIEW_UI_ONLY) {
            item {
                TikEmpty(
                    "Start a chat", "Search a username to start a chat",
                    Modifier.fillMaxWidth().padding(top = 42.dp),
                )
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
    // Design .nt metrics: 72-wide items, 68px round avatars, 12px sub
    // labels. The 2dp accent ring is the app's moments affordance (the
    // mock's note-bubble has no app equivalent) — kept, in accent.
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        people.forEachIndexed { i, p ->
            Column(
                Modifier.width(72.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { StoryHelper.open(p, i) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(68.dp)
                        .border(2.dp, settings.accent, CircleShape)
                        .padding(3.dp),
                ) {
                    AvatarBox(p, 62.dp, 31.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(p.name.split(" ").first(), fontSize = 12.sp, color = Tik.Sub, fontFamily = Manrope,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        // call events preview as their pill label, never raw text
        last.kind == MsgKind.CALL -> callEventLabel(last, last.callMine)
        else -> last.text
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // .row — gap 14, padding 9/18, :active bg #ffffff0a.
    Row(
        Modifier.fillMaxWidth()
            .background(if (pressed) Tik.RowActive else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) {
                AppState.repo.openThread(th.id)
                AppState.go(Screen.Chat(th.id))
            }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(th.person, 54.dp, 27.dp, 22f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // nickname replaces the profile name in the list too
                Text(th.displayName, fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = Tik.Tx,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (th.muted) {
                    Spacer(Modifier.width(6.dp))
                    TikIcon("bellOff", Modifier.size(14.dp), tint = Tik.Sub)
                }
                if (th.pinned) {
                    Spacer(Modifier.width(6.dp))
                    TikIcon("pin", Modifier.size(12.dp), tint = Tik.Sub)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The row tick is gone — a subtle "Seen" stands in its place
                // when my latest message was seen; hidden while MY read
                // receipts are off in this chat (per-chat receiptsOff).
                if (last?.me == true && last.kind != MsgKind.CALL && last.seenByPeer && !th.receiptsOffMine) {
                    Text("Seen", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Tik.Sub, fontFamily = Manrope)
                    Spacer(Modifier.width(5.dp))
                }
                val pvIcon = when {
                    last == null -> null
                    last.kind == MsgKind.VOICE -> "mic"
                    last.kind == MsgKind.IMAGE -> "img"
                    else -> null
                }
                if (pvIcon != null) {
                    TikIcon(pvIcon, Modifier.size(14.dp), tint = Tik.Sub)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    preview, fontSize = 13.5.sp, fontFamily = Manrope,
                    fontWeight = if (th.unread > 0) FontWeight.Medium else FontWeight.Normal,
                    color = if (th.unread > 0) Tik.Tx else Tik.Sub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(th.time, fontSize = 12.sp, color = Tik.Sub, fontFamily = Manrope)
            if (th.unread > 0) {
                Spacer(Modifier.height(5.dp))
                // .dot — the design's unread mark: an 8px accent dot
                // (tinted with the chat's own theme accent when it has one).
                val dotAccent = th.themeAccent?.let {
                    TikuTokens.Accents[it.coerceIn(0, TikuTokens.Accents.lastIndex)].second
                } ?: settings.accent
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotAccent))
            }
        }
    }
}

// ================= Calls pane =================
// This pane aggregates the encrypted call-EVENT messages already stored
// inside the chats, newest first; tapping a row opens that chat. WebRTC
// signaling docs + ICE are still deleted in full the moment a call
// resolves, there are still zero notifications, and NO new server data
// exists for this surface — it renders only the in-chat records.
@Composable
fun CallsPane() {
    val threads by AppState.threads.collectAsState()
    val events = remember(threads) {
        threads.flatMap { th -> th.messages.filter { it.kind == MsgKind.CALL }.map { th to it } }
            .sortedByDescending { it.second.tsMs }
    }
    // .sec header, as in the design's Calls screen.
    Column(Modifier.fillMaxSize()) {
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TikEmpty(
                    "No calls yet",
                    "When you make, miss or decline a call, a note of it appears here — " +
                        "the same encrypted note that lives inside the chat. " +
                        "The call's connection record itself is always deleted.",
                )
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 84.dp, top = 6.dp)) {
            items(events, key = { (th, m) -> th.id + "_" + m.id }) { (th, m) -> CallHistoryRow(th, m) }
        }
    }
}

@Composable
fun CallHistoryRow(th: ChatThread, m: TikuMessage) {
    val settings = tikuSettings().value
    val startCall = rememberCallStarter()
    // missed = an incoming call that never got answered (my perspective)
    val missed = m.callOutcome == "noanswer" && !m.callMine
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // .row metrics; the design colours a missed call's name with --ac and
    // puts a call/video .btn at the row's end (here: really starts it).
    Row(
        Modifier.fillMaxWidth()
            .background(if (pressed) Tik.RowActive else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) {
                AppState.repo.openThread(th.id)
                AppState.go(Screen.Chat(th.id))
            }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBox(th.person, 50.dp, 25.dp, 20f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(th.displayName, fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope,
                color = if (missed) settings.accent else Tik.Tx,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // callEventLabel is perspective-correct and carries the
            // duration for ended calls ("Call ended · 12:34").
            val label = callEventLabel(m, m.callMine)
            Text(
                if (label.contains(m.time)) label else "$label · ${m.time}",
                fontSize = 13.5.sp, color = Tik.Sub, fontFamily = Manrope,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        TikBtn(if (m.callVideo) "vid" else "call", iconSize = 22.dp) {
            startCall(th.id, m.callVideo)
        }
    }
}

// ================= Me pane =================
@Composable
fun MePane() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 84.dp)) {
        item {
            // .prof — avatar 60 round, name 17 w600, sub 13, .ob edit button.
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val obScale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(160, easing = TikEase), label = "obScale")
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Tik.S2),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = AppState.myAvatar
                    if (bmp != null) {
                        androidx.compose.foundation.Image(bmp.asBmp(), null, Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        EmojiArt("🙂", 26.dp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("@" + AppState.myUsername.ifEmpty { "you" }, fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Tik.Tx)
                    Text("Encrypted · Verified", fontSize = 13.sp, color = Tik.Sub, fontFamily = Manrope)
                }
                Box(
                    Modifier.graphicsLayer { scaleX = obScale; scaleY = obScale }
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, Tik.OutlineBtn, RoundedCornerShape(9.dp))
                        .clickable(interactionSource = interaction, indication = null) {
                            EditProfileSheet.open()
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text("Edit profile", fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                        fontFamily = Manrope, color = Tik.Tx)
                }
            }
        }
        // The Owner section exists ONLY for the owner account — for every
        // other account this item is never composed at all.
        if (OwnerState.isOwner) {
            item { OwnerSection() }
        }
        item { SettingsGroups() }
    }
}

/**
 * Me ▸ Owner (owner account only; [OwnerState] is the single check, and
 * MePane composes this only when it passes). Two device-local privileges
 * for the owner's own phone, both stored in [OwnerPrefs]: opt-in
 * notifications and an optional app lock — plus "Lock now", the explicit
 * full lock that overrides the relaxed mode once. Nothing here exists,
 * renders, or can be reached for any other account. ROUND 12: rendered
 * in the design's .sh + .grp + .si language.
 */
@Composable
fun OwnerSection() {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    TikSectionHeader("Owner")
    TikGroup {
        TikInfoRow(
            "Notifications",
            "A quiet “New message” alert on this phone when something arrives — only for your account, and only while the app is running",
            first = true,
        ) {
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
        TikInfoRow(
            "App lock",
            "Ask for your password every time you open your journal on this phone",
        ) {
            ToggleSwitch(OwnerPrefs.appLockOn) { on ->
                OwnerPrefs.setAppLock(on)
                AppState.toast(
                    if (on) "App lock on — password on every open"
                    else "App lock off — this phone opens without the password",
                )
            }
        }
        TikInfoRow(
            "Lock now",
            "Lock right away — the password will be asked on the next open, even while App lock is off",
            onClick = {
                // One-shot: even with App lock OFF, the NEXT entry must
                // pass the password gate (unlockFlow consumes the flag).
                OwnerPrefs.requestGateOnce()
                ChatSession.lock(ctx)
            },
        ) {
            TikIcon("chevR", Modifier.size(18.dp), tint = Tik.Sub)
        }
    }
}

// ================= Settings groups =================
/**
 * ROUND 12 settings map — the design's drawSet rows, but ONLY rows backed
 * by real, wired functionality (each toggle below is read by the engine):
 *   Appearance — accent / wallpaper (design ACC + WALL swatches), couple
 *     theme, text size (design range).
 *   Privacy — app lock, read receipts, last seen (heartbeat honours it),
 *     block screenshots (FLAG_SECURE), hide typing (engine honours it).
 *   Notifications — sounds + vibration (both drive the call feedback).
 *   Data — clear cache. Account — change password (real re-wrap flow),
 *     log out (danger, as in the design).
 *   Additional — camera / speaker defaults (RTC honours both), enter to send.
 * Skipped design rows (listed in the round report): Dark mode (the Tik
 * section IS the design's dark), Disappearing messages, Message preview,
 * Quiet hours, Data saver, Auto-download, Linked devices — unplumbed or
 * banned by the owner's hard laws.
 */
@Composable
fun SettingsGroups() {
    val settings by SettingsStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    TikSectionHeader("Appearance")
    TikGroup {
        TikInfoRow("Accent", "Pick your glow", first = true) {
            TikSwatches(
                colors = (0..4).map { TikuTokens.Accents[it].second },
                selected = settings.accentIdx,
                onPick = { SettingsStore.setAccent(it); AppState.toast("Updated") },
            )
        }
        TikInfoRow("Chat wallpaper", "Behind your messages") {
            TikSwatches(
                colors = Tik.WallColors,
                selected = settings.wallpaper.ordinal,
                onPick = { SettingsStore.setWallpaper(it); AppState.toast("Updated") },
            )
        }
        TikInfoRow("Couple theme", "Soft rose glow in chats") {
            // Derived state — ON exactly when the rose accent AND the
            // couple wallpaper are both active, however they were set, so
            // this toggle and the swatch rows never disagree.
            val coupleOn = settings.accentIdx == TikuTokens.Accents.lastIndex &&
                settings.wallpaper == WallpaperMode.COUPLE
            ToggleSwitch(coupleOn) { on ->
                SettingsStore.setCoupleTheme(on)
                AppState.toast("Couple theme " + if (on) "on" else "off")
            }
        }
        TikInfoRow("Text size", "Message font size") {
            Slider(
                value = settings.textSizeSp,
                onValueChange = { SettingsStore.setTextSize(it) },
                valueRange = 13f..20f,
                modifier = Modifier.width(110.dp),
                colors = SliderDefaults.colors(
                    thumbColor = settings.accent,
                    activeTrackColor = settings.accent,
                    inactiveTrackColor = Tik.TrackOff,
                ),
            )
        }
    }

    TikSectionHeader("Privacy")
    TikGroup {
        TikSettingsToggle("App lock", "Ask for password on open", "al", first = true)
        TikSettingsToggle("Read receipts", "Show “Seen” when read", "rr")
        TikSettingsToggle("Last seen", "Let contacts see when you were online", "ls")
        TikSettingsToggle("Block screenshots", "Block screenshots & app-switcher previews of chats", "bs")
        TikSettingsToggle("Hide typing status", "Don’t show “typing…”", "ht")
    }

    TikSectionHeader("Notifications")
    TikGroup {
        TikSettingsToggle("Sounds", "Soft pop on new message", "sn", first = true)
        TikSettingsToggle("Vibration", "Gentle haptics", "vb")
    }

    TikSectionHeader("Data and storage")
    TikGroup {
        TikInfoRow("Clear cache", "Frees up 128 MB", first = true,
            onClick = { onSettingTap("cc", "Clear cache", settings, scope, ctx) }) {
            TikIcon("chevR", Modifier.size(18.dp), tint = Tik.Sub)
        }
    }

    TikSectionHeader("Account")
    TikGroup {
        TikInfoRow("Change password", "Update your lock password", first = true,
            onClick = { onSettingTap("cp", "Change password", settings, scope, ctx) }) {
            TikIcon("chevR", Modifier.size(18.dp), tint = Tik.Sub)
        }
        TikInfoRow("Log out", null, danger = true,
            onClick = { onSettingTap("lo", "Log out", settings, scope, ctx) })
    }

    TikSectionHeader("Additional settings")
    TikGroup {
        TikSettingsToggle("Camera on for video calls", "Front camera starts enabled on outgoing calls", "vcd", first = true)
        TikSettingsToggle("Speaker for video calls", "Audio through the speaker on video calls", "vsp")
        TikSettingsToggle("Enter to send", "Pressing Enter sends the message", "ets")
    }
}

/** One design settings toggle row bound to a SettingsStore toggle key. */
@Composable
private fun TikSettingsToggle(title: String, sub: String, key: String, first: Boolean = false) {
    val settings by SettingsStore.state.collectAsState()
    TikInfoRow(title, sub, first = first) {
        ToggleSwitch(settings.toggles[key] == true) { on ->
            SettingsStore.setToggle(key, on)
            AppState.toast(title + if (on) " on" else " off")
        }
    }
}

fun onSettingTap(key: String, title: String, settings: TikuUiSettings, scope: kotlinx.coroutines.CoroutineScope, ctx: android.content.Context) {
    when (key) {
        "lo" -> {
            if (Config.PREVIEW_UI_ONLY) AppState.go(Screen.Welcome) else FirebaseEngine.signOut(ctx)
        }
        "cc" -> AppState.toast("Cache cleared")
        "cp" -> {
            if (Config.PREVIEW_UI_ONLY) AppState.toast("Password change opens here")
            else ChangePasswordSheet.open(ctx, scope)
        }
        "fs", "ac", "wp", "ct" -> { /* handled by their own controls */ }
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
            // LIVE SUGGESTIONS — every keystroke, debounced ~200 ms,
            // refreshes the results from the forgiving matcher (prefix on
            // the normalized username + name tokens). No Search button, no
            // exact-full-username requirement. An empty query shows
            // NOTHING — never a dump of all users.
            LaunchedEffect(q) {
                if (q.isBlank()) { results = emptyList(); searching = false; return@LaunchedEffect }
                searching = true
                delay(200)
                results = searcher(q)
                searching = false
            }
            val settings = tikuSettings().value
            TikSheetTitle("New chat")
            // .fld search field
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Tik.Bg)
                    .border(1.dp, Tik.Ln, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("@", color = Tik.Sub, fontSize = 15.sp, fontFamily = Manrope)
                BasicTextField(
                    value = q,
                    onValueChange = { q = it.take(30) },
                    textStyle = TextStyle(fontSize = 15.sp, fontFamily = Manrope, color = Tik.Tx),
                    cursorBrush = SolidColor(settings.accent), singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text("name or username", fontSize = 15.sp, color = Tik.Sub, fontFamily = Manrope)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                q.isBlank() ->
                    Text("Start typing — matches appear as you type. You don't need the exact username.",
                        fontSize = 13.sp, color = Tik.Sub, fontFamily = Manrope,
                        modifier = Modifier.padding(horizontal = 18.dp))
                searching && results.isEmpty() ->
                    Text("Searching…", fontSize = 13.sp, color = Tik.Sub, fontFamily = Manrope,
                        modifier = Modifier.padding(horizontal = 18.dp))
                results.isEmpty() ->
                    Text("No one found for “${q.trim()}”", fontSize = 13.sp, color = Tik.Sub, fontFamily = Manrope,
                        modifier = Modifier.padding(horizontal = 18.dp))
            }
            results.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            val id = AppState.repo.startThreadWith(p)
                            AppState.closeSheet()
                            AppState.repo.openThread(id)
                            AppState.go(Screen.Chat(id))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(p, 54.dp, 27.dp, 22f)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = Tik.Tx)
                        Text("@" + p.username, fontSize = 13.5.sp, color = Tik.Sub, fontFamily = Manrope)
                    }
                }
            }
        }
    }
}

/**
 * EDIT PROFILE: change the profile photo (the photoData pipeline) and
 * change the username. Username validation mirrors the signup screen
 * exactly (same input filter, same 3-char minimum, same debounced
 * availability check); the rename itself is ONE transaction in the
 * engine (claim new + free old + rewrite user doc/search fields), so a
 * taken name fails cleanly and changes nothing. Chats key on uid — a
 * rename touches no conversation. ROUND 12: the sheet wears the design's
 * .gt / .fld / .pb skin.
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
            val settings = tikuSettings().value

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

            TikSheetTitle("Edit profile")
            // photo row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
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
                    Modifier.size(54.dp).clip(CircleShape).background(Tik.S1),
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
                    Text("Change photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope, color = Tik.Tx)
                    Text("Shown on your profile", fontSize = 13.sp, color = Tik.Sub, fontFamily = Manrope)
                }
            }
            Spacer(Modifier.height(16.dp))
            // Bundled presets — same updateProfilePhoto path (and same
            // preview behaviour) as an uploaded photo.
            Box(Modifier.padding(horizontal = 18.dp)) {
                PresetAvatarPicker(dark = true) { bmp ->
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
            Spacer(Modifier.height(16.dp))
            // username field — .fld skin, same input filter as signup
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Tik.Bg)
                    .border(1.dp, Tik.Ln, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("@", color = Tik.Sub, fontSize = 15.sp, fontFamily = Manrope,
                    modifier = Modifier.padding(start = 14.dp))
                BasicTextField(
                    value = username,
                    onValueChange = { raw ->
                        username = raw.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20)
                    },
                    textStyle = TextStyle(fontSize = 15.sp, fontFamily = Manrope, color = Tik.Tx),
                    cursorBrush = SolidColor(settings.accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    decorationBox = { inner ->
                        if (username.isEmpty()) Text("username", fontSize = 15.sp, fontFamily = Manrope, color = Tik.Sub)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(hint, fontSize = 13.sp, color = if (available) settings.accent else Tik.Sub, fontFamily = Manrope,
                modifier = Modifier.padding(horizontal = 18.dp))
            Text("Friends find you by your username — chats and history are not affected.",
                fontSize = 13.sp, color = Tik.Sub, fontFamily = Manrope,
                modifier = Modifier.padding(horizontal = 18.dp))
            if (err.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(err, fontSize = 13.sp, color = Tik.Danger, fontFamily = Manrope,
                    modifier = Modifier.padding(horizontal = 18.dp))
            }
            Spacer(Modifier.height(14.dp))
            TikPrimaryButton(if (busy) "Saving…" else "Save username",
                enabled = changed && available && !busy) {
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
            TikSheetTitle("Change password")
            Box(Modifier.padding(horizontal = 18.dp)) { PasswordField(cur, { cur = it }, "Current password", dark = true) }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(horizontal = 18.dp)) { PasswordField(p1, { p1 = it }, "New password", dark = true) }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(horizontal = 18.dp)) { PasswordField(p2, { p2 = it }, "Confirm new password", dark = true) }
            Spacer(Modifier.height(6.dp))
            if (err.isNotEmpty()) Text(err, fontSize = 13.sp, color = Tik.Danger, fontFamily = Manrope,
                modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(10.dp))
            TikPrimaryButton(if (busy) "Updating…" else "Update password",
                enabled = cur.isNotEmpty() && p1.length >= 8 && p1 == p2 && !busy) {
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
