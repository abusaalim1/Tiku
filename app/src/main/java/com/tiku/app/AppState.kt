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
    data object Welcome : Screen
    data object Password : Screen
    /** Returning user on a new device: enter the backup password to restore the key. */
    data object Restore : Screen
    data object ProfileSetup : Screen
    data object Locked : Screen
    data object Home : Screen
    data class Chat(val threadId: String) : Screen
    data class Call(val threadId: String, val video: Boolean) : Screen
    data object Story : Screen
}

enum class HomeTab { CHATS, CALLS, ME }

/** Single UI state hub: navigation, toast, bottom sheet, profile, preview plumbing. */
object AppState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    // ---- threads / calls ----
    val threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    /** threadId -> peer is typing (drives the chat header status + typing bubble). */
    val typing = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    fun thread(id: String): ChatThread? = threads.value.firstOrNull { it.id == id }

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

    /** Simulated <-> real repository facade (see Repository.kt). */
    lateinit var repo: ChatRepo
}

/** Operations the chat UI needs; PreviewRepo (demo data) and FirebaseRepo implement it. */
interface ChatRepo {
    fun openThread(id: String)
    fun closeThread(id: String) {}
    fun sendText(id: String, text: String, quote: String?)
    fun sendPhoto(id: String, bmp: Bitmap)
    fun sendVoice(id: String, audioFile: java.io.File?, durSec: Int, amps: List<Int>)
    fun react(id: String, msgId: String, emoji: String?)
    fun toggleStar(id: String, msgId: String)
    fun deleteForMe(id: String, msgId: String)
    fun clearChat(id: String)
    fun setTyping(id: String, on: Boolean)
    fun logCall(id: String, video: Boolean)
    fun startThreadWith(person: Person): String
}
