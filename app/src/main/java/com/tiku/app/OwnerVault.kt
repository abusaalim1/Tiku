package com.tiku.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * ROUND 30 — the OWNER-ONLY "deleted stays with me" vault.
 *
 * The SERVER semantics of Delete-for-everyone do NOT change for anyone: the
 * sender's tombstone still wipes the ciphertext and the media chunks exactly
 * as before, and every other device renders the plain tombstone. This vault
 * is a purely LOCAL safety net on the OWNER's phone only: whenever the
 * owner's own client decrypts an INCOMING message (the engine's decryptDoc
 * funnel — chat open, thread preview, tap-to-load), a copy is quietly kept
 * in app-private storage (`filesDir/owner_vault/`), behind the journal lock
 * like everything else on this device. If that message is later tombstoned,
 * the owner's chat renders it FROM THE VAULT (text with a "Deleted" marker;
 * media blurred behind a tap-to-reveal) instead of the placeholder.
 *
 * Coverage law (accepted, by design): only content this phone actually
 * loaded BEFORE the delete exists in the vault. Media enters ONLY when the
 * owner's client fetched/decrypted it for viewing — the standing
 * no-auto-download law is untouched: a video that was never tapped never
 * enters the vault, and its tombstone renders exactly as today.
 *
 * Every read/write is gated on [OwnerState.isOwner] (the signed-in EMAIL
 * vs Config.OWNER_EMAIL — never a uid). For every other account every entry
 * point is a no-op and nothing is ever written.
 *
 * Storage shape: one `index.json` (metadata for every entry) + one file per
 * media payload in the same folder. The in-memory map updates synchronously
 * at call time (so a tombstone processed in the same snapshot pass sees the
 * stash), while all disk writes funnel through ONE daemon executor, which
 * also preserves stash-before-delete ordering. Index writes are atomic
 * (tmp file + rename).
 */
object OwnerVault {

    class Entry(
        val chatId: String,
        val msgId: String,
        /** "text" | "image" | "voice" | "video" | "audio" (Firestore kind strings). */
        val kind: String,
        val text: String = "",
        val senderName: String = "",
        val senderUsername: String = "",
        val sentAtMs: Long = 0L,
        /** Payload file name inside owner_vault/ ("" = none — text entries, or none yet). */
        val mediaFile: String = "",
        val mime: String = "",
        val durSec: Int = 0,
        val amps: List<Int> = emptyList(),
        /** Server `delAt` of the tombstone when known, else the first-observed time. */
        var deletedAtMs: Long = 0L,
        /** When THIS device first saw the tombstone (fallback / audit). */
        var firstSeenDeletedMs: Long = 0L,
    ) {
        val hasContent: Boolean
            get() = if (kind == "text") text.isNotEmpty() else mediaFile.isNotEmpty()
    }

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    @Volatile private var loaded = false
    @Volatile private var appCtx: Context? = null
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tiku-owner-vault").apply { isDaemon = true }
    }

    /** Compose subscription tick: bumped on every mutation so an open chat
     *  and the settings log re-render when the vault changes under them. */
    var stateVersion by mutableIntStateOf(0)
        private set

    private fun key(chatId: String, msgId: String) = "$chatId|$msgId"

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "owner_vault").apply { mkdirs() }

    // ------------------------------------------------------------ lifecycle

    /** Idempotent: remembers the app context and loads the index once. */
    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching {
                val f = File(dir(ctx), "index.json")
                if (f.exists()) {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val e = Entry(
                            chatId = o.optString("chatId"),
                            msgId = o.optString("msgId"),
                            kind = o.optString("kind"),
                            text = o.optString("text"),
                            senderName = o.optString("senderName"),
                            senderUsername = o.optString("senderUsername"),
                            sentAtMs = o.optLong("sentAtMs"),
                            mediaFile = o.optString("mediaFile"),
                            mime = o.optString("mime"),
                            durSec = o.optInt("durSec"),
                            amps = (o.optJSONArray("amps") ?: JSONArray()).let { a ->
                                List(a.length()) { a.optInt(it) }
                            },
                            deletedAtMs = o.optLong("deletedAtMs"),
                            firstSeenDeletedMs = o.optLong("firstSeenDeletedMs"),
                        )
                        if (e.chatId.isNotEmpty() && e.msgId.isNotEmpty()) {
                            entries[key(e.chatId, e.msgId)] = e
                        }
                    }
                }
            }
            loaded = true
        }
    }

    private fun persistAsync() {
        val ctx = appCtx ?: return
        io.execute {
            runCatching {
                val snapshot = synchronized(lock) { entries.values.toList() }
                val arr = JSONArray()
                for (e in snapshot) {
                    val amps = JSONArray()
                    e.amps.forEach { amps.put(it) }
                    arr.put(JSONObject().apply {
                        put("chatId", e.chatId); put("msgId", e.msgId); put("kind", e.kind)
                        put("text", e.text); put("senderName", e.senderName)
                        put("senderUsername", e.senderUsername); put("sentAtMs", e.sentAtMs)
                        put("mediaFile", e.mediaFile); put("mime", e.mime)
                        put("durSec", e.durSec); put("amps", amps)
                        put("deletedAtMs", e.deletedAtMs)
                        put("firstSeenDeletedMs", e.firstSeenDeletedMs)
                    })
                }
                val d = dir(ctx)
                val tmp = File(d, "index.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(File(d, "index.json"))
            }
        }
    }

    private fun bump() { stateVersion++ }

    // ------------------------------------------------------------ stash

    /** Keep an incoming TEXT message's plaintext. Called from decryptDoc —
     *  covers the chat screen AND thread previews (both decrypt text). */
    fun stashText(ctx: Context, chatId: String, m: TikuMessage, senderName: String, senderUsername: String) {
        if (!OwnerState.isOwner) return
        init(ctx)
        if (m.me || m.kind != MsgKind.TEXT || m.text.isEmpty() || m.deletedForEveryone) return
        synchronized(lock) {
            val k = key(chatId, m.id)
            if (entries.containsKey(k)) return
            entries[k] = Entry(
                chatId = chatId, msgId = m.id, kind = "text", text = m.text,
                senderName = senderName, senderUsername = senderUsername,
                sentAtMs = m.tsMs,
            )
            bump()
        }
        persistAsync()
    }

    /** True when a media payload for this message is already vaulted —
     *  lets callers skip re-reading bytes they no longer need. */
    fun hasMedia(chatId: String, msgId: String): Boolean = synchronized(lock) {
        entries[key(chatId, msgId)]?.mediaFile?.isNotEmpty() == true
    }

    /**
     * Keep an incoming media message's DECRYPTED bytes. Callers pass bytes
     * the client already produced for viewing (a fresh chunk download in
     * decryptDoc, the tap-to-load in fetchMedia, or the in-memory payload
     * on the incremental path) — this function never triggers a download.
     */
    fun stashMedia(
        ctx: Context, chatId: String, m: TikuMessage, bytes: ByteArray,
        senderName: String, senderUsername: String,
    ) {
        if (!OwnerState.isOwner) return
        init(ctx)
        if (m.me || m.deletedForEveryone) return
        val kindStr = when (m.kind) {
            MsgKind.IMAGE -> "image"; MsgKind.VOICE -> "voice"
            MsgKind.VIDEO -> "video"; MsgKind.AUDIO -> "audio"
            else -> return
        }
        val fname = "${chatId}_${m.id}.${mediaExt(m.mimeType, kindStr)}"
        synchronized(lock) {
            val k = key(chatId, m.id)
            val existing = entries[k]
            if (existing != null && existing.mediaFile.isNotEmpty()) return
            entries[k] = Entry(
                chatId = chatId, msgId = m.id, kind = kindStr,
                senderName = senderName, senderUsername = senderUsername,
                sentAtMs = m.tsMs, mediaFile = fname, mime = m.mimeType,
                durSec = m.voiceDurSec, amps = m.amps,
                deletedAtMs = existing?.deletedAtMs ?: 0L,
                firstSeenDeletedMs = existing?.firstSeenDeletedMs ?: 0L,
            )
            bump()
        }
        io.execute {
            runCatching { File(dir(ctx), fname).writeBytes(bytes) }
            persistAsync()
        }
    }

    private fun mediaExt(mime: String, kindStr: String): String = when {
        kindStr == "image" -> "jpg"
        kindStr == "voice" -> "m4a"
        mime.contains("webm") -> "webm"
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        mime.contains("mp4") || mime.contains("m4a") -> "mp4"
        mime.contains("ogg") -> "ogg"
        kindStr == "video" -> "mp4"
        else -> "mp3"
    }

    // ------------------------------------------------------------ deletion

    /**
     * Record that a message was tombstoned. [delAtMs] is the server `delAt`
     * (ROUND 30) when the tombstone carries one; 0 for older tombstones,
     * which fall back to the first-observed time. No entry → nothing to do:
     * content this phone never loaded stays unrecoverable, by design.
     */
    fun markDeleted(ctx: Context, chatId: String, msgId: String, delAtMs: Long) {
        if (!OwnerState.isOwner) return
        init(ctx)
        synchronized(lock) {
            val e = entries[key(chatId, msgId)] ?: return
            val now = System.currentTimeMillis()
            if (e.firstSeenDeletedMs == 0L) e.firstSeenDeletedMs = now
            e.deletedAtMs = when {
                delAtMs > 0L -> delAtMs
                e.deletedAtMs > 0L -> e.deletedAtMs
                else -> e.firstSeenDeletedMs
            }
            bump()
        }
        persistAsync()
    }

    // ------------------------------------------------------------ reads

    /** The vault copy for a message, or null when this phone never loaded
     *  its content (or the account is not the owner's). Chat rendering
     *  consults this ONLY for incoming tombstones. */
    fun entry(chatId: String, msgId: String): Entry? {
        if (!OwnerState.isOwner) return null
        return synchronized(lock) { entries[key(chatId, msgId)]?.takeIf { it.hasContent } }
    }

    /** Deleted TEXT entries, newest deletion first — the settings log. */
    fun deletedTextLog(): List<Entry> = synchronized(lock) {
        entries.values
            .filter { it.kind == "text" && it.deletedAtMs > 0L && it.text.isNotEmpty() }
            .sortedWith(compareByDescending<Entry> { it.deletedAtMs }.thenByDescending { it.sentAtMs })
    }

    /** Total entries (texts + media) — drives the Clear vault row's
     *  visibility on the log page (ROUND 33). */
    fun size(): Int = synchronized(lock) { entries.size }

    /**
     * ROUND 33 — CLEAR VAULT (owner-only; the log page confirms before
     * calling this): every kept copy goes — the in-memory index, the
     * index file and all payload files. The chats themselves are never
     * touched: messages still standing in their chats simply re-stash
     * the next time this client decrypts them (they were never deleted
     * — only the safety net is emptied).
     */
    fun clear() {
        if (!OwnerState.isOwner) return
        synchronized(lock) { entries.clear() }
        bump()
        val ctx = appCtx ?: return
        io.execute {
            runCatching {
                val d = dir(ctx)
                d.listFiles()?.forEach { it.delete() }
                File(d, "index.json").writeText("[]")
            }
        }
    }

    fun mediaFile(e: Entry): File? {
        val ctx = appCtx ?: return null
        if (e.mediaFile.isEmpty()) return null
        return File(dir(ctx), e.mediaFile).takeIf { it.exists() }
    }

    fun loadBitmap(e: Entry): Bitmap? {
        val f = mediaFile(e) ?: return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }
}

// ================= ROUND 30 — owner settings page =================

/**
 * The OWNER-ONLY "Deleted messages" log (Settings ▸ Owner): every incoming
 * TEXT the owner's phone kept and later saw tombstoned — the text itself,
 * who sent it (chat), when it was sent and when it was deleted. Media is
 * deliberately NOT listed here; it lives blurred in its chat, per the
 * owner's order.
 */
@Composable
fun DeletedMessagesPage(onBack: () -> Unit) {
    val tick = OwnerVault.stateVersion
    val log = remember(tick) { OwnerVault.deletedTextLog() }
    val fmt = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    // ROUND 33: the Clear vault confirm's visibility (dialog below).
    var confirmClear by remember { mutableStateOf(false) }
    SubPage("Deleted messages", onBack) {
        if (!OwnerState.isOwner) {
            item {
                Text(
                    "Available on the owner account only.",
                    color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily,)
            }
        } else if (log.isEmpty()) {
            item {
                Text(
                    "Nothing here yet. When someone deletes a text for everyone, " +
                        "the copy on this phone stays — and shows up here with its times.",
                    color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily,)
            }
        } else {
            items(log, key = { it.chatId + "|" + it.msgId }) { e ->
                val chatName = AppState.thread(e.chatId)?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: e.senderName.takeIf { it.isNotBlank() }
                    ?: "Chat"
                val handle = e.senderUsername.takeIf { it.isNotBlank() }
                    ?: AppState.thread(e.chatId)?.person?.username.orEmpty()
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)).background(CardC)
                        .padding(14.dp),
                ) {
                    Text(
                        if (handle.isNotBlank()) "$chatName · @$handle" else chatName,
                        color = Rose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e.text, color = Color.White, fontSize = 15.sp,
                        fontFamily = rememberChatFontFamily(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sent ${fmt.format(Date(e.sentAtMs))}",
                        color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily,)
                    Text(
                        "Deleted ${fmt.format(Date(e.deletedAtMs))}",
                        color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily,)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        // ROUND 33 — Clear vault: the one destructive owner action,
        // so it sits behind the confirm dialog below. Shown whenever
        // the vault holds anything (texts OR media — media never
        // appears in this log but lives in the same vault).
        if (OwnerState.isOwner && OwnerVault.size() > 0) {
            item {
                NavRow(
                    "Clear vault",
                    "Remove every kept copy — texts and media — from this phone",
                    danger = true, chevron = false,
                ) { confirmClear = true }
            }
        }
    }
    if (confirmClear) {
        Dialog(onDismissRequest = { confirmClear = false }) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(CardC)
                    .padding(22.dp),
            ) {
                Text(
                    "Clear the vault?", color = Color.White,
                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
                Text(
                    "Every copy kept on this phone — the deleted texts " +
                        "listed here and any blurred media in your chats — " +
                        "is removed for good. The chats themselves are " +
                        "not touched.",
                    color = Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp), fontFamily = PoppinsFamily,)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Cancel", Modifier.weight(1f), accent = false) {
                        confirmClear = false
                    }
                    Pill("Clear vault", Modifier.weight(1f), accent = true) {
                        OwnerVault.clear()
                        confirmClear = false
                        AppState.toast("Vault cleared")
                    }
                }
            }
        }
    }
}
