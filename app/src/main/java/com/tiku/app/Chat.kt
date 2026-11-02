package com.tiku.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** ROUND 12: chat wallpapers are the design's WALL set — near-black tints
 *  (DEFAULT #000000, PLAIN #0F0E18, PEACH #120C0F, DOTS #0B1311,
 *  COUPLE #1A1214). The per-chat wallpaper choice itself is unchanged. */
@Composable
fun ChatWallpaperBrush(settings: TikuUiSettings) =
    Brush.verticalGradient(
        listOf(
            Tik.WallColors[settings.wallpaper.ordinal],
            Tik.WallColors[settings.wallpaper.ordinal],
        ),
    )

/** Tiny heart path centred at (cx, cy), [s] ≈ half-width in px. */
private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.85f)
    cubicTo(cx - s * 1.35f, cy + s * 0.05f, cx - s * 0.95f, cy - s * 0.95f, cx, cy - s * 0.32f)
    cubicTo(cx + s * 0.95f, cy - s * 0.95f, cx + s * 1.35f, cy + s * 0.05f, cx, cy + s * 0.85f)
    close()
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
    val globalSettings by SettingsStore.state.collectAsState()
    // ROUND 10: this chat's own theme (chat doc themeAccent/themeWallpaper,
    // set from Chat Details) overrides the global appearance for everything
    // rendered inside this chat — bubbles, wallpaper, accents.
    val settings = thread.effectiveSettings(globalSettings)
    val typingMap by AppState.typing.collectAsState()
    val peerTyping = typingMap[threadId] == true

    var input by remember { mutableStateOf("") }
    // ROUND 8: reply state carries the quote AND whose it was, so the
    // composer bar can render the IG-form header ("Replying to …").
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    // ROUND 8: the message currently open in the full-screen image viewer.
    var viewerMsg by remember { mutableStateOf<TikuMessage?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableIntStateOf(0) }
    val recorder = remember { VoiceRecorder(ctx) }
    val player = remember { VoicePlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

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

    // ROUND 8 (item A): presence is freshness-aware (see
    // FirebaseEngine.presenceText). The profile listener pushes updates
    // live; this 30s tick additionally re-evaluates the freshness window so
    // a peer whose app died (no more writes, ever) flips from "online" to
    // their exact last seen without waiting for the next snapshot.
    var presenceTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(threadId) {
        while (true) { delay(30_000); presenceTick++ }
    }
    val statusText = when {
        peerTyping -> "typing…"
        !Config.PREVIEW_UI_ONLY -> {
            @Suppress("UNUSED_EXPRESSION") presenceTick // read: re-evaluate per tick
            FirebaseEngine.presenceText(person)
        }
        else -> "online"
    }

    // ROUND 10 (C5): the message being edited, if any — the composer
    // prefills with its text and Send re-encrypts in place instead of
    // posting a new message.
    var editTarget by remember { mutableStateOf<TikuMessage?>(null) }

    fun sendCurrent() {
        val v = input.trim()
        if (v.isEmpty()) return
        val et = editTarget
        if (et != null) {
            AppState.repo.editText(threadId, et.id, v)
            editTarget = null
            input = ""
            scope.launch { AppState.repo.setTyping(threadId, false) }
            return
        }
        AppState.repo.sendText(threadId, v, replyTarget?.quote)
        input = ""; replyTarget = null
        scope.launch { AppState.repo.setTyping(threadId, false) }
        // ROUND 8: my own send always lands me at the latest message (IG)
        scope.launch { runCatching { listState.animateScrollToItem(0) } }
    }

    /** ROUND 10 (C6): scroll to a message (the pinned strip's tap) — if it
     *  is beyond the loaded window, page older messages in until it lands
     *  (capped at ~10 pages), then scroll. The lazy index is computed from
     *  live state: [typing item?, seen item?, …messages reversed…]. */
    fun jumpToMessage(msgId: String) {
        scope.launch {
            var tries = 0
            while (true) {
                val th = AppState.thread(threadId) ?: return@launch
                val msgs = th.messages.filter { !it.deletedForMe }
                val chronoIdx = msgs.indexOfFirst { it.id == msgId }
                if (chronoIdx >= 0) {
                    val lastV = msgs.lastOrNull()
                    val seenItem = lastV != null && lastV.me && lastV.kind != MsgKind.CALL &&
                        lastV.status == SendStatus.SENT && lastV.seenByPeer && !th.receiptsOffMine
                    val offsetItems = (if (AppState.typing.value[threadId] == true) 1 else 0) +
                        (if (seenItem) 1 else 0)
                    runCatching { listState.animateScrollToItem(offsetItems + (msgs.lastIndex - chronoIdx)) }
                    return@launch
                }
                if (!th.hasOlder || tries >= 10) {
                    AppState.toast("Couldn't find that message")
                    return@launch
                }
                AppState.repo.loadOlder(threadId)
                tries++
                delay(700)
            }
        }
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
                        drawCircle(Color(0x0FFFFFFF), r, Offset(x, y))
                        x += spacing
                    }
                    y += spacing
                }
            }
        }
        if (settings.wallpaper == WallpaperMode.COUPLE) {
            // Design .couple.msgs: a soft rose glow pooled at the top —
            // radial-gradient(120% 50% at 50% 0, #E5677F1C, transparent).
            val glow = settings.accent
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.11f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 1.2f,
                    ),
                )
            }
        }
        Column(Modifier.fillMaxSize()) {
        // .chead — padding 8, bottom hairline ln, avatar 34 round, name
        // 15.5 w600, status 12 sub; bare .btn call / video buttons.
        // Tapping the identity still opens Chat Details (Round 10).
        val startCall = rememberCallStarter()
        Column(Modifier.fillMaxWidth().background(Tik.Bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TikBtn("back") { AppState.go(Screen.Home) }
                Spacer(Modifier.width(2.dp))
                Row(
                    Modifier.weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            AppState.go(Screen.ChatDetails(threadId))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(person, 34.dp, 17.dp, 14f)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        // the header title is the chat's display name — the
                        // peer's nickname when one is set, else their name.
                        Text(thread.displayName, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = Tik.Tx,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(statusText, fontSize = 12.sp, color = if (peerTyping) settings.accent else Tik.Sub, fontFamily = Manrope)
                    }
                }
                TikBtn("call", iconSize = 23.dp) { startCall(threadId, false) }
                TikBtn("vid", iconSize = 23.dp) { startCall(threadId, true) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tik.Ln))
        }
        // ROUND 10 (C6): pinned strip — a slim bar under the header showing
        // the most recent pin; tap jumps to the message, long-press unpins.
        if (thread.pinnedMsgIds.isNotEmpty()) {
            val pinId = thread.pinnedMsgIds.last()
            val pinnedMsg = thread.messages.firstOrNull { it.id == pinId }
            val pinPreview = when (pinnedMsg?.kind) {
                MsgKind.IMAGE -> "Photo"
                MsgKind.VOICE -> "Voice message"
                MsgKind.CALL -> "Call"
                else -> pinnedMsg?.text?.take(60) ?: "Pinned message"
            }
            Row(
                Modifier.fillMaxWidth().background(Tik.S1)
                    .pointerInput(pinId) {
                        detectTapGestures(
                            onTap = { jumpToMessage(pinId) },
                            onLongPress = {
                                AppState.repo.setPinnedMsg(threadId, pinId, false)
                                AppState.toast("Message unpinned")
                            },
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TikIcon("pin", Modifier.size(13.dp), tint = settings.accent)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Pinned · $pinPreview",
                    fontSize = 12.5.sp, fontFamily = Manrope, color = Tik.Tx,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (thread.pinnedMsgIds.size > 1) {
                    Text("${thread.pinnedMsgIds.size} pinned", fontSize = 11.5.sp,
                        color = Tik.Sub, fontFamily = Manrope)
                }
            }
        }
        // messages — ROUND 8 REWORK (items 4 + 7b):
        // The list is REVERSED: index 0 IS the latest message, glued to the
        // bottom edge by the layout itself. Opening a chat, the keyboard
        // opening/closing, and older pages prepending all need NO scroll
        // calls at all — which removes the old "animateScrollToItem on every
        // size change" effect, the direct cause of the owner's scroll
        // up/down jitter (it fired on every snapshot rebuild). Position now
        // only ever moves when the USER scrolls, when their own send lands,
        // or when they tap the jump-to-latest pill.
        val visibleMessages = thread.messages.filter { !it.deletedForMe }
        val nearBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
        var unseenNew by remember { mutableIntStateOf(0) }
        // arrivals are measured against the NEWEST timestamp known so far —
        // older pages loading in (pagination) must never count as "new".
        var prevNewestTs by remember {
            mutableStateOf(visibleMessages.maxOfOrNull { it.tsMs } ?: 0L)
        }
        LaunchedEffect(visibleMessages.size) {
            val newest = visibleMessages.maxOfOrNull { it.tsMs } ?: 0L
            if (newest > prevNewestTs) {
                val fresh = visibleMessages.count { it.tsMs > prevNewestTs }
                // follow the conversation only if I'm already at the bottom;
                // if I'm reading history, count the arrivals on the pill
                if (nearBottom) runCatching { listState.animateScrollToItem(0) }
                else unseenNew += fresh
            }
            if (newest > prevNewestTs) prevNewestTs = newest
        }
        LaunchedEffect(nearBottom) { if (nearBottom) unseenNew = 0 }
        // pagination: approaching the oldest loaded message fetches the
        // next older page (the engine no-ops when there is none / one is
        // already in flight)
        LaunchedEffect(threadId, thread.hasOlder) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                    listState.layoutInfo.totalItemsCount
            }.collect { (lastIdx, total) ->
                if (thread.hasOlder && lastIdx != null && total > 0 && lastIdx >= total - 3) {
                    AppState.repo.loadOlder(threadId)
                }
            }
        }
        // IG "Seen": under my latest message, only when it is also the
        // latest message overall and the peer's uid is in its seenBy.
        // ROUND 10: hidden while MY read receipts are off in this chat.
        val lastVisible = visibleMessages.lastOrNull()
        val showSeen = lastVisible != null && lastVisible.me &&
            lastVisible.kind != MsgKind.CALL && lastVisible.status == SendStatus.SENT &&
            lastVisible.seenByPeer && !thread.receiptsOffMine
        // ROUND 10 (C2): entrance bookkeeping — a bubble animates in ONLY
        // when it is a genuinely new TAIL arrival: not part of the first
        // loaded window, not an older pagination prepend (its ts predates
        // the first window's newest), and never seen composed before.
        val knownIds = remember(threadId) { mutableSetOf<String>() }
        var firstNewestTs by remember(threadId) { mutableStateOf<Long?>(null) }
        LaunchedEffect(threadId, visibleMessages.isNotEmpty()) {
            if (firstNewestTs == null && visibleMessages.isNotEmpty()) {
                firstNewestTs = visibleMessages.maxOf { it.tsMs }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 14.dp).testTag("message_list"),
            state = listState,
            reverseLayout = true,
            // .msgs padding 12/14/8 (top = the history end in this
            // reversed layout, bottom = the composer side)
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 8.dp),
            // ROUND 10 (C3): no fixed arrangement spacing — each bubble item
            // carries its own top gap (2dp inside a same-sender group, 10dp
            // between groups), which a uniform spacedBy could never express.
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (peerTyping) {
                item(key = "typing") { TypingBubble() }
            }
            if (showSeen) {
                item(key = "seen-caption") {
                    // .seen — right aligned, 11.5 sub, margin 4/6/0, fi .3s fade
                    val seenAlpha = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { seenAlpha.animateTo(1f, tween(300, easing = TikEase)) }
                    Row(
                        Modifier.fillMaxWidth().padding(end = 6.dp, top = 4.dp)
                            .graphicsLayer { alpha = seenAlpha.value },
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text("Seen", fontSize = 11.5.sp, color = Tik.Sub, fontFamily = Manrope)
                    }
                }
            }
            itemsIndexed(visibleMessages.asReversed(), key = { _, m -> m.id }) { revIdx, m ->
                // ROUND 10 (C3): grouping math on the CHRONOLOGICAL list —
                // consecutive same-sender messages ≤2 min apart form a group.
                val chronoIdx = visibleMessages.lastIndex - revIdx
                val prevMsg = visibleMessages.getOrNull(chronoIdx - 1)
                val nextMsg = visibleMessages.getOrNull(chronoIdx + 1)
                val groupedWithPrev = prevMsg != null && m.kind != MsgKind.CALL &&
                    prevMsg.kind != MsgKind.CALL && prevMsg.senderUid == m.senderUid &&
                    (m.tsMs - prevMsg.tsMs) in 0..120_000L
                val groupLast = nextMsg == null || m.kind == MsgKind.CALL ||
                    nextMsg.kind == MsgKind.CALL || nextMsg.senderUid != m.senderUid ||
                    (nextMsg.tsMs - m.tsMs) > 120_000L
                // ROUND 10 (C2): genuinely-new tail arrivals only (see the
                // bookkeeping above the list); composing marks the id known.
                val animateIn = firstNewestTs != null && m.id !in knownIds &&
                    m.tsMs >= (firstNewestTs ?: Long.MAX_VALUE)
                LaunchedEffect(m.id) { knownIds += m.id }
                if (m.kind == MsgKind.CALL) {
                    // round 4: call events are centred system pills, never bubbles
                    CallEventPill(m)
                } else {
                // Design .mr margin is a uniform 2px; the app keeps a small
                // group distinction (2dp inside a group, 4dp between), and
                // a reacted message keeps .mr.hx's extra bottom room so
                // its pill (bottom -13) clears the next bubble.
                Box(Modifier.padding(
                    top = if (groupedWithPrev) 2.dp else 4.dp,
                    bottom = if (m.reactions.isNotEmpty()) 12.dp else 2.dp,
                )) {
                MessageBubble(
                    m = m, threadId = threadId, settings = settings,
                    playing = playingId == m.id,
                    animateIn = animateIn,
                    groupLast = groupLast,
                    showAvatar = !m.me && groupLast,
                    peer = person,
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
                        ChatSheets.context(
                            threadId, m,
                            onReply = { msg ->
                                replyTarget = ReplyTarget(replyQuoteLabel(msg), msg.me)
                            },
                            onEdit = { msg ->
                                editTarget = msg
                                input = msg.text
                                replyTarget = null
                            },
                            pinned = m.id in thread.pinnedMsgIds,
                            onTogglePin = {
                                AppState.repo.setPinnedMsg(threadId, m.id, m.id !in thread.pinnedMsgIds)
                            },
                        )
                    },
                    onSwipeReply = {
                        replyTarget = ReplyTarget(replyQuoteLabel(m), m.me)
                    },
                    onImageClick = { viewerMsg = m },
                )
                }
                }
            }
            // the E2EE banner is declared LAST — in the reversed layout it
            // renders at the very TOP of the history, as designed.
            item(key = "e2ee-banner") {
                // The design carries no banner pill; the encryption note
                // renders as quiet centred sub text (copy unchanged).
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 30.dp, end = 30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TikuLockIcon(Modifier.size(12.dp), tint = Tik.Sub)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Messages are end-to-end encrypted. Only you and ${thread.displayName} can read them.",
                            fontSize = 12.sp, color = Tik.Sub, fontFamily = Manrope,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
        // ROUND 8: IG-style jump-to-latest pill — appears when scrolled up;
        // counts messages that arrived while reading history. (Fully
        // qualified: the bare name resolves to ColumnScope.AnimatedVisibility
        // from the enclosing Column and cannot be called inside this Box.)
        androidx.compose.animation.AnimatedVisibility(
            visible = !nearBottom,
            enter = fadeIn(tween(200, easing = TikuEase)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200, easing = TikuEase)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 14.dp),
        ) {
            Row(
                Modifier.testTag("jump_to_latest")
                    .shadow(10.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)).background(Tik.S2)
                    .border(1.dp, Tik.Ln, RoundedCornerShape(20.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { runCatching { listState.animateScrollToItem(0) } }
                        unseenNew = 0
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp), tint = settings.accent)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (unseenNew > 0) "$unseenNew new" else "Latest",
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    color = Tik.Tx, fontFamily = Manrope,
                )
            }
        }
        // ROUND 8: full-screen image viewer (same window — FLAG_SECURE and
        // zero-trace behaviour are inherited, never a separate dialog window)
        viewerMsg?.let { vm ->
            ImageViewerOverlay(vm) { viewerMsg = null }
        }
        } // close messages Box
        // reply / edit bars — the design's .rbar: s1 card r16 with an
        // accent left border, expanding/collapsing over .28s (the CSS
        // animates max-height; Compose expands the layout height, the
        // closest equivalent). The last values are kept through the
        // collapse so the exit doesn't flash empty.
        val shownReply = remember { mutableStateOf<ReplyTarget?>(null) }
        LaunchedEffect(replyTarget) { if (replyTarget != null) shownReply.value = replyTarget }
        ChatContextBar(
            visible = replyTarget != null,
            title = shownReply.value?.let {
                if (it.mine) "Replying to yourself" else "Replying to ${thread.displayName}"
            } ?: "",
            quote = shownReply.value?.quote ?: "",
            accent = settings.accent,
            onClose = { replyTarget = null },
        )
        val shownEdit = remember { mutableStateOf<TikuMessage?>(null) }
        LaunchedEffect(editTarget) { if (editTarget != null) shownEdit.value = editTarget }
        ChatContextBar(
            visible = editTarget != null,
            title = "Editing message",
            quote = shownEdit.value?.text ?: "",
            accent = settings.accent,
            onClose = { editTarget = null; input = "" },
        )
        // quick chips (app feature; skinned to the design's chip language)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("👋" to "Hey!", "😂" to "Haha", "📍" to "Let’s meet", "❤️" to "Love it").forEach { (emoji, label) ->
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Tik.S2)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            AppState.repo.sendText(threadId, label, replyTarget?.quote)
                            replyTarget = null
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiArt(emoji, 16.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(label, fontSize = 13.sp, fontFamily = Manrope, color = Tik.Tx)
                    }
                }
            }
        }
        // .bar — the design's composer: margin 8/12/10, bg s2, r26,
        // padding 5/6. One bar hosts all three states: typing (camera
        // disc + field + Send text), idle (camera + field + mic/img/plus
        // tools), and recording (.recui: blink dot, timer, live wave,
        // trash, send disc).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(26.dp)).background(Tik.S2)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
        if (recording) {
            // .recui — .rd blink (bl keyframes), timer, .rw wave, trash, .cm send
            val blink = rememberInfiniteTransition(label = "recblink")
            val blinkA by blink.animateFloat(1f, 0.3f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "recblinkA")
            val wave = rememberInfiniteTransition(label = "recwave")
            val waveP by wave.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "recwaveP")
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(9.dp).alpha(blinkA).clip(CircleShape).background(Tik.RecDot))
                Spacer(Modifier.width(8.dp))
                Text(
                    "%d:%02d".format(recSecs / 60, recSecs % 60),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = Manrope, color = Tik.Tx,
                    modifier = Modifier.widthIn(min = 34.dp),
                )
                Spacer(Modifier.width(6.dp))
                // .rw — the recorder's REAL sampled amplitudes (5 samples
                // per second, last 24), breathing on the wv keyframe curve.
                val liveAmps = recorder.amps.takeLast(24)
                Row(
                    Modifier.weight(1f).height(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val bars = if (liveAmps.isEmpty()) List(24) { 6 + ((it * 7) % 12) } else liveAmps
                    bars.forEachIndexed { i, a ->
                        val phase = when (i % 3) { 0 -> waveP; 1 -> 1f - (waveP - 0.4f) * 0.5f; else -> waveP * 0.8f }
                        Box(
                            Modifier.weight(1f).height((a.coerceIn(3, 24) * phase).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(settings.accent),
                        )
                    }
                }
                TikBtn("trash", iconSize = 20.dp, tint = Tik.Sub) {
                    recorder.stop(false); recording = false
                }
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(settings.accent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        val f = recorder.stop(true)
                        recording = false
                        if (f != null) AppState.repo.sendVoice(threadId, f, recSecs.coerceAtLeast(1), recorder.normalizedAmps())
                    },
                contentAlignment = Alignment.Center,
            ) {
                TikIcon("send", Modifier.size(18.dp), tint = Color.White)
            }
        } else {
            // .cm — the camera disc (opens the photo picker, as before)
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(settings.accent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        ChatSession.expectExternalActivity = true; pickImage.launch("image/*")
                    },
                contentAlignment = Alignment.Center,
            ) {
                TikIcon("cam", Modifier.size(19.dp), tint = Color.White)
            }
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (it.isNotEmpty()) AppState.repo.setTyping(threadId, true)
                        },
                        textStyle = TextStyle(fontSize = settings.textSizeSp.sp, fontFamily = Manrope, color = Tik.Tx, lineHeight = 22.sp),
                        cursorBrush = SolidColor(settings.accent),
                        singleLine = false,
                        maxLines = 5,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = if (settings.enterToSend) androidx.compose.ui.text.input.ImeAction.Send
                            else androidx.compose.ui.text.input.ImeAction.Default,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = { if (settings.enterToSend) sendCurrent() },
                        ),
                        modifier = Modifier.weight(1f).heightIn(max = 96.dp).padding(horizontal = 4.dp, vertical = 7.dp),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text("Message…", fontSize = settings.textSizeSp.sp, color = Tik.Sub, fontFamily = Manrope)
                            inner()
                        },
                    )
            if (editTarget == null) {
                // .tools — bare mic / image / plus buttons, shown only
                // while the field is empty (.bar.tx hides them for Send).
                // The plus opens the emoji sheet (its app function).
                androidx.compose.animation.AnimatedVisibility(
                    visible = input.isEmpty(),
                    enter = fadeIn(tween(200, easing = TikEase)),
                    exit = fadeOut(tween(150, easing = TikEase)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TikBtn("mic", iconSize = 22.dp) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startRecording(recorder) { recording = true }
                            } else askMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        TikBtn("img", iconSize = 22.dp) {
                            ChatSession.expectExternalActivity = true; pickImage.launch("image/*")
                        }
                        TikBtn("plus", iconSize = 22.dp) {
                            ChatSheets.emoji { e -> input += e }
                        }
                    }
                }
            }
            // .snd — the design's Send is a TEXT button (accent, w600),
            // fading in over .2s whenever there is something to send.
            androidx.compose.animation.AnimatedVisibility(
                visible = input.isNotEmpty(),
                enter = fadeIn(tween(200, easing = TikEase)),
                exit = fadeOut(tween(150, easing = TikEase)),
            ) {
                Box(
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sendCurrent() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("Send", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = settings.accent, fontFamily = Manrope)
                }
            }
        }
        }
        }
    }
}

/** The design's .rbar — reply/edit context bar above the composer. */
@Composable
private fun ChatContextBar(
    visible: Boolean,
    title: String,
    quote: String,
    accent: Color,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(280, easing = TikEase)) + fadeIn(tween(280, easing = TikEase)),
        exit = shrinkVertically(tween(220, easing = TikEase)) + fadeOut(tween(220, easing = TikEase)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)
                .clip(RoundedCornerShape(16.dp)).background(Tik.S1)
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(2.dp).height(30.dp).background(accent))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                    fontFamily = Manrope, color = Tik.Tx)
                Text(quote, fontSize = 12.5.sp, fontFamily = Manrope, color = Tik.Sub,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            TikBtn("x", iconSize = 18.dp, tint = Tik.Sub) { onClose() }
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
        Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(Tik.RecDot))
        Spacer(Modifier.width(8.dp))
        Text("%d:%02d".format(secs / 60, secs % 60), color = Tik.Tx,
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

/** ROUND 10 (C7): the composer's send button — a round accent disc with a
 *  DRAWN up-arrow (IG form), stroked on a Canvas in the app's style rather
 *  than a stock glyph. */
@Composable
fun SendArrowButton(accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(accent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width; val h = size.height
            val sw = 2.4.dp.toPx()
            val cap = androidx.compose.ui.graphics.StrokeCap.Round
            drawLine(Color.White, Offset(w * 0.5f, h * 0.84f), Offset(w * 0.5f, h * 0.16f), strokeWidth = sw, cap = cap)
            drawLine(Color.White, Offset(w * 0.22f, h * 0.44f), Offset(w * 0.5f, h * 0.15f), strokeWidth = sw, cap = cap)
            drawLine(Color.White, Offset(w * 0.78f, h * 0.44f), Offset(w * 0.5f, h * 0.15f), strokeWidth = sw, cap = cap)
        }
    }
}

/** .ty — the design's typing row: three accent hearts breathing on the
 *  hb keyframes (scale .72 <-> 1, opacity .45 <-> 1, 1.2s ease-in-out,
 *  staggered .16s). No bubble behind them in the design. */
@Composable
fun TypingBubble() {
    val settings = tikuSettings().value
    val inf = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val p by inf.animateFloat(0f, 1f, infiniteRepeatable(
                tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                RepeatMode.Reverse,
                initialStartOffset = androidx.compose.animation.core.StartOffset(i * 160),
            ), label = "th$i")
            TikHeart(
                Modifier.size(11.dp).graphicsLayer {
                    val s = 0.72f + 0.28f * p
                    scaleX = s; scaleY = s
                    alpha = 0.45f + 0.55f * p
                },
                tint = settings.accent,
            )
        }
    }
}

// ================= call events (round 4) =================
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

/** Centred system pill for a call event, in the design's chip language
 *  (s1 bg, r12, padding 12/6, 12px sub text). Never a bubble, never
 *  side-aligned. A missed incoming call tints its glyph with the error
 *  red, as the old skin did. */
@Composable
fun CallEventPill(m: TikuMessage) {
    val iAmCaller = m.callMine
    val missedIn = m.callOutcome == "noanswer" && !iAmCaller
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Tik.S1)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TikIcon("call", Modifier.size(13.dp), tint = if (missedIn) Tik.Danger else Tik.Sub)
            Spacer(Modifier.width(6.dp))
            Text(
                "${callEventLabel(m, iAmCaller)} · ${m.time}",
                fontSize = 12.sp, color = Tik.Sub, fontFamily = Manrope,
            )
        }
    }
}

// ================= replies (round 8) =================
/** An armed reply: the quote text that will ride the next send, plus whose
 *  message it was (drives the composer bar's "Replying to …" header). */
data class ReplyTarget(val quote: String, val mine: Boolean)

/** The quote label a reply attaches — the message text, or a kind label for
 *  media. Shared by swipe-to-reply and the long-press sheet so both paths
 *  attach exactly the same quote. */
fun replyQuoteLabel(m: TikuMessage): String = when (m.kind) {
    MsgKind.IMAGE -> "Photo"
    MsgKind.VOICE -> "Voice message"
    else -> m.text
}.take(60)

// ================= image viewer (round 8) =================
/**
 * Full-screen image viewer (item 5): tap an image message → the photo fills
 * the screen (fit), pinch to zoom (1–4×, transformable — transform-only, no
 * layout work), ✕ or Back closes. Saving is the EXPLICIT "Save to device"
 * button (the same MediaSaver pipeline as the message sheet) — opening the
 * viewer never writes anything anywhere. Rendered as an overlay INSIDE the
 * chat (same activity window), so FLAG_SECURE + zero-trace behaviour are
 * inherited rather than re-implemented on a dialog window.
 */
@Composable
fun ImageViewerOverlay(m: TikuMessage, onClose: () -> Unit) {
    val bmp = m.photo
    if (bmp == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    androidx.activity.compose.BackHandler { onClose() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        if (zoom > 1f) { panX += panChange.x; panY += panChange.y } else { panX = 0f; panY = 0f }
    }
    Box(
        // .lb — the design's lightbox scrim: #000e
        Modifier.fillMaxSize().background(Color(0xE6000000)).testTag("image_viewer"),
    ) {
        androidx.compose.foundation.Image(
            bmp.asBmp(), null,
            Modifier.fillMaxSize().padding(12.dp)
                .graphicsLayer {
                    scaleX = zoom; scaleY = zoom
                    translationX = panX; translationY = panY
                }
                .transformable(transformState),
            contentScale = ContentScale.Fit,
        )
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, Modifier.size(20.dp), tint = Color.White) }
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x33FFFFFF))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (Config.PREVIEW_UI_ONLY) {
                            AppState.toast("Saved to device")
                        } else {
                            val ok = MediaSaver.saveToDevice(AppState.appCtx, m)
                            AppState.toast(if (ok) "Saved to device" else "Couldn't save")
                        }
                        onClose()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SaveAlt, null, Modifier.size(17.dp), tint = Color.White)
                Spacer(Modifier.width(7.dp))
                Text("Save to device", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, fontFamily = Manrope)
            }
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
    onSwipeReply: () -> Unit = {},
    onImageClick: () -> Unit = {},
    // ROUND 10: entrance/grouping inputs computed by the list (ChatContent)
    animateIn: Boolean = true,
    groupLast: Boolean = true,
    showAvatar: Boolean = false,
    peer: Person? = null,
) {
    // Entrance = the design's `pop` keyframes: from opacity 0,
    // translateY(8px), scale(.96), .3s on --e. Gated by [animateIn] —
    // history and pagination prepends compose already-settled; only
    // genuinely new tail arrivals play it.
    val enter = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (animateIn) enter.animateTo(1f, tween(300, easing = TikEase)) }
    // ROUND 10 (C1): double-tap heart pop — a drawn heart springs over the
    // bubble while the ❤ reaction lands through the existing plumbing.
    var heartPopKey by remember { mutableIntStateOf(0) }
    val heartScale = remember { Animatable(0f) }
    LaunchedEffect(heartPopKey) {
        if (heartPopKey > 0) {
            heartScale.snapTo(0f)
            heartScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
            delay(450)
            heartScale.animateTo(0f, tween(180))
        }
    }
    // The design's .b is a uniform 20px radius on every bubble (no tail
    // corners in the file) — grouping still reads from the avatar + gaps.
    val shape = RoundedCornerShape(20.dp)
    val textColor = if (m.me) Color.White else Tik.Tx

    // ROUND 8 swipe-to-reply (item 1): drag state lives in plain snapshot
    // state (synchronous, tracks the finger exactly); an Animatable runs
    // ONLY the release spring-back. Own messages swipe LEFT, peer messages
    // swipe RIGHT (IG directions), clamped to 84dp; 56dp arms the reply.
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 84.dp.toPx() }
    val thresholdPx = with(density) { 56.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    val springBack = remember { Animatable(0f) }
    val bubbleScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var swipeArmed by remember { mutableStateOf(false) }
    val swipeProgress = (abs(dragX) / thresholdPx).coerceIn(0f, 1f)

    // The bubble's pieces are wrapped in ONE Column: inside a reverseLayout
    // LazyColumn item, multiple top-level children stack from the item's
    // bottom edge (they rendered flipped — caption under the bubble). A
    // Column always stacks in emission order, top-down.
    Column {
    // .qx — the design's quote block: OUTSIDE the bubble, above it, with
    // a 2px accent left border; the direction line bold (tx at 80%), the
    // quoted text sub, single line. (The app stores only the quote text,
    // not its author — the direction line carries that role.)
    if (m.replyQuote != null) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
        ) {
            if (!m.me) Spacer(Modifier.width(34.dp))
            Row(Modifier.widthIn(max = 280.dp)) {
                Box(Modifier.width(2.dp).height(30.dp).background(settings.accent))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (m.me) "You replied" else "Replied to you",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = Tik.Tx.copy(alpha = 0.8f), fontFamily = Manrope,
                    )
                    Text(
                        m.replyQuote, fontSize = 12.sp, color = Tik.Sub, fontFamily = Manrope,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        // .sr — the swipe-reveal: 28px s2 circle, sub reply glyph,
        // scale .5 + p*.5, opacity p, on the edge the bubble vacates.
        Box(
            Modifier.align(if (m.me) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 4.dp).size(28.dp)
                .graphicsLayer {
                    alpha = swipeProgress
                    val s = 0.5f + 0.5f * swipeProgress
                    scaleX = s; scaleY = s
                }
                .clip(CircleShape).background(Tik.S2),
            contentAlignment = Alignment.Center,
        ) { TikIcon("reply", Modifier.size(15.dp), tint = Tik.Sub) }
    Row(
        Modifier.fillMaxWidth()
            .offset { androidx.compose.ui.unit.IntOffset(dragX.roundToInt(), 0) }
            .pointerInput(m.id) {
                detectTapGestures(
                    onDoubleTap = { heartPopKey++; onReact("❤️") },
                    onLongPress = { onLongPress() },
                    onTap = { if (m.kind == MsgKind.IMAGE && m.photo != null) onImageClick() },
                )
            }
            .pointerInput(m.id + "_swipe") {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val hit = abs(dragX) >= thresholdPx
                        val from = dragX
                        bubbleScope.launch {
                            springBack.snapTo(from)
                            springBack.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { dragX = value }
                        }
                        if (hit) onSwipeReply()
                        swipeArmed = false
                    },
                    onDragCancel = {
                        val from = dragX
                        bubbleScope.launch {
                            springBack.snapTo(from)
                            springBack.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { dragX = value }
                        }
                        swipeArmed = false
                    },
                ) { change, dragAmount ->
                    change.consume()
                    dragX = if (m.me) (dragX + dragAmount).coerceIn(-maxSwipePx, 0f)
                        else (dragX + dragAmount).coerceIn(0f, maxSwipePx)
                    val nowArmed = abs(dragX) >= thresholdPx
                    if (nowArmed && !swipeArmed) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    swipeArmed = nowArmed
                }
            },
        horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start,
    ) {
        // ROUND 10 (C3): incoming avatar — on the group's LAST message only,
        // bottom-aligned with the bubble; an empty slot on the other group
        // members keeps every bubble in the group on the same left edge.
        if (!m.me) {
            if (showAvatar && peer != null) {
                Box(Modifier.align(Alignment.Bottom)) { AvatarBox(peer, 28.dp, 14.dp, 12f) }
            } else {
                Spacer(Modifier.width(28.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        // ROUND 6 (bug 1 — bubbles not flush): the 78%-cap box previously had
        // NO contentAlignment, so the wrap-content bubble Column always sat at
        // the box's TopStart — own bubbles began at a uniform ~22% offset (a
        // centre column) instead of hugging the right edge, exactly as the
        // owner's screenshot showed. contentAlignment now pins the bubble to
        // its own edge; text bubbles wrap and sit FLUSH right (mine) / left
        // (theirs), long/media bubbles still cap at 78%. The reactions badge
        // moved to a wrap box around the bubble itself, so it anchors to the
        // bubble's real corner at any length (it used to float at the 78%
        // box's corner, away from short bubbles).
        Box(
            Modifier.fillMaxWidth(0.78f),
            contentAlignment = if (m.me) Alignment.TopEnd else Alignment.TopStart,
        ) {
            Box(
                Modifier.graphicsLayer {
                    val v = enter.value
                    alpha = v
                    val s = 0.96f + 0.04f * v
                    scaleX = s; scaleY = s
                    translationY = (1f - v) * with(density) { 8.dp.toPx() }
                },
            ) {
            // .b — padding 9/14, r20; mine = accent (couple wallpaper:
            // the design's 160deg accent -> color-mix(accent, #5A2A8A)
            // gradient), theirs = s2. Media bubbles (.b.md) pad 3px.
            val bubbleBg = when {
                !m.me -> Brush.linearGradient(listOf(Tik.S2, Tik.S2))
                settings.wallpaper == WallpaperMode.COUPLE -> Brush.linearGradient(
                    listOf(settings.accent, coupleGradientEnd(settings.accent)))
                else -> Brush.linearGradient(listOf(settings.accent, settings.accent))
            }
            Column(
                Modifier.clip(shape)
                    .background(bubbleBg)
                    .then(
                        if (m.kind == MsgKind.IMAGE && m.photo != null) Modifier.padding(3.dp)
                        else Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    ),
            ) {
                if (m.kind == MsgKind.IMAGE && m.photo != null) {
                    androidx.compose.foundation.Image(
                        m.photo.asBmp(), null,
                        // .pic — max-width 240, radius 17
                        Modifier.widthIn(max = 240.dp).fillMaxWidth().clip(RoundedCornerShape(17.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                    if (m.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
                }
                if (m.kind == MsgKind.VOICE) {
                    VoiceBubbleContent(m, textColor, playing, onPlayToggle, m.me, settings.accent)
                } else if (m.text.isNotEmpty()) {
                    Text(
                        m.text, fontSize = settings.textSizeSp.sp, color = textColor, fontFamily = Manrope,
                        lineHeight = (settings.textSizeSp * 1.38f).sp,
                        modifier = if (m.kind == MsgKind.IMAGE && m.photo != null)
                            Modifier.padding(horizontal = 11.dp, vertical = 4.dp) else Modifier,
                    )
                }
                Row(Modifier.align(Alignment.End).padding(top = 2.dp)) {
                    Text(
                        // ROUND 10 (C5): edited messages carry a muted
                        // "Edited" caption ahead of the time.
                        (if (m.editedAtMs > 0) "Edited · " else "") + when {
                            m.me && m.status == SendStatus.SENDING -> "Sending…"
                            m.me && m.status == SendStatus.FAILED -> "Not sent"
                            // ROUND 8 (item 7b): ticks are GONE. Read state
                            // renders IG-style as the small "Seen" caption
                            // under the latest own message (in ChatContent),
                            // driven by the same seenByPeer derivation.
                            else -> (if (m.starredByMe) "★ " else "") + m.time
                        },
                        fontSize = 11.sp, color = textColor.copy(alpha = 0.7f), fontFamily = Manrope,
                    )
                }
            }
            // The double-tap heart — the design's heart in the chat
            // accent, springing over the bubble (rp-family overshoot).
            if (heartScale.value > 0f) {
                Canvas(
                    Modifier.size(46.dp).align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = heartScale.value; scaleY = heartScale.value
                            alpha = heartScale.value
                        },
                ) {
                    drawPath(
                        heartPath(size.width / 2f, size.height / 2f, size.minDimension / 2.3f),
                        settings.accent,
                    )
                }
            }
            val reactions = m.reactions.values.distinct()
            if (reactions.isNotEmpty()) {
                // .rx — pill at the bubble's bottom edge (bottom -13,
                // inset 12 from the outer edge): bg s1, 2px border in the
                // WALL colour, r12, padding 0/5; pops with the rp curve.
                val rxPop = remember { Animatable(0f) }
                LaunchedEffect(reactions) {
                    rxPop.snapTo(0f)
                    rxPop.animateTo(1f, tween(380, easing = TikPopEase))
                }
                Box(
                    Modifier.align(if (m.me) Alignment.BottomEnd else Alignment.BottomStart)
                        .offset(x = if (m.me) (-12).dp else 12.dp, y = 10.dp)
                        .graphicsLayer { scaleX = rxPop.value; scaleY = rxPop.value }
                        .clip(RoundedCornerShape(12.dp)).background(Tik.S1)
                        .border(2.dp, Tik.WallColors[settings.wallpaper.ordinal], RoundedCornerShape(12.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        reactions.forEach { EmojiArt(it, 14.dp) }
                    }
                }
            }
            }
        }
    }
    } // close swipe-reveal Box
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
                color = Tik.Danger, fontFamily = Manrope,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onRetry() },
            )
        }
    }
    Spacer(Modifier.height(2.dp))
    } // close bubble Column
}

/** .b.vn — voice bubble: min-width 210, 34px play disc (mine: white 15%;
 *  theirs: accent, white glyph), 26px waveform in currentColor at .35
 *  (played = full), duration 12 w500 right. The bars are the message's
 *  REAL recorded amplitudes; playback progress isn't exposed by the
 *  player, so "played" lights the whole wave while playing. */
@Composable
fun VoiceBubbleContent(m: TikuMessage, textColor: Color, playing: Boolean, onToggle: () -> Unit, mine: Boolean, accent: Color) {
    Row(Modifier.fillMaxWidth().widthIn(min = 210.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (mine) Color(0x26FFFFFF) else accent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            TikIcon(if (playing) "pauseFill" else "playFill", Modifier.size(16.dp), tint = Color.White)
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
                        .clip(RoundedCornerShape(2.dp))
                        .background(textColor.copy(alpha = if (playing) 1f else 0.35f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "0:%02d".format(m.voiceDurSec), fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = 0.8f), fontFamily = Manrope,
            modifier = Modifier.widthIn(min = 30.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

// ================= chat sheets =================
object ChatSheets {
    private val EMOJIS = listOf("😊", "😂", "🥹", "😍", "🤍", "🔥", "✨", "🌿", "🌸", "☕", "🎧", "🎨",
        "🙌", "👀", "💫", "🍋", "☁️", "🫶", "🥳", "😌", "🤭", "💬", "📍", "🌙")
    // The design's context reactions (EM), in the file's order.
    private val REACTIONS = Tik.Reactions

    fun emoji(onPick: (String) -> Unit) {
        AppState.sheet {
            TikSheetTitle("Emoji")
            Column(Modifier.padding(horizontal = 12.dp)) {
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

    // NOTE on parameter order: onReply sits LAST so the screenshot
    // harness's trailing-lambda call `context(id, msg) { }` binds to it
    // (production passes every callback by name, so order is free there).
    fun context(
        threadId: String, m: TikuMessage,
        onEdit: (TikuMessage) -> Unit = {},
        pinned: Boolean = false,
        onTogglePin: () -> Unit = {},
        onReply: (TikuMessage) -> Unit,
    ) {
        AppState.sheet {
            // .rp — reaction bar: pill r26, padding 5/8, 24px emoji that
            // pop in staggered (.36s rp curve, +30ms per button). The
            // design floats it over a black scrim; inside the s2 sheet it
            // sits on s1 so the pill still reads.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(26.dp)).background(Tik.S1)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                REACTIONS.forEachIndexed { i, e ->
                    val pop = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        delay(i * 30L)
                        pop.animateTo(1f, tween(360, easing = TikPopEase))
                    }
                    Box(
                        Modifier.size(44.dp)
                            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                val cur = m.reactions.values.firstOrNull()
                                AppState.repo.react(threadId, m.id, if (cur == e) null else e)
                                AppState.closeSheet()
                            },
                        contentAlignment = Alignment.Center,
                    ) { EmojiArt(e, 24.dp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            SheetAction("Reply", icon = "reply", first = true) {
                onReply(m); AppState.closeSheet()
            }
            // Edit — own TEXT messages only, inside the 15-minute window
            // (the composer switches to edit mode).
            if (m.me && m.kind == MsgKind.TEXT && m.status == SendStatus.SENT &&
                m.tsMs > 0 && System.currentTimeMillis() - m.tsMs <= 15 * 60_000L) {
                SheetAction("Edit", icon = "edit") {
                    onEdit(m); AppState.closeSheet()
                }
            }
            SheetAction("Copy", icon = "copy") {
                // Copy actually copies. The text is already decrypted on
                // this device; the user explicitly chose to put it on
                // their own clipboard.
                if (m.kind == MsgKind.TEXT) {
                    val cm = AppState.appCtx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("message", m.text))
                }
                AppState.toast("Copied"); AppState.closeSheet()
            }
            SheetAction(if (m.starredByMe) "Unstar" else "Star", icon = "heart") {
                AppState.repo.toggleStar(threadId, m.id)
                AppState.toast("Star updated"); AppState.closeSheet()
            }
            // Pin/unpin this message (chat doc pinnedMsgs, max 3 — the
            // cap toast comes from the engine).
            SheetAction(if (pinned) "Unpin" else "Pin", icon = "pin") {
                onTogglePin(); AppState.closeSheet()
            }
            // Media rule (owner): NOTHING auto-saves to the gallery. This explicit tap
            // is the only exit — decrypted photo -> Pictures/Tiku, voice -> Music/Tiku.
            if (m.kind == MsgKind.IMAGE || m.kind == MsgKind.VOICE) {
                SheetAction("Save to device", icon = "img") {
                    if (Config.PREVIEW_UI_ONLY) {
                        AppState.toast("Saved to device")
                    } else {
                        val ok = MediaSaver.saveToDevice(AppState.appCtx, m)
                        AppState.toast(if (ok) "Saved to device" else "Couldn't save")
                    }
                    AppState.closeSheet()
                }
            }
            SheetAction("Delete", icon = "trash", danger = true) {
                AppState.repo.deleteForMe(threadId, m.id); AppState.closeSheet()
            }
        }
    }

    /**
     * Details sheet (identity + the detail actions). Chat Details
     * (ChatDetailsScreen, from the chat header) remains the full surface;
     * this sheet is its compact companion — and the entry the screenshot
     * harness drives for the nickname flow.
     */
    fun more(thread: ChatThread, person: Person) {
        AppState.sheet {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBox(person, 54.dp, 27.dp, 22f)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(thread.displayName, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                        fontFamily = Manrope, color = Tik.Tx,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("@" + person.username, fontSize = 13.5.sp, color = Tik.Sub, fontFamily = Manrope)
                }
            }
            Spacer(Modifier.height(8.dp))
            SheetAction("Edit nickname", icon = "edit", first = true) {
                nickname(thread)
            }
            SheetAction("Chat details", icon = "chevR") {
                AppState.closeSheet()
                AppState.go(Screen.ChatDetails(thread.id))
            }
            SheetAction("Clear chat", icon = "trash", danger = true) {
                AppState.repo.clearChat(thread.id)
                AppState.closeSheet()
                AppState.toast("Chat cleared")
            }
        }
    }

    /** Nickname editor: prefilled with the current nickname; Save writes it
     *  (blank input clears), Clear removes it and the profile name returns. */
    fun nickname(thread: ChatThread) {
        AppState.sheet {
            var nick by remember { mutableStateOf(thread.nickname ?: "") }
            TikSheetTitle("Nickname")
            Text(
                "Shown instead of ${thread.person.name} in this chat — for both of you.",
                fontSize = 13.5.sp, color = Tik.Sub, fontFamily = Manrope,
                modifier = Modifier.padding(horizontal = 18.dp, bottom = 12.dp),
            )
            TikField(nick, { nick = it.take(40) }, "Nickname")
            Spacer(Modifier.height(14.dp))
            TikPrimaryButton("Save nickname") {
                AppState.repo.setNickname(thread.id, nick)
                AppState.closeSheet()
                AppState.toast(if (nick.isBlank()) "Nickname cleared" else "Nickname saved")
            }
            if (!thread.nickname.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                SheetAction("Clear nickname", danger = true, first = true) {
                    AppState.repo.setNickname(thread.id, null)
                    AppState.closeSheet()
                    AppState.toast("Nickname cleared")
                }
            }
        }
    }
}

/** .sa — sheet action row: padding 15/22, 16px, top hairline (except the
 *  first), label left + design icon right; danger rows in the error red. */
@Composable
fun SheetAction(
    label: String,
    icon: String? = null,
    danger: Boolean = false,
    first: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier.fillMaxWidth()
            .background(if (pressed) Tik.RowActive else Color.Transparent)
            .then(
                if (first) Modifier
                else Modifier.drawTopBorderLn(),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontSize = 16.sp, fontFamily = Manrope,
            color = if (danger) Tik.Danger else Tik.Tx,
            modifier = Modifier.weight(1f),
        )
        if (icon != null) {
            TikIcon(icon, Modifier.size(21.dp), tint = if (danger) Tik.Danger else Tik.Sub)
        }
    }
}

private fun Modifier.drawTopBorderLn(): Modifier = this.then(
    androidx.compose.ui.draw.drawWithContent {
        drawContent()
        drawLine(
            Tik.Ln,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
        )
    },
)
