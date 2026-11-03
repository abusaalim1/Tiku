package com.tiku.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ROUND 13 — K-DRAMA FACADE CONTENT. The app's outer face is a Korean
 * love-story app ("Tiku — Korean love stories, in your language"): Hindi /
 * Bangla dubbed drama episodes that stream from YouTube, a shelf of short
 * love stories to read, and a gallery of romantic stills. Everything except
 * the video streams themselves is bundled in `assets/facade/` and works
 * offline; playback is a facade-owned WebView (see DramaShell.kt).
 *
 * Exact pack shapes:
 *   dramas.json:  { appName, tagline,
 *                   hero: { id, title, sub, genre, badge },
 *                   rows: [ { title, items: [ { id, title, sub, genre, lang } ] } ] }
 *   stories.json: { sectionTitle, stories: [ { id, title, tag, minutes, body } ] }
 *   photos/photo_1..8.webp with the fixed captions in [PHOTOS].
 *
 * The whole loader is failure-tolerant (same law as the old content pack):
 * a missing or damaged asset degrades to empty lists and the facade renders
 * its calm empty states instead of crashing.
 *
 * Zero-trace rule: this surface carries NO chat data, NO usernames, and the
 * word "Tik" never appears on it — "Tiku" is the drama brand.
 */
object DramaContent {
    data class Drama(val id: String, val title: String, val sub: String, val genre: String, val lang: String)
    data class Row(val title: String, val items: List<Drama>)
    data class Story(val id: String, val title: String, val tag: String, val minutes: Int, val body: String)

    /** Bundled romantic stills (assets/facade/photos/) with their captions. */
    val PHOTOS: List<Pair<String, String>> = listOf(
        "photo_1.webp" to "Rainy Day in Seoul",
        "photo_2.webp" to "Table No. 7",
        "photo_3.webp" to "Cherry Blossom Walk",
        "photo_4.webp" to "Rooftop Sunset",
        "photo_5.webp" to "The Last Train",
        "photo_6.webp" to "First Snow",
        "photo_7.webp" to "The Bookshop",
        "photo_8.webp" to "Han River Picnic",
    )

    /** The hero card's backdrop still (cherry-blossom walk). */
    const val HERO_PHOTO = "photo_3.webp"

    var appName: String = "Tiku"
    var tagline: String = "Korean love stories, in your language"
    var hero: Drama? = null
    var rows: List<Row> = emptyList()
    var storiesTitle: String = "Love Stories"
    var stories: List<Story> = emptyList()

    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val text = ctx.assets.open("facade/dramas.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            appName = root.optString("appName", appName)
            tagline = root.optString("tagline", tagline)
            root.optJSONObject("hero")?.let { h ->
                hero = Drama(
                    id = h.getString("id"),
                    title = h.optString("title"),
                    sub = h.optString("sub"),
                    genre = h.optString("genre"),
                    // the hero's badge carries its dub language ("Hindi Dubbed")
                    lang = h.optString("badge").removeSuffix(" Dubbed").ifBlank { "Hindi" },
                )
            }
            val rs = root.optJSONArray("rows")
            rows = (0 until (rs?.length() ?: 0)).map { ri ->
                val r = rs!!.getJSONObject(ri)
                val items = r.optJSONArray("items")
                Row(
                    title = r.optString("title"),
                    items = (0 until (items?.length() ?: 0)).map { ii ->
                        val d = items!!.getJSONObject(ii)
                        Drama(
                            id = d.getString("id"),
                            title = d.optString("title"),
                            sub = d.optString("sub"),
                            genre = d.optString("genre"),
                            lang = d.optString("lang"),
                        )
                    },
                )
            }
        }
        runCatching {
            val text = ctx.assets.open("facade/stories.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            storiesTitle = root.optString("sectionTitle", storiesTitle)
            val ss = root.optJSONArray("stories")
            stories = (0 until (ss?.length() ?: 0)).map { si ->
                val s = ss!!.getJSONObject(si)
                Story(
                    id = s.optString("id"),
                    title = s.optString("title"),
                    tag = s.optString("tag"),
                    minutes = s.optInt("minutes", 3),
                    body = s.optString("body"),
                )
            }
        }
    }

    /** Every drama the facade knows (hero first), for history/favourite lookups. */
    fun allDramas(): List<Drama> =
        listOfNotNull(hero) + rows.flatMap { it.items }

    fun drama(id: String): Drama? = allDramas().firstOrNull { it.id == id }

    fun story(id: String): Story? = stories.firstOrNull { it.id == id }

    /** Coil model for a bundled facade photo. */
    fun photoModel(file: String) = "file:///android_asset/facade/photos/$file"

    /** YouTube poster for a drama card. */
    fun thumbUrl(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}

/**
 * ROUND 13 — the facade's REAL local state (SharedPrefs `tikuu_facade`):
 * watch history written by the player (newest first, capped) and the
 * favourites toggled from drama cards (the hearts still toggle on the
 * Home cards; the old My List surface was removed in Round 14).
 * Local-only, drama-only — nothing here touches the vault, and nothing
 * chat-shaped is ever stored.
 */
object FacadeStore {
    private const val PREF = "tikuu_facade"
    private const val HISTORY_CAP = 12

    /** One history entry: a drama id + the title seen when it was played
     *  (so the row still renders if a future pack renames the drama). */
    data class HistoryEntry(val id: String, val title: String, val ts: Long)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun history(ctx: Context): List<HistoryEntry> {
        val raw = prefs(ctx).getString("history", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(o.getString("id"), o.optString("title"), o.optLong("ts"))
            }
        }.getOrDefault(emptyList())
    }

    /** Called by the facade player when a drama closes — the ONLY writer. */
    fun recordWatch(ctx: Context, drama: DramaContent.Drama) {
        val rest = history(ctx).filter { it.id != drama.id }
        val next = (listOf(HistoryEntry(drama.id, drama.title, System.currentTimeMillis())) + rest)
            .take(HISTORY_CAP)
        val arr = JSONArray()
        next.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("title", e.title).put("ts", e.ts))
        }
        prefs(ctx).edit().putString("history", arr.toString()).apply()
    }

    fun favourites(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("favs", emptySet())?.toSet() ?: emptySet()

    /** Toggles one drama's favourite flag; returns the NEW state (true = saved). */
    fun toggleFavourite(ctx: Context, id: String): Boolean {
        val cur = favourites(ctx).toMutableSet()
        val now = if (cur.contains(id)) { cur.remove(id); false } else { cur.add(id); true }
        prefs(ctx).edit().putStringSet("favs", cur).apply()
        return now
    }
}
