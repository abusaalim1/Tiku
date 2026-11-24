package com.tiku.app

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ROUND 71 (Fix 3) — AI jobs outlive the screens that start them.
 *
 * The bug (owner, 2026-11-14): send a /tiku, /tika, /babeg or companion
 * message, press Back (or navigate anywhere) while Tika is still
 * generating, and the reply NEVER lands. Every AI launch rode the
 * chat screen's own `rememberCoroutineScope`, which Compose cancels
 * the moment the screen leaves composition — the suspending
 * GeminiClient call died mid-flight, before its result could reach
 * the (screen-independent) landing sink: AppState.repo.sendTextAi /
 * sendPhotoAi / sendGame for a normal chat, TikaBotStore for the
 * companion. The single-flight gate died with the screen too, so a
 * re-entered chat could also fire a duplicate.
 *
 * The fix is ownership, nothing else: the jobs now run on this
 * app-lifetime scope, and the in-flight state (the per-chat indicator
 * label + the companion's busy flag) is hoisted here with them, so a
 * re-entered screen reads the SAME state — the indicator is still up
 * and the duplicate gate still holds. The sinks were already
 * screen-independent (FirebaseEngine singletons / the local store);
 * only the launch scope and the gate/indicator state moved.
 *
 * Scope note: this survives IN-PROCESS navigation only. Process death
 * still drops an in-flight generation — deliberately out of scope for
 * this round (no WorkManager, no service).
 *
 * ROUND 73 (Workstream B) — the STUCK indicator. R71 made the reply
 * land after Back, but the "typing…" / thinking indicator then stayed
 * on screen after the job finished. Two hardenings land here:
 *
 * 1. SINGLE SOURCE OF TRUTH: [companionBusy] used to be a second,
 *    independent `mutableStateOf` written alongside the [thinking]
 *    entry — two stores for one fact, free to diverge. It is now a
 *    pure VIEW over [thinking] (see its doc): the boolean and the
 *    map can never disagree, so no path can clear one and strand
 *    the other.
 *
 * 2. BOUNDED JOBS: a job whose provider call wedges forever never
 *    reaches its finally, so its indicator (and its single-flight
 *    gate) would live forever. Every job now runs under a generous
 *    backstop ceiling (see [launch]); on timeout the block is
 *    cancelled, nothing is posted by the timeout path, the finally
 *    still clears the indicator, and the timeout is logged. The
 *    ceilings sit ABOVE every self-bounded workload (GeminiClient
 *    .generateGame already self-bounds at 600 s), so they can only
 *    ever fire on a genuinely wedged job.
 *
 * The remaining half of the R73 root cause lives at the READ sites
 * (Chat.kt's RdChatContent / TikaBot.kt's TikaBotScreen read this
 * state only inside their LazyColumn content lambdas — see the
 * Round-73 worker-B report); the dynamic-flip tests in
 * ScreenshotsTest pin the behaviour end to end.
 *
 * ROUND 82 — the bound that could never fire, and delete = cancel.
 *
 * The owner (2026-11-24): a slash-command reply "takes hours", the
 * typing animation shows the whole time, and UNSENDING or permanently
 * deleting the command message leaves the animation running. Two
 * distinct defects, both pinned from code:
 *
 * 1. R73's backstop was `withTimeoutOrNull` AROUND the block — and a
 *    timeout coroutine cannot complete while its block sits inside a
 *    NON-CANCELLABLE blocking call. GeminiClient's transport is
 *    exactly that: HttpURLConnection reads on Dispatchers.IO whose
 *    readTimeout is a per-read INACTIVITY timeout, not a total
 *    deadline, so a slow-dripping response holds one call open
 *    indefinitely and the model cascade chains such calls. Timeout
 *    waits on the block, the block waits on the socket, the finally
 *    that removes the [thinking] entry never runs — the animation
 *    (and the single-flight gate) lives as long as the socket drips.
 *    FIX: the bound is now a WATCHDOG — a separate coroutine that
 *    sleeps on `delay` (a true suspension, fires on time no matter
 *    what the block is doing) and then EXPIRES the job: the
 *    [thinking] entry is removed synchronously, the work job is
 *    cancelled, and the job's generation goes stale (see [Handle]).
 *    The indicator's lifetime no longer depends on the block ever
 *    returning.
 *
 * 2. Deletion was never wired into the job lifecycle: jobs were
 *    keyed by chat only, the command message's id was minted inside
 *    the send path and recorded nowhere, and the delete paths never
 *    touched AiJobs — so the job ran on and, the sinks being
 *    fire-and-forget (calling one synchronously upserts the AI
 *    message), its reply posted in full for a deleted command.
 *    FIX: [launch] records the command message's id ([Record]), the
 *    repo delete paths call [cancelForCommand], and every block
 *    receives a [Handle] whose [Handle.isActive] it must check
 *    immediately before dispatching to a sink or a toast. A stale
 *    handle can never post, even if its block is still unwinding
 *    out of a blocking call when the cancel lands.
 *
 * Generations: every launch takes a monotonically increasing
 * generation for its key. A job's finally clears the indicator ONLY
 * if its generation is still the key's current one — a stale job
 * finishing late can never clear a newer job's indicator, and an
 * expired job's late return is a no-op everywhere.
 *
 * Nothing here is persisted: [thinking] and the records live in
 * memory only, so process death takes every indicator with it —
 * after relaunch there is no job, therefore no animation, by
 * construction.
 */
object AiJobs {

    /** App-lifetime scope for AI generation. SupervisorJob: one job's
     *  failure can never cancel a sibling chat's in-flight ask. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * In-flight indicator labels, keyed by chat threadId — the exact
     * strings the chat indicator has always shown ("Tika is
     * thinking…" / "Tika is creating…" / "Tika is creating your
     * game…"). A key's presence IS the per-chat single-flight gate:
     * [launch] refuses a second job for a key already present.
     * Compose-observable: RdChatContent reads `thinking[threadId]`
     * in composition, so leaving and re-entering a chat mid-flight
     * shows the indicator still running.
     *
     * ROUND 73: this map is now the ONLY in-flight store — the
     * companion's busy flag is a view over its [COMPANION_KEY]
     * entry (see [companionBusy]).
     *
     * ROUND 82: the map stays the indicator's only store, but its
     * entries are now owned by [Record]s — written at launch,
     * removed by the job's own finally (while still current), by
     * the watchdog on expiry, or by a delete-cancel — never left
     * behind by a wedged block.
     */
    val thinking = mutableStateMapOf<String, String>()

    /**
     * ROUND 82: the capability handed to every job block. A block
     * MUST consult [isActive] immediately before landing anything
     * (a sink post, a store append, a toast): the sinks are
     * fire-and-forget, so this check — not coroutine cancellation —
     * is what guarantees a cancelled/expired job never posts. It is
     * cheap, non-suspending, and safe to call from any thread.
     */
    class Handle internal constructor(
        private val key: String,
        private val generation: Long,
    ) {
        /** True only while this job is still its key's current,
         *  unexpired, uncancelled job. */
        fun isActive(): Boolean = isCurrent(key, generation)
    }

    /** One in-flight job's bookkeeping. [generation] identifies it
     *  forever; [commandMsgId] links it to the chat message whose
     *  deletion must cancel it (null for the companion's photo turn,
     *  whose trigger is appended inside the job — the companion
     *  store cancels by identity instead, see TikaBotStore). */
    private class Record(
        val generation: Long,
        val commandMsgId: String?,
        var job: Job? = null,
        var watchdog: Job? = null,
    )

    private val lock = Any()
    private val records = mutableMapOf<String, Record>()
    private var nextGeneration = 0L

    private fun isCurrent(key: String, generation: Long): Boolean =
        synchronized(lock) { records[key]?.generation == generation } &&
            thinking.containsKey(key)

    /** The work job finished (any outcome). Clears the indicator
     *  ONLY if this generation is still the key's current one — a
     *  stale job's finally must never clear a newer job's entry. */
    private fun finish(key: String, generation: Long) {
        val wd: Job?
        synchronized(lock) {
            val rec = records[key]
            if (rec == null || rec.generation != generation) return
            records.remove(key)
            thinking.remove(key)
            wd = rec.watchdog
        }
        wd?.cancel()
    }

    /** Expire the key's job of [generation]: the indicator entry is
     *  removed SYNCHRONOUSLY (the animation dies now, not whenever
     *  the block unwinds), the record is dropped (the generation is
     *  stale from this instant — [Handle.isActive] false forever),
     *  and the work job is cancelled as a courtesy to whatever part
     *  of it can still hear cancellation. Returns false when the
     *  generation was already gone (nothing to expire). */
    private fun expire(key: String, generation: Long): Boolean {
        val rec: Record
        synchronized(lock) {
            val r = records[key]
            if (r == null || r.generation != generation) return false
            records.remove(key)
            thinking.remove(key)
            rec = r
        }
        rec.watchdog?.cancel()
        rec.job?.cancel()
        return true
    }

    /** Cancel the key's current job, whatever it is (companion
     *  cleanup, test teardown). The indicator clears immediately. */
    fun cancel(key: String) {
        val gen = synchronized(lock) { records[key]?.generation } ?: run {
            // No record (a label written directly, e.g. by a test):
            // still honour the clear.
            thinking.remove(key)
            return
        }
        expire(key, gen)
    }

    /**
     * ROUND 82 (delete = cancel): if the in-flight job for [key]
     * was triggered by the command message [msgId], cancel it NOW —
     * indicator cleared synchronously, generation staled, so no
     * reply can ever land for that command. Called by the repo
     * delete paths (delete-for-me and delete-for-everyone, real
     * engine and preview store alike) before they do their own
     * work. Returns true when a job was cancelled.
     */
    fun cancelForCommand(key: String, msgId: String): Boolean {
        val gen = synchronized(lock) {
            records[key]?.takeIf { it.commandMsgId == msgId }?.generation
        } ?: return false
        val cancelled = expire(key, gen)
        if (cancelled) {
            Log.i("AiJobs", "AI job for '$key' cancelled — its command message was deleted")
        }
        return cancelled
    }

    /**
     * The companion chat's single-flight busy flag (TikaBotScreen's
     * old screen-local `sending`), hoisted with the job so it
     * survives navigation and still blocks duplicate sends on
     * re-entry. Managed exclusively by [launchCompanion] — set
     * synchronously at launch, cleared in its finally.
     *
     * ROUND 73: this is a VIEW over [thinking], not a second store:
     * the getter reads the companion's map entry and the setter
     * writes/removes it, so the flag and the map entry are the same
     * fact and can never diverge (the pre-R73 code kept an
     * independent `mutableStateOf` in lockstep by hand). The setter
     * stays live for test cleanup (`AiJobs.companionBusy = false`
     * in the harness setup clears the companion's map entry).
     *
     * ROUND 82: setting it false is now a full cancel of the
     * companion's current job (record, watchdog and all), not just
     * a label removal — cleanup can never leave a job running
     * behind a cleared flag.
     */
    var companionBusy: Boolean
        get() = thinking.containsKey(COMPANION_KEY)
        set(value) {
            if (value) {
                if (!thinking.containsKey(COMPANION_KEY)) {
                    thinking[COMPANION_KEY] = COMPANION_LABEL
                }
            } else {
                cancel(COMPANION_KEY)
            }
        }

    /** The companion's key in [thinking] — registered by
     *  [launchCompanion] (via [companionBusy]) so every in-flight
     *  AI job is observable in one place. The companion UI reads
     *  [companionBusy] (its indicator is the fixed "typing…"
     *  bubble, not a label). */
    const val COMPANION_KEY: String = "tika-companion"
    private const val COMPANION_LABEL: String = "typing…"

    /** ROUND 73: the companion job's backstop ceiling — the same
     *  660 s as [launch]'s default, because the companion also runs
     *  /babeg, whose own self-bound inside GeminiClient is 600 s.
     *  Pure backstop against a wedged job; see [launch]. */
    private const val COMPANION_TIMEOUT_MS: Long = 660_000L

    /**
     * Launch [block] as the AI job for chat [key], showing [label]
     * in [thinking] while it runs.
     *
     * Single-flight, defence-in-depth: returns false WITHOUT
     * launching when [key] already has a job in flight (the call
     * sites gate on `thinking[threadId]` first and toast; this is
     * the backstop so no path can ever double-fire). The label is
     * registered SYNCHRONOUSLY, before the coroutine is dispatched,
     * so the gate has no window.
     *
     * ROUND 82: the [timeoutMs] backstop is enforced by a WATCHDOG,
     * not by wrapping the block (the R73 `withTimeoutOrNull` could
     * not complete while the block sat in a non-cancellable blocking
     * read — see the class doc). The watchdog sleeps on `delay`,
     * then expires the job: indicator removed, job cancelled,
     * generation staled. [commandMsgId] links the job to its
     * command message so deleting that message cancels the job via
     * [cancelForCommand]. The block receives a [Handle] and must
     * check [Handle.isActive] immediately before landing anything.
     */
    fun launch(
        key: String,
        label: String,
        timeoutMs: Long = 660_000L,
        commandMsgId: String? = null,
        block: suspend (Handle) -> Unit,
    ): Boolean {
        val record: Record
        synchronized(lock) {
            if (thinking.containsKey(key)) return false
            record = Record(++nextGeneration, commandMsgId)
            records[key] = record
            thinking[key] = label
        }
        val handle = Handle(key, record.generation)
        val job = scope.launch {
            try {
                block(handle)
            } finally {
                finish(key, record.generation)
            }
        }
        val watchdog = scope.launch {
            delay(timeoutMs)
            if (expire(key, record.generation)) {
                Log.w(
                    "AiJobs",
                    "AI job for '$key' hit the ${timeoutMs}ms backstop and was expired; " +
                        "indicator cleared, and its handle is stale so nothing can post",
                )
            }
        }
        synchronized(lock) {
            record.job = job
            record.watchdog = watchdog
        }
        // The block may have finished (or been expired) before the
        // handles were wired in — never leave a watchdog running
        // behind a dead record.
        if (!isCurrent(key, record.generation)) watchdog.cancel()
        return true
    }

    /**
     * Launch [block] as the companion's one AI job. Same contract
     * as [launch], on the companion's own gate: returns false
     * without launching while [companionBusy]; the flag is set
     * synchronously and cleared when the job ends — by completion,
     * by the [COMPANION_TIMEOUT_MS] watchdog, or by
     * [cancelCompanion] (the companion store's delete hook). The
     * block's [Handle] guards every landing, exactly as in a
     * normal chat.
     */
    fun launchCompanion(
        timeoutMs: Long = COMPANION_TIMEOUT_MS,
        block: suspend (Handle) -> Unit,
    ): Boolean = launch(COMPANION_KEY, COMPANION_LABEL, timeoutMs, null, block)

    /** ROUND 82: cancel the companion's in-flight job (its trigger
     *  message was deleted). Busy flag + indicator clear now; the
     *  job's handle goes stale so its reply can never land. */
    fun cancelCompanion() {
        cancel(COMPANION_KEY)
    }
}
