package com.tiku.app

import android.content.Context
import org.json.JSONObject

/**
 * Study content pack loader — reads the bundled asset `study-content.json`.
 *
 * Exact pack shape (CBSE Class 11):
 *   { board, class,
 *     subjects: [ { name, icon, chapters: [ { title, points: [String],
 *                    questions: [ { q, a, src? } ] } ] } ],
 *     dailyQuestions: [ { subject, q, options: [4], correct: Int, why, src? } ],
 *     revisionCards: [ { front, back } ] }
 *
 * `src` is optional (a sourcing tag like "NCERT Exercise"); rendered when present.
 * The whole loader is failure-tolerant: a missing or damaged asset degrades to
 * empty lists and the UI falls back to study tips instead of crashing.
 */
object StudyContent {
    data class QA(val q: String, val a: String, val src: String? = null)
    /** Content v2 adds per-chapter `mcqs` (same shape as DailyQ) and wraps key terms
     *  in points strings with **double asterisks** for highlight rendering.
     *  Both are optional — the loader tolerates their absence (v1 pack). */
    data class Chapter(
        val title: String, val points: List<String>, val questions: List<QA>,
        val mcqs: List<DailyQ> = emptyList(),
    )
    data class Subject(val name: String, val icon: String, val chapters: List<Chapter>)
    data class DailyQ(
        val subject: String, val q: String, val options: List<String>,
        val correct: Int, val why: String, val src: String? = null,
    )
    data class Flashcard(val front: String, val back: String)

    var subjects: List<Subject> = emptyList()
    var dailyQuestions: List<DailyQ> = emptyList()
    var flashcards: List<Flashcard> = emptyList()

    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        // chapter art is independent of the content pack (bundled at build time
        // from ~/workspace/tiku/art) — load it first, tolerate its absence
        StudyArt.load(ctx)
        val json = runCatching {
            ctx.assets.open("study-content.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        runCatching {
            val root = JSONObject(json)
            subjects = (0 until root.optJSONArray("subjects")!!.length()).map { si ->
                val s = root.optJSONArray("subjects")!!.getJSONObject(si)
                val chapters = (0 until s.optJSONArray("chapters")!!.length()).map { ci ->
                    val ch = s.optJSONArray("chapters")!!.getJSONObject(ci)
                    val points = ch.optJSONArray("points")
                    val pts = (0 until (points?.length() ?: 0)).map { points!!.getString(it) }
                    val qs = ch.optJSONArray("questions")
                    val qa = (0 until (qs?.length() ?: 0)).map { qi ->
                        val o = qs!!.getJSONObject(qi)
                        QA(o.getString("q"), o.getString("a"), o.optString("src").takeIf { it.isNotEmpty() })
                    }
                    val mc = ch.optJSONArray("mcqs")
                    val mcqList = (0 until (mc?.length() ?: 0)).mapNotNull { mi ->
                        runCatching {
                            val o = mc!!.getJSONObject(mi)
                            val opts = o.getJSONArray("options")
                            DailyQ(
                                s.optString("name"), o.getString("q"),
                                (0 until opts.length()).map { opts.getString(it) },
                                o.getInt("correct"), o.optString("why"),
                                o.optString("src").takeIf { it.isNotEmpty() },
                            )
                        }.getOrNull()
                    }
                    Chapter(ch.getString("title"), pts, qa, mcqList)
                }
                Subject(s.getString("name"), s.optString("icon", "📘"), chapters)
            }
            val dq = root.optJSONArray("dailyQuestions")
            dailyQuestions = (0 until (dq?.length() ?: 0)).map { i ->
                val o = dq!!.getJSONObject(i)
                val opts = o.getJSONArray("options")
                DailyQ(
                    o.getString("subject"), o.getString("q"),
                    (0 until opts.length()).map { opts.getString(it) },
                    o.getInt("correct"), o.getString("why"),
                    o.optString("src").takeIf { it.isNotEmpty() },
                )
            }
            val rc = root.optJSONArray("revisionCards")
            flashcards = (0 until (rc?.length() ?: 0)).map { i ->
                val o = rc!!.getJSONObject(i)
                Flashcard(o.getString("front"), o.getString("back"))
            }
        }
    }

    fun subject(name: String): Subject? = subjects.firstOrNull { it.name == name }

    /** Today's question — rotates through the pack by day of year. */
    fun questionOfTheDay(): DailyQ? {
        if (dailyQuestions.isEmpty()) return null
        val idx = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % dailyQuestions.size
        return dailyQuestions[idx]
    }

    fun cardOfTheDay(): Flashcard? {
        if (flashcards.isEmpty()) return null
        val idx = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % flashcards.size
        return flashcards[idx]
    }
}

/**
 * Chapter art index (owner round-3 item 7): compressed WebP heroes copied into
 * `assets/art/` at build time from ~/workspace/tiku/art, driven ENTIRELY by
 * `assets/art/manifest.json`. Manifest keys:
 *   "Subject|Chapter title" -> chapter hero file
 *   "Subject" (or "subject:Subject") -> subject-page header hero file
 * Any key/file the manifest doesn't list is ignored; chapters without art
 * render the upgraded card layout with NO hero and NO fallback — clean.
 */
object StudyArt {
    private var chapterFiles: Map<String, String> = emptyMap()
    private var subjectFiles: Map<String, String> = emptyMap()

    fun load(ctx: Context) {
        runCatching {
            val text = ctx.assets.open("art/manifest.json").bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val ch = mutableMapOf<String, String>()
            val su = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val file = json.optString(k)
                if (file.isEmpty()) continue
                if ('|' in k) ch[k] = file
                else su[k.removePrefix("subject:")] = file
            }
            chapterFiles = ch
            subjectFiles = su
        }.onFailure {
            chapterFiles = emptyMap(); subjectFiles = emptyMap()
        }
    }

    /** Hero for exactly this chapter (no fallbacks — final item-7 scope). */
    fun chapterArt(subject: String, title: String): String? = chapterFiles["$subject|$title"]

    /** Header hero for the subject page (4 subject heroes live in the manifest). */
    fun subjectArt(subject: String): String? = subjectFiles[subject]

    fun assetModel(file: String) = "file:///android_asset/art/$file"
}
