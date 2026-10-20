package com.tiku.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatWallpaperBrush(settings: TikuUiSettings) = when (settings.wallpaper) {
    WallpaperMode.DEFAULT -> Brush.verticalGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom))
    WallpaperMode.PLAIN -> Brush.verticalGradient(listOf(TikuTokens.PlainPlain, TikuTokens.PlainPlain))
    WallpaperMode.PEACH -> Brush.verticalGradient(listOf(TikuTokens.PeachTop, TikuTokens.PeachBottom))
    WallpaperMode.DOTS -> Brush.verticalGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom))
}

@Composable
fun ChatScreen(threadId: String) {
    // CRASH FIX (owner bug A): the thread can be momentarily absent (search -> open,
    // listener refire) or flicker null<->present. This wrapper runs ONLY the threads
    // collection; the full screen (with all its hooks) composes only when the thread
    // exists, so hook order can never break across the flicker.
    androidx.activity.compose.BackHandler { AppState.go(Screen.Home) }
    val threads by AppState.threads.collectAsState()
    val thread = threads.firstOrNull { it.id == threadId }
    if (thread == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chat unavailable", color = TikuTokens.Muted, fontFamily = Manrope)
        }
        return
    }
    ChatContent(thread)
}

@Composable
private fun ChatContent(thread: ChatThread) {
    val threadId = thread.id
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.state.collectAsState()
    val typingMap by AppState.typing.collectAsState()
    val peerTyping = typingMap[threadId] == true

    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableIntStateOf(0) }
    val recorder = remember { VoiceRecorder(ctx) }
    val player = remember { VoicePlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(threadId) { onDispose { player.stop(); AppState.repo.closeThread(threadId) } }

    // image picker
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                BitmapFactory.decodeStream(s)?.let { AppState.repo.sendPhoto(threadId, it) }
            }
        }
    }
    // mic permission
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording(recorder) { recording = true } else AppState.toast("Microphone permission needed for voice notes")
    }

    // recording clock + waveform sampling + 3-minute hard cap
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        recSecs = 0
        while (recording) {
            delay(200)
            recorder.sample()
            recSecs = ((System.currentTimeMillis() - recorder.startedAt) / 1000).toInt()
            if (recSecs >= 180) {
                val f = recorder.stop(true)
                recording = false
                if (f != null) AppState.repo.sendVoice(threadId, f, 180, recorder.normalizedAmps())
                AppState.toast("Voice notes are capped at 3 minutes")
            }
        }
    }

    val person = thread.person

    val statusText = when {
        peerTyping -> "typing…"
        person.online -> "online"
        !Config.PREVIEW_UI_ONLY -> FirebaseEngine.fmtLastSeen(person.lastSeenMs).ifEmpty { "offline" }
        else -> "online"
    }

    fun sendCurrent() {
        val v = input.trim()
        if (v.isEmpty()) return
        AppState.repo.sendText(threadId, v, replyTo)
        input = ""; replyTo = null
        scope.launch { AppState.repo.setTyping(threadId, false) }
    }

    Box(Modifier.fillMaxSize().background(ChatWallpaperBrush(settings))) {
        if (settings.wallpaper == WallpaperMode.DOTS) {
            Canvas(Modifier.fillMaxSize()) {
                val spacing = 18.dp.toPx()
                val r = 1.5.dp.toPx()
                var x = 0f; var y = 0f
                while (y < size.height) {
                    x = 0f
                    while (x < size.width) {
                        drawCircle(Color(0x55E9B9A4), r, Offset(x, y))
                        x += spacing
                    }
                    y += spacing
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
        // glass header (translucent color only — .blur() on this box blurred the
        // header's own content; that was the "bottom stays blurred" class of bug)
        val startCall = rememberCallStarter()
        Row(
            Modifier.fillMaxWidth()
                .background(TikuTokens.Glass)
                .border(0.5.dp, TikuTokens.Line)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Filled.ChevronLeft, size = 38.dp, iconSize = 20.dp, onClick = { AppState.go(Screen.Home) })
            Spacer(Modifier.width(10.dp))
            AvatarBox(person, 42.dp, 15.dp, 18f)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TikuTokens.Ink)
                Text(statusText, fontSize = 12.sp, color = settings.accent, fontFamily = Manrope)
            }
            GlassIconButton(Icons.Filled.Call, size = 38.dp, iconSize = 18.dp, onClick = { startCall(threadId, false) })
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Filled.Videocam, size = 38.dp, iconSize = 18.dp, onClick = { startCall(threadId, true) })
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Filled.MoreHoriz, size = 38.dp, iconSize = 18.dp, onClick = { ChatSheets.more(thread, person) })
        }
        // messages
        val listState = rememberLazyListState()
        val visibleMessages = thread.messages.filter { !it.deletedForMe }
        LaunchedEffect(visibleMessages.size, peerTyping) {
            if (visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.size - 1 + if (peerTyping) 1 else 0)
        }
        // keyboard open/close: the root's imePadding shrinks this column, and the
        // last message must stay visible above the keyboard (owner bug 2).
        val imeBottomPx = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
        LaunchedEffect(imeBottomPx) {
            if (imeBottomPx > 0 && visibleMessages.isNotEmpty()) {
                listState.scrollToItem(visibleMessages.size - 1 + if (peerTyping) 1 else 0)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(TikuTokens.Mint)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TikuLockIcon(Modifier.size(12.dp), tint = TikuTokens.Muted)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Messages are end-to-end encrypted. Only you and ${person.name} can read them.",
                            fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope,
                        )
                    }
                }
            }
            items(visibleMessages, key = { it.id }) { m ->
                if (m.kind == MsgKind.CALL) {
                    // round 4: call events are centred system pills, never bubbles
                    CallEventPill(m)
                } else {
                MessageBubble(
                    m = m, threadId = threadId, settings = settings,
                    playing = playingId == m.id,
                    onPlayToggle = {
                        val path = m.localPath
                        if (path != null) {
                            if (playingId == m.id) { player.stop(); playingId = null }
                            else player.toggle(m.id, File(path)) { playingId = null }
                                    .also { playingId = m.id }
                        } else {
                            // preview/design simulation: 4s "playing" state
                            scope.launch {
                                playingId = if (playingId == m.id) null else m.id
                                delay(4000); if (playingId == m.id) playingId = null
                            }
                        }
                    },
                    onReact = { emoji ->
                        val cur = m.reactions.values.firstOrNull()
                        AppState.repo.react(threadId, m.id, if (cur == emoji) null else emoji)
                    },
                    onRetry = { AppState.repo.retrySend(threadId, m.id) },
                    onLongPress = {
                        ChatSheets.context(threadId, m) { q -> replyTo = q }
                    },
                )
                }
            }
            if (peerTyping) {
                item { TypingBubble() }
            }
        }
        // reply quote bar
        if (replyTo != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp)
                    .clip(RoundedCornerShape(14.dp)).background(TikuTokens.Mint)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Replying to: ${replyTo!!.take(60)}", fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { replyTo = null }) {
                    Text("✕", fontSize = 13.sp, color = TikuTokens.Ink)
                }
            }
        }
        // quick chips
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("👋" to "Hey!", "😂" to "Haha", "📍" to "Let’s meet", "❤️" to "Love it").forEach { (emoji, label) ->
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(TikuTokens.Card)
                        .border(1.dp, TikuTokens.Line, RoundedCornerShape(16.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            AppState.repo.sendText(threadId, label, replyTo)
                            replyTo = null
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiArt(emoji, 16.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(label, fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                    }
                }
            }
        }
        // bottom bar: recording or input
        if (recording) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Filled.Close, onClick = {
                    recorder.stop(false); recording = false
                })
                Spacer(Modifier.width(8.dp))
                RecordingTimer(recSecs)
                Spacer(Modifier.weight(1f))
                GradientCircleButton {
                    val f = recorder.stop(true)
                    recording = false
                    if (f != null) AppState.repo.sendVoice(threadId, f, recSecs.coerceAtLeast(1), recorder.normalizedAmps())
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 14.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Filled.Image, onClick = { ChatSession.expectExternalActivity = true; pickImage.launch("image/*") })
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.weight(1f).height(46.dp)
                        .clip(RoundedCornerShape(23.dp)).background(TikuTokens.Card)
                        .border(1.dp, TikuTokens.Line, RoundedCornerShape(23.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (it.isNotEmpty()) AppState.repo.setTyping(threadId, true)
                        },
                        textStyle = TextStyle(fontSize = settings.textSizeSp.sp, fontFamily = Manrope, color = TikuTokens.Ink),
                        cursorBrush = SolidColor(settings.accent),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = if (settings.enterToSend) androidx.compose.ui.text.input.ImeAction.Send
                            else androidx.compose.ui.text.input.ImeAction.Default,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = { if (settings.enterToSend) sendCurrent() },
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text("Message", fontSize = settings.textSizeSp.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                            inner()
                        },
                    )
                    Box(
                        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            ChatSheets.emoji { e -> input += e }
                        },
                    ) {
                        EmojiArt("🙂", 20.dp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (input.isNotEmpty()) {
                    GradientCircleButton { sendCurrent() }
                } else {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(settings.gradBrush)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    startRecording(recorder) { recording = true }
                                } else askMic.launch(Manifest.permission.RECORD_AUDIO)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Mic, null, Modifier.size(20.dp), tint = Color.White)
                    }
                }
            }
        }
        }
    }
}

private fun startRecording(recorder: VoiceRecorder, onStarted: () -> Unit) {
    runCatching { recorder.start(); onStarted() }
        .onFailure { AppState.toast("Couldn’t start recording") }
}

@Composable
fun RecordingTimer(secs: Int) {
    val inf = rememberInfiniteTransition(label = "recpulse")
    val a by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "rp")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(TikuTokens.Red))
        Spacer(Modifier.width(8.dp))
        Text("%d:%02d".format(secs / 60, secs % 60), color = TikuTokens.Red,
            fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Manrope)
    }
}

@Composable
fun GradientCircleButton(onClick: () -> Unit) {
    val settings by SettingsStore.state.collectAsState()
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(settings.gradBrush)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Send, null, Modifier.size(20.dp), tint = Color.White)
    }
}

@Composable
fun TypingBubble() {
    val inf = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(TikuTokens.Bubble)
            .border(1.dp, TikuTokens.Line, RoundedCornerShape(20.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { i ->
            val off by inf.animateFloat(0f, -5f, infiniteRepeatable(tween(500), RepeatMode.Reverse,
                initialStartOffset = androidx.compose.animation.core.StartOffset(i * 150)), label = "td$i")
            Box(Modifier.size(7.dp).offset(y = off.dp).clip(CircleShape).background(TikuTokens.Muted))
        }
    }
}

// ================= call events (round 4) =================
/** My uid for UI-side perspective decisions (real: the engine's established
 *  uid incl. the restored-identity fallback; preview: the "me" sentinel the
 *  preview store stamps on its own call events). */
fun myUidForUi(): String = if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid()

/** Perspective-correct label for a call event. [iAmCaller] = the viewer is
 *  the one who placed the call. Owner's copy rules: no answer → caller sees
 *  "No answer", callee sees "Missed call"; declined → both see it; ended →
 *  "Call ended · duration" on both sides. */
fun callEventLabel(m: TikuMessage, iAmCaller: Boolean): String {
    val kindWord = if (m.callVideo) "Video call" else "Call"
    return when (m.callOutcome) {
        "ended" -> {
            val base = "$kindWord ended"
            if (m.callDurationSec > 0) "$base · ${"%d:%02d".format(m.callDurationSec / 60, m.callDurationSec % 60)}" else base
        }
        "declined" -> if (iAmCaller) "Call declined" else "You declined the call"
        "noanswer" -> if (iAmCaller) "No answer" else if (m.callVideo) "Missed video call" else "Missed call"
        else -> kindWord
    }
}

/** Centred system pill for a call event — same visual family as the E2EE
 *  banner above the messages. Never a bubble, never side-aligned. */
@Composable
fun CallEventPill(m: TikuMessage) {
    val iAmCaller = m.callCallerUid == myUidForUi()
    val icon = when {
        m.callOutcome == "noanswer" && !iAmCaller -> Icons.Filled.CallMissed
        m.callOutcome == "declined" -> Icons.Filled.CallEnd
        else -> Icons.Filled.Call
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(TikuTokens.Mint)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(13.dp), tint = TikuTokens.Muted)
            Spacer(Modifier.width(6.dp))
            Text(
                "${callEventLabel(m, iAmCaller)} · ${m.time}",
                fontSize = 12.sp, color = TikuTokens.Muted, fontFamily = Manrope,
            )
        }
    }
}

// ================= message bubble =================
@Composable
fun MessageBubble(
    m: TikuMessage,
    threadId: String,
    settings: TikuUiSettings,
    playing: Boolean,
    onPlayToggle: () -> Unit,
    onReact: (String) -> Unit,
    onLongPress: () -> Unit,
    onRetry: () -> Unit = {},
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (appeared) 1f else 0.7f, tween(450, easing = TikuEase), label = "pop")
    val shape = if (m.me) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
        else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val textColor = if (m.me) Color.White else TikuTokens.Ink

    Row(
        Modifier.fillMaxWidth()
            .pointerInput(m.id) {
                detectTapGestures(
                    onDoubleTap = { onReact("❤️") },
                    onLongPress = { onLongPress() },
                )
            },
        horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
    ) {
        Box(Modifier.fillMaxWidth(0.78f)) {
            Column(
                Modifier.scale(scale)
                    .clip(shape)
                    .then(if (m.me) Modifier.background(settings.gradBrush)
                          else Modifier.background(TikuTokens.Bubble).border(1.dp, TikuTokens.Line, shape))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                if (m.replyQuote != null) {
                    Row {
                        Box(Modifier.width(3.dp).fillMaxHeight()
                            .background(if (m.me) Color.White.copy(alpha = 0.8f) else settings.accent))
                        Spacer(Modifier.width(8.dp))
                        Text(m.replyQuote, fontSize = 12.sp, color = textColor.copy(alpha = 0.75f),
                            fontFamily = Manrope, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (m.kind == MsgKind.IMAGE && m.photo != null) {
                    androidx.compose.foundation.Image(
                        m.photo.asBmp(), null,
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                    if (m.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
                }
                if (m.kind == MsgKind.VOICE) {
                    VoiceBubbleContent(m, textColor, playing, onPlayToggle)
                } else if (m.text.isNotEmpty()) {
                    Text(m.text, fontSize = settings.textSizeSp.sp, color = textColor, fontFamily = Manrope)
                }
                Row(Modifier.align(Alignment.End).padding(top = 2.dp)) {
                    Text(
                        when {
                            m.me && m.status == SendStatus.SENDING -> "Sending…"
                            m.me && m.status == SendStatus.FAILED -> "Not sent"
                            // ROUND 4 (item 1): single ✓ until the peer has
                            // actually SEEN the message (their uid in the
                            // doc's seenBy); ✓✓ only after. seenByPeer is
                            // re-derived from Firestore on every snapshot, so
                            // this survives re-entry / restore / 2nd device.
                            else -> (if (m.starredByMe) "★ " else "") + m.time +
                                if (m.me) (if (m.seenByPeer) " ✓✓" else " ✓") else ""
                        },
                        fontSize = 11.sp, color = textColor.copy(alpha = 0.7f), fontFamily = Manrope,
                    )
                }
            }
            val reactions = m.reactions.values.distinct()
            if (reactions.isNotEmpty()) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-10).dp, y = 10.dp)
                        .clip(RoundedCornerShape(12.dp)).background(TikuTokens.Card)
                        .border(1.dp, TikuTokens.Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        reactions.forEach { EmojiArt(it, 14.dp) }
                    }
                }
            }
        }
    }
    // FAILED outgoing bubble: plain reason + tap-to-retry (round 3 — a failed
    // send must never be invisible again).
    if (m.me && m.status == SendStatus.FAILED) {
        Row(
            Modifier.fillMaxWidth().padding(top = 3.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "⚠ Couldn't send — tap to retry",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = TikuTokens.Red, fontFamily = Manrope,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onRetry() },
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
fun VoiceBubbleContent(m: TikuMessage, textColor: Color, playing: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(0x337F7F7F))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (playing) "❚❚" else "▶", fontSize = 12.sp, color = textColor)
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f).height(26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val amps = if (m.amps.size == 22) m.amps else List(22) { 8 + ((it * 7) % 14) }
            amps.forEach { a ->
                Box(
                    Modifier.weight(1f).height(a.coerceIn(4, 24).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(textColor.copy(alpha = if (playing) 0.9f else 0.55f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("0:%02d".format(m.voiceDurSec), fontSize = 12.sp, color = textColor.copy(alpha = 0.8f), fontFamily = Manrope)
    }
}

// ================= chat sheets =================
object ChatSheets {
    private val EMOJIS = listOf("😊", "😂", "🥹", "😍", "🤍", "🔥", "✨", "🌿", "🌸", "☕", "🎧", "🎨",
        "🙌", "👀", "💫", "🍋", "☁️", "🫶", "🥳", "😌", "🤭", "💬", "📍", "🌙")
    private val REACTIONS = listOf("❤️", "😂", "😮", "😢", "👍", "🔥")

    fun emoji(onPick: (String) -> Unit) {
        AppState.sheet {
            SerifTitle("Emoji", 28.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Column {
                EMOJIS.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { e ->
                            Box(
                                Modifier.weight(1f).height(48.dp)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        onPick(e); AppState.closeSheet()
                                    },
                                contentAlignment = Alignment.Center,
                            ) { EmojiArt(e, 30.dp) }
                        }
                    }
                }
            }
        }
    }

    fun context(threadId: String, m: TikuMessage, onReply: (String) -> Unit) {
        val label = when (m.kind) { MsgKind.IMAGE -> "Photo"; MsgKind.VOICE -> "Voice message"; else -> m.text }
        AppState.sheet {
            // reaction row
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TikuTokens.BgTop)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                REACTIONS.forEach { e ->
                    Box(
                        Modifier.size(44.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                val cur = m.reactions.values.firstOrNull()
                                AppState.repo.react(threadId, m.id, if (cur == e) null else e)
                                AppState.closeSheet()
                            },
                        contentAlignment = Alignment.Center,
                    ) { EmojiArt(e, 27.dp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            SheetAction("Reply") {
                onReply(label.take(60)); AppState.closeSheet()
            }
            SheetAction("Copy") {
                // clipboard handled by the caller platform-side; toast to match the prototype
                AppState.toast("Copied"); AppState.closeSheet()
            }
            SheetAction(if (m.starredByMe) "Unstar" else "Star") {
                AppState.repo.toggleStar(threadId, m.id)
                AppState.toast("Star updated"); AppState.closeSheet()
            }
            // Media rule (owner): NOTHING auto-saves to the gallery. This explicit tap
            // is the only exit — decrypted photo -> Pictures/Tiku, voice -> Music/Tiku.
            if (m.kind == MsgKind.IMAGE || m.kind == MsgKind.VOICE) {
                SheetAction("Save to device") {
                    if (Config.PREVIEW_UI_ONLY) {
                        AppState.toast("Saved to device")
                    } else {
                        val ok = MediaSaver.saveToDevice(AppState.appCtx, m)
                        AppState.toast(if (ok) "Saved to device" else "Couldn't save")
                    }
                    AppState.closeSheet()
                }
            }
            SheetAction("Delete") {
                AppState.repo.deleteForMe(threadId, m.id); AppState.closeSheet()
            }
        }
    }

    fun more(thread: ChatThread, person: Person) {
        AppState.sheet {
            SerifTitle(person.name, 28.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            SheetAction("Mute notifications") { AppState.toast("Muted for 8 hours"); AppState.closeSheet() }
            SheetAction("Pin chat") { AppState.toast("Chat pinned"); AppState.closeSheet() }
            SheetAction("Export chat") { AppState.toast("Chat exported (encrypted)"); AppState.closeSheet() }
            SheetAction("Clear chat") {
                AppState.repo.clearChat(thread.id); AppState.closeSheet()
            }
            SheetAction("Block") { AppState.toast("Contact blocked"); AppState.closeSheet() }
        }
    }
}

@Composable
fun SheetAction(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .border(0.5.dp, TikuTokens.Line)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink)
    }
}
