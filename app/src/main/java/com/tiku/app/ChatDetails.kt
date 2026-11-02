package com.tiku.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CHAT DETAILS — ROUND 12: rebuilt as the design's drawInfo screen with
 * its openSub sub-pages (Customize / Privacy & safety / Nicknames /
 * Profile), in the design's language (.ihd identity, .acts round
 * actions, .grp rows, .prev preview card, .mg media grid). Everything
 * behind it is unchanged:
 *  - Theme: PER-CHAT accent + wallpaper on the chat doc (themeAccent /
 *    themeWallpaper) so BOTH members see the same look — the chat renders
 *    via ChatThread.effectiveSettings with global fallback.
 *  - Nicknames: the round-6 system (chat doc nicknames map); the peer's
 *    nickname is editable, mine (set by the peer) is display-only.
 *  - Read receipts: per-chat switch (receiptsOff on the chat doc). Off ⇒
 *    my client writes no seenBy marks here and "Seen" captions hide.
 * The design's mock-only rows are NOT here: no "Create a group chat"
 * (the app is 1:1 by scope), no Block account (no block feature exists).
 */
@Composable
fun ChatDetailsScreen(threadId: String) {
    val threads by AppState.threads.collectAsState()
    val thread = threads.firstOrNull { it.id == threadId }
    var sub by remember { mutableStateOf<String?>(null) }
    BackHandler {
        if (sub != null) sub = null else AppState.go(Screen.Chat(threadId))
    }
    if (thread == null) {
        Box(Modifier.fillMaxSize().background(Tik.Bg), contentAlignment = Alignment.Center) {
            Text("Chat unavailable", color = Tik.Sub, fontFamily = Manrope)
        }
        return
    }
    val globalSettings by SettingsStore.state.collectAsState()
    val settings = thread.effectiveSettings(globalSettings)
    val person = thread.person

    // Sub-pages enter with the design's screen motion (`in`: fade + rise).
    AnimatedContent(
        targetState = sub,
        transitionSpec = {
            (fadeIn(tween(260, easing = TikEase)) +
                slideInVertically(tween(260, easing = TikEase)) { 24 })
                .togetherWith(fadeOut(tween(180, easing = TikEase)))
        },
        label = "chat-details-pages",
    ) { page ->
        when (page) {
            "customize" -> CustomizePage(thread, settings, globalSettings) { sub = null }
            "privacy" -> PrivacyPage(thread) { sub = null }
            "nick" -> NicknamesPage(thread) { sub = null }
            "profile" -> ProfilePage(thread) { sub = null }
            else -> InfoMainPage(
                thread = thread,
                settings = settings,
                onBack = { AppState.go(Screen.Chat(threadId)) },
                onOpen = { sub = it },
            )
        }
    }
}

/** .top2 + back .btn scaffold for the sub-pages (.top with back + h1). */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Tik.Bg)) {
        TikTopBar(title = title, leading = { TikBtn("back") { onBack() } })
        content()
    }
}

@Composable
private fun InfoMainPage(
    thread: ChatThread,
    settings: TikuUiSettings,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val person = thread.person
    val media = thread.messages.filter { it.kind == MsgKind.IMAGE && it.photo != null && !it.deletedForMe }
    LazyColumn(
        Modifier.fillMaxSize().background(Tik.Bg),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            // .top2 — just the back button
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp)) {
                TikBtn("back") { onBack() }
            }
        }
        item {
            // .ihd — avatar 92 round, name 22 w600, @username sub.
            // Round-6 rule stands: the REAL profile name leads here —
            // the nickname never hides who this actually is.
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                AvatarBox(person, 92.dp, 46.dp, 38f)
                Spacer(Modifier.height(12.dp))
                Text(
                    person.name, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp, letterSpacing = (-0.66).sp, color = Tik.Tx,
                )
                if (person.username.isNotEmpty()) {
                    Text(
                        "@${person.username}", fontSize = 15.sp, color = Tik.Sub, fontFamily = Manrope,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            // .acts — round action buttons (46px s2 discs, 12px labels).
            // The design's Search / Mute have no app function behind
            // them, so the row carries the two real ones.
            val startCall = rememberCallStarter()
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ActButton("user", "Profile") { onOpen("profile") }
                Spacer(Modifier.width(10.dp))
                ActButton("call", "Call") { startCall(thread.id, false) }
                Spacer(Modifier.width(10.dp))
                ActButton("dots", "Options") { ChatSheets.more(thread, person) }
            }
        }
        item {
            TikGroup {
                TikInfoRow("Customize", "Theme and font", first = true, onClick = { onOpen("customize") }) {
                    TikIcon("chevR", Modifier.size(18.dp), tint = Tik.Sub)
                }
                TikInfoRow("Privacy & safety", null, onClick = { onOpen("privacy") }) {
                    TikIcon("chevR", Modifier.size(18.dp), tint = Tik.Sub)
                }
                TikInfoRow("Nicknames", null, onClick = { onOpen("nick") }) {
                    TikIcon("chevR", Modifier.size(18.dp), tint = Tik.Sub)
                }
                TikInfoRow(
                    "Clear chat", "Hides this conversation's history on this phone only",
                    onClick = {
                        AppState.repo.clearChat(thread.id)
                        AppState.toast("Chat cleared")
                    },
                )
            }
        }
        item {
            TikSectionHeader("Shared media")
            if (media.isEmpty()) {
                TikEmpty(
                    "Shared media",
                    "Photos and videos shared in this chat will appear here.",
                    Modifier.fillMaxWidth(),
                )
            } else {
                // .mg — 3 columns, gap 2, radius 6 (loaded window only)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    media.chunked(3).forEach { rowMsgs ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            rowMsgs.forEach { m ->
                                Image(
                                    m.photo!!.asImageBitmap(), null,
                                    Modifier.weight(1f).aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { AppState.go(Screen.Chat(thread.id)) },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            repeat(3 - rowMsgs.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** .acts button: 78 wide, 46px s2 disc, 12px w500 label, press scale .92. */
@Composable
private fun ActButton(icon: String, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.92f else 1f, tween(160, easing = TikEase), label = "actScale")
    Column(
        Modifier.width(78.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(androidx.compose.foundation.shape.CircleShape).background(Tik.S2),
            contentAlignment = Alignment.Center,
        ) {
            TikIcon(icon, Modifier.size(21.dp), tint = Tik.Tx)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = Manrope, color = Tik.Tx)
    }
}

@Composable
private fun CustomizePage(
    thread: ChatThread,
    settings: TikuUiSettings,
    globalSettings: TikuUiSettings,
    onBack: () -> Unit,
) {
    SubPage("Customize", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                TikGroup {
                    TikInfoRow("Accent", null, first = true) {
                        TikSwatches(
                            colors = TikuTokens.Accents.map { it.first },
                            selected = thread.themeAccent ?: globalSettings.accentIdx,
                            onPick = { AppState.repo.setTheme(thread.id, it, null) },
                        )
                    }
                    TikInfoRow("Wallpaper", null) {
                        TikSwatches(
                            colors = Tik.WallColors,
                            selected = thread.themeWallpaper ?: globalSettings.wallpaper.ordinal,
                            onPick = { AppState.repo.setTheme(thread.id, null, it) },
                        )
                    }
                    TikInfoRow("Couple theme", "Soft rose glow in chats") {
                        val coupleOn = settings.accentIdx == TikuTokens.Accents.lastIndex &&
                            settings.wallpaper == WallpaperMode.COUPLE
                        ToggleSwitch(coupleOn) { on ->
                            if (on) {
                                AppState.repo.setTheme(
                                    thread.id, TikuTokens.Accents.lastIndex,
                                    WallpaperMode.COUPLE.ordinal,
                                )
                            } else {
                                AppState.repo.setTheme(
                                    thread.id, globalSettings.accentIdx,
                                    globalSettings.wallpaper.ordinal,
                                )
                            }
                            AppState.toast("Couple theme " + if (on) "on" else "off")
                        }
                    }
                    TikInfoRow("Text size", "Message font size") {
                        Slider(
                            value = globalSettings.textSizeSp,
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
                Text(
                    "One theme for this chat — both of you see it.",
                    fontSize = 12.5.sp, color = Tik.Sub, fontFamily = Manrope,
                    modifier = Modifier.padding(start = 34.dp, end = 18.dp, top = 6.dp),
                )
            }
            item {
                // .prev — the design's preview card: two sample bubbles on
                // this chat's wallpaper colour.
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tik.WallColors[settings.wallpaper.ordinal])
                        .border(1.dp, Tik.Ln, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(Tik.S2)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text("Good morning", fontSize = 15.sp, color = Tik.Tx, fontFamily = Manrope)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val mineBg = if (settings.wallpaper == WallpaperMode.COUPLE) {
                            Brush.linearGradient(listOf(settings.accent, coupleGradientEnd(settings.accent)))
                        } else {
                            Brush.linearGradient(listOf(settings.accent, settings.accent))
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(mineBg)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text("Morning, you", fontSize = 15.sp,
                                color = androidx.compose.ui.graphics.Color.White, fontFamily = Manrope)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPage(thread: ChatThread, onBack: () -> Unit) {
    val globalSettings by SettingsStore.state.collectAsState()
    SubPage("Privacy & safety", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                TikGroup {
                    TikInfoRow(
                        "Read receipts", "When off, you don't send “Seen” in this chat",
                        first = true,
                    ) {
                        ToggleSwitch(!thread.receiptsOffMine) { on ->
                            AppState.repo.setReceipts(thread.id, on)
                            AppState.toast("Read receipts " + if (on) "on" else "off")
                        }
                    }
                    TikInfoRow("Last seen", "Let contacts see when you were online") {
                        ToggleSwitch(globalSettings.toggles["ls"] == true) { on ->
                            SettingsStore.setToggle("ls", on)
                            AppState.toast("Last seen " + if (on) "on" else "off")
                        }
                    }
                    TikInfoRow("Hide typing status", "Don’t show “typing…”") {
                        ToggleSwitch(globalSettings.toggles["ht"] == true) { on ->
                            SettingsStore.setToggle("ht", on)
                            AppState.toast("Hide typing " + if (on) "on" else "off")
                        }
                    }
                    TikInfoRow("Block screenshots", "Block screenshots & app-switcher previews of chats") {
                        ToggleSwitch(globalSettings.toggles["bs"] == true) { on ->
                            SettingsStore.setToggle("bs", on)
                            AppState.toast("Block screenshots " + if (on) "on" else "off")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknamesPage(thread: ChatThread, onBack: () -> Unit) {
    var nick by remember(thread.id) { mutableStateOf(thread.nickname ?: "") }
    SubPage("Nicknames", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                TikSectionHeader("Their nickname")
                TikField(nick, { nick = it.take(40) }, thread.person.name)
                Text(
                    "Shown instead of ${thread.person.name} in this chat — for both of you.",
                    fontSize = 12.5.sp, color = Tik.Sub, fontFamily = Manrope,
                    modifier = Modifier.padding(horizontal = 18.dp, top = 2.dp),
                )
                TikSectionHeader("Your nickname")
                TikField(
                    thread.myNickname ?: "", {}, "Not set", enabled = false,
                )
                Text(
                    "Set by ${thread.person.name} — only they can change it.",
                    fontSize = 12.5.sp, color = Tik.Sub, fontFamily = Manrope,
                    modifier = Modifier.padding(horizontal = 18.dp, top = 2.dp),
                )
                Spacer(Modifier.height(14.dp))
                TikPrimaryButton("Save nickname") {
                    AppState.repo.setNickname(thread.id, nick)
                    AppState.toast(if (nick.isBlank()) "Nickname cleared" else "Nickname saved")
                    onBack()
                }
            }
        }
    }
}

@Composable
private fun ProfilePage(thread: ChatThread, onBack: () -> Unit) {
    val person = thread.person
    val startCall = rememberCallStarter()
    SubPage(person.name, onBack) {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarBox(person, 92.dp, 46.dp, 38f)
                Spacer(Modifier.height(12.dp))
                Text(
                    person.name, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp, letterSpacing = (-0.66).sp, color = Tik.Tx,
                    textAlign = TextAlign.Center,
                )
                if (person.username.isNotEmpty()) {
                    Text(
                        "@${person.username}", fontSize = 15.sp, color = Tik.Sub, fontFamily = Manrope,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            TikPrimaryButton("Message") { AppState.go(Screen.Chat(thread.id)) }
            Spacer(Modifier.height(10.dp))
            TikPrimaryButton("Call", outline = true) { startCall(thread.id, false) }
        }
    }
}
