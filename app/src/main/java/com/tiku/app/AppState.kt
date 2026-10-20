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
    // ---- study facade (default surface — what the APK opens to) ----
    data object Facade : Screen
    data class StudySubject(val name: String) : Screen
    data class StudyChapter(val subject: String, val index: Int) : Screen
    data object StudyNotes : Screen
    data object StudyTimer : Screen
    data object StudyRevision : Screen
    data object StudyFormulas : Screen
    /** Password gate behind the discreet "Tik" row on the Chemistry page. */
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
    data class Call(val threadId: String, val video: Boolean) : Screen
    data object Story : Screen
}

enum class HomeTab { CHATS, CALLS, ME }

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
    /** threadId -> peer is typing (drives the chat header status + typing bubble). */
    val typing = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    fun thread(id: String): ChatThread? = threads.value.firstOrNull { it.id == id }

    /** Insert or replace a (local optimistic) message in a thread, keyed by id. */
    fun upsertMessage(threadId: String, msg: TikuMessage) {
        threads.value = threads.value.map { t ->
            if (t.id != threadId) t
            else t.copy(messages = (t.messages.filter { it.id != msg.id } + msg).toMutableList())
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

    fun setTyping(id: String, on: Boolean) { typing.value = typing.value + (id to on) }

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
        is Screen.TikGate, is Screen.Home, is Screen.Chat, is Screen.Call, is Screen.Story -> true
        else -> false
    }

    /** True for any study-facade destination (the study surface; never gated). */
    fun isFacade(dest: Screen): Boolean = when (dest) {
        is Screen.Facade, is Screen.StudySubject, is Screen.StudyChapter,
        is Screen.StudyNotes, is Screen.StudyTimer, is Screen.StudyRevision,
        is Screen.StudyFormulas -> true
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
    /** Re-attempt a FAILED outgoing message (tap-to-retry on the bubble). */
    fun retrySend(id: String, msgId: String) {}
    fun sendPhoto(id: String, bmp: Bitmap)
    fun sendVoice(id: String, audioFile: java.io.File?, durSec: Int, amps: List<Int>)
    fun react(id: String, msgId: String, emoji: String?)
    fun toggleStar(id: String, msgId: String)
    fun deleteForMe(id: String, msgId: String)
    fun clearChat(id: String)
    fun setTyping(id: String, on: Boolean)
    fun startThreadWith(person: Person): String
    /** ROUND 6: set (or clear, with null/blank) the PEER's nickname in this
     *  chat — stored on the chat doc (nicknames map), applies for both
     *  members. Default no-op keeps test doubles compiling. */
    fun setNickname(id: String, nickname: String?) {}
}
