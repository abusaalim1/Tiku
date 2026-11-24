package com.tiku.app

/**
 * ROUND 84 — the pinned-strip jump's paging plan, as pure functions so
 * the contract is unit-testable without Firestore.
 *
 * The defect (owner, 2026-11-24): tapping the pinned strip on a very
 * old pin answered "Couldn't find that message". The build-47 jump
 * loop called the fire-and-forget `loadOlder` and re-polled the thread
 * on a fixed 700 ms timer, giving up after 10 tries. Two failure modes
 * fell out of that: (1) a HARD CAP — 10 pages × the engine's 60-message
 * page ≈ 600 older messages, so any pin deeper than ~660 messages
 * could NEVER be reached; (2) a RACE — a real page (a Firestore fetch
 * plus decrypting 60 docs, on a quota-throttled connection) routinely
 * takes longer than 700 ms, and `loadOlder` no-ops while a page is in
 * flight, so most of the 10 tries were burned without loading anything.
 *
 * The fix drives the jump off COMPLETION instead of a timer: the
 * engine's `loadOlderAndWait` suspends until a page is actually applied
 * to the thread, and this planner decides each step. The jump keeps
 * paging while history remains and pages keep landing; it gives up
 * ONLY when the history is genuinely exhausted (the one case where
 * "couldn't find" is honest — e.g. the user deleted that message for
 * themselves), when the formal per-tap bound is hit, or when page
 * loads repeatedly change nothing at all (a dead network / exhausted
 * read quota — retrying forever would hang the tap).
 */
internal object PinJumpPlan {

    /** Formal per-tap bound: 100 pages × the engine's 60-message page
     *  ≈ 6,000 messages of history — far beyond any real chat, but a
     *  bound all the same: a runaway loop is impossible. */
    const val MAX_PAGES = 100

    /** Consecutive completed page loads that changed NOTHING (the
     *  fetch failed — e.g. the read quota is exhausted) before the
     *  jump gives up. A load that lands, even partially, resets this. */
    const val MAX_STALLS = 3

    enum class Step { SCROLL, LOAD, GIVE_UP }

    /** What the jump does next, given what it currently sees:
     *  [foundIndex] = the target's index in the loaded, visible list
     *  (negative while absent); [hasOlder] = the engine says older
     *  history exists beyond the loaded window; [pagesLoaded] = pages
     *  this tap has already landed; [stalls] = consecutive landed-or-
     *  failed loads that changed nothing. */
    fun step(foundIndex: Int, hasOlder: Boolean, pagesLoaded: Int, stalls: Int): Step = when {
        foundIndex >= 0 -> Step.SCROLL
        !hasOlder -> Step.GIVE_UP
        pagesLoaded >= MAX_PAGES -> Step.GIVE_UP
        stalls > MAX_STALLS -> Step.GIVE_UP
        else -> Step.LOAD
    }

    /** Did a completed page load move the jump forward? The loaded
     *  window grew or its head moved (new older messages landed), or
     *  the load exhausted the history ([afterHasOlder] false — the
     *  next check then settles the jump one way or the other). A load
     *  that changed none of these was a failed fetch. */
    fun progressed(
        beforeCount: Int, beforeOldestId: String?,
        afterCount: Int, afterOldestId: String?,
        afterHasOlder: Boolean,
    ): Boolean =
        afterCount != beforeCount || afterOldestId != beforeOldestId || !afterHasOlder
}
