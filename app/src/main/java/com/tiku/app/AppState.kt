package com.tiku.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Screens, mirroring the prototype's sections. Chat/Call/Story overlay Home. */
sealed interface Screen {
    // ---- K-drama facade (default surface — what the APK opens to) ----
    data object Facade : Screen
    /** Password gate behind the discreet "For You, Always" row on the
     *  facade's For You page (ROUND 13; the facade's sub-surfaces — tabs,
     *  player, reader, viewer — are facade-local state, not Screens). */
    data object TikGate : Screen
    // ---- ChatRoot (hidden behind the gate) ----
    data object Welcome : Screen
    data object Password : Screen
    /** Returning user on a new device: enter the backup password to restore the key. */
    data object Restore : Screen
    /** Rescue (owner Bug 0): local key present but verifier missing/corrupt —
     *  re-auth the same Google account, then re-wrap the key under a new password. */
    data object PasswordReset : Screen
    data object ProfileSetup : Screen
    data object Home : Screen
    data class Chat(val threadId: String) : Screen
    /** ROUND 10: IG-style chat details (per-chat theme, nicknames, read
     *  receipts) — opened by tapping the chat header; Back returns to the chat. */
    data class ChatDetails(val threadId: String) : Screen
    data class Call(val threadId: String, val video: Boolean) : Screen
    data object Story : Screen
    /** ROUND 14 (owner order, 2026-11-03): the couple-memories WEB app
     *  (tiku-moments.vercel.app) integrated as a Tik surface — opened
     *  from the Messages "Memories" entry, full-screen WebView inside
     *  the app (Moments.kt). Tik-side only, behind the gate; the facade
     *  never references it. */
    data object Moments : Screen
}

enum class HomeTab { CHATS, CALLS, WATCH, ME }

/** Engine-wide crash guard: no engine coroutine may ever kill the process with an
 *  uncaught exception (this was the crash vector on the chat-open path). */
val TikuEngineHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
    android.util.Log.e("TikuEngine", "unguarded engine coroutine failed", e)
}

/** Single UI state hub: navigation, toast, bottom sheet, profile, preview plumbing. */
object AppState {

    /** True while the user is inside the first-run / gate flow (welcome, create
     *  password, profile setup, restore, reset, the gate itself). Backgrounding
     *  there is NOT a ChatRoot exit: the instant re-lock must not yank the user
     *  out of onboarding (owner Bug 0). */
    fun isSetupFlow(s: Screen): Boolean = when (s) {
        is Screen.Welcome, is Screen.Password, is Screen.ProfileSetup,
        is Screen.Restore, is Screen.PasswordReset, is Screen.TikGate -> true
        else -> false
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + TikuEngineHandler)

    /** Application context, set once by MainActivity (for MediaStore saves, etc.). */
    lateinit var appCtx: android.content.Context

    var screen by mutableStateOf<Screen>(Screen.Welcome)
    var homeTab by mutableStateOf(HomeTab.CHATS)
    var authBusy by mutableStateOf(false)

    /** ROUND 14 — the redesign shell's destination INSIDE Screen.Home:
     *  0 Messages · 1 Calls · 2 Watch · 3 Setting · 7 Requests. (Chat and
     *  Info stay Screen-routed overlays — Screen.Chat / Screen.ChatDetails.)
     *  Kept in AppState so the screenshot harness and deep links can land
     *  on a pane deterministically. homeTab mirrors it for legacy readers
     *  (WatchControl / NowWatchingBar). */
    var tikDestination by mutableStateOf(0)
    fun goTik(dest: Int) {
        tikDestination = dest
        homeTab = when (dest) {
            1 -> HomeTab.CALLS
            2 -> HomeTab.WATCH
            3 -> HomeTab.ME
            else -> HomeTab.CHATS
        }
    }

    // ---- profile (mine) ----
    var myUsername by mutableStateOf("")
    var myAvatar: Bitmap? by mutableStateOf(null)
    var myAvatarEmoji by mutableStateOf("🙂")

    /** Transient onboarding hand-off (password typed on the create-password screen,
     *  consumed by profile setup / engine bootstrap; never persisted in plaintext). */
    var pendingPassword: String = ""

    // ---- threads ----
    // NOTE: call SIGNALING intentionally leaves NO trace — no call-log store exists.
    // Round 6: the Calls tab only aggregates the encrypted in-chat call-event
    // messages; no other call record is kept anywhere.
    val threads = MutableStateFlow<List<ChatThread>>(emptyList())
    /** threadId -> the PEER's typing stamp, epoch ms on the WRITER's
     *  clock (ROUND 22 — was a Boolean before). 0 / absent = not
     *  typing. Readers show "typing…" only while the stamp is younger
     *  than 5 s, so a peer who dies mid-typing ages out on its own.
     *  Drives the chat header status + typing bubble. */
    val typing = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** ROUND 19 — an own-send's request to glide the open chat to the
     *  tail. The engine raises this AFTER the optimistic local is already
     *  upserted, carrying the local's id: the chat waits until THAT item
     *  is the composed tail, then animates exactly once (sender intent is
     *  absolute — no near-bottom condition on own sends). Consumed by the
     *  chat when it fires or when the open-settle lands the same tail. */
    data class TailScroll(val seq: Long, val tailId: String)
    val tailScrollRequests = MutableStateFlow<Map<String, TailScroll>>(emptyMap())
    private var tailScrollSeq = 0L
    @Synchronized fun requestTailScroll(threadId: String, tailId: String) {
        tailScrollSeq += 1
        tailScrollRequests.value = tailScrollRequests.value + (threadId to TailScroll(tailScrollSeq, tailId))
    }
    fun consumeTailScroll(threadId: String, seq: Long) {
        val cur = tailScrollRequests.value[threadId] ?: return
        if (cur.seq == seq) tailScrollRequests.value = tailScrollRequests.value - threadId
    }

    /** ROUND 14 — public notes by owner uid (mine + every thread partner
     *  with a live note). The engine keeps only notes younger than 24h in
     *  this map; the UI re-checks age at render. */
    val notes = MutableStateFlow<Map<String, NoteUi>>(emptyMap())

    /** ROUND 14 — uids I have blocked (users/{myUid}.blockedUids). Their
     *  threads are filtered out of [threads] by the engine listener; the
     *  Settings ▸ Blocked list resolves these uids to people. */
    val blockedUids = MutableStateFlow<Set<String>>(emptySet())

    /** People behind [blockedUids], resolved by the engine so Settings can
     *  render names/avatars for the Blocked accounts list. */
    val blockedPeople = MutableStateFlow<List<Person>>(emptyList())

    fun thread(id: String): ChatThread? = threads.value.firstOrNull { it.id == id }

    /** Insert or replace a (local optimistic) message in a thread, keyed by id. */
    fun upsertMessage(threadId: String, msg: TikuMessage) {
        threads.value = threads.value.map { t ->
            if (t.id != threadId) t
            else t.copy(messages = (t.messages.filter { it.id != msg.id } + msg).toMutableList())
        }
    }

    /** ROUND 18: optimistic tombstone for delete-for-everyone — replace the
     *  message IN PLACE (its position must not move); content fields are
     *  blanked so nothing of it can render while the snapshot echo is in
     *  flight. The echo rebuilds the identical shape from the doc. */
    fun markDeletedForEveryone(threadId: String, msgId: String) {
        threads.value = threads.value.map { t ->
            if (t.id != threadId) t
            else t.copy(messages = t.messages.map { m ->
                if (m.id == msgId) m.copy(
                    deletedForEveryone = true, text = "", photo = null,
                    localPath = null, replyQuote = null, reactions = emptyMap(),
                ) else m
            }.toMutableList())
        }
    }

    /** Flip the delivery state of one message (round 3 send-state surfacing). */
    fun setMessageStatus(threadId: String, msgId: String, status: SendStatus) {
        threads.value = threads.value.map { t ->
            if (t.id != threadId) t
            else t.copy(messages = t.messages.map { m ->
                if (m.id == msgId) m.copy(status = status) else m
            }.toMutableList())
        }
    }

    fun updateThread(id: String, block: (ChatThread) -> Unit) {
        val cur = threads.value.map { if (it.id == id) it.copy().also { c -> block(c) } else it }
        threads.value = cur
    }

    /** Boolean shim kept for the preview store, whose simulated peer
     *  typing has no real clock behind it: "on" stamps the peer fresh
     *  as of now, "off" clears the stamp. */
    fun setTyping(id: String, on: Boolean) =
        setPeerTypingAt(id, if (on) System.currentTimeMillis() else 0L)

    /** Record the peer's typing stamp for a thread (epoch ms; 0
     *  removes the entry). Written ONLY by the engine's chat-doc
     *  listener — the messages listeners never see typing state. */
    fun setPeerTypingAt(id: String, epochMs: Long) {
        typing.value = if (epochMs > 0L) typing.value + (id to epochMs) else typing.value - id
    }

    // ---- toast (design style, 2.2s) ----
    var toastText by mutableStateOf<String?>(null)
    private var toastJob: Job? = null
    fun toast(msg: String) {
        toastText = msg
        toastJob?.cancel()
        toastJob = scope.launch { delay(2200); toastText = null }
    }

    // ---- bottom sheet host ----
    var sheetContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    fun sheet(content: @Composable () -> Unit) { sheetContent = content }
    fun closeSheet() { sheetContent = null }

    // ---- story viewer content ----
    var story by mutableStateOf<StoryContent?>(null)

    fun go(s: Screen) { screen = s }

    /** True while the next navigation must snap with no transition (quick exit / re-lock). */
    var instantNav by mutableStateOf(false)
    fun instantGo(s: Screen) { instantNav = true; screen = s }

    fun isChatRoot(dest: Screen): Boolean = when (dest) {
        is Screen.Welcome, is Screen.Password, is Screen.Restore, is Screen.ProfileSetup,
        is Screen.TikGate, is Screen.Home, is Screen.Chat, is Screen.ChatDetails,
        is Screen.Call, is Screen.Story, is Screen.Moments -> true
        else -> false
    }

    /** True for the K-drama facade destination (the outer surface; never
     *  gated). Every facade sub-surface lives inside Screen.Facade as
     *  facade-local state, so this stays a single case — exactly the set
     *  the lock paths treated as "already home" before ROUND 13. */
    fun isFacade(dest: Screen): Boolean = when (dest) {
        is Screen.Facade -> true
        else -> false
    }

    /** Simulated <-> real repository facade (see Repository.kt). */
    lateinit var repo: ChatRepo
}

/** Operations the chat UI needs; PreviewRepo (demo data) and FirebaseRepo implement it. */
interface ChatRepo {
    fun openThread(id: String)
    fun closeThread(id: String) {}
    fun sendText(id: String, text: String, quote: String?)
    /** ROUND 22: send a text message carrying an Instagram-style send
     *  effect ([fx] = "gift" | "fire" | "confetti" | "hearts", null =
     *  plain). Default routes to the plain send and drops the effect,
     *  so implementers that predate effects (the preview store) keep
     *  compiling and behaving exactly as before; the real engine
     *  overrides it and writes the additive `fx` doc field. */
    fun sendTextFx(id: String, text: String, quote: String?, fx: String?) {
        sendText(id, text, quote)
    }
    /** ROUND 14: send a reply to a partner's NOTE — a real text message in
     *  our thread whose encrypted reply-quote snapshot reads "Note: <text>"
     *  and whose doc carries replyToId "__note__" (the note sentinel).
     *  Default no-op keeps test doubles compiling. */
    fun sendNoteReply(id: String, text: String, noteText: String) {}
    // ---- ROUND 14: requests / block / notes ----
    /** Accept a message request: the thread joins the main list
     *  (chat doc acceptedFor[me] = now). Default no-op. */
    fun acceptThread(id: String) {}
    /** Locally hide a thread (chat doc hiddenFor[me] = now). It reappears
     *  if the partner sends a NEW message after the hide. Default no-op. */
    fun hideThread(id: String) {}
    /** Block a user (users/{me}.blockedUids += uid) and hide our thread.
     *  Default no-op. */
    fun blockUser(uid: String) {}
    /** Unblock a user. Default no-op. */
    fun unblockUser(uid: String) {}
    /** Publish/replace MY note (users/{me}: noteText + noteAt, hearts reset).
     *  Default no-op. */
    fun setMyNote(text: String) {}
    /** Remove MY note. Default no-op. */
    fun deleteMyNote() {}
    /** Heart/unheart another user's note (their noteHearts array).
     *  Default no-op. */
    fun toggleNoteHeart(ownerUid: String) {}
    /** Re-attempt a FAILED outgoing message (tap-to-retry on the bubble). */
    fun retrySend(id: String, msgId: String) {}
    fun sendPhoto(id: String, bmp: Bitmap)
    fun sendVoice(id: String, audioFile: java.io.File?, durSec: Int, amps: List<Int>)
    // ---- ROUND 15: media files (video + audio file) ----
    /** Send a VIDEO file (picker copy or camera capture — always an
     *  app-cache file) through the encrypted chunk pipeline. The engine
     *  enforces the size cap. Default no-op keeps test doubles compiling. */
    fun sendVideo(id: String, file: java.io.File, mime: String, durSec: Int) {}
    /** Send an AUDIO file (picked file copied to cache, or a camera-app
     *  audio capture). Same pipeline + cap rules as [sendVideo]. */
    fun sendAudio(id: String, file: java.io.File, mime: String, durSec: Int) {}
    /** Load one VIDEO / AUDIO message's payload on demand (tap-to-load —
     *  these kinds never auto-download). Default no-op. */
    fun fetchMedia(id: String, msgId: String) {}
    fun react(id: String, msgId: String, emoji: String?)
    fun toggleStar(id: String, msgId: String)
    fun deleteForMe(id: String, msgId: String)
    /** ROUND 18: delete one of MY OWN messages for BOTH sides — the doc is
     *  tombstoned (never removed; chats never disappear) and both clients
     *  render "This message was deleted". Default no-op keeps test doubles
     *  compiling. */
    fun deleteForEveryone(id: String, msgId: String) {}
    fun clearChat(id: String)
    fun setTyping(id: String, on: Boolean)
    fun startThreadWith(person: Person): String
    /** ROUND 6: set (or clear, with null/blank) the PEER's nickname in this
     *  chat — stored on the chat doc (nicknames map), applies for both
     *  members. Default no-op keeps test doubles compiling. */
    fun setNickname(id: String, nickname: String?) {}
    /** ROUND 8: fetch the next page of OLDER messages for an open chat
     *  (pagination — a chat opens at its newest ~60). Default no-op. */
    fun loadOlder(id: String) {}
    /** ROUND 8: pin/unpin a chat in the list (IG-style). A per-user, local
     *  view preference — never written to the chat doc. Default no-op. */
    fun setPinned(id: String, on: Boolean) {}
    // ---- ROUND 10 ----
    /** Edit one of MY OWN text messages in place (re-encrypts through the
     *  existing path; the doc gains an editedAt stamp). Default no-op. */
    fun editText(id: String, msgId: String, newText: String) {}
    /** Pin/unpin a MESSAGE inside the chat (chat doc pinnedMsgs, max 3,
     *  shared by both members). Default no-op. */
    fun setPinnedMsg(id: String, msgId: String, pin: Boolean) {}
    /** Set this chat's own theme (chat doc themeAccent/themeWallpaper;
     *  null leaves that half unchanged). Default no-op. */
    fun setTheme(id: String, accentIdx: Int?, wallpaperIdx: Int?) {}
    /** ROUND 26: set the SHARED wallpaper theme (chat doc `theme`, a
     *  ChatThemes id; "" = Default). Either member setting it re-skins
     *  both phones via the threads listener. Default no-op. */
    fun setTheme(id: String, themeId: String) {}
    /** My per-chat read-receipts switch (chat doc receiptsOff array).
     *  Default no-op. */
    fun setReceipts(id: String, on: Boolean) {}
}
