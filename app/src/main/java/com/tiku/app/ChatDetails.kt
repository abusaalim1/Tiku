package com.tiku.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ROUND 10 — CHAT DETAILS (IG-style): the single home for everything about
 * one chat, opened by tapping the chat header (avatar or name). Back
 * returns to the chat.
 *
 *  - Theme: a PER-CHAT accent + wallpaper, stored on the chat doc
 *    (themeAccent / themeWallpaper) so BOTH members see the same look —
 *    the chat screen renders from it via ChatThread.effectiveSettings,
 *    falling back to the global appearance when unset.
 *  - Nicknames: the existing round-6 system (chat doc nicknames map). The
 *    peer's nickname is edited through the SAME sheet composable as
 *    before (ChatSheets.nickname) — this screen is now its only entry
 *    point. My own nickname (set by the peer) is display-only here,
 *    matching the existing editor's semantics.
 *  - Read receipts: a per-chat switch backed by the chat doc's receiptsOff
 *    array. Off ⇒ my client writes no seenBy marks in this chat and my
 *    "Seen" captions stay hidden.
 */
@Composable
fun ChatDetailsScreen(threadId: String) {
    BackHandler { AppState.go(Screen.Chat(threadId)) }
    val threads by AppState.threads.collectAsState()
    val thread = threads.firstOrNull { it.id == threadId }
    if (thread == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chat unavailable", color = TikuTokens.Muted, fontFamily = Manrope)
        }
        return
    }
    val globalSettings by SettingsStore.state.collectAsState()
    val settings = thread.effectiveSettings(globalSettings)
    val person = thread.person
    val bgBrush = Brush.verticalGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom))

    LazyColumn(
        Modifier.fillMaxSize().background(bgBrush),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Filled.ChevronLeft, size = 38.dp, iconSize = 20.dp) {
                    AppState.go(Screen.Chat(threadId))
                }
                Spacer(Modifier.width(10.dp))
                SerifTitle("Details", 26.sp)
            }
        }
        item {
            // identity block: REAL profile name + @username (round 6 rule —
            // the nickname never hides who this actually is, here)
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarBox(person, 92.dp, 32.dp, 38f)
                Spacer(Modifier.height(12.dp))
                Text(person.name, fontFamily = Bricolage, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, color = TikuTokens.Ink)
                if (person.username.isNotEmpty()) {
                    Text("@${person.username}", fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                }
            }
        }
        item {
            DetailsCard("Theme") {
                Text("Accent", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val selected = thread.themeAccent ?: globalSettings.accentIdx
                    TikuTokens.Accents.forEachIndexed { i, (a, b) ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(a, b)))
                                .border(
                                    if (i == selected) 2.5.dp else 1.dp,
                                    if (i == selected) TikuTokens.Ink else TikuTokens.Line,
                                    CircleShape,
                                )
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    AppState.repo.setTheme(threadId, i, null)
                                },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Wallpaper", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                Spacer(Modifier.height(10.dp))
                val wpSelected = thread.themeWallpaper ?: globalSettings.wallpaper.ordinal
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WallpaperMode.entries.forEachIndexed { i, mode ->
                        val label = when (mode) {
                            WallpaperMode.DEFAULT -> "Default"
                            WallpaperMode.PLAIN -> "Plain"
                            WallpaperMode.PEACH -> "Peach"
                            WallpaperMode.DOTS -> "Dots"
                            WallpaperMode.COUPLE -> "Couple"
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (i == wpSelected) TikuTokens.Mint else TikuTokens.Card)
                                .border(1.dp, if (i == wpSelected) settings.accent else TikuTokens.Line, RoundedCornerShape(16.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    AppState.repo.setTheme(threadId, null, i)
                                }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                        ) {
                            Text(label, fontSize = 13.sp, fontFamily = Manrope,
                                fontWeight = if (i == wpSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = TikuTokens.Ink)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("One theme for this chat — both of you see it.",
                    fontSize = 12.5.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
        }
        item {
            DetailsCard("Nicknames") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            ChatSheets.nickname(thread)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Nickname for ${person.name}", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                        Text("Shown in the chat header and list — for both of you",
                            fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                    }
                    Text(thread.nickname ?: "Set", fontSize = 14.sp, fontFamily = Manrope,
                        color = settings.accent, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp).background(TikuTokens.Line))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Your nickname", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                        Text("Set by ${person.name} — only they can change it",
                            fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                    }
                    Text(thread.myNickname ?: "Not set", fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                }
            }
        }
        item {
            DetailsCard("Read receipts") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Read receipts", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                        Text("When off, you don't send “Seen” in this chat",
                            fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                    }
                    ToggleSwitch(!thread.receiptsOffMine) { on ->
                        AppState.repo.setReceipts(threadId, on)
                        AppState.toast("Read receipts " + if (on) "on" else "off")
                    }
                }
            }
        }
        item {
            DetailsCard("More") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            AppState.repo.clearChat(threadId)
                            AppState.toast("Chat cleared")
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Clear chat", fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                        Text("Hides this conversation's history on this phone only",
                            fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                    }
                    Text("›", fontSize = 20.sp, color = TikuTokens.Muted)
                }
            }
        }
    }
}

/** A titled settings card in the Me-pane visual language. */
@Composable
private fun DetailsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SerifTitle(title, 20.sp, TikuTokens.Muted, Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp))
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)
            .clip(RoundedCornerShape(24.dp)).background(TikuTokens.Card)
            .border(1.dp, TikuTokens.Line, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        content = content,
    )
}
