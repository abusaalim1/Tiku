package com.tiku.app

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
     */
    val thinking = mutableStateMapOf<String, String>()

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
     */
    var companionBusy: Boolean
        get() = thinking.containsKey(COMPANION_KEY)
        set(value) {
            if (value) thinking[COMPANION_KEY] = COMPANION_LABEL
            else thinking.remove(COMPANION_KEY)
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
     * so the gate has no window; it is cleared in a finally inside
     * the job, so completion, failure and even a thrown exception
     * all release the chat.
     *
     * ROUND 73: [block] runs under a [timeoutMs] backstop
     * (withTimeoutOrNull). The default — 660 s — is deliberately
     * ABOVE the longest self-bounded workload (game generation
     * self-bounds at 600 s inside GeminiClient.generateGame), so
     * the ceiling never cuts a legitimate job; it exists only so a
     * wedged provider call can never hold the indicator (or the
     * gate) forever. On timeout the block is cancelled mid-flight:
     * its outcome handling simply never runs — nothing is posted —
     * the timeout is logged, and the finally clears the label,
     * exactly as if the job had failed.
     */
    fun launch(key: String, label: String, timeoutMs: Long = 660_000L, block: suspend () -> Unit): Boolean {
        if (thinking.containsKey(key)) return false
        thinking[key] = label
        scope.launch {
            try {
                val finished = withTimeoutOrNull(timeoutMs) {
                    block()
                    true
                }
                if (finished == null) {
                    Log.w(
                        "AiJobs",
                        "AI job for '$key' hit the ${timeoutMs}ms backstop and was cancelled; " +
                            "indicator cleared, nothing posted by the timeout path",
                    )
                }
            } finally {
                thinking.remove(key)
            }
        }
        return true
    }

    /**
     * Launch [block] as the companion's one AI job. Same contract as
     * [launch], on the companion's own gate: returns false without
     * launching while [companionBusy]; the flag is set synchronously
     * and cleared in the job's finally, whatever the block does.
     * ROUND 73: registration writes the companion's [thinking]
     * entry through the [companionBusy] view (one store, one fact),
     * and the job runs under the [COMPANION_TIMEOUT_MS] backstop —
     * on timeout nothing is posted, the timeout is logged, and the
     * finally still clears the flag.
     */
    fun launchCompanion(block: suspend () -> Unit): Boolean {
        if (companionBusy) return false
        companionBusy = true
        scope.launch {
            try {
                val finished = withTimeoutOrNull(COMPANION_TIMEOUT_MS) {
                    block()
                    true
                }
                if (finished == null) {
                    Log.w(
                        "AiJobs",
                        "Companion AI job hit the ${COMPANION_TIMEOUT_MS}ms backstop and was " +
                            "cancelled; indicator cleared, nothing posted by the timeout path",
                    )
                }
            } finally {
                companionBusy = false
            }
        }
        return true
    }
}
