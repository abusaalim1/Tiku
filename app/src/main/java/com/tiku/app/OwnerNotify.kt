package com.tiku.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * ROUND 9 → ROUND 18 → ROUND 19 — OPT-IN MESSAGE ALERTS (any user,
 * Instagram-style).
 *
 * Round 9 created this as the owner account's single exception to the
 * zero-notifications law. Round 18 made the toggles universal and the
 * alert CONTENT Instagram-style (sender name + decrypted text — his
 * explicit order), and tried to stop the false alerts with a persisted
 * timestamp ledger. ROUND 19 (owner's build-62 device report: "bas ekbar
 * hi notification ayega, aur jitne time wo message send krega bas wahi
 * time pe" — exactly one alert per message, at the moment it is sent)
 * replaces that ledger's TS-BASED judge with a DELTA judge, after the
 * timestamp design was found structurally unsound on device:
 *
 *  - `absorb` REPLACED the accounted id set with each applied window,
 *    and snapshot applications for one thread genuinely run out of
 *    order (the engine's preview path suspends on key fetches and a
 *    1.5 s retry) — a stale window applied late dropped newer ids from
 *    the set, and the ts tests could not tell an old message from a
 *    new one once that happened.
 *  - While a chat is OPEN the preview path early-returns, so the old
 *    ledger never absorbed what he read in the chat; the first
 *    background snapshot after closing judged those against a frozen
 *    pre-open high-water mark and called them "fresh".
 *  - Firestore delivers cache-then-server snapshot pairs; the old
 *    baseline absorbed the cache window, and a backlog message that
 *    only existed in the server window could beat the enable watermark
 *    (a DEVICE-clock value with a grace, compared against SERVER
 *    timestamps) and alert on a cold start — "koi message bheja hi
 *    nhi, bar bar notification aye ja raha tha".
 *
 * THE DELTA JUDGE (this round). Per thread the judge keeps the PREVIOUS
 * snapshot's message-id set in memory ([prevIds]). A message may alert
 * only if its id is NOT in that set — it genuinely just appeared in the
 * window — AND it is not in the persisted already-alerted set, AND it
 * is incoming, of an alertable kind, timestamped at/after the persisted
 * enable watermark, the gate is open, the app is not in the foreground
 * and the thread is not muted. (The engine never offers snapshots for
 * the currently open chat at all — its preview path early-returns while
 * a chat is open — so an open chat can never be judged.) Metadata-only
 * re-deliveries (read receipts, reactions, edits) change no id set and
 * therefore can NEVER alert, however often Firestore repeats them; the
 * judge is idempotent — judging the same snapshot twice finds zero
 * deltas the second time, so even a duplicated delivery is harmless.
 *
 * BASELINE: the first non-empty snapshot of a thread in a pipeline run
 * is history by definition — it seeds [prevIds] and is never judged, so
 * nothing that arrived while the pipeline was down can alert. (Accepted
 * trade-off, unchanged: the first message of a brand-new chat may be
 * absorbed silently if it lands in that chat's baseline snapshot.)
 *
 * PERSISTENCE DISCIPLINE: the alerted id is written to the persisted
 * set (synchronous commit) BEFORE the notification is posted, inside
 * the same synchronized block as the eligibility check — a crash or a
 * duplicate pass can never re-post it.
 *
 * RATE GUARD: at most one alert post per thread per 2 seconds; a burst
 * inside the window coalesces into the LATEST message's alert.
 *
 * FOREGROUND: snapshots absorbed while he is looking at the app never
 * alert, and any standing message alerts are CANCELLED — opening the
 * app means he has seen them (see also [clearStandingAlerts], which
 * [NotifyService] calls on the resume sync). The service's ongoing
 * banner is NOT posted from here and lives on its own silent channel.
 *
 * There is no FCM and no server push: the server holds ciphertext only.
 * Every entry point is runCatching-wrapped — the notification path can
 * never crash the process.
 */
object OwnerNotify {
    private const val CHANNEL_ID = "tiku_reminders"

    /** Kinds that may alert as messages. CALL is handled separately (only
     *  a genuinely missed incoming call), everything else never alerts. */
    private val MESSAGE_KINDS = setOf(
        MsgKind.TEXT, MsgKind.IMAGE, MsgKind.VIDEO, MsgKind.AUDIO, MsgKind.VOICE,
    )

    /** Rate guard: minimum spacing between two alert posts per thread. */
    private const val RATE_WINDOW_MS = 2_000L

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Threads whose first non-empty snapshot in THIS pipeline run has
     *  been absorbed as history. In-memory on purpose: every pipeline
     *  (re)start — process start, session unlock, service bootstrap —
     *  re-baselines each thread's current window before anything in it
     *  may alert. */
    private val baselined = mutableSetOf<String>()

    /** The DELTA primitive: per thread, the message-id set of the
     *  previous snapshot this judge absorbed. Updated on EVERY absorbed
     *  snapshot (baseline, foreground, muted, judged) so the next diff
     *  is always against the immediately preceding window. */
    private val prevIds = mutableMapOf<String, Set<String>>()

    /** Threads with a message/call alert currently standing on the
     *  shade — cleared when the app comes to the foreground. */
    private val standing = mutableSetOf<String>()

    /** Rate guard state: last post time per thread, the coalesced
     *  pending alert (latest wins), and threads with a flush posted. */
    private val lastPostAt = mutableMapOf<String, Long>()
    private val pending = mutableMapOf<String, PendingAlert>()
    private val flushScheduled = mutableSetOf<String>()

    private data class PendingAlert(
        val ctx: Context, val threadId: String, val msg: TikuMessage, val call: Boolean,
    )

    /** True once this run has (re)armed the global watermark. */
    @Volatile private var runArmed = false

    /** True while a live alert pipeline (session or service) has armed
     *  the watermark in THIS process. [NotifyService] consults it so a
     *  re-delivered start on an already-running pipeline never re-arms
     *  mid-run; a dead pipeline — process death, or [reset] on lock —
     *  reads false here, and the next start arms again. */
    val pipelineArmed: Boolean get() = synchronized(lock) { runArmed }

    /**
     * Called by the service when its pipeline starts (and cheap to call
     * again): raises the persisted global watermark so only messages
     * timestamped from ~now on can alert. Backlog — anything that landed
     * while the service/session was off — stays behind the watermark
     * forever (and is baselined per thread besides).
     */
    fun arm(ctx: Context) {
        runCatching {
            synchronized(lock) {
                AlertStore.arm(ctx)
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
            // anything, so this run's contents are measured against it.
            AlertStore.arm(ctx)
            runArmed = true
        }
        val ids = msgs.map { it.id }.toSet()

        if (threadId !in baselined) {
            // BASELINE: the first NON-EMPTY window of the run is history
            // — seed the delta state and never judge it. (An empty first
            // snapshot, e.g. an empty Firestore cache delivery ahead of
            // the server one, must not spend the baseline: the backlog
            // would diff against an empty set and look brand-new.)
            if (msgs.isEmpty()) {
                prevIds[threadId] = ids
                return
            }
            baselined += threadId
            prevIds[threadId] = ids
            return
        }

        // DELTA: only ids absent from the previous snapshot are new.
        // Computed BEFORE absorbing this one.
        val previous = prevIds[threadId] ?: emptySet()
        val deltas = msgs.filter { it.id !in previous }
        prevIds[threadId] = ids

        if (ChatSession.foreground) {
            // He's looking at the app — badges do the job and anything
            // still standing on the shade is seen by definition: clear
            // it. The ids above are absorbed either way, so nothing here
            // can alert later from a stale diff.
            clearStandingLocked(ctx)
            return
        }
        val thread = runCatching { AppState.thread(threadId) }.getOrNull()
        if (thread?.muted == true) return // muted chats never push

        val armedAt = AlertStore.armedAt(ctx)
        val alerted = AlertStore.alertedIds(ctx, threadId)

        fun eligible(m: TikuMessage): Boolean =
            !m.me && m.tsMs >= armedAt && m.id !in alerted

        val freshMessages = deltas.filter { it.kind in MESSAGE_KINDS && eligible(it) }
        val freshMissedCalls = deltas.filter {
            it.kind == MsgKind.CALL && eligible(it) &&
                !it.callMine && it.callOutcome == "noanswer"
        }

        if (freshMessages.isNotEmpty()) {
            val newest = freshMessages.maxByOrNull { it.tsMs } ?: return
            // Persist BEFORE posting, inside this same lock: from here
            // the id can never alert again, whatever replays.
            AlertStore.addAlerted(ctx, threadId, newest.id)
            offerAlertLocked(PendingAlert(ctx.applicationContext, threadId, newest, call = false))
        }
        if (freshMissedCalls.isNotEmpty()) {
            val newest = freshMissedCalls.maxByOrNull { it.tsMs } ?: return
            AlertStore.addAlerted(ctx, threadId, newest.id)
            offerAlertLocked(PendingAlert(ctx.applicationContext, threadId, newest, call = true))
        }
    }

    // ================= RATE GUARD =================

    /** Post now, or — inside the 2 s window — coalesce into the pending
     *  slot (latest wins) and flush when the window opens. */
    private fun offerAlertLocked(p: PendingAlert) {
        val now = System.currentTimeMillis()
        val last = lastPostAt[p.threadId] ?: 0L
        if (now - last >= RATE_WINDOW_MS) {
            postPendingLocked(p)
            return
        }
        pending[p.threadId] = p
        if (flushScheduled.add(p.threadId)) {
            mainHandler.postDelayed({
                runCatching {
                    synchronized(lock) {
                        flushScheduled.remove(p.threadId)
                        val held = pending.remove(p.threadId) ?: return@synchronized
                        // The world may have changed while held: if he
                        // has since opened the app, the alert is moot.
                        if (!OwnerPrefs.notifyOn || ChatSession.foreground) {
                            clearStandingLocked(held.ctx)
                            return@synchronized
                        }
                        postPendingLocked(held)
                    }
                }
            }, RATE_WINDOW_MS - (now - last))
        }
    }

    private fun postPendingLocked(p: PendingAlert) {
        lastPostAt[p.threadId] = System.currentTimeMillis()
        standing += p.threadId
        if (p.call) postMissedCall(p.ctx, p.threadId, p.msg) else postMessage(p.ctx, p.threadId, p.msg)
    }

    // ================= FOREGROUND CLEARING =================

    /** Opening the app means he has seen the standing message alerts:
     *  cancel them (the service's ongoing banner is a different id on a
     *  different channel and is never touched here). Called by
     *  [NotifyService] on the resume sync and by the judge's own
     *  foreground path. */
    fun clearStandingAlerts(ctx: Context) {
        runCatching { synchronized(lock) { clearStandingLocked(ctx) } }
    }

    private fun clearStandingLocked(ctx: Context) {
        if (standing.isEmpty() && pending.isEmpty()) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null) {
            for (tid in standing) {
                runCatching { nm.cancel(messageNotifId(tid)) }
                runCatching { nm.cancel(callNotifId(tid)) }
            }
        }
        standing.clear()
        pending.clear()
    }

    /** Pipeline died (lock / sign-out — via FirebaseEngine.lock): forget
     *  the per-run delta state so the next run re-baselines instead of
     *  alerting on old history. The PERSISTED alerted set and watermark
     *  deliberately survive: that is what makes restarts and replays
     *  never re-alert a message that already produced its one alert. */
    fun reset() {
        synchronized(lock) {
            baselined.clear()
            prevIds.clear()
            pending.clear()
            lastPostAt.clear()
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

    private fun messageNotifId(threadId: String): Int = (threadId.hashCode() and 0x3FFF) + 0x4000

    private fun callNotifId(threadId: String): Int = (threadId.hashCode() and 0x3FFF) + 0x8000

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
                // A re-post under the same id updates content silently —
                // it must never buzz or peek a second time.
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build()
            // One live alert per chat per kind — a burst replaces, never stacks.
            nm.notify(if (call) callNotifId(threadId) else messageNotifId(threadId), notif)
        }
    }

    /** The channel is created LAZILY, here, at the first actual alert — the
     *  app creates no notification channel at startup (see TikuApp). The
     *  name stays in the study spirit: it reads as a generic reminders
     *  channel. (The foreground service's banner uses its OWN low-importance
     *  channel — see NotifyService — so it can never peek like an alert.) */
    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

/**
 * ROUND 19 — THE PERSISTED ALERT STORE (SharedPrefs file "tiku_alerts").
 *
 * Two durable facts, nothing timestamp-derived:
 *
 *  - `armed_at` — the global enable-time watermark. Raised (never
 *    lowered) every time an alert pipeline (re)starts. A message whose
 *    server timestamp predates it can never alert. A 10-second grace is
 *    subtracted when arming so ordinary server/device clock skew cannot
 *    eat a message that genuinely arrives in the first moments of a
 *    run; the delta judge's baseline absorbs that window's contents
 *    anyway, so the grace cannot manufacture a false alert.
 *  - `alerted:<threadId>` — the ids this device has ALREADY posted an
 *    alert for, append-only, capped at the newest [MAX_ALERTED] ids.
 *    Written with commit() BEFORE the notification is posted (inside
 *    the judge's lock), so the one-alert-per-message promise survives
 *    process death, service restarts and snapshot replays.
 *
 * The Round 18 timestamp ledger (`hw:` / `ids:` / `lastid:` keys) is
 * gone: a high-water mark over a window that is filtered, re-ordered
 * and partially invisible (open chats) could not distinguish old from
 * new. Ids can; the previous snapshot's id set is the judge's memory,
 * and this set is its conscience.
 */
private object AlertStore {
    private const val PREF = "tiku_alerts"
    private const val ARM_GRACE_MS = 10_000L
    private const val MAX_ALERTED = 200

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

    fun alertedIds(ctx: Context, threadId: String): Set<String> =
        prefs(ctx).getStringSet("alerted:$threadId", emptySet()) ?: emptySet()

    /** Record that [messageId] has had its one alert. Synchronous
     *  commit on purpose: this write is the guarantee, and it happens
     *  before the post, inside the judge's synchronized block. */
    fun addAlerted(ctx: Context, threadId: String, messageId: String) {
        val p = prefs(ctx)
        val cur = (p.getStringSet("alerted:$threadId", emptySet()) ?: emptySet()).toMutableSet()
        if (!cur.add(messageId)) return
        val kept = if (cur.size > MAX_ALERTED) cur.drop(cur.size - MAX_ALERTED).toSet() else cur
        p.edit().putStringSet("alerted:$threadId", kept).commit()
    }
}
