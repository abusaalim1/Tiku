package com.tiku.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Local, fully-offline study store for the facade.
 *
 * Everything here stays on the device (SharedPreferences-backed JSON):
 * focused minutes per day, study streak, and the user's notes.
 * Facade data NEVER touches Firestore.
 */
object StudyStore {
    private const val PREFS = "tiku_study"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dayKey(d: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)

    // ---- focus minutes + streak ----
    private fun focusMap(ctx: Context): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString("focus", "{}") ?: "{}") }.getOrDefault(JSONObject())

    fun focusMinutesToday(ctx: Context): Int =
        focusMap(ctx).optInt(dayKey(), 0)

    /** Add completed minutes for today. Called when a focus session finishes or is stopped. */
    fun addFocusMinutes(ctx: Context, minutes: Int) {
        if (minutes <= 0) return
        val m = focusMap(ctx)
        m.put(dayKey(), m.optInt(dayKey(), 0) + minutes)
        prefs(ctx).edit().putString("focus", m.toString()).apply()
    }

    /** Consecutive days with focused minutes, ending today (or yesterday if today is untouched). */
    fun streak(ctx: Context): Int {
        val m = focusMap(ctx)
        val cal = java.util.Calendar.getInstance()
        if (m.optInt(dayKey(cal.time), 0) == 0) cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (m.optInt(dayKey(cal.time), 0) > 0) {
            n++
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            if (n > 3650) break
        }
        return n
    }

    // ---- notes (local CRUD) ----
    data class Note(
        val id: String = UUID.randomUUID().toString(),
        val subject: String,
        val title: String,
        val body: String,
        val updated: Long = System.currentTimeMillis(),
    )

    fun notes(ctx: Context): List<Note> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("notes", "[]") ?: "[]") }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                Note(o.getString("id"), o.getString("subject"), o.getString("title"),
                    o.getString("body"), o.optLong("updated", 0L))
            }.getOrNull()
        }.sortedByDescending { it.updated }
    }

    fun saveNote(ctx: Context, note: Note) {
        val list = notes(ctx).toMutableList()
        list.removeAll { it.id == note.id }
        list.add(note.copy(updated = System.currentTimeMillis()))
        persistNotes(ctx, list)
    }

    fun deleteNote(ctx: Context, id: String) {
        persistNotes(ctx, notes(ctx).filterNot { it.id == id })
    }

    private fun persistNotes(ctx: Context, list: List<Note>) {
        val arr = JSONArray()
        for (n in list) arr.put(JSONObject().apply {
            put("id", n.id); put("subject", n.subject); put("title", n.title)
            put("body", n.body); put("updated", n.updated)
        })
        prefs(ctx).edit().putString("notes", arr.toString()).apply()
    }
}
