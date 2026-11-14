package com.tiku.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
     */
    val thinking = mutableStateMapOf<String, String>()

    /**
     * The companion chat's single-flight busy flag (TikaBotScreen's
     * old screen-local `sending`), hoisted with the job so it
     * survives navigation and still blocks duplicate sends on
     * re-entry. Managed exclusively by [launchCompanion] — set
     * synchronously at launch, cleared in its finally.
     */
    var companionBusy by mutableStateOf(false)

    /** The companion's key in [thinking] — registered alongside
     *  [companionBusy] so every in-flight AI job is observable in
     *  one place. The companion UI reads [companionBusy] (its
     *  indicator is the fixed "typing…" bubble, not a label). */
    const val COMPANION_KEY: String = "tika-companion"
    private const val COMPANION_LABEL: String = "typing…"

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
     */
    fun launch(key: String, label: String, block: suspend () -> Unit): Boolean {
        if (thinking.containsKey(key)) return false
        thinking[key] = label
        scope.launch {
            try {
                block()
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
     */
    fun launchCompanion(block: suspend () -> Unit): Boolean {
        if (companionBusy) return false
        companionBusy = true
        thinking[COMPANION_KEY] = COMPANION_LABEL
        scope.launch {
            try {
                block()
            } finally {
                companionBusy = false
                thinking.remove(COMPANION_KEY)
            }
        }
        return true
    }
}
