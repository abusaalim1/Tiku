package com.tiku.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ROUND 14 — the Tik shell in the owner's TikuRedesign model: pure-black
 * root, the panes as the Haze source, AnimatedContent with the file's
 * screenSpec depth transitions over destinations (0 Messages · 1 Calls ·
 * 2 Watch · 3 Setting · 7 Requests), the sliding-lens NavPill (pane tabs
 * only), the CallPill above it for a minimized live call, and the note
 * sheet overlay. Chat (9) and Info (8) stay Screen-routed overlays in
 * TikuRoot (Screen.Chat / Screen.ChatDetails), per the round-14 split.
 * The build-47 glass panes (ChatsPane/CallsPane/MePane + glass nav) are
 * PERMANENTLY deleted. All data is the real engine's.
 */

/** The preview build's my-uid key for notes (the real engine's is the
 *  Firebase uid). Notes are keyed by owner uid in AppState.notes. */
fun myUidKey(): String = if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid()

/** Last-message preview, build-47 rules: media kinds by label, call events
 *  by their pill label, never raw call text. ROUND 18: a delete-for-everyone
 *  tombstone previews as itself, and the preview is NEVER blank while a
 *  message exists. ROUND 19: an undecryptable last message (retired-key
 *  ciphertext, flagged on the preview path's metadata stub) never blanks
 *  the preview. ROUND 50: in the LIST it reads as the neutral "Message"
 *  (the owner's order — the list is a glance, not a diagnosis); the chat
 *  itself keeps the honest "can't be opened on this phone" line. */
fun threadPreview(th: ChatThread): String {
    // ROUND 21 — IG LIST RULE (owner's Instagram reference): with
    // several unread, the preview line itself becomes the summary —
    // more than 5 unread reads "4+ new messages", 2–5 reads
    // "N new messages". A single unread (and any read thread) keeps
    // the ordinary last-message preview below.
    if (th.unread > 5) return "4+ new messages"
    if (th.unread >= 2) return "${th.unread} new messages"
    val last = th.messages.lastOrNull { !it.deletedForMe } ?: return ""
    if (last.deletedForEveryone) return "This message was deleted"
    if (last.undecryptable) return "Message"
    // ROUND 22: a gift-wrapped message previews as a gift — its text
    // stays sealed on EVERY surface until the chat is opened and the
    // gift is tapped open there.
    if (last.fx == "gift") return "Gift message"
    return when (last.kind) {
        MsgKind.VOICE -> "Voice message"
        MsgKind.IMAGE -> "Photo"
        MsgKind.VIDEO -> "Video"
        MsgKind.AUDIO -> "Audio file"
        MsgKind.CALL -> callEventLabel(last, last.callMine)
        else -> last.text.ifBlank { "Message" }
    }
}

internal sealed interface NoteTarget {
    data object Me : NoteTarget
    data class Peer(val thread: ChatThread) : NoteTarget
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    // QUICK EXIT (lock rule): Back at ChatRoot home re-locks instantly to the facade.
    BackHandler { ChatSession.quickExit(ctx) }
    val haze = LocalHazeState.current
    val dest = AppState.tikDestination
    val screen = AppState.screen
    val threads by AppState.threads.collectAsState()
    val blocked by AppState.blockedUids.collectAsState()
    var noteTarget by remember { mutableStateOf<NoteTarget?>(null) }

    // minimized live call -> CallPill (the file's model; replaces the
    // build-47 FloatingCallWindow, which TikuRoot no longer composes).
    val callState by CallController.state.collectAsState()
    val pip by CallController.pipMinimized.collectAsState()
    val connected = callState as? CallUiState.Connected
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connected?.callId) {
        if (connected == null) return@LaunchedEffect
        while (true) { delay(500); nowMs = System.currentTimeMillis() }
    }
    val callPerson: Person?
    val callVideo: Boolean
    val callSecs: Int
    when (val s = callState) {
        is CallUiState.Connected -> {
            callPerson = s.person; callVideo = s.video
            callSecs = (((nowMs - s.atMs) / 1000).coerceAtLeast(0)).toInt()
        }
        is CallUiState.Outgoing -> { callPerson = s.person; callVideo = s.video; callSecs = 0 }
        else -> { callPerson = null; callVideo = false; callSecs = 0 }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                .then(if (haze != null) Modifier.hazeSource(haze) else Modifier),
        ) {
            AnimatedContent(
                targetState = dest,
                transitionSpec = RdScreenSpec,
                label = "tik-screen",
            ) { s ->
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    when (s) {
                        0 -> MessagesScreen(
                            onNote = { noteTarget = it },
                            onRequests = { AppState.goTik(7) },
                        )
                        1 -> CallsScreen()
                        2 -> WatchScreen()
                        3 -> SettingsScreen()
                        7 -> RequestsScreen(onBack = { AppState.goTik(0) })
                        else -> MessagesScreen(
                            onNote = { noteTarget = it },
                            onRequests = { AppState.goTik(7) },
                        )
                    }
                }
            }
        }

        // ROUND 15: WatchHost + WatchFullscreenLayer moved OUT of this
        // shell to TikuRoot (MainActivity) — the floating mini player
        // must float ABOVE the chat overlay, which is a TikuRoot
        // sibling of HomeScreen, not a child of it. The host still owns
        // the pane for the whole Tik session from there; see Watch.kt.

        // NavPill (+ CallPill) — pane tabs only, never over chat/info/requests.
        AnimatedVisibility(
            visible = screen is Screen.Home && dest in 0..3,
            enter = slideInVertically(tween(480, easing = RdEase)) { it } + fadeIn(),
            exit = slideOutVertically(tween(320, easing = RdEase)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(
                    visible = pip && callPerson != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val p = callPerson
                    if (p != null) {
                        CallPill(
                            haze, p, if (callVideo) "Video call" else "Voice call", callSecs,
                            onTap = { CallController.restoreCall() },
                            onEnd = { CallController.hangup() },
                        )
                    }
                }
                NavPill(
                    haze, dest,
                    hasUnread = threads.any {
                        it.unread > 0 && !it.isRequest && !it.hiddenForMe && !it.muted &&
                            it.person.uid !in blocked
                    },
                ) { AppState.goTik(it) }
            }
        }

        // Note sheet overlay (the file's bottom glass sheet over a backdrop).
        AnimatedVisibility(
            visible = noteTarget != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            val nt = noteTarget
            Box(
                Modifier.fillMaxSize().rdBackdrop(haze)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { noteTarget = null },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (nt != null) Box(
                    Modifier.imePadding().animateEnterExit(
                        enter = slideInVertically(spring(0.85f, 360f)) { it } + fadeIn(),
                        exit = slideOutVertically(tween(220, easing = RdEase)) { it } + fadeOut(),
                    ),
                ) {
                    val notes by AppState.notes.collectAsState()
                    when (nt) {
                        is NoteTarget.Me -> NoteSheet(
                            haze, mine = true, headerName = "", note = notes[myUidKey()]?.text ?: "",
                            hearted = false, headerAvatar = {},
                            onShare = { t ->
                                if (t.isBlank()) AppState.toast("Write something first")
                                else { AppState.repo.setMyNote(t); AppState.toast("Note shared") }
                                noteTarget = null
                            },
                            onDelete = { AppState.repo.deleteMyNote(); AppState.toast("Note deleted"); noteTarget = null },
                            onHeart = {},
                            onReply = {},
                        )
                        is NoteTarget.Peer -> {
                            val person = nt.thread.person
                            val note = notes[person.uid]
                            NoteSheet(
                                haze, mine = false, headerName = nt.thread.displayName,
                                note = note?.text ?: "",
                                hearted = myUidKey() in (note?.hearts ?: emptySet()),
                                headerAvatar = { Av(person, 40) },
                                onShare = {},
                                onDelete = {},
                                onHeart = { AppState.repo.toggleNoteHeart(person.uid) },
                                onReply = { t ->
                                    if (t.isNotBlank()) {
                                        if (person.uid in blocked) {
                                            AppState.toast("Unblock to message")
                                        } else {
                                            AppState.repo.sendNoteReply(nt.thread.id, t.trim(), note?.text ?: "")
                                            AppState.toast("Reply sent")
                                        }
                                    }
                                    noteTarget = null
                                },
                            )
                        }
                    }
                }
            }
        }

        // (The fullscreen YouTube layer renders in TikuRoot beside
        // WatchHost — ROUND 15 hoist, see above. Empty unless fullscreen.)
    }
}

// ================= Messages =================
@Composable
internal fun MessagesScreen(onNote: (NoteTarget) -> Unit, onRequests: () -> Unit) {
    val threads by AppState.threads.collectAsState()
    val notes by AppState.notes.collectAsState()
    val blocked by AppState.blockedUids.collectAsState()
    val visible = threads.filter {
        !it.isRequest && !it.hiddenForMe && it.person.uid !in blocked
    }.sortedByDescending { it.pinned }
    val reqCount = threads.count { it.isRequest && !it.hiddenForMe && it.person.uid !in blocked }
    val now = System.currentTimeMillis()
    val notedThreads = visible.filter { th ->
        val n = notes[th.person.uid]
        n != null && n.text.isNotEmpty() && now - n.atMs < 24L * 3600 * 1000
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 210.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Title("Messages") }
                // the single sanctioned addition to the file's layout: the
                // REAL compose entry (NewChatSheet) as one circular button.
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(CardC)
                        .border(1.dp, Color.White.copy(0.1f), CircleShape)
                        .press { NewChatSheet.open() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
            }
        }
        item {
            LazyRow(
                Modifier.padding(top = 18.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    val myNote = notes[myUidKey()]
                    NoteItem(
                        "Your note", myNote?.text ?: "Share a thought", myNote == null,
                        heart = false, idx = 0,
                        onClick = { onNote(NoteTarget.Me) },
                    ) { AvMe(58) }
                }
                itemsIndexed(notedThreads) { idx, th ->
                    val n = notes[th.person.uid]
                    NoteItem(
                        th.displayName, n?.text ?: "", placeholder = false,
                        heart = myUidKey() in (n?.hearts ?: emptySet()), idx = idx + 1,
                        onClick = { onNote(NoteTarget.Peer(th)) },
                    ) { Av(th.person, 58) }
                }
            }
        }
        item {
            // ROUND 14 (owner order, 2026-11-03): the couple-memories web
            // app, integrated as a Tik surface (Moments.kt) — a fixed
            // feature entry (not data-gated) directly under the notes
            // row, in the file's CardRow language.
            CardRow({ AppState.go(Screen.Moments) }, idx = 2) {
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(Rose.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { TikIcon("heartFill", Modifier.size(24.dp), tint = Rose) }
                TwoLine(
                    "Memories", "Your private place, together",
                    Modifier.weight(1f).padding(start = 14.dp),
                )
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = Sub, modifier = Modifier.size(22.dp))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Messages", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontFamily = InterTight)
                Text(
                    if (reqCount > 0) "Requests · $reqCount" else "Requests",
                    color = Rose, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.press(onRequests).padding(6.dp), fontFamily = PoppinsFamily,)
            }
        }
        if (visible.isEmpty() && !Config.PREVIEW_UI_ONLY) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Start a chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = InterTight)
                    Text("Search a username to start a chat", color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily)
                }
            }
        }
        itemsIndexed(visible, key = { _, th -> th.id }) { idx, th ->
            ThreadRow(th, idx, Modifier.animateItem())
        }
    }
}

/** One Messages row (the file's CardRow shape) with the real thread data;
 *  long-press opens the thread actions sheet (Open / Clear / Delete). */
@Composable
private fun ThreadRow(th: ChatThread, idx: Int, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) 0.95f else 1f, spring(0.6f, 500f), label = "press")
    Row(
        modifier.fillMaxWidth().padding(bottom = 10.dp).rise(idx + 3)
            .clip(RoundedCornerShape(24.dp)).background(CardC).scale(s)
            .combinedClickable(
                interactionSource = src, indication = null,
                onClick = {
                    AppState.repo.openThread(th.id)
                    AppState.go(Screen.Chat(th.id))
                },
                onLongClick = { ThreadActionsSheet.open(th) },
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Av(th.person, 50)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(th.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = InterTight)
            Text(threadPreview(th), fontFamily = rememberChatFontFamily(), color = Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(th.time, color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
            if (th.unread > 0 && !th.muted) {
                Box(Modifier.padding(top = 8.dp).size(9.dp).clip(CircleShape).background(Rose))
            }
        }
    }
}

/** Long-press thread actions (re-skinned sheet): Open / Clear chat (local)
 *  / Delete (local hide). Minimal and real, per spec. */
object ThreadActionsSheet {
    fun open(th: ChatThread) {
        AppState.sheet {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Av(th.person, 44)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(th.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight)
                    Text("@" + th.person.username, color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily)
                }
            }
            MenuItem("Open", onClick = {
                AppState.closeSheet()
                AppState.repo.openThread(th.id)
                AppState.go(Screen.Chat(th.id))
            }) {}
            Divider1()
            MenuItem("Clear chat", onClick = {
                AppState.repo.clearChat(th.id)
                AppState.closeSheet()
                AppState.toast("Chat cleared")
            }) {}
            Divider1()
            MenuItem("Delete", danger = true, onClick = {
                AppState.repo.hideThread(th.id)
                AppState.closeSheet()
                AppState.toast("Chat deleted")
            }) {}
        }
    }
}

// ================= Requests =================
@Composable
fun RequestsScreen(onBack: () -> Unit) {
    val threads by AppState.threads.collectAsState()
    val blocked by AppState.blockedUids.collectAsState()
    val reqs = threads.filter { it.isRequest && !it.hiddenForMe && it.person.uid !in blocked }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
    ) {
        item {
            BackBtn(onBack); Spacer(Modifier.height(14.dp)); Title("Requests")
            Text(
                "People you don’t follow can’t message you until you accept.",
                color = Sub, fontSize = 14.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 18.dp), fontFamily = PoppinsFamily,)
        }
        if (reqs.isEmpty()) item { Text("No message requests", color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily) }
        itemsIndexed(reqs, key = { _, r -> r.id }) { idx, th ->
            var out by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            fun go(f: () -> Unit) { out = true; scope.launch { delay(340); f() } }
            AnimatedVisibility(
                visible = !out,
                exit = fadeOut(tween(260)) +
                    androidx.compose.animation.slideOutHorizontally(tween(320, easing = RdEase)) { it / 2 } +
                    shrinkVertically(tween(320, easing = RdEase)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).rise(idx)
                        .clip(RoundedCornerShape(24.dp)).background(CardC).padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Av(th.person, 56)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(th.displayName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = InterTight)
                            Text(threadPreview(th), fontFamily = rememberChatFontFamily(), color = Sub, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(th.time, color = Sub, fontSize = 13.sp, fontFamily = PoppinsFamily)
                    }
                    Row(
                        Modifier.padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Pill("Delete", Modifier.weight(1f), false) { go { AppState.repo.hideThread(th.id) } }
                        Pill("Block", Modifier.weight(1f), false) { go { AppState.repo.blockUser(th.person.uid) } }
                        Pill("Accept", Modifier.weight(1f), true) { go { AppState.repo.acceptThread(th.id) } }
                    }
                }
            }
        }
    }
}

// ================= Calls =================
@Composable
fun CallsScreen() {
    val threads by AppState.threads.collectAsState()
    val blocked by AppState.blockedUids.collectAsState()
    val events = remember(threads) {
        threads.filter { !it.hiddenForMe && it.person.uid !in blocked }
            // ROUND 19: an undecryptable call stub carries no call
            // payload (caller/outcome never decrypted) — it must not
            // surface in the Calls tab as an empty/unknown event.
            .flatMap { th -> th.messages.filter { it.kind == MsgKind.CALL && !it.undecryptable }.map { th to it } }
            .sortedByDescending { it.second.tsMs }
    }
    val startCall = rememberCallStarter()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 210.dp),
    ) {
        item { Title("Call"); Section("Recent") }
        if (events.isEmpty()) {
            item {
                Text(
                    "No calls yet — when you make, miss or decline a call, a note of it appears here.",
                    color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp), fontFamily = PoppinsFamily,)
            }
        }
        itemsIndexed(events, key = { _, (th, m) -> th.id + "_" + m.id }) { idx, (th, m) ->
            val missed = m.callOutcome == "noanswer" && !m.callMine
            val kind = if (m.callVideo) "Video" else "Voice"
            val meta = buildString {
                append(kind); append(" · ")
                append(
                    when (m.callOutcome) {
                        "noanswer" -> if (m.callMine) "No answer" else "Missed"
                        "declined" -> "Declined"
                        else -> "Ended"
                    },
                )
                if (m.callOutcome == "ended" && m.callDurationSec > 0) {
                    append(" · ${m.callDurationSec / 60}:${(m.callDurationSec % 60).toString().padStart(2, '0')}")
                }
                append(" · ${m.time}")
            }
            CardRow({ startCall(th.id, m.callVideo) }, idx = idx) {
                Av(th.person, 50)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(th.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = InterTight)
                    Text(meta, color = if (missed) Danger else Sub, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = PoppinsFamily)
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (m.callVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                        null, tint = Color.White, modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ================= Watch =================
/** The redesign chrome (black + Title) for the REAL YouTube pane. The
 *  pane is NOT composed here: WatchHost (Watch.kt) owns it at the
 *  TikuRoot level (ROUND 15 hoist — it must float above the chat
 *  overlay) so a playing video can shrink into the floating mini
 *  player on other tabs and over chats. This screen renders
 *  only the header; the empty weight-1 box below reserves EXACTLY the
 *  area the host's EXPANDED mode covers — header height =
 *  WatchChromeTopPadding (24) + WatchChromeButtonSize (46) =
 *  WatchChromeHeaderHeight (70), the host's expanded top offset. The
 *  two sides stay in sync through those shared constants in Watch.kt. */
@Composable
fun WatchScreen() {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = WatchChromeTopPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { Title("Watch") }
            Box(
                Modifier.size(WatchChromeButtonSize).clip(CircleShape).background(CardC)
                    .border(1.dp, Color.White.copy(0.1f), CircleShape)
                    .press { WatchControl.goHome() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Home, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
        }
        // Reserved for WatchHost's EXPANDED rect — see the doc above.
        Box(Modifier.weight(1f))
    }
}

// ================= Settings =================
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.state.collectAsState()
    val blockedUids by AppState.blockedUids.collectAsState()
    val blockedPeople by AppState.blockedPeople.collectAsState()
    var page by remember { mutableStateOf("") }
    // REAL measured Coil disk-cache size for the Clear cache row.
    var cacheBytes by remember { mutableStateOf(-1L) }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) {
            runCatching { coil.Coil.imageLoader(ctx.applicationContext).diskCache?.size ?: 0L }
                .getOrDefault(0L)
        }
    }
    val cacheLabel = when {
        cacheBytes < 0 -> "…"
        cacheBytes >= 1_000_000 -> "Frees up %.1f MB".format(cacheBytes / 1_000_000.0)
        cacheBytes >= 1_000 -> "Frees up %.0f KB".format(cacheBytes / 1_000.0)
        else -> "Empty"
    }
    BackHandler(enabled = page.isNotEmpty()) { page = "" }
    AnimatedContent(
        targetState = page, transitionSpec = RdSubSpec, label = "settings-page",
    ) { pv ->
        when (pv) {
            "edit" -> EditProfilePage { page = "" }
            "blocked" -> SubPage("Blocked accounts", { page = "" }) {
                if (blockedPeople.isEmpty()) {
                    item { Text("No blocked accounts", color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily) }
                }
                items(blockedPeople) { p ->
                    CardRow({
                        AppState.repo.unblockUser(p.uid)
                        AppState.toast("Unblocked @${p.username}")
                    }) {
                        Av(p, 48)
                        TwoLine(p.name, "@${p.username}", Modifier.weight(1f).padding(start = 14.dp))
                        Text("Unblock", color = Rose, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp), fontFamily = PoppinsFamily)
                    }
                }
            }
            // ROUND 30: the owner-only deleted-messages log page. The row
            // that leads here exists only in the Owner section; the page
            // itself also re-checks isOwner before listing anything.
            "deleted" -> DeletedMessagesPage { page = "" }
            // ROUND 33: the owner-only hide-recovery page — same deal:
            // the row exists only in the Owner section and the page
            // re-checks isOwner before listing anything.
            "hidden" -> HiddenMessagesPage { page = "" }
            "devices" -> SubPage("Linked devices", { page = "" }) {
                item {
                    CardRow({}) {
                        TwoLine("This phone", "Active now", Modifier.weight(1f))
                    }
                }
            }
            else -> SettingsMain(
                settings = settings, cacheLabel = cacheLabel,
                blockedCount = blockedUids.size,
                onEdit = { page = "edit" },
                onBlocked = { page = "blocked" },
                onDevices = { page = "devices" },
                onChangePassword = { ChangePasswordSheet.open(ctx, scope) },
                onClearCache = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val loader = coil.Coil.imageLoader(ctx.applicationContext)
                                loader.memoryCache?.clear()
                                loader.diskCache?.clear()
                                android.webkit.WebStorage.getInstance().deleteAllData()
                            }
                        }
                        cacheBytes = 0L
                        AppState.toast("Cache cleared")
                    }
                },
                onLogout = {
                    // ROUND 15: the notify service dies with the session —
                    // signed out means the owner condition is broken.
                    NotifyService.stop(ctx)
                    if (Config.PREVIEW_UI_ONLY) AppState.go(Screen.Welcome)
                    else FirebaseEngine.signOut(ctx)
                },
                onDeleted = { page = "deleted" },
                onHidden = { page = "hidden" },
            )
        }
    }
}

@Composable
private fun SettingsMain(
    settings: TikuUiSettings,
    cacheLabel: String,
    blockedCount: Int,
    onEdit: () -> Unit,
    onBlocked: () -> Unit,
    onDevices: () -> Unit,
    onChangePassword: () -> Unit,
    onClearCache: () -> Unit,
    onLogout: () -> Unit,
    /** ROUND 30: owner-only "Deleted messages" log navigation. */
    onDeleted: () -> Unit,
    /** ROUND 33: owner-only "Hidden by me" recovery navigation. */
    onHidden: () -> Unit,
) {
    val ctx = LocalContext.current
    // Launched from the Notifications row's enable path (ROUND 18: that
    // row is for every user now — same pattern as the Owner section's
    // toggle, which keeps its own launcher).
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val myName = FirebaseEngine.myPerson.name.takeIf { it.isNotBlank() && it != "You" }
        ?: AppState.myUsername.ifEmpty { "You" }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 210.dp),
    ) {
        item { Title("Settings") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(24.dp))
                    .background(CardC).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvMe(58)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(myName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = InterTight)
                    Text(
                        "@${AppState.myUsername.ifEmpty { "you" }} · Encrypted",
                        color = Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = PoppinsFamily,)
                }
                Box(
                    Modifier.clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Rose, Color(0xFFFF7AA2))))
                        .press(onEdit).padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("Edit profile", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily) }
            }
        }
        item { Section("Appearance") }
        item {
            CardRow({
                AppState.toast(
                    if (!settings.darkMode) "Light mode coming soon" else "Dark mode is Tiku's only look",
                )
            }, idx = 1) {
                listOf(false to "Light", true to "Dark").forEach { (dark, label) ->
                    val on = settings.darkMode == dark
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(CircleShape)
                            .background(if (on) Color.White else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) Color.Black else Sub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily) }
                }
            }
        }
        item {
            CardRow({}, idx = 2) {
                Column(Modifier.weight(1f)) {
                    TwoLine("Text size", "Adjust chat text size", Modifier.fillMaxWidth())
                    Slider(
                        value = settings.textSizeSp, onValueChange = { SettingsStore.setTextSize(it) },
                        valueRange = 12f..20f,
                        colors = SliderDefaults.colors(
                            thumbColor = Rose, activeTrackColor = Rose, inactiveTrackColor = Color.White.copy(0.15f),
                        ),
                    )
                }
            }
        }
        item { Section("Privacy") }
        item {
            // ROUND 15: this row reads/writes OwnerPrefs — the SINGLE
            // source of truth the password gate itself reads (OwnerGate).
            // It previously wrote only SettingsStore's "al" key, which the
            // gate never consults, so flipping this switch changed
            // nothing at the gate. "al" is still mirrored so the settings
            // state flow can't drift.
            // ROUND 18 (owner order): the row is functional for EVERY
            // signed-in user, not just the owner — OwnerPrefs is a
            // device-local prefs store despite the name, the defaults are
            // unchanged (lock ON), and the gate honours it per device.
            // (Non-owners used to get a dummy row stuck on with an
            // "Always on for safety" toast.)
            ToggleRow(
                "App lock", "Ask for password on open", OwnerPrefs.appLockOn,
            ) {
                val on = !OwnerPrefs.appLockOn
                OwnerPrefs.setAppLock(on)
                SettingsStore.setToggle("al", on)
                NotifyGate.sync(ctx)
                AppState.toast(if (on) "App lock on" else "App lock off — the password won't be asked on open")
            }
        }
        item {
            // ROUND 15 (owner order): the Notifications toggle lives
            // HERE, directly under App lock — the Owner section's copy at
            // the bottom of Settings keeps working off the same
            // OwnerPrefs.notifyOn (one state, two surfaces).
            // ROUND 18 (owner order): visible and functional for EVERY
            // signed-in user — same OwnerPrefs binding, same default
            // (OFF), same POST_NOTIFICATIONS request on enable.
            ToggleRow(
                "Notifications",
                "A quiet “New message” alert on this phone, even when the app isn't open — while your journal is unlocked",
                OwnerPrefs.notifyOn,
            ) {
                val on = !OwnerPrefs.notifyOn
                OwnerPrefs.setNotify(on)
                if (on) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    NotifyGate.sync(ctx)
                    AppState.toast("Notifications on — only on this phone, only for you")
                } else {
                    NotifyGate.sync(ctx)
                    OwnerNotify.cancelAll(ctx)
                    AppState.toast("Notifications off")
                }
            }
        }
        item {
            // ROUND 49 (owner's device complaint: "notification turant
            // aata hi nahi hai" on a good network): while Notifications
            // are ON, show this phone's battery-optimization status
            // right under the toggle. Without the exemption Doze defers
            // the alert service's network, so alerts land late however
            // good the connection is. Settings row ONLY — no popup, no
            // nagging: not exempt → the row + its button fire the
            // system's own per-app exemption dialog; exempt → a calm
            // confirmation line. The state re-reads on every ON_RESUME
            // so returning from the system dialog updates the row.
            if (OwnerPrefs.notifyOn) {
                val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
                var batteryExempt by remember {
                    mutableStateOf(pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false)
                }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            batteryExempt =
                                pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: batteryExempt
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                val askExemption: () -> Unit = {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + ctx.packageName),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        // OEM without the per-app dialog: the system
                        // battery-optimization list is the fallback.
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    Unit
                }
                if (batteryExempt) {
                    CardRow({}, idx = 0) {
                        TwoLine(
                            "Instant notifications",
                            "Battery restriction removed ✓",
                            Modifier.weight(1f),
                        )
                    }
                } else {
                    CardRow(askExemption, idx = 0) {
                        TwoLine(
                            "Instant notifications",
                            "Remove the battery restriction for instant notifications — with it on, message alerts can arrive late",
                            Modifier.weight(1f),
                        )
                        Pill("Remove", Modifier.padding(start = 10.dp), true, askExemption)
                    }
                }
            }
        }
        item {
            ToggleRow("Read receipts", "Show “Seen” when read", settings.readReceipts) {
                SettingsStore.setToggle("rr", !settings.readReceipts)
            }
        }
        item {
            ToggleRow("Last seen", "Let contacts see when you were online", settings.lastSeen) {
                SettingsStore.setToggle("ls", !settings.lastSeen)
            }
        }
        item {
            ToggleRow("Block screenshots", "Block screenshots and app-switcher previews of chats", settings.blockScreenshots) {
                SettingsStore.setToggle("bs", !settings.blockScreenshots)
            }
        }
        item {
            ToggleRow("Hide typing status", "Don’t show “typing…”", settings.hideTyping) {
                SettingsStore.setToggle("ht", !settings.hideTyping)
            }
        }
        item { NavRow("Blocked accounts", "$blockedCount accounts", onClick = onBlocked) }
        item { Section("Data and storage") }
        item { NavRow("Clear cache", cacheLabel, onClick = onClearCache) }
        item { Section("Account") }
        item { NavRow("Linked devices", "Manage where Tiku is open", onClick = onDevices) }
        item { NavRow("Change password", "Update your lock password", onClick = onChangePassword) }
        item { NavRow("Log out", danger = true, chevron = false, onClick = onLogout) }
        item { Section("Additional settings") }
        item {
            ToggleRow("Camera on for video calls", "Front camera starts enabled on outgoing calls", settings.cameraDefaultOn) {
                SettingsStore.setToggle("vcd", !settings.cameraDefaultOn)
            }
        }
        item {
            ToggleRow("Speaker on video calls", "Audio through the speaker on video calls", settings.speakerDefaultOn) {
                SettingsStore.setToggle("vsp", !settings.speakerDefaultOn)
            }
        }
        item {
            // ROUND 18 (owner order): in-app updates — an "App update" row
            // for EVERY user, in Additional settings. The sub shows this
            // install's CI build number (stamped into assets at build
            // time); 0 = a pre-system install, which shows the check
            // prompt instead. Tap = a manual check that ignores the 6h
            // throttle: newer → the download page opens in the browser;
            // up to date / offline → a toast says which.
            val installed = remember { UpdateCheck.installedBuild(ctx) }
            NavRow(
                "App update",
                if (installed > 0) "Version $installed" else "Check for the latest version",
            ) {
                UpdateCheck.checkNow(ctx) { result ->
                    when (result) {
                        is UpdateCheck.ManualResult.Newer ->
                            UpdateCheck.openDownloadPage(ctx, result.url)
                        UpdateCheck.ManualResult.UpToDate ->
                            AppState.toast("You're on the latest version")
                        UpdateCheck.ManualResult.Failed ->
                            AppState.toast("Couldn't check — check your connection")
                    }
                }
            }
        }
        if (OwnerState.isOwner) {
            item { OwnerSection(onOpenDeleted = onDeleted, onOpenHidden = onHidden) }
        }
    }
}

/** Edit profile subpage (the file's shape; every control is the app's REAL
 *  flow: photo presets/gallery, display name, validated username). */
@Composable
private fun EditProfilePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember {
        mutableStateOf(
            FirebaseEngine.myPerson.name.takeIf { it.isNotBlank() && it != "You" }
                ?: AppState.myUsername,
        )
    }
    var username by remember { mutableStateOf(AppState.myUsername) }
    var avail by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(username) {
        val u = username.trim()
        avail = null
        if (u == AppState.myUsername) return@LaunchedEffect
        if (u.length < 3) return@LaunchedEffect
        checking = true
        delay(450)
        avail = runCatching { !FirebaseEngine.usernameTaken(u) }.getOrNull()
        checking = false
    }
    val un = username.trim()
    val unOk = un == AppState.myUsername || (un.length >= 3 && avail == true)
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val res = runCatching {
                val bytes = ctx.contentResolver.openInputStream(uri)!!.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                FirebaseEngine.updateProfilePhoto(bmp)
            }
            if (res.isSuccess) AppState.toast("Photo updated")
            else AppState.toast("Couldn't update your photo — try again")
        }
    }
    SubPage("Edit profile", onBack) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                AvMe(84)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Change photo", color = Rose, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.press { pickPhoto.launch("image/*") }.padding(6.dp), fontFamily = PoppinsFamily,)
            }
        }
        item {
            PresetAvatarPicker(dark = true) { bmp ->
                scope.launch {
                    val r = runCatching { FirebaseEngine.updateProfilePhoto(bmp) }
                    if (r.isSuccess) AppState.toast("Photo updated")
                    else AppState.toast("Couldn't update your photo — try again")
                }
            }
        }
        item { Section("Name") }
        item { Field(name, { name = it }, "Display name") }
        item { Section("Username") }
        item {
            Field(username, { v ->
                username = v.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20)
            }, "username")
        }
        item {
            val hint = when {
                checking -> "Checking…"
                un == AppState.myUsername -> "This is your current username"
                un.length < 3 -> "At least 3 letters"
                avail == true -> "Available ✓"
                avail == false -> "Already taken"
                else -> "Letters, numbers, . and _"
            }
            Text(hint, color = Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp), fontFamily = PoppinsFamily)
        }
        item {
            Pill("Save", Modifier.fillMaxWidth(), true) {
                if (saving) return@Pill
                saving = true
                scope.launch {
                    var ok = true
                    if (un != AppState.myUsername) {
                        val r = FirebaseEngine.changeUsername(un)
                        ok = r.isSuccess
                        if (!ok) AppState.toast(r.exceptionOrNull()?.message ?: "Couldn't change username")
                    }
                    if (ok && name.trim().isNotEmpty() && name.trim() != FirebaseEngine.myPerson.name) {
                        val r = FirebaseEngine.updateDisplayName(name)
                        ok = r.isSuccess
                        if (!ok) AppState.toast(r.exceptionOrNull()?.message ?: "Couldn't save name")
                    }
                    saving = false
                    if (ok) { AppState.toast("Profile saved"); onBack() }
                }
            }
        }
    }
}

/**
 * The OWNER section (the build-47 OwnerSection, transplanted with identical
 * behaviour into the redesign rows): composed ONLY for the owner account —
 * the rest of the app never references notifications or an optional lock.
 */
@Composable
fun OwnerSection(
    onOpenDeleted: () -> Unit = {},
    /** ROUND 33: opens the "Hidden by me" recovery page. */
    onOpenHidden: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    Section("Owner")
    ToggleRow(
        "Notifications",
        "A quiet “New message” alert on this phone when something arrives — even when the app isn't open, while your journal is unlocked — only for your account",
        OwnerPrefs.notifyOn,
    ) {
        val on = !OwnerPrefs.notifyOn
        OwnerPrefs.setNotify(on)
        if (on) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotifyGate.sync(ctx)
            AppState.toast("Notifications on — only on this phone, only for you")
        } else {
            NotifyGate.sync(ctx)
            OwnerNotify.cancelAll(ctx)
            AppState.toast("Notifications off")
        }
    }
    ToggleRow(
        "App lock",
        "Ask for your password every time you open your journal on this phone",
        OwnerPrefs.appLockOn,
    ) {
        val on = !OwnerPrefs.appLockOn
        OwnerPrefs.setAppLock(on)
        SettingsStore.setToggle("al", on)
        NotifyGate.sync(ctx)
        AppState.toast(if (on) "App lock on" else "App lock off — the password won't be asked on open")
    }
    // ROUND 31 — the view-once override: with this on, photos and
    // videos OTHERS sent as view once / view twice never run out of
    // views on this phone (opens don't consume, the bubble never
    // expires, Save stays available). Owner-section only, and the
    // stored choice is per account (VoStore's per-uid prefs) — every
    // other account keeps the normal limits and never sees this row.
    var voUnlimited by remember { mutableStateOf(VoStore.unlimited(ctx)) }
    ToggleRow(
        "View once media — unlimited for me",
        "Photos and videos sent to you as view once or twice never run out of views on this phone — only for your account",
        voUnlimited,
    ) {
        val on = !voUnlimited
        VoStore.setUnlimited(ctx, on)
        voUnlimited = on
        AppState.toast(
            if (on) "View once media is unlimited for you"
            else "View once limits apply to you again",
        )
    }
    // ROUND 33 — GHOST READ: while this is on, opening a chat marks
    // nothing — no Seen for the sender, and the chat stays unread on
    // this phone too (the engine skips both read writes; see
    // GhostStore). The stored choice is per account (GhostStore's
    // per-uid prefs) — the row exists solely for the owner account.
    var ghostRead by remember { mutableStateOf(GhostStore.enabled(ctx)) }
    ToggleRow(
        "Read without Seen",
        "While this is on, opening chats doesn't mark them read — no Seen for the sender, and chats stay unread here too — only for your account",
        ghostRead,
    ) {
        val on = !ghostRead
        GhostStore.setEnabled(ctx, on)
        ghostRead = on
        AppState.toast(
            if (on) "Ghost read on — your Seen stays hidden"
            else "Ghost read off — chats mark read as usual",
        )
    }
    // ROUND 30 — the owner vault log: texts others deleted for everyone,
    // kept only on this phone (see OwnerVault). Owner-section only, so the
    // row exists solely for the owner account.
    NavRow(
        "Deleted messages",
        "Texts others deleted for everyone — the copies kept on this phone, with their times",
    ) { onOpenDeleted() }
    // ROUND 33 — hide recovery: the messages the owner himself hid with
    // Delete for me, with a way back (see HiddenMessagesPage).
    NavRow(
        "Hidden by me",
        "Messages you hid with Delete for me — bring any of them back",
    ) { onOpenHidden() }
    NavRow(
        "Lock now",
        "Lock right away — the password will be asked on the next open, even while App lock is off",
    ) {
        OwnerPrefs.requestGateOnce()
        ChatSession.lock(ctx)
        // ROUND 15: a locked vault is a silent vault — the one-shot gate
        // breaks the notify service's run gate until the next unlock.
        NotifyGate.sync(ctx)
    }
}

// ================= Sheets (kept flows, re-skinned where Round 14 owns them) =================

/**
 * NEW-CHAT sheet (Round 14 re-skin: the host's dark sheet + the redesign
 * Field/CardRow rows). Search, debounce and start behaviour are the
 * build-47 flow, unchanged. TEST SEAM preserved: [searcher] is injectable.
 */
object NewChatSheet {
    internal var searcher: suspend (String) -> List<Person> = { q ->
        if (Config.PREVIEW_UI_ONLY) {
            delay(250)
            PreviewStore.people.filter {
                it.username.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true)
            }
        } else FirebaseEngine.searchPeople(q)
    }

    fun open() {
        var query = ""
        var people: List<Person> = emptyList()
        var searching = false
        AppState.sheet {
            val scope = rememberCoroutineScope()
            var q by remember { mutableStateOf(query) }
            var results by remember { mutableStateOf(people) }
            var busy by remember { mutableStateOf(searching) }
            val threads by AppState.threads.collectAsState()
            LaunchedEffect(q) {
                val queryText = q.trim()
                if (queryText.length < 2) {
                    results = emptyList(); busy = false; return@LaunchedEffect
                }
                busy = true
                delay(280)
                results = runCatching { searcher(queryText) }.getOrDefault(emptyList())
                busy = false
            }
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text(
                    "Start a chat", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontFamily = InterTight,)
                Field(q, { q = it }, "Search by username…")
                if (busy) {
                    Text("Searching…", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontFamily = PoppinsFamily)
                } else if (q.trim().length >= 2 && results.isEmpty()) {
                    Text("No one with that username yet", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontFamily = PoppinsFamily)
                }
                results.forEach { p ->
                    val existingId = threads.firstOrNull { it.person.uid == p.uid }?.id
                    CardRow({
                        AppState.closeSheet()
                        val id = AppState.repo.startThreadWith(p)
                        AppState.repo.openThread(id)
                        AppState.go(Screen.Chat(id))
                        check(existingId == null || existingId == id)
                    }) {
                        Av(p, 46)
                        TwoLine(p.name, "@${p.username}", Modifier.weight(1f).padding(start = 12.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/**
 * EDIT-PROFILE sheet (photo + presets only). Kept for compatibility — the
 * Round 14 surface for the same actions is the Settings ▸ Edit profile
 * subpage; the sheet's behaviour (gallery pick + 7 presets) is unchanged.
 */
object EditProfileSheet {
    fun open() {
        AppState.sheet {
            val scope = rememberCoroutineScope()
            val ctx = LocalContext.current
            val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) scope.launch {
                    val res = runCatching {
                        val bytes = ctx.contentResolver.openInputStream(uri)!!.readBytes()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        FirebaseEngine.updateProfilePhoto(bmp)
                    }
                    if (res.isSuccess) { AppState.toast("Photo updated"); AppState.closeSheet() }
                    else AppState.toast("Couldn't update your photo — try again")
                }
            }
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text(
                    "Your photo", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontFamily = InterTight,)
                Pill("Choose from gallery", Modifier.fillMaxWidth().padding(vertical = 6.dp), false) {
                    pick.launch("image/*")
                }
                PresetAvatarPicker(dark = true) { bmp ->
                    scope.launch {
                        val r = runCatching { FirebaseEngine.updateProfilePhoto(bmp) }
                        if (r.isSuccess) { AppState.toast("Photo updated"); AppState.closeSheet() }
                        else AppState.toast("Couldn't update your photo — try again")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * CHANGE-PASSWORD sheet (existing real flow, untouched): verifies the
 * current password locally against the stored verifier, then re-wraps the
 * identity key under the new password and uploads the new backup blob.
 */
object ChangePasswordSheet {
    fun open(ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
        var current = ""
        var newPw = ""
        var confirm = ""
        var busy = false
        AppState.sheet {
            var cur by remember { mutableStateOf(current) }
            var np by remember { mutableStateOf(newPw) }
            var cf by remember { mutableStateOf(confirm) }
            var b by remember { mutableStateOf(busy) }
            Column(Modifier.padding(horizontal = 6.dp)) {
                TikSheetTitle("Change password")
                Text(
                    "Your chats stay exactly as they are — this only changes the password that unlocks them.",
                    color = Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontFamily = PoppinsFamily,)
                Spacer(Modifier.height(10.dp))
                PasswordField(cur, { cur = it }, "Current password")
                Spacer(Modifier.height(10.dp))
                PasswordField(np, { np = it }, "New password (min 6 characters)")
                Spacer(Modifier.height(10.dp))
                PasswordField(cf, { cf = it }, "Confirm new password")
                Spacer(Modifier.height(16.dp))
                TikPrimaryButton(if (b) "Changing…" else "Change password", enabled = !b) {
                    when {
                        SettingsStore.passwordHash(ctx)?.let { Crypto.passwordVerify(cur, it) } != true ->
                            AppState.toast("Current password doesn't match")
                        np.length < 6 -> AppState.toast("New password needs at least 6 characters")
                        np != cf -> AppState.toast("New passwords don't match")
                        else -> {
                            b = true
                            scope.launch {
                                val r = runCatching {
                                    val backup = FirebaseEngine.backupBlob()
                                        ?: error("No key backup found for this account.")
                                    val priv = runCatching { Crypto.backupUnwrap(backup, cur) }
                                        .getOrElse { error("Couldn't unlock your key with the current password.") }
                                    val blob = Crypto.backupWrap(priv, np)
                                    FirebaseEngine.updateBackupBlob(blob)
                                    SettingsStore.savePasswordHash(ctx, Crypto.passwordHash(np))
                                }
                                b = false
                                if (r.isSuccess) { AppState.closeSheet(); AppState.toast("Password changed") }
                                else AppState.toast(r.exceptionOrNull()?.message ?: "Couldn't change the password")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ---- story placeholder (facade-adjacent; kept from build 47) ----
// Merged model: the (title, body, emoji) shape StoryHelper writes, plus the
// build-47 StoryScreen fields (quotes/grads/personIndex/person) defaulted
// from it, so both consumers compile against one declaration.
data class StoryContent(
    val title: String,
    val body: String,
    val emoji: String,
    val quotes: List<String> = listOf(body),
    val grads: List<Pair<Color, Color>> = themeList,
    val personIndex: Int = 0,
    val person: Person = Person(name = title),
)

object StoryHelper {
    fun open(title: String, body: String, emoji: String) {
        AppState.story = StoryContent(title, body, emoji)
    }
}
