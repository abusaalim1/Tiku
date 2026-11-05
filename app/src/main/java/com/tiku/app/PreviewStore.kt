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
        // ROUND 14 seed: a message-request sender (not in the main list).
        Person("p-naina", "Naina", "naina.verma", "🌙", argb(0xFFB07A5A)),
    )

    /** ROUND 14 preview notes (uid -> note), mirroring the redesign file's
     *  seed notes. "me" is the preview my-uid key. Aarav's note is hearted
     *  by me so the heart badge renders. */
    fun seedNotes(): Map<String, NoteUi> {
        val now = System.currentTimeMillis()
        return mapOf(
            "p-aarav" to NoteUi("p-aarav", "Dreaming of Goa 🌴", setOf("me"), now - 3600_000),
            "p-meera" to NoteUi("p-meera", "Chai time ☕", emptySet(), now - 1800_000),
            "p-zoya" to NoteUi("p-zoya", "Golden hour 🌇", emptySet(), now - 600_000),
        )
    }

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
        // ROUND 14 seeds: creation times (thread 0 = 263 days together,
        // like the redesign file's Info pill) + one REQUEST thread whose
        // only messages are incoming (partner wrote first, I never did).
        val nowMs = System.currentTimeMillis()
        for (i in t.indices) {
            t[i] = t[i].copy(createdAtMs = nowMs - (i + 3) * 86_400_000L)
        }
        if (t.isNotEmpty()) t[0] = t[0].copy(createdAtMs = nowMs - 263L * 86_400_000L)
        t += ChatThread(
            id = "preview-request-naina",
            person = people[6],
            messages = mutableListOf(
                msg("Hey! Loved your note ✨", time = "2h"),
                msg("Are you around this weekend?", time = "2h"),
            ),
            unread = 2, time = "2h",
            isRequest = true,
            createdAtMs = nowMs - 7200_000,
        )
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
            MsgKind.VIDEO -> "Video"
            MsgKind.AUDIO -> "Audio file"
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

    /** ROUND 22 (parent): keep the armed effect on preview sends too,
     *  so the harness renders gift/fire/confetti/hearts bubbles. */
    override fun sendTextFx(id: String, text: String, quote: String?, fx: String?) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.TEXT, text, "now", me = true,
            amps = List(22) { 8 }, replyQuote = quote, tsMs = System.currentTimeMillis(),
            fx = fx?.takeIf { it in MESSAGE_FX_KINDS }))
        simulateReply(id)
    }

    override fun sendPhoto(id: String, bmp: Bitmap) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.IMAGE, "", "now", me = true,
            amps = List(22) { 8 }, photo = PreviewStore.scaled(bmp)))
        simulateReply(id)
    }

    /** ROUND 31: keep the view-once allowance on preview sends too, so
     *  the harness renders the sender's vo badge. (The preview partner
     *  never consumes views — markVoDone stays the interface default.) */
    override fun sendPhotoVo(id: String, bmp: Bitmap, vo: Int?) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.IMAGE, "", "now", me = true,
            amps = List(22) { 8 }, photo = PreviewStore.scaled(bmp),
            vo = vo?.takeIf { it in 1..2 }))
        simulateReply(id)
    }

    /** ROUND 37: the AI (Tika) sends, preview mirrors — the pushed
     *  copy carries the ai flag so the harness renders the "✨ Tika AI"
     *  bubble. No simulated partner reply: the AI message IS the
     *  reply. (GeminiClient answers canned text / a drawn bitmap in
     *  preview mode, so the whole command flow works in the harness.) */
    override fun sendTextAi(id: String, text: String) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.TEXT, text, "now", me = true,
            amps = List(22) { 8 }, tsMs = System.currentTimeMillis(), ai = true))
    }

    override fun sendPhotoAi(id: String, bmp: Bitmap) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.IMAGE, "", "now", me = true,
            amps = List(22) { 8 }, photo = PreviewStore.scaled(bmp),
            tsMs = System.currentTimeMillis(), ai = true))
    }

    override fun sendVoice(id: String, audioFile: File?, durSec: Int, amps: List<Int>) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.VOICE, "", "now", me = true,
            voiceDurSec = durSec, amps = amps.ifEmpty { List(22) { 8 } },
            localPath = audioFile?.absolutePath))
        simulateReply(id)
    }

    /** ROUND 15 preview sinks: the file kinds appear as already-loaded
     *  bubbles (the harness has no real chunk pipeline to fetch from). */
    override fun sendVideo(id: String, file: File, mime: String, durSec: Int) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.VIDEO, "", "now", me = true,
            voiceDurSec = durSec, localPath = file.absolutePath,
            mimeType = mime, mediaBytes = if (file.exists()) file.length() else 0L))
        simulateReply(id)
    }

    /** ROUND 31: the view-once video send, preview mirror of [sendVideo]. */
    override fun sendVideoVo(id: String, file: File, mime: String, durSec: Int, vo: Int?) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.VIDEO, "", "now", me = true,
            voiceDurSec = durSec, localPath = file.absolutePath,
            mimeType = mime, mediaBytes = if (file.exists()) file.length() else 0L,
            vo = vo?.takeIf { it in 1..2 }))
        simulateReply(id)
    }

    override fun sendAudio(id: String, file: File, mime: String, durSec: Int) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.AUDIO, "", "now", me = true,
            voiceDurSec = durSec, amps = List(22) { 8 }, localPath = file.absolutePath,
            mimeType = mime, mediaBytes = if (file.exists()) file.length() else 0L))
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

    /** ROUND 26: the shared wallpaper theme, local-only in preview. */
    override fun setTheme(id: String, themeId: String) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(themeId = themeId) else it
        }
    }

    override fun setReceipts(id: String, on: Boolean) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(receiptsOffMine = !on) else it
        }
    }

    // ---- ROUND 14 preview sinks (local-only mirrors of the engine) ----
    override fun acceptThread(id: String) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(isRequest = false) else it
        }
    }

    override fun hideThread(id: String) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(hiddenForMe = true) else it
        }
    }

    override fun blockUser(uid: String) {
        AppState.blockedUids.value = AppState.blockedUids.value + uid
        AppState.blockedPeople.value = PreviewStore.people.filter { it.uid in AppState.blockedUids.value }
        AppState.threads.value = AppState.threads.value.filter { it.person.uid != uid }
    }

    override fun unblockUser(uid: String) {
        AppState.blockedUids.value = AppState.blockedUids.value - uid
        AppState.blockedPeople.value = PreviewStore.people.filter { it.uid in AppState.blockedUids.value }
    }

    override fun setMyNote(text: String) {
        val t = text.trim().take(60)
        if (t.isEmpty()) { deleteMyNote(); return }
        AppState.notes.value = AppState.notes.value +
            ("me" to NoteUi("me", t, emptySet(), System.currentTimeMillis()))
    }

    override fun deleteMyNote() {
        AppState.notes.value = AppState.notes.value - "me"
    }

    override fun toggleNoteHeart(ownerUid: String) {
        val cur = AppState.notes.value[ownerUid] ?: return
        AppState.notes.value = AppState.notes.value + (ownerUid to cur.copy(
            hearts = if ("me" in cur.hearts) cur.hearts - "me" else cur.hearts + "me",
        ))
    }

    override fun sendNoteReply(id: String, text: String, noteText: String) {
        push(id, TikuMessage(UUID.randomUUID().toString(), "", MsgKind.TEXT, text, "now", me = true,
            amps = List(22) { 8 }, replyQuote = "Note: $noteText", replyToId = "__note__",
            tsMs = System.currentTimeMillis()))
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
