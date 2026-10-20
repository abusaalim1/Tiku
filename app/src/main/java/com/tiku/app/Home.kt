package com.tiku.app

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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import kotlinx.coroutines.launch

private val TabsIcons = listOf(
    Icons.Outlined.ChatBubbleOutline,
    Icons.Filled.Call,
    Icons.Outlined.PersonOutline,
)

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    // QUICK EXIT (lock rule): Back at ChatRoot home re-locks instantly to the facade.
    androidx.activity.compose.BackHandler { ChatSession.quickExit(ctx) }
    val settings = tikuSettings().value
    val tab = AppState.homeTab
    val title = when (tab) { HomeTab.CHATS -> "Chats"; HomeTab.CALLS -> "Calls"; HomeTab.ME -> "Me" }
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
                    GlassIconButton(Icons.Filled.DarkMode, label = "Theme") {
                        AppState.toast("Light mode is Tikuu’s home")
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(Icons.Filled.Add, label = "New chat") { NewChatSheet.open() }
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    HomeTab.CHATS -> ChatsPane()
                    HomeTab.CALLS -> CallsPane()
                    HomeTab.ME -> MePane()
                }
            }
        }
        // glass floating tab bar
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .align(Alignment.BottomCenter)
                .height(64.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(TikuTokens.Glass).blur(24.dp)
                .border(1.dp, TikuTokens.Line, RoundedCornerShape(26.dp))
                .shadow(20.dp, RoundedCornerShape(26.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(HomeTab.CHATS, HomeTab.CALLS, HomeTab.ME).zip(listOf("Chats", "Calls", "Me")).zip(TabsIcons).forEach { (tt, icon) ->
                val (t, label) = tt
                val on = t == tab
                Column(
                    Modifier.weight(1f).height(52.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) TikuTokens.Mint else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AppState.homeTab = t },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = if (on) settings.accent else TikuTokens.Muted)
                    Text(label, fontSize = 12.sp, fontFamily = Manrope, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) settings.accent else TikuTokens.Muted)
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
        val filtered = threads.filter { it.person.name.contains(query, ignoreCase = true) }
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
            Text(th.person.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (last?.me == true) Text("✓✓ ", fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
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
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(settings.accent)
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
@Composable
fun CallsPane() {
    val logs by AppState.callLogs.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
        items(logs) { log ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBox(log.person, 52.dp, 19.dp, 22f)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(log.person.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                    Text((if (log.direction == "missed") "↙ Missed" else "↗ Outgoing") + " · " +
                        log.kind.replaceFirstChar { it.uppercase() },
                        fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                }
                GlassIconButton(Icons.Filled.Call, label = "Call", onClick = {
                    val tid = log.threadId.ifEmpty {
                        AppState.threads.value.firstOrNull { it.person.uid == log.person.uid }?.id ?: ""
                    }
                    if (tid.isNotEmpty()) AppState.go(Screen.Call(tid, log.kind == "video"))
                })
            }
        }
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
            }
        }
        item { SettingsGroups() }
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
            RowDef("rg", "Text size", "Message font size", "fs"),
        ),
        "Privacy" to listOf(
            RowDef("tg", "App lock", "Ask for password on open", "al"),
            RowDef("tg", "Disappearing messages", "Vanish after 24 hours", "dm"),
            RowDef("tg", "Read receipts", "Show ✓✓ when seen", "rr", true),
            RowDef("tg", "Last seen", "Let contacts see when you were online", "ls", true),
            RowDef("tg", "Block screenshots", "Hide chats in app switcher", "bs"),
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
        "fs", "ac", "wp", "dk" -> { /* handled by their own controls */ }
    }
}

// ================= New chat sheet =================
object NewChatSheet {
    fun open() {
        if (Config.PREVIEW_UI_ONLY) {
            AppState.sheet {
                SerifTitle("New chat", 28.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                PreviewStore.people.forEach { p ->
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
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope)
                            Text("on Tikuu", fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                        }
                    }
                }
            }
        } else {
            var q by mutableStateOf("")
            var result by mutableStateOf<Person?>(null)
            var busy by mutableStateOf(false)
            var note by mutableStateOf("")
            AppState.sheet {
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
                        onValueChange = {
                            q = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' }.take(20)
                            result = null; note = ""
                        },
                        textStyle = TextStyle(fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink),
                        cursorBrush = SolidColor(TikuTokens.Ink), singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
                        decorationBox = { inner ->
                            if (q.isEmpty()) Text("username", fontSize = 16.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                val scope = rememberCoroutineScope()
                PrimaryButton(if (busy) "Searching…" else "Search", enabled = q.length >= 3 && !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    busy = true
                    scope.launch {
                        result = runCatching { FirebaseEngine.lookupPerson(q) }.getOrNull()
                        note = if (result == null) "No user found for @$q" else ""
                        busy = false
                    }
                }
                if (note.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(note, fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                }
                result?.let { p ->
                    Spacer(Modifier.height(6.dp))
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
                        Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope)
                    }
                }
            }
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
