package com.tiku.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * ROUND 78 (WS-3) — the outgoing SEND OUTBOX.
 *
 * The vanish this kills: every send used to stage an optimistic local
 * in memory plus a fire-and-forget coroutine whose FIRST hop is
 * `ensureChatDoc(...).await()` — a Firestore write Task that cannot
 * complete while the network is down, so the message's own
 * `msgRef.set()` was not even queued until connectivity returned. If
 * the process died while delivery was parked there (bad network is
 * exactly when a phone gets pocketed, backgrounded and killed — and
 * Vivo kills aggressively), the write had never been queued anywhere
 * and the local was memory-only: the message ceased to exist.
 *
 * The outbox makes a send durable BEFORE any async hop: at staging
 * time the send functions persist a [PendingSend] here (device-local,
 * per-account SharedPreferences `tiku_outbox_<uid>` — the DraftStore
 * pattern; never Firestore, never synced, no message-doc field, so
 * the schema is untouched). Delivery success removes the entry;
 * failure keeps it. The engine drains the box when it starts, when
 * the threads list (re)builds, and when connectivity returns: an
 * entry whose doc already landed (its id is in the message cache) is
 * dropped WITHOUT resending — the write is a `set()` of the same
 * client-chosen doc id, so even a blind re-delivery is idempotent —
 * and every other entry is re-staged as a SENDING bubble and
 * delivered again. A sent message therefore survives bad networks,
 * process death and app restarts, and lands exactly once.
 *
 * Payloads: text rides inline; media rides as a FILE — the voice /
 * video / audio / game source file that already lives in the app
 * cache, or, for a photo, a staged copy of the compressed bytes the
 * send itself produced (`outbox_<msgId>.jpg` in the cache dir,
 * written synchronously at staging so the entry never points at
 * bytes that exist only in a dead coroutine's memory).
 */
object OutboxStore {

    /** One persisted pending send. [kind] is the wire vocabulary the
     *  engine's drain switches on: "text" | "image" | "voice" |
     *  "video" | "audio" | "game". */
    data class PendingSend(
        val msgId: String,
        val chatId: String,
        val kind: String,
        val tsMs: Long,
        // text payload
        val text: String = "",
        val replyQuote: String? = null,
        val replyToId: String? = null,
        val fx: String? = null,
        val ai: Boolean = false,
        // media payload
        val localPath: String? = null,
        val voiceDurSec: Int = 0,
        val amps: List<Int> = emptyList(),
        val mimeType: String = "",
        val vo: Int? = null,
        val gameTitle: String = "",
    )

    private const val KEY = "entries"

    // ================= ROUND 80 (WS-Q2): the OWED-NOTIFY section =================
    // A second, independent section in the SAME per-account prefs
    // file, under its own key ("owed"). When a message's push POST
    // to the notify Worker finishes with a non-2xx verdict (the
    // classic case: the Worker's Firestore reads die while the
    // project's free read quota is burned), the message itself is
    // already safe in Firestore — only its NOTIFICATION is owed.
    // PushSender parks an [OwedNotify] here and the engine's drain
    // (drainOutbox → drainOwedNotifies) retries the push on the
    // next drain trigger, for up to [OWED_MAX_AGE_MS]. Calls never
    // owe (a ring hours later is wrong). This section has its own
    // raw helpers and never touches the "entries" key or its logic.

    /** One owed notification retry. [type] is the Worker message
     *  vocabulary (text / ai_text / photo / video / voice); [ct]/[iv]
     *  are the message's own stored ciphertext, riding only for the
     *  text types — exactly what the original push carried. */
    data class OwedNotify(
        val msgId: String,
        val chatId: String,
        val peerUid: String,
        val type: String,
        val ct: String? = null,
        val iv: String? = null,
        val queuedAtMs: Long,
    )

    private const val OWED_KEY = "owed"

    /** An owed notify older than this is dropped, never retried —
     *  a notification delivered hours late is noise, not help. */
    const val OWED_MAX_AGE_MS = 6L * 3600 * 1000

    /** In-memory stand-in used whenever no prefs file is available
     *  (preview/harness processes, or a missing app context) — the
     *  DraftStore discipline, so the harness can exercise the store's
     *  logic directly. */
    private val mem = mutableMapOf<String, String>()

    private fun storeKey(): String =
        (if (Config.PREVIEW_UI_ONLY) "preview" else runCatching { myUidKey() }.getOrDefault(""))
            .replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun prefs(): SharedPreferences? {
        if (Config.PREVIEW_UI_ONLY) return null
        return runCatching {
            AppState.appCtx.getSharedPreferences("tiku_outbox_${storeKey()}", Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private fun readRaw(): String {
        val p = prefs()
        if (p != null) return p.getString(KEY, "") ?: ""
        return synchronized(mem) { mem[KEY] ?: "" }
    }

    private fun writeRaw(raw: String) {
        val p = prefs()
        if (p != null) {
            val e = p.edit()
            if (raw.isEmpty()) e.remove(KEY) else e.putString(KEY, raw)
            e.apply()
        }
        synchronized(mem) {
            if (raw.isEmpty()) mem.remove(KEY) else mem[KEY] = raw
        }
    }

    // ---- pure encode / decode / drain planning (unit-tested) ----

    internal fun encodeEntries(entries: List<PendingSend>): String {
        val arr = JSONArray()
        for (e in entries) {
            val o = JSONObject()
            o.put("id", e.msgId); o.put("chat", e.chatId); o.put("kind", e.kind)
            o.put("ts", e.tsMs)
            o.put("text", e.text)
            e.replyQuote?.let { o.put("rq", it) }
            e.replyToId?.let { o.put("rid", it) }
            e.fx?.let { o.put("fx", it) }
            if (e.ai) o.put("ai", true)
            e.localPath?.let { o.put("path", it) }
            if (e.voiceDurSec != 0) o.put("dur", e.voiceDurSec)
            if (e.amps.isNotEmpty()) o.put("amps", JSONArray(e.amps))
            if (e.mimeType.isNotEmpty()) o.put("mime", e.mimeType)
            e.vo?.let { o.put("vo", it) }
            if (e.gameTitle.isNotEmpty()) o.put("gt", e.gameTitle)
            arr.put(o)
        }
        return arr.toString()
    }

    internal fun decodeEntries(raw: String): List<PendingSend> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<PendingSend>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ampsArr = o.optJSONArray("amps")
                out += PendingSend(
                    msgId = o.getString("id"),
                    chatId = o.getString("chat"),
                    kind = o.getString("kind"),
                    tsMs = o.optLong("ts", 0L),
                    text = o.optString("text", ""),
                    replyQuote = if (o.has("rq")) o.getString("rq") else null,
                    replyToId = if (o.has("rid")) o.getString("rid") else null,
                    fx = if (o.has("fx")) o.getString("fx") else null,
                    ai = o.optBoolean("ai", false),
                    localPath = if (o.has("path")) o.getString("path") else null,
                    voiceDurSec = o.optInt("dur", 0),
                    amps = if (ampsArr != null) List(ampsArr.length()) { ampsArr.getInt(it) } else emptyList(),
                    mimeType = o.optString("mime", ""),
                    vo = if (o.has("vo")) o.getInt("vo") else null,
                    gameTitle = o.optString("gt", ""),
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    /** The RENDER selection, pure (ROUND 79): the entries for
     *  [chatId] whose doc id is NOT in [present] (the ids a list
     *  rebuild has already accounted for — server cache, snapshot
     *  window, or a copy already visible). Both of the engine's
     *  list-building paths select their pending locals through this
     *  one rule, so a staged send is re-derived from the store on
     *  every rebuild instead of depending on a copy that some other
     *  rebuild may have dropped. */
    internal fun selectPending(
        entries: List<PendingSend>,
        chatId: String,
        present: Set<String>,
    ): List<PendingSend> = entries.filter { it.chatId == chatId && it.msgId !in present }

    /** The drain decision, pure: an entry whose message doc is
     *  already known on the server ([landedIds] — the engine passes
     *  the ids its message cache holds) is DROPPED without a resend
     *  (exactly-once); every other entry is DELIVERED. */
    internal fun planDrain(
        entries: List<PendingSend>,
        landedIds: Set<String>,
    ): Pair<List<PendingSend>, List<PendingSend>> {
        val drop = entries.filter { it.msgId in landedIds }
        val deliver = entries.filter { it.msgId !in landedIds }
        return deliver to drop
    }

    // ---- store operations ----

    @Synchronized
    fun enqueue(entry: PendingSend) {
        val cur = decodeEntries(readRaw()).filter { it.msgId != entry.msgId }
        writeRaw(encodeEntries(cur + entry))
    }

    @Synchronized
    fun remove(msgId: String) {
        val cur = decodeEntries(readRaw())
        if (cur.none { it.msgId == msgId }) return
        writeRaw(encodeEntries(cur.filter { it.msgId != msgId }))
    }

    fun all(): List<PendingSend> = decodeEntries(readRaw())

    fun contains(msgId: String): Boolean = all().any { it.msgId == msgId }

    /** Test/harness hygiene: empty the box. */
    @Synchronized
    fun clearAll() = writeRaw("")

    // ---- ROUND 80 (WS-Q2): owed-notify raw helpers (own key) ----

    private fun readOwedRaw(): String {
        val p = prefs()
        if (p != null) return p.getString(OWED_KEY, "") ?: ""
        return synchronized(mem) { mem[OWED_KEY] ?: "" }
    }

    private fun writeOwedRaw(raw: String) {
        val p = prefs()
        if (p != null) {
            val e = p.edit()
            if (raw.isEmpty()) e.remove(OWED_KEY) else e.putString(OWED_KEY, raw)
            e.apply()
        }
        synchronized(mem) {
            if (raw.isEmpty()) mem.remove(OWED_KEY) else mem[OWED_KEY] = raw
        }
    }

    // ---- ROUND 80 (WS-Q2): owed encode / decode / planning (unit-tested) ----

    internal fun encodeOwed(entries: List<OwedNotify>): String {
        val arr = JSONArray()
        for (e in entries) {
            val o = JSONObject()
            o.put("id", e.msgId); o.put("chat", e.chatId); o.put("peer", e.peerUid)
            o.put("type", e.type)
            e.ct?.let { o.put("ct", it) }
            e.iv?.let { o.put("iv", it) }
            o.put("at", e.queuedAtMs)
            arr.put(o)
        }
        return arr.toString()
    }

    internal fun decodeOwed(raw: String): List<OwedNotify> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<OwedNotify>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += OwedNotify(
                    msgId = o.getString("id"),
                    chatId = o.getString("chat"),
                    peerUid = o.getString("peer"),
                    type = o.getString("type"),
                    ct = if (o.has("ct")) o.getString("ct") else null,
                    iv = if (o.has("iv")) o.getString("iv") else null,
                    queuedAtMs = o.optLong("at", 0L),
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    /** The owed-drain decision, pure: an entry queued longer than
     *  [maxAgeMs] ago is DROPPED (too stale to notify now); every
     *  fresher entry is DELIVERED (retried). Boundary: an age of
     *  exactly [maxAgeMs] still delivers — only strictly older
     *  drops. Returns (deliver, drop), mirroring [planDrain]. */
    internal fun planOwed(
        entries: List<OwedNotify>,
        nowMs: Long,
        maxAgeMs: Long,
    ): Pair<List<OwedNotify>, List<OwedNotify>> {
        val drop = entries.filter { nowMs - it.queuedAtMs > maxAgeMs }
        val deliver = entries.filter { nowMs - it.queuedAtMs <= maxAgeMs }
        return deliver to drop
    }

    // ---- ROUND 80 (WS-Q2): owed store operations ----

    /** Park (or re-park) one owed notify. Dedupes by msgId: an
     *  existing entry for the same message is REPLACED, never
     *  duplicated — one message can only ever owe one push. */
    @Synchronized
    fun enqueueOwed(entry: OwedNotify) {
        val cur = decodeOwed(readOwedRaw()).filter { it.msgId != entry.msgId }
        writeOwedRaw(encodeOwed(cur + entry))
    }

    @Synchronized
    fun removeOwed(msgId: String) {
        val cur = decodeOwed(readOwedRaw())
        if (cur.none { it.msgId == msgId }) return
        writeOwedRaw(encodeOwed(cur.filter { it.msgId != msgId }))
    }

    fun owedAll(): List<OwedNotify> = decodeOwed(readOwedRaw())
}
