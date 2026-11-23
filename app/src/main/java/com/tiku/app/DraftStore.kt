package com.tiku.app

import android.content.Context
import android.content.SharedPreferences

/**
 * ROUND 75 (WS5) — per-chat composer DRAFTS.
 *
 * Text typed into a chat composer (a NORMAL chat, or the Tika
 * companion) survives leaving the chat and is restored on re-entry;
 * it is cleared when the message is actually sent. Drafts are
 * LOCAL ONLY: a dedicated per-account SharedPreferences file
 * (`tiku_drafts_<uid>`) — never Firestore, never synced, never part
 * of any message doc (schema untouched, additive-only by absence).
 *
 * The pattern mirrors TikaBotStore: the prefs file is keyed by the
 * signed-in account (accounts sharing one phone never see each
 * other's drafts), and when [Config.PREVIEW_UI_ONLY] is on — or the
 * application context is somehow unavailable — an in-memory map
 * stands in, so the Roborazzi harness can stage a draft with
 * [setChat] / [setTika] and watch the composer restore it.
 *
 * Keys inside the file: a normal chat's draft lives under
 * `"c:<threadId>"`; the companion's under the reserved slot
 * `"tika"`. The prefix + reserved slot make a collision between the
 * companion's draft and any real thread id impossible, and switching
 * chats can never leak one chat's text into another — every read
 * and write names its exact slot.
 *
 * Writes happen as the text changes (the composers call [setChat] /
 * [setTika] from an effect on every input change), NOT only on
 * dispose: a draft therefore survives Back, navigation, and process
 * death alike. Clearing is just writing the empty string (the slot
 * is removed), driven by the same effect when a send empties the
 * field — a send that is refused before it enqueues (empty text,
 * companion busy) never empties the field, so its draft stands.
 */
object DraftStore {

    /** The companion's reserved slot name (see the key scheme above). */
    private const val TIKA_SLOT = "tika"

    /** In-memory stand-in used whenever no prefs file is available
     *  (preview/harness builds, or a missing app context). */
    private val mem = mutableMapOf<String, String>()

    /** The current account's store key — names the prefs file, so
     *  accounts sharing one phone never mix (TikaBotStore.storeKey,
     *  mirrored). */
    private fun storeKey(): String =
        (if (Config.PREVIEW_UI_ONLY) "preview" else runCatching { myUidKey() }.getOrDefault(""))
            .replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun prefs(): SharedPreferences? {
        if (Config.PREVIEW_UI_ONLY) return null
        return runCatching {
            AppState.appCtx.getSharedPreferences("tiku_drafts_${storeKey()}", Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private fun read(slot: String): String {
        val p = prefs()
        if (p != null) return p.getString(slot, "") ?: ""
        return synchronized(mem) { mem[slot] ?: "" }
    }

    private fun write(slot: String, text: String) {
        val p = prefs()
        if (p != null) {
            val e = p.edit()
            if (text.isEmpty()) e.remove(slot) else e.putString(slot, text)
            e.apply()
        }
        synchronized(mem) {
            if (text.isEmpty()) mem.remove(slot) else mem[slot] = text
        }
    }

    // ---- normal chats ----

    fun getChat(threadId: String): String = read("c:$threadId")

    fun setChat(threadId: String, text: String) = write("c:$threadId", text)

    // ---- the Tika companion ----

    fun getTika(): String = read(TIKA_SLOT)

    fun setTika(text: String) = write(TIKA_SLOT, text)
}
