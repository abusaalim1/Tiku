package com.tiku.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * PREVIEW DEMO MODE store: in-memory data seeded from the prototype's sample
 * people/conversations, with the prototype's simulated reply behavior.
 * Never presented as real/live data outside preview mode.
 */
object PreviewStore {
    private fun argb(hex: Long) = Color(hex).copy(alpha = 1f).let {
        android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
    }

    val people = listOf(
        Person("p-aarav", "Aarav", "aarav", "🌿", argb(0xFFE4714C)),
        Person("p-meera", "Meera", "meera", "🌸", argb(0xFFD98FA0)),
        Person("p-crew", "Design Crew", "designcrew", "🎨", argb(0xFFE0A04A)),
        Person("p-zoya", "Zoya", "zoya", "☁️", argb(0xFFB9876F)),
        Person("p-kabir", "Kabir", "kabir", "🎧", argb(0xFFC45A5A)),
        Person("p-riya", "Riya", "riya", "🍋", argb(0xFFC9B13A)),
    )

    private fun msg(text: String = "", me: Boolean = false, time: String = "now", kind: MsgKind = MsgKind.TEXT,
                    voiceDurSec: Int = 0, photo: Bitmap? = null): TikuMessage =
        TikuMessage(UUID.randomUUID().toString(), "", kind, text, time, me, voiceDurSec,
            List(22) { i -> (6 + kotlin.math.abs(kotlin.math.sin(i * 1.7)) * 18).toInt() }, photo)

    fun seedThreads(): List<ChatThread> {
        val t = mutableListOf<ChatThread>()
        fun add(idx: Int, unread: Int, time: String, vararg m: TikuMessage) {
            t += ChatThread(UUID.randomUUID().toString(), people[idx], m.toMutableList(), unread, time)
        }
        add(0, 2, "9:41",
            msg("Sent you the new moodboard", time = "9:30"),
            // round 4 proof seed: an own message the peer HAS seen (✓✓)…
            msg("Ooh, the sage tones are perfect 😍", me = true, time = "9:38")
                .copy(seenByPeer = true),
            // …a call event pill (centred, not a bubble)…
            msg("", time = "9:39", kind = MsgKind.CALL)
                .copy(callCallerUid = "me", callOutcome = "ended",
                    callDurationSec = 754, callVideo = false, callMine = true),
            msg("Aaj shaam milte hain? ✨", time = "9:41"),
            // …and an own message NOT yet seen (single ✓)
            msg("On my way!", me = true, time = "9:41"))
        add(1, 0, "8:12", msg("Thanks for the font suggestions!", time = "8:12"))
        add(2, 5, "Yesterday", msg("Final deck goes out at 6", time = "Yesterday"))
        add(3, 0, "Yesterday", msg("", time = "Yesterday", kind = MsgKind.VOICE, voiceDurSec = 12))
        add(4, 1, "Mon", msg("Playlist updated 🎧", time = "Mon"))
        add(5, 0, "Sun", msg("See you at the café!", time = "Sun"))
        return t
    }

    /** Last-message preview exactly like the prototype: ✓✓ / 🎙 / 📷.
     *  Round 4: the tick prefix follows the real seen state (single ✓ until
     *  the peer has seen it); call events preview as their pill label. */
    fun previewLine(th: ChatThread): String {
        val m = th.messages.lastOrNull { !it.deletedForMe } ?: return ""
        val body = when (m.kind) {
            MsgKind.VOICE -> "🎙 Voice message"
            MsgKind.IMAGE -> "📷 Photo"
            MsgKind.CALL -> callEventLabel(m, m.callMine)
            else -> m.text
        }
        val tick = if (m.me && m.kind != MsgKind.CALL) (if (m.seenByPeer) "✓✓ " else "✓ ") else ""
        return tick + body
    }

    val storyQuotes = listOf(
        "Slow mornings, good coffee ☕",
        "Found a new font. Obsessed.",
        "Golden hour hits different 🌇",
        "Small steps, softly 🌿",
        "Playlist on repeat 🎧",
        "Sunday reset ✨",
    )
    val storyGrads = listOf(
        Pair(Color(0xFFF7B58F), Color(0xFFC9552F)),
        Pair(Color(0xFFFBD3C0), Color(0xFFB8607A)),
        Pair(Color(0xFFEBC38F), Color(0xFF9A5A3B)),
    )

    private val replies = listOf(
        "Haha yes! 😄", "That sounds perfect ✨", "Send me a pic later?",
        "Okay, 7 pm works 🌿", "Love that 🤍",
    )

    fun scaled(bmp: Bitmap, maxDim: Int = 1024): Bitmap {
        val w = bmp.width; val h = bmp.height
        if (w <= maxDim && h <= maxDim) return bmp
        val s = maxDim / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(bmp, (w * s).toInt(), (h * s).toInt(), true)
    }
}

/** Preview implementation of ChatRepo: simulated sign-in/replies, in-memory. */
object PreviewRepo : ChatRepo {
    override fun openThread(id: String) {
        val list = AppState.threads.value.map { if (it.id == id) it.copy(unread = 0) else it }
        AppState.threads.value = list
    }

    private fun refresh() { AppState.threads.value = AppState.threads.value.toList() }

    private fun push(id: String, m: TikuMessage) {
        AppState.updateThread(id) { it.messages += m; it.time = "now" }
    }

    private fun simulateReply(id: String) {
        AppState.scope.launch {
            AppState.setTyping(id, true)
            delay(1600)
            AppState.setTyping(id, false)
            push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.TEXT,
                text = replies(), me = false, time = "now",
                amps = List(22) { 8 }))
        }
    }

    private fun replies(): String = listOf(
        "Haha yes! 😄", "That sounds perfect ✨", "Send me a pic later?",
        "Okay, 7 pm works 🌿", "Love that 🤍",
    ).random()

    override fun sendText(id: String, text: String, quote: String?) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.TEXT, text, "now", me = true,
            amps = List(22) { 8 }, replyQuote = quote))
        simulateReply(id)
    }

    override fun sendPhoto(id: String, bmp: Bitmap) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.IMAGE, "", "now", me = true,
            amps = List(22) { 8 }, photo = PreviewStore.scaled(bmp)))
        simulateReply(id)
    }

    override fun sendVoice(id: String, audioFile: File?, durSec: Int, amps: List<Int>) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.VOICE, "", "now", me = true,
            voiceDurSec = durSec, amps = amps.ifEmpty { List(22) { 8 } },
            localPath = audioFile?.absolutePath))
        simulateReply(id)
    }

    override fun react(id: String, msgId: String, emoji: String?) {
        AppState.updateThread(id) {
            it.messages.replaceAll { m ->
                if (m.id == msgId) m.copy(reactions = if (emoji == null) emptyMap() else mapOf("me" to emoji)) else m
            }
        }
    }

    override fun toggleStar(id: String, msgId: String) {
        AppState.updateThread(id) {
            it.messages.replaceAll { m -> if (m.id == msgId) m.copy(starredByMe = !m.starredByMe) else m }
        }
    }

    override fun deleteForMe(id: String, msgId: String) {
        AppState.updateThread(id) {
            it.messages.replaceAll { m -> if (m.id == msgId) m.copy(deletedForMe = true) else m }
        }
    }

    override fun clearChat(id: String) {
        AppState.updateThread(id) { it.messages.clear(); it.time = "now" }
    }

    override fun setTyping(id: String, on: Boolean) = AppState.setTyping(id, on)

    override fun startThreadWith(person: Person): String {
        val existing = AppState.threads.value.firstOrNull { it.person.uid == person.uid }
        if (existing != null) return existing.id
        val th = ChatThread(UUID.randomUUID().toString(), person)
        AppState.threads.value = listOf(th) + AppState.threads.value
        return th.id
    }

    /** Preview sink for CallController.recordOutcome: appends the same centred
     *  call-event pill the real engine writes as an encrypted kind="call"
     *  message (owner round 4, item 3). */
    fun appendCallEvent(threadId: String, callerUid: String, video: Boolean, outcome: String, durSec: Int) {
        AppState.upsertMessage(threadId, TikuMessage(
            id = "call_preview_${System.currentTimeMillis()}",
            senderUid = callerUid, kind = MsgKind.CALL, text = "",
            time = "now", me = false, tsMs = System.currentTimeMillis(),
            callCallerUid = callerUid, callOutcome = outcome,
            callDurationSec = durSec, callVideo = video,
            callMine = callerUid == "me",
        ))
    }
}
