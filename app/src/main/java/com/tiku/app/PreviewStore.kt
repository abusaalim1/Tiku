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
        Person("p-aarav", "Aarav", "aarav", "🌿", argb(0xFFB95B72)),
        Person("p-meera", "Meera", "meera", "🌸", argb(0xFFB07A5C)),
        Person("p-crew", "Design Crew", "designcrew", "🎨", argb(0xFF7D6FA8)),
        Person("p-zoya", "Zoya", "zoya", "☁️", argb(0xFF4F8F7E)),
        Person("p-kabir", "Kabir", "kabir", "🎧", argb(0xFF5B86B8)),
        Person("p-riya", "Riya", "riya", "🍋", argb(0xFF9A6B8A)),
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
            // proof seed: an own message the peer HAS seen ("Seen")…
            msg("Ooh, the sage tones are perfect 😍", me = true, time = "9:38")
                .copy(seenByPeer = true),
            // …a call event pill (centred, not a bubble)…
            msg("", time = "9:39", kind = MsgKind.CALL)
                .copy(callCallerUid = "me", callOutcome = "ended",
                    callDurationSec = 754, callVideo = false, callMine = true, tsMs = 6000),
            msg("Aaj shaam milte hain? ✨", time = "9:41"),
            // …and an own message NOT yet seen (no caption)
            msg("On my way!", me = true, time = "9:41"))
        // ROUND 6 seeds: more call events (Calls tab history proof) — a missed
        // incoming call, a declined outgoing call, an ended incoming video call.
        add(1, 0, "8:12",
            msg("Thanks for the font suggestions!", time = "8:12"),
            msg("", time = "8:14", kind = MsgKind.CALL)
                .copy(callCallerUid = "p-meera", callOutcome = "noanswer",
                    callMine = false, tsMs = 5000))
        add(2, 5, "Yesterday", msg("Final deck goes out at 6", time = "Yesterday"))
        add(3, 0, "Yesterday", msg("", time = "Yesterday", kind = MsgKind.VOICE, voiceDurSec = 12))
        add(4, 1, "Mon",
            msg("Playlist updated 🎧", time = "Mon"),
            msg("", time = "Mon", kind = MsgKind.CALL)
                .copy(callCallerUid = "me", callOutcome = "declined",
                    callMine = true, tsMs = 4000))
        add(5, 0, "Sun",
            msg("See you at the café!", time = "Sun"),
            msg("", time = "Sun", kind = MsgKind.CALL)
                .copy(callCallerUid = "p-riya", callOutcome = "ended",
                    callDurationSec = 125, callVideo = true, callMine = false, tsMs = 3000))
        // ROUND 6 seed: the first thread carries a nickname for its peer —
        // header + chat list must show "Aaru", details keep the real name.
        // ROUND 10 seed: the same thread also pins its first text message,
        // so the pinned strip + Chat Details theme/nicknames render in the
        // harness without a backend.
        if (t.isNotEmpty()) {
            val firstText = t[0].messages.firstOrNull { it.kind == MsgKind.TEXT }
            t[0] = t[0].copy(
                nickname = "Aaru",
                pinnedMsgIds = listOfNotNull(firstText?.id),
                themeAccent = 5,
                themeWallpaper = WallpaperMode.COUPLE.ordinal,
            )
        }
        return t
    }

    /** Last-message preview. Round 8: ticks are gone everywhere — a seen own
     *  message prefixes a subtle "Seen · "; call events preview as their
     *  pill label. */
    fun previewLine(th: ChatThread): String {
        val m = th.messages.lastOrNull { !it.deletedForMe } ?: return ""
        val body = when (m.kind) {
            MsgKind.VOICE -> "🎙 Voice message"
            MsgKind.IMAGE -> "📷 Photo"
            MsgKind.CALL -> callEventLabel(m, m.callMine)
            else -> m.text
        }
        val seen = if (m.me && m.kind != MsgKind.CALL && m.seenByPeer) "Seen · " else ""
        return seen + body
    }

    val storyQuotes = listOf(
        "Slow mornings, good coffee ☕",
        "Found a new font. Obsessed.",
        "Golden hour hits different 🌇",
        "Small steps, softly 🌿",
        "Playlist on repeat 🎧",
        "Sunday reset ✨",
    )
    // ROUND 12: story gradients are the design's dark card gradients
    // (its Watch mock cards) — white quote text stays legible on them.
    val storyGrads = listOf(
        Pair(Color(0xFF3A1F2B), Color(0xFF150D12)),
        Pair(Color(0xFF2A2540), Color(0xFF100E18)),
        Pair(Color(0xFF1A1214), Color(0xFF000000)),
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
                amps = List(22) { 8 }, tsMs = System.currentTimeMillis()))
        }
    }

    private fun replies(): String = listOf(
        "Haha yes! 😄", "That sounds perfect ✨", "Send me a pic later?",
        "Okay, 7 pm works 🌿", "Love that 🤍",
    ).random()

    override fun sendText(id: String, text: String, quote: String?) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.TEXT, text, "now", me = true,
            amps = List(22) { 8 }, replyQuote = quote, tsMs = System.currentTimeMillis()))
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

    /** Preview pin: local-only (the real engine persists it in prefs). */
    override fun setPinned(id: String, on: Boolean) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(pinned = on) else it
        }
    }

    /** Preview nickname: local-only (the real engine writes nicknames.<peerUid>
     *  on the chat doc). */
    override fun setNickname(id: String, nickname: String?) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(nickname = nickname?.trim()?.takeIf { n -> n.isNotEmpty() }) else it
        }
    }

    // ---- ROUND 10 preview sinks (local-only mirrors of the engine) ----
    override fun editText(id: String, msgId: String, newText: String) {
        AppState.updateThread(id) {
            it.messages.replaceAll { m ->
                if (m.id == msgId) m.copy(text = newText.trim(), editedAtMs = System.currentTimeMillis()) else m
            }
        }
    }

    override fun setPinnedMsg(id: String, msgId: String, pin: Boolean) {
        val th = AppState.thread(id) ?: return
        if (pin && msgId !in th.pinnedMsgIds && th.pinnedMsgIds.size >= 3) {
            AppState.toast("Up to 3 pinned messages")
            return
        }
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(
                pinnedMsgIds = if (pin) (it.pinnedMsgIds + msgId).distinct().takeLast(3)
                    else it.pinnedMsgIds - msgId,
            ) else it
        }
    }

    override fun setTheme(id: String, accentIdx: Int?, wallpaperIdx: Int?) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(
                themeAccent = accentIdx ?: it.themeAccent,
                themeWallpaper = wallpaperIdx ?: it.themeWallpaper,
            ) else it
        }
    }

    override fun setReceipts(id: String, on: Boolean) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(receiptsOffMine = !on) else it
        }
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
