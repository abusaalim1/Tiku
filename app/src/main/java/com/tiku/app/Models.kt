package com.tiku.app

import android.graphics.Bitmap

/** UI-side message kind, mirrored by the Firestore `kind` field. */
enum class MsgKind { TEXT, IMAGE, VOICE }

/** Delivery state of an outgoing message (round 3: no silent send failures).
 *  Local optimistic copies start SENDING; the Firestore write landing flips
 *  them SENT; any failure flips them FAILED and the bubble offers tap-to-retry. */
enum class SendStatus { SENDING, SENT, FAILED }

/** One chat message, plaintext on-device (always stored/decrypted on-device only). */
data class TikuMessage(
    val id: String = "",
    val senderUid: String = "",
    val kind: MsgKind = MsgKind.TEXT,
    val text: String = "",
    val time: String = "now",
    val me: Boolean = false,
    val voiceDurSec: Int = 0,
    val amps: List<Int> = emptyList(),
    val photo: Bitmap? = null,          // decrypted image bytes, in-memory/cache only
    val replyQuote: String? = null,
    val reactions: Map<String, String> = emptyMap(), // uid -> emoji, plaintext per design
    val starredByMe: Boolean = false,
    val deletedForMe: Boolean = false,
    val tsMs: Long = 0L,                // server timestamp (ms) for read watermarks
    val localPath: String? = null,      // decrypted voice file in app-private cache
    val status: SendStatus = SendStatus.SENT, // outgoing delivery state (mine only)
)

/** One 1:1 conversation. */
data class ChatThread(
    val id: String = "",
    val person: Person,
    val messages: MutableList<TikuMessage> = mutableListOf(),
    var unread: Int = 0,
    var time: String = "now",
    var pinned: Boolean = false,
    var muted: Boolean = false,
    var clearedBeforeMs: Long = 0L,
)

/** Contact / person. In real mode, username/uid come from Firestore. */
data class Person(
    val uid: String = "",
    val name: String,
    val username: String = "",
    val emoji: String = "🙂",
    val colorArgb: Int = 0xFFE4714C.toInt(),
    val photoUrl: String = "",
    val photoBitmap: Bitmap? = null,
    var online: Boolean = true,
    val lastSeenMs: Long = 0L,
)

/** Firestore schema mirrors (real mode). */
object Schema {
    object Users {
        const val COLL = "users"
        const val USERNAME_CLAIM = "usernames"
        data class Doc(
            val username: String = "", val displayName: String = "", val photoUrl: String = "",
            val publicKey: String = "", val keyBackup: Map<String, String> = emptyMap(),
            val online: Boolean = false, val lastSeen: Any? = null,
        )
    }
    object Chats {
        const val COLL = "chats"
        const val MESSAGES = "messages"
        data class Doc(
            val participants: List<String> = emptyList(),
            val typing: Map<String, Boolean> = emptyMap(),
            val clearedBefore: Map<String, Long> = emptyMap(),
            val updatedAt: Any? = null,
        )
    }
    object Messages {
        data class Doc(
            val senderId: String = "", val kind: String = "text",
            val ct: String = "", val iv: String = "",
            val ts: Any? = null,
            val replyToId: String? = null, val rqCt: String = "", val rqIv: String = "",
            val reaction: Map<String, String> = emptyMap(),
            val starredBy: List<String> = emptyList(),
            val deletedFor: List<String> = emptyList(),
            val seenBy: List<String> = emptyList(),
            val durationMs: Int = 0, val amps: List<Int> = emptyList(),
            /** Media kinds (image/voice): ciphertext lives in the chunks subcollection;
              * iv on the doc describes the whole-blob AES-GCM encryption. */
            val mediaChunks: Int = 0,
        )
    }
    object Moments {
        const val COLL = "moments"
        data class Item(val quote: String = "", val ts: Long = 0L)
    }
}
