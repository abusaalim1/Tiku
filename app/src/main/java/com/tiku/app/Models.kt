package com.tiku.app

import android.graphics.Bitmap

/** UI-side message kind, mirrored by the Firestore `kind` field.
 *  CALL (round 4) is a system event rendered as a centred pill, never a bubble:
 *  an encrypted note that a call happened (missed / declined / ended). The
 *  WebRTC signaling record is still deleted in full — this is a chat message,
 *  not a call log. ROUND 6 SUPERSESSION (owner's new rule): the Calls tab now
 *  aggregates THESE in-chat call-event messages into a history list; that is
 *  the only call surface that changed — signaling deletion is untouched. */
/** ROUND 15: VIDEO (a video file, Firestore kind "video") and AUDIO (an
 *  audio FILE, kind "audio") join the media kinds. AUDIO is deliberately
 *  distinct from VOICE: a voice note is recorded in-app, auto-downloads
 *  with the chat and renders the waveform player; an audio file is picked
 *  or captured, carries a mime type, and — like VIDEO — loads its payload
 *  ONLY on tap (no auto-download). Both ride the same encrypted chunk
 *  pipeline as IMAGE/VOICE. */
enum class MsgKind { TEXT, IMAGE, VOICE, CALL, VIDEO, AUDIO, GAME }

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
    /** Media duration in seconds: the voice note's length, and (round 15)
     *  the VIDEO / AUDIO file's duration — the Firestore `durationMs`
     *  field carries it for every media kind. */
    val voiceDurSec: Int = 0,
    val amps: List<Int> = emptyList(),
    val photo: Bitmap? = null,          // decrypted image bytes, in-memory/cache only
    val replyQuote: String? = null,
    /** ROUND 14: the Firestore `replyToId` of the quoted message. The
     *  sentinel "__note__" marks a note reply (the quote snapshot text is
     *  "Note: <text>", encrypted via rqCt/rqIv like every quote). */
    val replyToId: String? = null,
    val reactions: Map<String, String> = emptyMap(), // uid -> emoji, plaintext per design
    val starredByMe: Boolean = false,
    val deletedForMe: Boolean = false,
    val tsMs: Long = 0L,                // server timestamp (ms) for read watermarks
    val localPath: String? = null,      // decrypted voice file in app-private cache
    val status: SendStatus = SendStatus.SENT, // outgoing delivery state (mine only)
    // ---- round 4 ----
    /** True only when the PEER's uid is in the doc's seenBy — drives the
     *  IG-style "Seen" caption (round 8; ticks were removed). Derived from
     *  the Firestore field on every snapshot, never local state, so it
     *  survives re-entry / restore / a second device. */
    val seenByPeer: Boolean = false,
    /** CALL events: who placed the call, how it resolved, how long it lasted.
     *  outcome ∈ "ended" | "declined" | "noanswer". The pill renders
     *  perspective-correct copy by comparing callCallerUid to my uid. */
    val callCallerUid: String = "",
    val callOutcome: String = "",
    val callDurationSec: Int = 0,
    val callVideo: Boolean = false,
    /** True when the VIEWER placed this call — computed where the message is
     *  built (engine decrypt / preview seed), never in composition, so the
     *  pill renders perspective copy without touching Firebase at draw time. */
    val callMine: Boolean = false,
    /** ROUND 8: chunk count of the media payload on the server doc. Lets the
     *  incremental snapshot path tell "metadata-only change" (seen/reaction —
     *  reuse the in-memory payload, skip the chunk re-download) from "payload
     *  actually changed" (upload finished — download it). */
    val mediaChunks: Int = 0,
    /** ROUND 10: server timestamp (ms) of the last in-place edit of a TEXT
     *  message (0 = never edited). Drives the muted "Edited" caption; the
     *  ciphertext itself is replaced through the normal encrypt path. */
    val editedAtMs: Long = 0L,
    // ---- round 15 (appended last on purpose: every existing positional
    // TikuMessage construction keeps compiling unchanged) ----
    /** Mime type of a VIDEO / AUDIO file payload ("" for the older kinds —
     *  images are always the JPEG pipeline, voice notes always m4a).
     *  Drives the cache-file extension and the MediaStore save mime.
     *  Firestore field `mime`. For a loaded VIDEO, [photo] doubles as the
     *  poster frame extracted from the decrypted file. */
    val mimeType: String = "",
    /** Plaintext byte size of a VIDEO / AUDIO payload (shown on the
     *  not-yet-loaded bubble; the send caps are enforced against it).
     *  Firestore field `mediaBytes`. */
    val mediaBytes: Long = 0L,
    // ---- round 18 (appended last, same discipline as round 15) ----
    /** Tombstone: the SENDER deleted this message for everyone (Firestore
     *  `delAll=true`). The doc stays — chats never disappear — but its
     *  ciphertext was wiped by the delete, and every surface renders the
     *  "This message was deleted" tombstone instead of any content.
     *  Distinct from [deletedForMe] (a local hide that only affects me). */
    val deletedForEveryone: Boolean = false,
    // ---- round 19 (appended last, same discipline) ----
    /** This device cannot open the message's ciphertext (e.g. history
     *  written under a retired keypair from before a journal reset). The
     *  engine builds these from the doc's plaintext metadata ONLY (kind /
     *  sender / ts) when decryption fails, and every surface renders the
     *  honest placeholder "This message can't be opened on this phone"
     *  instead of a blank bubble. Local optimistic copies and anything
     *  decryptable NEVER carry this flag, and a stub must never overwrite
     *  a copy whose plaintext this device already holds. */
    val undecryptable: Boolean = false,
    // ---- round 22 (appended last, same discipline) ----
    /** Instagram-style send effect (Firestore `fx`, plaintext metadata
     *  exactly like `reaction`): "gift" | "fire" | "confetti" |
     *  "hearts", null = no effect. The field is ADDITIVE — docs written
     *  by older builds simply lack it (fx reads null), and older builds
     *  reading a doc that carries it ignore the unknown field and render
     *  the plain message, so cross-version messaging never breaks.
     *  "gift" seals the text behind a tap-to-open wrap on every surface
     *  (bubble, list preview, notification, reply quote) until THIS
     *  device opens it; the other three are one-shot overlay animations
     *  played when the message is sent / arrives in the open chat, and
     *  replayed by tapping the bubble. TEXT messages only. */
    val fx: String? = null,
    // ---- round 31 (appended last, same discipline) ----
    /** View-once allowance the SENDER chose for a PHOTO / VIDEO
     *  (Firestore `vo`): 1 = view once, 2 = view twice, null = unlimited
     *  (the field is absent — docs from older builds read null and
     *  render as plain media, and older builds reading a `vo` doc
     *  ignore the field and show the media normally: the accepted
     *  cross-version shape of an additive field). Enforcement is
     *  client-side on the recipient's phone — see VoStore. */
    val vo: Int? = null,
    /** The recipient's client exhausted the [vo] allowance and wrote
     *  the additive `voDone=true` on the doc (member write — the same
     *  class as `reaction`), so the SENDER's bubble can show "Opened".
     *  Absent on every doc the recipient hasn't finished viewing. */
    val voDone: Boolean = false,
    // ---- round 37 (appended last, same discipline) ----
    /** Authored by the in-chat AI (Tika) on behalf of the sender
     *  (Firestore `ai=true`): a `/tiku` text answer or a `/tika`
     *  generated image, written by the ASKING client through the
     *  ordinary encrypted send path after the Gemini call returned.
     *  The doc's senderId is still the asking user — the flag only
     *  drives rendering (left-aligned "✨ Tika AI" bubble). Additive:
     *  older builds lack it, read false, and render the message as a
     *  normal message from the sender. */
    val ai: Boolean = false,
    // ---- round 41 (appended last, same discipline) ----
    /** An AI-built 2-player GAME (Firestore `game=true`): the payload
     *  riding the chunk pipeline (mime "text/html") is ONE self-contained
     *  HTML file Tika wrote for the `/babeg` command — never shown as
     *  code in the chat, only as a tappable game card + full-screen
     *  player (GamePlayer.kt). The doc kind is "game"; older builds map
     *  the unknown kind to a plain (empty) TEXT bubble — degraded, never
     *  a crash — and ignore both additive fields. Local optimistic and
     *  loaded copies hold the HTML file in [localPath] (dl_<id>.html),
     *  exactly like the VIDEO/AUDIO file kinds. */
    val game: Boolean = false,
    /** The game's display title, derived from the asker's command text
     *  (first ~48 chars) and stored as the plaintext doc field `gtitle`
     *  — metadata, readable on the not-yet-loaded stub like mime/size,
     *  so the card can name the game before its bytes are fetched. */
    val gameTitle: String = "",
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
    /** ROUND 6: the peer's nickname in THIS chat (Instagram semantics — set
     *  by either member from inside the chat, stored on the chat doc as
     *  `nicknames[peerUid]`, so it shows for BOTH members). Null = no nickname;
     *  [displayName] then falls back to the profile name. */
    val nickname: String? = null,
    /** ROUND 8: the engine has older history beyond the currently loaded
     *  window (pagination — the chat opens at the newest ~60 messages and
     *  pages older ones in on scroll-up). UI-only bookkeeping. */
    val hasOlder: Boolean = false,
    // ---- ROUND 10: per-chat details (chat-doc metadata, shared by both
    // members exactly like the nicknames map) ----
    /** MY nickname in this chat (nicknames[myUid] on the chat doc) — the
     *  peer sets it, I only display it. [nickname] above is the peer's. */
    val myNickname: String? = null,
    /** Per-chat theme: index into TikuTokens.Accents / WallpaperMode,
     *  stored on the chat doc (themeAccent / themeWallpaper). Null = fall
     *  back to the global appearance settings. */
    val themeAccent: Int? = null,
    val themeWallpaper: Int? = null,
    /** True when MY uid sits in the chat doc's receiptsOff array: my client
     *  must not write seenBy marks in this chat, and the "Seen" caption
     *  under my own messages stays hidden. */
    val receiptsOffMine: Boolean = false,
    /** Pinned message ids for this chat (chat doc pinnedMsgs, max 3,
     *  oldest first — the strip shows the most recent pin). */
    val pinnedMsgIds: List<String> = emptyList(),
    // ---- ROUND 14: message requests + Info "together" pill ----
    /** True while this thread is a message REQUEST: the partner sent the
     *  first message(s), I have never sent one, and I have not accepted
     *  (chat doc acceptedFor map). Requests live on the Requests screen,
     *  never in the main Messages list. Derived by the engine on every
     *  snapshot; preview seeds set it directly. */
    val isRequest: Boolean = false,
    /** Thread creation time (chat doc createdAt when present, else the
     *  earliest known message ts, else the doc's updatedAt). Drives the
     *  Info screen's "Day N together" pill; 0 = unknown (pill omitted). */
    val createdAtMs: Long = 0L,
    /** True while I have locally hidden this thread (chat doc
     *  hiddenFor[me]) and the partner has not sent a newer message since.
     *  The engine KEEPS hidden threads in AppState.threads (their preview
     *  listeners must keep running so a new partner message can un-hide
     *  them); every list surface filters them out. */
    val hiddenForMe: Boolean = false,
    // ---- ROUND 20 (appended last, same discipline) ----
    /** The unread count as it stood the moment this chat was OPENED —
     *  recorded by the engine inside openThread BEFORE markRead advances
     *  the read watermark and the live listener zeroes [unread]. The chat
     *  screen pins its "N new messages" divider from THIS value: reading
     *  `unread` at first composition raced the engine's zeroing and lost
     *  on device (rounds 18/19 — the divider never appeared). UI-only
     *  bookkeeping, rewritten on every open. */
    val unreadAtOpen: Int = 0,
    // ROUND 26 — the shared chat theme (chats/{id}.theme): "" = Default.
    // Additive + a plain string, so old builds ignore the field entirely.
    val themeId: String = "",
    // ROUND 35 — when [themeId] is a custom gallery theme
    // ("custom" / "custom:<paletteId>"), this is the server timestamp
    // (ms) of the encrypted theme asset at chats/{id}/themeasset/main
    // (chat doc field themeAssetAt). It versions the on-device cache:
    // a strictly newer value means the photo was replaced and must be
    // re-fetched. 0 = no asset known. Old builds ignore the field.
    val themeAssetAt: Long = 0L,
) {
    /** What every surface renders as this chat's title: nickname first,
     *  profile name otherwise. Real name + @username stay visible in the
     *  chat details sheet. */
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: person.name
}

/** ROUND 10: the settings a chat surface actually renders with — the global
 *  appearance, overridden by this chat's own theme (accent + wallpaper)
 *  wherever the chat doc carries one. Everything else (text size, toggles)
 *  stays global. */
/** ROUND 13 — per-chat theme index codec.
 *
 *  Firestore's `themeAccent` int predates the couple themes: build-44 peers
 *  wrote/read legacy swatch indices 0..5, and a build-44 client clamps any
 *  stored value into its own couple slot. To keep both generations sane
 *  WITHOUT touching the engine:
 *   - values >= 10 are ROUND-13 space: stored - 10 = couple theme 0..4.
 *     (A build-44 peer reading 10..14 clamps to its couple accent — graceful.)
 *   - legacy 0..3 map to the same couple theme; 4 -> 1; 5 -> 0 (build 44's
 *     couple index, and PreviewStore's seeded value, both mean Rose Petal).
 *  New writes from this build always use [toStored]. */
object ThemeCodec {
    fun toCoupleIndex(stored: Int): Int = when {
        stored in 10..14 -> stored - 10
        stored in 0..3 -> stored
        stored == 4 -> 1
        stored == 5 -> 0
        else -> 0
    }

    fun toStored(coupleIdx: Int): Int = 10 + coupleIdx.coerceIn(0, COUPLE_THEMES.lastIndex)
}

fun ChatThread.effectiveSettings(global: TikuUiSettings): TikuUiSettings {
    var s = global
    themeAccent?.let { s = s.copy(accentIdx = ThemeCodec.toCoupleIndex(it)) }
    themeWallpaper?.let { idx ->
        s = s.copy(wallpaper = WallpaperMode.entries[idx.coerceIn(0, WallpaperMode.entries.lastIndex)])
    }
    return s
}

/** ROUND 14 — one user's public note (users/{uid}: noteText / noteAt /
 *  noteHearts). Visible to thread partners for 24h after [atMs]. [hearts]
 *  is the set of uids who hearted it. The map in AppState.notes is keyed
 *  by owner uid; "mine" is simply uid == my uid. */
data class NoteUi(
    val uid: String = "",
    val text: String = "",
    val hearts: Set<String> = emptySet(),
    val atMs: Long = 0L,
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

/** Username search normalization (round 4, item 4). Pure + shared by the real
 *  engine (Firestore field writes + queries) and the preview store.
 *  norm(): lowercase, letters/digits only — "Abu Saalim", "abu_saalim" and
 *  "ABU_SAALIM" all collapse to "abusaalim".
 *  tokens(): the name-parts of username + display name (split on anything
 *  non-alphanumeric) plus the full norm — so "saalim" alone finds abu_saalim
 *  through an array-contains query on the tokens field. */
object SearchNorm {
    fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    fun tokens(username: String, displayName: String): List<String> {
        val parts = "$username $displayName".lowercase()
            .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return (parts + norm(username)).distinct()
    }

    /** Local matcher mirroring the Firestore query pair (prefix on norm +
     *  token membership), used by preview mode. */
    fun matches(username: String, displayName: String, rawQuery: String): Boolean {
        val q = norm(rawQuery)
        if (q.isEmpty()) return false
        if (norm(username).startsWith(q)) return true
        val toks = tokens(username, displayName)
        val parts = rawQuery.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        return parts.any { it in toks }
    }
}

/** Firestore schema mirrors (real mode). */
object Schema {
    object Users {
        const val COLL = "users"
        const val USERNAME_CLAIM = "usernames"
        data class Doc(
            val username: String = "", val displayName: String = "", val photoUrl: String = "",
            val publicKey: String = "", val keyBackup: Map<String, String> = emptyMap(),
            val online: Boolean = false, val lastSeen: Any? = null,
            /** Round 4 search fields (see SearchNorm): usernameNorm powers the
             *  prefix range query, nameTokens the array-contains query. */
            val usernameNorm: String = "", val nameTokens: List<String> = emptyList(),
        )
    }
    object Chats {
        const val COLL = "chats"
        const val MESSAGES = "messages"
        data class Doc(
            val participants: List<String> = emptyList(),
            /** ROUND 22: typing presence stamps — uid -> epoch ms of
             *  that member's last typing refresh (was Map<String,
             *  Boolean> before round 22; readers treat a legacy Boolean
             *  true as fresh-on-arrival, see FirebaseEngine). */
            val typing: Map<String, Long> = emptyMap(),
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
            /** ROUND 18: sender's delete-for-everyone tombstone flag. */
            val delAll: Boolean = false,
            /** ROUND 30: server timestamp of the tombstone write, riding
             *  the same update as [delAll]. Additive — older tombstones
             *  lack it. Read only by the OWNER's client (vault log). */
            val delAt: Any? = null,
            /** ROUND 22: Instagram-style send effect ("gift" | "fire" |
             *  "confetti" | "hearts"); absent/null = none. Additive —
             *  see [TikuMessage.fx]. */
            val fx: String? = null,
            /** ROUND 31: view-once allowance for image/video (1 or 2);
             *  absent = unlimited. Additive — see [TikuMessage.vo]. */
            val vo: Int? = null,
            /** ROUND 31: recipient exhausted the view-once allowance.
             *  Additive — see [TikuMessage.voDone]. */
            val voDone: Boolean = false,
            /** ROUND 37: authored by the in-chat AI (Tika). Additive —
             *  see [TikuMessage.ai]. */
            val ai: Boolean = false,
            /** ROUND 41: an AI-built game message (kind "game", payload
             *  = one HTML file in the chunks). Additive — see
             *  [TikuMessage.game] / [TikuMessage.gameTitle]. */
            val game: Boolean = false,
            val gtitle: String = "",
            val seenBy: List<String> = emptyList(),
            val durationMs: Int = 0, val amps: List<Int> = emptyList(),
            /** Media kinds (image/voice/video/audio): ciphertext lives in the chunks
              * subcollection; iv on the doc describes the whole-blob AES-GCM
              * encryption. Round 15: `mime` + `mediaBytes` ride the doc for
              * the VIDEO / AUDIO file kinds. */
            val mediaChunks: Int = 0,
            val mime: String = "", val mediaBytes: Long = 0L,
        )
    }
    object Moments {
        const val COLL = "moments"
        data class Item(val quote: String = "", val ts: Long = 0L)
    }
}
