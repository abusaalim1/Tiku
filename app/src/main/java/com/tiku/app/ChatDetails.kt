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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
        val pal = LocalPalette.current
        Box(Modifier.fillMaxSize().background(pal.bg), contentAlignment = Alignment.Center) {
            Text("Chat unavailable", color = pal.sub, fontFamily = InterTight)
        }
        return
    }
    val globalSettings by SettingsStore.state.collectAsState()
    val settings = thread.effectiveSettings(globalSettings)
    val person = thread.person
    // ROUND 13: the whole details surface renders in THIS chat's palette
    // (global mode + the chat's couple theme).
    val detailsPalette = remember(globalSettings.darkMode, settings.accentIdx) {
        buildPalette(if (globalSettings.darkMode) "dark" else "light", settings.accentIdx)
    }

    CompositionLocalProvider(LocalPalette provides detailsPalette) {
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
}

/** Sub-page scaffold: the design's .top2 (glass back disc + bold title). */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxSize().background(pal.bg)) {
        TikTopBar(title = title, leading = { GlassCircleIconButton("back", onClick = onBack) })
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
    val pal = LocalPalette.current
    val person = thread.person
    val media = thread.messages.filter { it.kind == MsgKind.IMAGE && it.photo != null && !it.deletedForMe }
    LazyColumn(
        Modifier.fillMaxSize().background(pal.bg),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            // .top2 — just the glass back button
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                GlassCircleIconButton("back", onClick = onBack)
            }
        }
        item {
            // .ihd — avatar 92 round inside the accent ring, name 26 Bold,
            // @username sub. Round-6 rule stands: the REAL profile name
            // leads here — the nickname never hides who this actually is.
            // (The design's "days together" pill needs an anniversary date
            // the app does not store — dropped, never faked.)
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.border(2.dp, pal.ac, CircleShape)
                        .background(pal.bg, CircleShape)
                        .padding(3.dp),
                ) {
                    AvatarBox(person, 92.dp, 46.dp, 38f)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    person.name, fontFamily = InterTight, fontWeight = FontWeight.Bold,
                    fontSize = 26.sp, letterSpacing = (-1).sp, color = pal.tx,
                )
                if (person.username.isNotEmpty()) {
                    Text(
                        "@${person.username}", fontSize = 15.sp, color = pal.sub, fontFamily = InterTight,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            // .acts — 54px glass action discs, 12px labels. Only REAL
            // actions: the design's Search (no in-chat search exists) and
            // Mute (no engine op) are dropped.
            val startCall = rememberCallStarter()
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ActButton("user", "Profile") { onOpen("profile") }
                Spacer(Modifier.width(2.dp))
                ActButton("call", "Call") { startCall(thread.id, false) }
                Spacer(Modifier.width(2.dp))
                ActButton("vid", "Video") { startCall(thread.id, true) }
                Spacer(Modifier.width(2.dp))
                ActButton("dots", "Options") { ChatSheets.more(thread, person) }
            }
        }
        item {
            TikGroup {
                TikInfoRow("Customize", "Couple themes and text size", first = true, onClick = { onOpen("customize") }) {
                    TikIcon("chevR", Modifier.size(18.dp), tint = pal.sub)
                }
                TikInfoRow("Privacy & safety", null, onClick = { onOpen("privacy") }) {
                    TikIcon("chevR", Modifier.size(18.dp), tint = pal.sub)
                }
                TikInfoRow("Nicknames", null, onClick = { onOpen("nick") }) {
                    TikIcon("chevR", Modifier.size(18.dp), tint = pal.sub)
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
                // .mg — 3 columns, gap 4, radius 16 (loaded window only)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    media.chunked(3).forEach { rowMsgs ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowMsgs.forEach { m ->
                                Image(
                                    m.photo!!.asImageBitmap(), null,
                                    Modifier.weight(1f).aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
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

/** .acts button: 78 wide, 54px glass disc, 12px w500 label, press scale .92. */
@Composable
private fun ActButton(icon: String, label: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
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
            Modifier.size(54.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .glassSurface(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TikIcon(icon, Modifier.size(22.dp), tint = pal.tx)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterTight, color = pal.tx)
    }
}

@Composable
private fun CustomizePage(
    thread: ChatThread,
    settings: TikuUiSettings,
    globalSettings: TikuUiSettings,
    onBack: () -> Unit,
) {
    val pal = LocalPalette.current
    // The chat's resolved couple theme (effectiveSettings already ran the
    // stored int through ThemeCodec).
    val selectedIdx = settings.accentIdx
    SubPage("Customize", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                // The design's couple-theme picker: one glass card, a row
                // per theme (pattern tile + name + subtitle + check).
                TikSectionHeader("Couple themes")
                TikGroup {
                    COUPLE_THEMES.forEachIndexed { i, th ->
                        ThemePickRow(th, selected = i == selectedIdx, first = i == 0) {
                            // ROUND 13 storage: new writes use the >=10
                            // space so both generations read them sanely
                            // (see ThemeCodec in Models.kt).
                            AppState.repo.setTheme(thread.id, ThemeCodec.toStored(i), null)
                        }
                    }
                }
                Text(
                    "One theme for this chat — both of you see it.",
                    fontSize = 12.5.sp, color = pal.sub, fontFamily = InterTight,
                    modifier = Modifier.padding(start = 34.dp, end = 18.dp, top = 6.dp),
                )
            }
            item {
                // The design's Preview card, rendered in the SELECTED
                // theme's palette over this chat's wallpaper.
                TikSectionHeader("Preview")
                ThemePreviewCard(selectedIdx, settings.wallpaper)
            }
            item {
                TikSectionHeader("Wallpaper")
                TikGroup {
                    TikInfoRow("Wallpaper", null, first = true) {
                        TikSwatches(
                            colors = WallpaperMode.entries.map { mode ->
                                val c = wallTint(mode, globalSettings.darkMode)
                                if (c == androidx.compose.ui.graphics.Color.Unspecified) pal.bg else c
                            },
                            selected = thread.themeWallpaper ?: globalSettings.wallpaper.ordinal,
                            onPick = { AppState.repo.setTheme(thread.id, null, it) },
                        )
                    }
                    TikInfoRow("Couple theme", "Rose Petal with the heart wallpaper") {
                        val coupleOn = settings.accentIdx == 0 &&
                            settings.wallpaper == WallpaperMode.COUPLE
                        ToggleSwitch(coupleOn) { on ->
                            if (on) {
                                AppState.repo.setTheme(
                                    thread.id, ThemeCodec.toStored(0),
                                    WallpaperMode.COUPLE.ordinal,
                                )
                            } else {
                                AppState.repo.setTheme(
                                    thread.id, ThemeCodec.toStored(globalSettings.accentIdx),
                                    globalSettings.wallpaper.ordinal,
                                )
                            }
                            AppState.toast("Couple theme " + if (on) "on" else "off")
                        }
                    }
                }
            }
            item {
                TikSectionHeader("Text")
                TikGroup {
                    FontSizeSliderRow(
                        fontSizeSp = globalSettings.textSizeSp,
                        onFontSizeChange = { SettingsStore.setTextSize(it) },
                    )
                }
            }
        }
    }
}

/** One couple-theme row (design Customize): 64dp pattern tile with two
 *  mini bubble chips, name + subtitle, check indicator when selected. */
@Composable
private fun ThemePickRow(
    th: CoupleTheme,
    selected: Boolean,
    first: Boolean,
    onClick: () -> Unit,
) {
    val pal = LocalPalette.current
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth()) {
        if (!first) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(pal.ln))
        }
        Row(
            Modifier.fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(lerp(pal.bg, th.ac, 0.30f), pal.bg),
                        ),
                    )
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) th.ac else pal.gb,
                        RoundedCornerShape(20.dp),
                    )
                    .drawBehind {
                        with(density) { drawThemePattern(th.pat, th.ac, pal.isDark, 64.dp.toPx()) }
                    },
            ) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(width = 18.dp, height = 10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.linearGradient(listOf(th.ac, th.ac2))),
                    )
                    Box(
                        Modifier.size(width = 18.dp, height = 10.dp)
                            .glassSurface(RoundedCornerShape(5.dp)),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(th.name, fontFamily = InterTight, fontWeight = FontWeight.Medium,
                    fontSize = 15.sp, color = pal.tx)
                Text(th.subtitle, fontFamily = InterTight, fontSize = 12.5.sp, color = pal.sub,
                    modifier = Modifier.padding(top = 2.dp))
            }
            CheckCircleIndicator(selected)
        }
    }
}

/** The design's Preview card: sample bubbles in the candidate theme's
 *  palette (nested provider) over the chat's current wallpaper. */
@Composable
private fun ThemePreviewCard(themeIdx: Int, wallpaper: WallpaperMode) {
    val mode = tikuSettings().value.darkMode
    val candidate = remember(mode, themeIdx) { buildPalette(if (mode) "dark" else "light", themeIdx) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalPalette provides candidate) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (wallpaper == WallpaperMode.DEFAULT) candidate.bg
                    else wallTint(wallpaper, candidate.isDark),
                )
                .drawBehind {
                    if (wallpaper != WallpaperMode.DEFAULT && wallpaper != WallpaperMode.DOTS) {
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(candidate.ac.copy(alpha = 0.20f), androidx.compose.ui.graphics.Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                                radius = size.width * 0.9f,
                            ),
                        )
                        with(density) {
                            drawThemePattern(candidate.pat, candidate.ac, candidate.isDark, 160.dp.toPx())
                        }
                    }
                }
                .border(1.dp, candidate.gb, RoundedCornerShape(28.dp))
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box(
                    Modifier.glassSurface(RoundedCornerShape(23.dp))
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                ) {
                    Text("Good morning", fontSize = 15.sp, color = candidate.tx, fontFamily = InterTight)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.clip(RoundedCornerShape(23.dp)).background(candidate.acGradient)
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                ) {
                    Text("Morning, you", fontSize = 15.sp,
                        color = androidx.compose.ui.graphics.Color.White, fontFamily = InterTight)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box(
                    Modifier.glassSurface(RoundedCornerShape(23.dp))
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                ) {
                    Text("See you tonight", fontSize = 15.sp, color = candidate.tx, fontFamily = InterTight)
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
    val pal = LocalPalette.current
    var nick by remember(thread.id) { mutableStateOf(thread.nickname ?: "") }
    SubPage("Nicknames", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                TikSectionHeader("Their nickname")
                TikField(nick, { nick = it.take(40) }, thread.person.name)
                Text(
                    "Shown instead of ${thread.person.name} in this chat — for both of you.",
                    fontSize = 12.5.sp, color = pal.sub, fontFamily = InterTight,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp),
                )
                TikSectionHeader("Your nickname")
                TikField(
                    thread.myNickname ?: "", {}, "Not set", enabled = false,
                )
                Text(
                    "Set by ${thread.person.name} — only they can change it.",
                    fontSize = 12.5.sp, color = pal.sub, fontFamily = InterTight,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp),
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
    val pal = LocalPalette.current
    val person = thread.person
    val startCall = rememberCallStarter()
    SubPage(person.name, onBack) {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.border(2.dp, pal.ac, CircleShape)
                        .background(pal.bg, CircleShape)
                        .padding(3.dp),
                ) {
                    AvatarBox(person, 92.dp, 46.dp, 38f)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    person.name, fontFamily = InterTight, fontWeight = FontWeight.Bold,
                    fontSize = 26.sp, letterSpacing = (-1).sp, color = pal.tx,
                    textAlign = TextAlign.Center,
                )
                if (person.username.isNotEmpty()) {
                    Text(
                        "@${person.username}", fontSize = 15.sp, color = pal.sub, fontFamily = InterTight,
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
