package com.tiku.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ROUND 33 — four OWNER-ONLY extras. Every surface in this file (and
 * every gate it feeds) keys on [OwnerState.isOwner] — the signed-in
 * EMAIL vs Config.OWNER_EMAIL, never a uid. For every other account
 * the toggles don't exist, the pages never compose, and the engine
 * gates below read "off", so behaviour is exactly what it was before
 * this round. Nothing here touches the wire: all four features are
 * client-local (a prefs flag, an in-memory registry over the existing
 * deletedFor field, a share intent, a local file wipe).
 */

// ================= 1. GHOST READ =================

/**
 * "Read without Seen" — the owner's switch for reading a chat WITHOUT
 * telling anyone. While it is on, the engine skips BOTH read writes:
 *
 *  - [FirebaseEngine.markIncomingSeen] returns immediately, so no
 *    `seenBy` arrayUnion is ever written — senders never see his Seen.
 *  - [FirebaseEngine.markRead] returns immediately, so the local read
 *    watermark (`tiku_read`) never advances — his own chat list keeps
 *    showing those chats as unread. That is the honest twin of the
 *    first half: nothing is faked as read anywhere, locally or remote.
 *
 * Toggling off simply stops gating: the next open marks through the
 * normal paths as usual. Nothing is retro-marked at toggle time.
 *
 * Storage follows the ROUND-31 per-account scheme (VoStore): the prefs
 * file name carries the signed-in uid (`tiku_ghost_prefs_<uid>`), so
 * on a shared phone one account's choice never leaks into another's.
 * [enabled] additionally self-gates on [OwnerState.isOwner] — even a
 * leftover pref can never engage the bypass for a non-owner account.
 */
object GhostStore {

    /** Compose subscription tick: bumped on every toggle so the
     *  settings row re-renders with the fresh value. */
    var stateVersion by mutableIntStateOf(0)
        private set

    /** The fx logs' uid namespace (Chat.kt, ROUND 28): sanitized,
     *  capped, "anon" when signed out — never a crash, never a leak
     *  into another account's file. */
    private fun uidKey(): String =
        (if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid())
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(64)
            .ifEmpty { "anon" }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("tiku_ghost_prefs_${uidKey()}", Context.MODE_PRIVATE)

    /** True ONLY for the owner with his toggle on (default OFF). Every
     *  other account reads false, so every gated path is a no-op. */
    fun enabled(ctx: Context): Boolean =
        OwnerState.isOwner && prefs(ctx).getBoolean("ghost", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("ghost", on).apply()
        stateVersion++
    }
}

// ================= 2. HIDE RECOVERY =================

/**
 * One message the signed-in user hid with Delete-for-me, as recorded
 * by the engine's hide registry (see FirebaseEngine.noteHidden): the
 * chat it belongs to plus the decrypted copy this device holds.
 * [message] may be a metadata stub (a light preview copy, or a
 * tombstone whose ciphertext is gone) — the page renders those as
 * metadata lines, never as invented content.
 */
data class HiddenMessage(
    val chatId: String,
    val message: TikuMessage,
)

/** The preview line for a hidden entry: real text when this device
 *  holds it, an honest placeholder otherwise. */
private fun hiddenPreview(m: TikuMessage): String = when {
    m.deletedForEveryone -> "[Deleted message]"
    m.undecryptable -> "This message can't be opened on this phone"
    m.kind == MsgKind.TEXT -> m.text.ifEmpty { "[Message]" }
    m.kind == MsgKind.IMAGE -> "[Photo]"
    m.kind == MsgKind.VIDEO -> "[Video]"
    m.kind == MsgKind.VOICE -> "[Voice message]"
    m.kind == MsgKind.AUDIO -> "[Audio file]"
    m.kind == MsgKind.CALL -> "[Call]"
    else -> "[Message]"
}

/**
 * The OWNER-ONLY "Hidden by me" page (Settings ▸ Owner): every
 * Delete-for-me hide this device has decrypted, newest first, with an
 * Unhide action per entry that removes the hide (the engine drops my
 * uid from the doc's `deletedFor` array) so the message returns to
 * its chat. Coverage is the honest one: messages this phone loaded —
 * the open chat's pages and each chat's preview window — because the
 * registry is fed by the same decrypt funnel that builds the chats.
 */
@Composable
fun HiddenMessagesPage(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val hidden = remember(tick) { AppState.repo.hiddenMessages() }
    val fmt = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    SubPage("Hidden by me", onBack) {
        if (!OwnerState.isOwner) {
            item {
                Text(
                    "Available on the owner account only.",
                    color = Sub, fontSize = 15.sp,
                )
            }
        } else if (hidden.isEmpty()) {
            item {
                Text(
                    "Nothing hidden right now. Messages you hide with " +
                        "Delete for me wait here — unhide one and it " +
                        "returns to its chat.",
                    color = Sub, fontSize = 15.sp,
                )
            }
        } else {
            items(hidden, key = { it.chatId + "|" + it.message.id }) { h ->
                val m = h.message
                val chatName = AppState.thread(h.chatId)?.displayName
                    ?.takeIf { it.isNotBlank() } ?: "Chat"
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)).background(CardC)
                        .padding(14.dp),
                ) {
                    Text(
                        if (m.me) "$chatName · You" else chatName,
                        color = Rose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        hiddenPreview(m), color = Color.White, fontSize = 15.sp,
                        fontFamily = rememberEmojiFontFamily(),
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Sent ${fmt.format(Date(m.tsMs))}",
                            color = Sub, fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Unhide", color = Rose, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                AppState.repo.unhideForMe(h.chatId, m.id)
                                tick++
                                AppState.toast("Message restored")
                            }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ================= 3. CHAT EXPORT =================

/**
 * OWNER-ONLY chat export: a plain-text transcript of the messages
 * currently loaded for one chat (the open chat's window — a chat
 * exports what this device holds, the same honesty as every other
 * local surface), shared as a .txt through the standard Android
 * share sheet. Media rides as bracketed placeholders; tombstones
 * export as [Deleted message] — the transcript reflects the chat as
 * it honestly stands, so the owner vault's kept copies of deleted
 * messages are NEVER exported from here (the vault has its own
 * surfaces). Messages the owner hid with Delete-for-me are excluded,
 * exactly as the chat itself excludes them.
 *
 * The file lands in the app cache and goes out through the existing
 * FileProvider (`tiku.app.fileprovider`, whose cache-path covers the
 * whole cache dir — the same provider the camera captures use).
 */
object ChatExport {

    private fun bodyOf(m: TikuMessage): String = when {
        m.deletedForEveryone -> "[Deleted message]"
        m.undecryptable -> "[Message can't be opened on this phone]"
        m.kind == MsgKind.TEXT -> m.text
        m.kind == MsgKind.IMAGE -> "[Photo]"
        m.kind == MsgKind.VIDEO -> "[Video]"
        m.kind == MsgKind.VOICE -> "[Voice message]"
        m.kind == MsgKind.AUDIO -> "[Audio]"
        m.kind == MsgKind.CALL -> {
            val what = if (m.callVideo) "Video call" else "Voice call"
            val how = when (m.callOutcome) {
                "ended" -> "ended"
                "declined" -> "declined"
                "noanswer" -> "no answer"
                else -> "call"
            }
            "[$what — $how]"
        }
        else -> "[Message]"
    }

    fun buildText(thread: ChatThread): String {
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        val myName = FirebaseEngine.myPerson.name
            .takeIf { it.isNotBlank() && it != "You" }
            ?: AppState.myUsername.ifEmpty { "You" }
        val peer = thread.displayName
        val sb = StringBuilder()
        sb.append("Tiku chat with ").append(peer)
        if (thread.person.username.isNotBlank()) {
            sb.append(" (@").append(thread.person.username).append(")")
        }
        sb.append('\n')
        sb.append("Exported ").append(fmt.format(Date(System.currentTimeMillis())))
        sb.append("\n\n")
        val visible = thread.messages.filter { !it.deletedForMe }.sortedBy { it.tsMs }
        if (visible.isEmpty()) {
            sb.append("(No messages loaded)\n")
        }
        for (m in visible) {
            sb.append('[').append(fmt.format(Date(m.tsMs))).append("] ")
                .append(if (m.me) myName else peer).append(": ")
                .append(bodyOf(m)).append('\n')
        }
        return sb.toString()
    }

    fun share(ctx: Context, thread: ChatThread) {
        val ok = runCatching {
            val safe = thread.person.username.ifBlank { thread.id }
                .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                .ifEmpty { "chat" }
            val f = File(ctx.cacheDir, "tiku-chat-$safe.txt")
            f.writeText(buildText(thread), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, "tiku.app.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Tiku chat with ${thread.displayName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Export chat"))
            true
        }.getOrDefault(false)
        if (!ok) AppState.toast("Couldn't export this chat")
    }
}
