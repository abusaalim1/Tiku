package com.tiku.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * ROUND 9 → ROUND 18 — OPT-IN MESSAGE ALERTS (any user, Instagram-style).
 *
 * Round 9 created this as the owner account's single exception to the
 * zero-notifications law, with generic content ("New message", never a
 * sender, never text). ROUND 18 (owner's device-test orders on build 59)
 * changes three things:
 *
 *  1. UNIVERSAL: the alerts belong to the DEVICE-LOCAL toggles now, not to
 *     an account — any signed-in user who turned Notifications ON (and App
 *     lock OFF, the [NotifyGate] discipline) gets them. [OwnerState] is no
 *     longer consulted here.
 *  2. CONTENT: an alert shows WHO and WHAT, like Instagram — title is the
 *     sender's display name (the chat's nickname/profile name), body is the
 *     decrypted message text, or a fixed label for media ("Photo" /
 *     "Video" / "Voice message" / "Audio file"). The text comes from the
 *     engine's own preview decryption (the same plaintext the thread list
 *     shows); a message whose text is somehow blank falls back to the
 *     generic "New message" for that one alert. Ciphertext never reaches
 *     a notification.
 *  3. STRICT ALERT RULES (the false-alert fix — "koi message bheja hi nhi,
 *     bar bar notification aye ja raha tha"): see [AlertLedger]. In short,
 *     a message can alert only if it is a genuinely NEW incoming document
 *     that arrived AFTER this alert pipeline (re)armed, was never
 *     accounted before, and is not a call/metadata/preview reshuffle.
 *
 * How it works: the engine's per-chat preview listeners decrypt the newest
 * messages on-device and offer every snapshot here. [NotifyService] keeps
 * that pipeline alive while the app is closed; while the user is looking
 * at the app (foreground) nothing posts — the in-app unread badges do the
 * job, and the snapshot is absorbed into the ledger so it can never alert
 * later. There is no FCM and no server push: the server holds ciphertext
 * only and could not say what arrived anyway.
 *
 * Every entry point is runCatching-wrapped: a half-ready engine (no
 * identity yet, decrypt not ready, process being killed) must SKIP an
 * alert silently — the notification path can never crash the process.
 */
object OwnerNotify {
    private const val CHANNEL_ID = "tiku_reminders"

    /** Kinds that may alert as messages. CALL is handled separately (only
     *  a genuinely missed incoming call), everything else never alerts. */
    private val MESSAGE_KINDS = setOf(
        MsgKind.TEXT, MsgKind.IMAGE, MsgKind.VIDEO, MsgKind.AUDIO, MsgKind.VOICE,
    )

    private val lock = Any()

    /** Threads whose first snapshot in THIS pipeline run has been absorbed
     *  as history. In-memory on purpose: every pipeline (re)start — session
     *  unlock or service bootstrap — re-baselines each thread's current
     *  window before anything in it may alert, so nothing that arrived
     *  while the pipeline was down can ever slip through as "new". */
    private val baselined = mutableSetOf<String>()

    /** True once this run has (re)armed the global watermark. */
    @Volatile private var runArmed = false

    /** True while a live alert pipeline (session or service) has armed
     *  the watermark in THIS process. [NotifyService] consults it so a
     *  re-delivered start on an already-running pipeline never re-arms
     *  mid-run (which could suppress a message whose snapshot is still in
     *  flight); a dead pipeline — process death, or [reset] on lock —
     *  reads false here, and the next start arms again. */
    val pipelineArmed: Boolean get() = synchronized(lock) { runArmed }

    /**
     * Called by the service when its pipeline starts (and cheap to call
     * again): raises the persisted global watermark so only messages that
     * arrive from ~now on can alert. Backlog — anything that landed while
     * the service/session was off — stays behind the watermark forever.
     */
    fun arm(ctx: Context) {
        runCatching {
            synchronized(lock) {
                AlertLedger.arm(ctx)
                runArmed = true
            }
        }
    }

    fun onPreviewMessages(ctx: Context, threadId: String, msgs: List<TikuMessage>) {
        runCatching {
            if (!OwnerPrefs.notifyOn) return
            if (Config.PREVIEW_UI_ONLY) return
            synchronized(lock) { onPreviewLocked(ctx, threadId, msgs) }
        }
    }

    private fun onPreviewLocked(ctx: Context, threadId: String, msgs: List<TikuMessage>) {
        if (!runArmed) {
            // First snapshot of this pipeline run (in-app session path —
            // the service arms explicitly at start): arm BEFORE judging
            // anything in it, so this snapshot's contents are backlog by
            // definition if they predate the run.
            AlertLedger.arm(ctx)
            runArmed = true
        }
        val armedAt = AlertLedger.armedAt(ctx)
        val firstSnapshot = threadId !in baselined

        if (firstSnapshot) {
            // HISTORY: whatever the window currently holds arrived before
            // this run existed (or while it was down). Absorb it all into
            // the persisted ledger and never alert on it.
            baselined += threadId
            AlertLedger.absorb(ctx, threadId, msgs)
            return
        }
        if (ChatSession.foreground) {
            // He's looking at the app — badges do the job. Still absorb
            // the ids so these messages don't alert later from a stale
            // baseline.
            AlertLedger.absorb(ctx, threadId, msgs)
            return
        }
        val thread = runCatching { AppState.thread(threadId) }.getOrNull()
        if (thread?.muted == true) {
            // Muted chats never push (Instagram semantics).
            AlertLedger.absorb(ctx, threadId, msgs)
            return
        }

        val hw = AlertLedger.highWater(ctx, threadId)
        val lastAlerted = AlertLedger.lastAlertedId(ctx, threadId)
        val accounted = AlertLedger.accountedIds(ctx, threadId)

        fun isFresh(m: TikuMessage): Boolean =
            !m.me &&
                m.tsMs > hw &&
                m.tsMs > armedAt &&
                m.id != lastAlerted &&
                m.id !in accounted

        val freshMessages = msgs.filter { it.kind in MESSAGE_KINDS && isFresh(it) }
        val freshMissedCalls = msgs.filter {
            it.kind == MsgKind.CALL && isFresh(it) &&
                !it.callMine && it.callOutcome == "noanswer"
        }

        // Absorb EVERYTHING the snapshot carried (alerted or not — read
        // receipts, reactions, presence rows, his own sends) so no doc is
        // ever judged twice.
        AlertLedger.absorb(ctx, threadId, msgs)

        if (freshMessages.isNotEmpty()) {
            val newest = freshMessages.maxByOrNull { it.tsMs } ?: return
            AlertLedger.markAlerted(ctx, threadId, newest.id)
            postMessage(ctx, threadId, newest)
        }
        if (freshMissedCalls.isNotEmpty()) {
            val newest = freshMissedCalls.maxByOrNull { it.tsMs } ?: return
            AlertLedger.markAlerted(ctx, threadId, newest.id)
            postMissedCall(ctx, threadId, newest)
        }
    }

    /** Pipeline died (lock / sign-out — via FirebaseEngine.lock): forget
     *  the per-run baselines so the next run re-baselines instead of
     *  alerting on old history. The PERSISTED ledger deliberately
     *  survives: that is what makes restarts and replays never re-alert. */
    fun reset() {
        synchronized(lock) {
            baselined.clear()
            runArmed = false
        }
    }

    /** The toggle was flipped OFF: clear anything still on the shade. */
    fun cancelAll(ctx: Context) {
        runCatching {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancelAll()
        }
    }

    // ================= POSTING =================

    private fun senderName(threadId: String): String {
        val th = runCatching { AppState.thread(threadId) }.getOrNull()
        return th?.displayName?.takeIf { it.isNotBlank() }
            ?: th?.person?.username?.takeIf { it.isNotBlank() }
            ?: "Tiku"
    }

    private fun messageBody(m: TikuMessage): String = when (m.kind) {
        MsgKind.IMAGE -> "Photo"
        MsgKind.VIDEO -> "Video"
        MsgKind.VOICE -> "Voice message"
        MsgKind.AUDIO -> "Audio file"
        else -> m.text.replace(Regex("\\s+"), " ").trim()
            .take(140)
            .ifBlank { "New message" }
    }

    private fun postMessage(ctx: Context, threadId: String, m: TikuMessage) {
        post(ctx, threadId, call = false, title = senderName(threadId), body = messageBody(m))
    }

    private fun postMissedCall(ctx: Context, threadId: String, m: TikuMessage) {
        post(
            ctx, threadId, call = true,
            title = senderName(threadId),
            body = if (m.callVideo) "Missed video call" else "Missed call",
        )
    }

    private fun post(ctx: Context, threadId: String, call: Boolean, title: String, body: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
            ) return // he hasn't granted the OS permission (yet) — stay silent
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannel(nm)
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx, threadId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notif = Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build()
            // One live alert per chat per kind — a burst replaces, never stacks.
            val id = (threadId.hashCode() and 0x3FFF) + if (call) 0x8000 else 0x4000
            nm.notify(id, notif)
        }
    }

    /** The channel is created LAZILY, here, at the first actual alert — the
     *  app creates no notification channel at startup (see TikuApp). The
     *  name stays in the study spirit: it reads as a generic reminders
     *  channel. */
    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

/**
 * ROUND 18 — THE PERSISTED ALERT LEDGER (SharedPrefs file "tiku_alerts").
 *
 * What the old in-memory baseline got wrong: it lived and died with the
 * process, and it treated "an id I have not seen in this process" as new —
 * so service restarts, cache→server snapshot replays, window reshuffles and
 * answered-call rows could all surface as alerts for messages nobody had
 * just sent. The ledger makes "already accounted" a durable fact:
 *
 *  - `armed_at` — the global enable-time watermark. Raised (never lowered)
 *    every time an alert pipeline (re)starts: service bootstrap, session
 *    unlock's first snapshot, and it starts at 0 (unset). A message whose
 *    server timestamp is at or before the watermark can NEVER alert — it
 *    is backlog by definition. A 10-second grace is subtracted when
 *    arming so ordinary server/device clock skew cannot eat a message
 *    that genuinely arrives in the first moments of a run; the per-thread
 *    first-snapshot baseline (in [OwnerNotify]) absorbs that window's
 *    contents anyway.
 *  - `hw:<threadId>` — the thread's high-water mark: the greatest message
 *    timestamp ever absorbed for the thread. Only a strictly newer
 *    timestamp can be a new message.
 *  - `ids:<threadId>` — the accounted message ids of the latest absorbed
 *    window (bounded by the preview window's own size). A re-delivered or
 *    edited-in-place doc is recognised and skipped.
 *  - `lastid:<threadId>` — the id of the most recent message an alert was
 *    actually posted for; belt-and-braces against any replay of it.
 *
 * All writes are apply() (async) — losing the very last write to a sudden
 * process kill can at worst re-judge one snapshot against a slightly older
 * high-water mark, and the watermark + baseline rules still hold it back.
 */
private object AlertLedger {
    private const val PREF = "tiku_alerts"
    private const val ARM_GRACE_MS = 10_000L

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Raise the global watermark to ~now (monotonic — never goes back). */
    fun arm(ctx: Context) {
        val p = prefs(ctx)
        val target = System.currentTimeMillis() - ARM_GRACE_MS
        if (target > p.getLong("armed_at", 0L)) {
            p.edit().putLong("armed_at", target).apply()
        }
    }

    fun armedAt(ctx: Context): Long = prefs(ctx).getLong("armed_at", 0L)

    fun highWater(ctx: Context, threadId: String): Long =
        prefs(ctx).getLong("hw:$threadId", 0L)

    fun lastAlertedId(ctx: Context, threadId: String): String =
        prefs(ctx).getString("lastid:$threadId", "") ?: ""

    fun accountedIds(ctx: Context, threadId: String): Set<String> =
        prefs(ctx).getStringSet("ids:$threadId", emptySet()) ?: emptySet()

    /** Record a whole snapshot window as accounted: its ids become the
     *  thread's accounted set and its newest timestamp the high-water. */
    fun absorb(ctx: Context, threadId: String, msgs: List<TikuMessage>) {
        if (msgs.isEmpty()) return
        val p = prefs(ctx)
        val maxTs = msgs.maxOf { it.tsMs }
        val e = p.edit()
        e.putStringSet("ids:$threadId", msgs.map { it.id }.toSet())
        if (maxTs > p.getLong("hw:$threadId", 0L)) e.putLong("hw:$threadId", maxTs)
        e.apply()
    }

    fun markAlerted(ctx: Context, threadId: String, messageId: String) {
        prefs(ctx).edit().putString("lastid:$threadId", messageId).apply()
    }
}
