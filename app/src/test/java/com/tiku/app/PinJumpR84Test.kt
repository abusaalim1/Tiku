package com.tiku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ROUND 84 — the pinned-jump paging plan (PinJumpPlan), tested the way
 * the R78/R80 planners were: the pure decision functions directly,
 * plus a simulated walk that drives the planner exactly as
 * ChatScreen.jumpToMessage does (check → step → page → progress).
 *
 * The build-47 loop this replaces fired loadOlder on a 700 ms poll and
 * gave up after 10 tries: ~10 pages × 60 ≈ 600 older messages was the
 * deepest pin it could EVER reach, and slow pages burned the tries
 * without loading anything. These tests pin the new contract:
 *  - a found target scrolls, at any depth;
 *  - an absent target keeps loading while history remains and pages
 *    keep landing — thousands of messages deep, not 600;
 *  - it gives up ONLY when the history is exhausted (hasOlder false —
 *    the deleted-for-me case), at the formal page bound, or after
 *    repeated no-change loads (a dead network / exhausted quota);
 *  - a load that lands (window grows / head moves / history runs out)
 *    counts as progress and resets the stall counter; one that
 *    changes nothing is a stall.
 */
class PinJumpR84Test {

    // ---------- step(): the branch contract ----------

    @Test fun foundScrollsAtAnyDepth() {
        assertEquals(PinJumpPlan.Step.SCROLL, PinJumpPlan.step(0, hasOlder = true, pagesLoaded = 0, stalls = 0))
        assertEquals(PinJumpPlan.Step.SCROLL, PinJumpPlan.step(5_999, hasOlder = false, pagesLoaded = 99, stalls = 0))
    }

    @Test fun absentWithHistoryLoads() {
        assertEquals(PinJumpPlan.Step.LOAD, PinJumpPlan.step(-1, hasOlder = true, pagesLoaded = 0, stalls = 0))
        assertEquals(PinJumpPlan.Step.LOAD, PinJumpPlan.step(-1, hasOlder = true, pagesLoaded = 99, stalls = 3))
    }

    @Test fun absentWithoutHistoryGivesUp() {
        // The deleted-for-me end state: every page landed, the history
        // ran out, the target simply is not there for this user.
        assertEquals(PinJumpPlan.Step.GIVE_UP, PinJumpPlan.step(-1, hasOlder = false, pagesLoaded = 40, stalls = 0))
    }

    @Test fun pageBoundGivesUp() {
        assertEquals(
            PinJumpPlan.Step.GIVE_UP,
            PinJumpPlan.step(-1, hasOlder = true, pagesLoaded = PinJumpPlan.MAX_PAGES, stalls = 0),
        )
    }

    @Test fun repeatedStallsGiveUp() {
        assertEquals(PinJumpPlan.Step.LOAD, PinJumpPlan.step(-1, hasOlder = true, pagesLoaded = 2, stalls = PinJumpPlan.MAX_STALLS))
        assertEquals(
            PinJumpPlan.Step.GIVE_UP,
            PinJumpPlan.step(-1, hasOlder = true, pagesLoaded = 2, stalls = PinJumpPlan.MAX_STALLS + 1),
        )
    }

    // ---------- progressed(): what counts as a landed page ----------

    @Test fun progressDetection() {
        // window grew
        assertTrue(PinJumpPlan.progressed(60, "a", 120, "b", afterHasOlder = true))
        // head moved without growth (same-size replacement window)
        assertTrue(PinJumpPlan.progressed(60, "a", 60, "b", afterHasOlder = true))
        // the load exhausted the history
        assertTrue(PinJumpPlan.progressed(60, "a", 60, "a", afterHasOlder = false))
        // nothing changed at all — a failed fetch
        assertFalse(PinJumpPlan.progressed(60, "a", 60, "a", afterHasOlder = true))
    }

    // ---------- the walk: jumpToMessage's loop over a fake history ----------

    /** A fake thread history of [total] messages ("m0" oldest …
     *  "m{total-1}" newest). The loaded window starts as the newest
     *  [page] and each LOAD prepends one more page; hasOlder holds
     *  while unloaded history remains. Mirrors the engine's paging
     *  shape (60-message pages) without Firestore. */
    private class FakeHistory(val total: Int, val page: Int = 60) {
        var loadedFrom = maxOf(0, total - page)   // index of the oldest loaded message
        val hasOlder get() = loadedFrom > 0
        fun indexOf(id: String): Int {
            val all = id.removePrefix("m").toInt()
            return if (all >= loadedFrom) all - loadedFrom else -1
        }
        fun loadPage() { loadedFrom = maxOf(0, loadedFrom - page) }
    }

    /** Drives PinJumpPlan exactly as jumpToMessage does; returns the
     *  terminal step and the number of pages the walk loaded. */
    private fun walk(h: FakeHistory, target: String): Pair<PinJumpPlan.Step, Int> {
        var pages = 0
        var stalls = 0
        while (true) {
            val idx = h.indexOf(target)
            when (val s = PinJumpPlan.step(idx, h.hasOlder, pages, stalls)) {
                PinJumpPlan.Step.SCROLL, PinJumpPlan.Step.GIVE_UP -> return s to pages
                PinJumpPlan.Step.LOAD -> {
                    val beforeFrom = h.loadedFrom
                    val beforeHasOlder = h.hasOlder
                    h.loadPage()
                    if (PinJumpPlan.progressed(
                            beforeCount = h.total - beforeFrom, beforeOldestId = "m$beforeFrom",
                            afterCount = h.total - h.loadedFrom, afterOldestId = "m${h.loadedFrom}",
                            afterHasOlder = h.hasOlder,
                        )
                    ) { pages++; stalls = 0 } else { stalls++ }
                    check(beforeHasOlder) { "LOAD stepped with no history left" }
                }
            }
        }
    }

    @Test fun walkReachesPinThousandsDeep() {
        // 5,000-message chat; the pin sits 4,900 messages back — the
        // old loop's ~600-message cap could never reach it.
        val h = FakeHistory(total = 5_000)
        val (end, pages) = walk(h, "m100")
        assertEquals(PinJumpPlan.Step.SCROLL, end)
        assertTrue("the walk must page deep, was $pages", pages > 10)
        assertEquals(81, pages)
        assertEquals(80, h.loadedFrom)
    }

    @Test fun walkReachesPinJustPastTheOldCap() {
        // 700-message chat, pin at the very top: 11 pages — one past
        // the old loop's 10-try budget.
        val h = FakeHistory(total = 700)
        val (end, pages) = walk(h, "m0")
        assertEquals(PinJumpPlan.Step.SCROLL, end)
        assertEquals(11, pages)
    }

    @Test fun walkAbsentTargetGivesUpOnlyAtHistoryEnd() {
        // A pin this user deleted for themselves: it never appears,
        // and the walk must not give up a single page early — only
        // when the history is fully loaded and hasOlder is false.
        val h = FakeHistory(total = 700)
        val (end, pages) = walk(h, "m9999")
        assertEquals(PinJumpPlan.Step.GIVE_UP, end)
        assertEquals(0, h.loadedFrom)
        assertEquals(11, pages)
    }
}
