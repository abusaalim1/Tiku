package com.tiku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ROUND 90 — Watch Together fix #2 contracts (pure, no Android).
 *
 *  Bug 1: the player NEVER loaded on a real phone. Proven root
 *  cause (real Chromium, round-90 report): the player WebView's
 *  shouldOverrideUrlLoading returned true for EVERY navigation,
 *  and that callback fires for SUBFRAME documents too — so the
 *  IFrame API's own embed iframe was cancelled before it could
 *  load, onReady never fired, and every attempt died on the
 *  watchdog. The policy below is the fix: block the main frame
 *  only, let subframes through.
 *
 *  Bug 2: Back from the pane left the rejoin banner on the chat
 *  forever (a Back-leave keeps the session "active", and the
 *  banner had no dismissal). The visibility rule below is the
 *  fix: dismissed-for-this-session never shows; a new session
 *  shows again. */
class WatchTogetherR90Test {

    // ---------- navigation policy (bug 1) ----------

    @Test fun playerNav_mainFrameAlwaysBlocked() {
        // The player page itself never navigates anywhere — a tap
        // on the YouTube logo, a redirect, anything at main-frame
        // level is refused, so nothing can leave Tik.
        assertTrue(watchPlayerBlocksNavigation(isForMainFrame = true))
    }

    @Test fun playerNav_subframesAlwaysAllowed() {
        // The embed iframe (youtube.com/embed/<id>) and everything
        // it nests are subframes — blocking them was the bug.
        assertFalse(watchPlayerBlocksNavigation(isForMainFrame = false))
    }

    // ---------- refused-embed routing (bug 1, part 2) ----------

    @Test fun refusedEmbed_codesOfferBrowser() {
        // 101/150 = embedding forbidden by the owner; 152 =
        // YouTube refuses playback in this player/environment.
        // "Try again" can never fix any of them.
        assertTrue(watchErrorOffersBrowser(101))
        assertTrue(watchErrorOffersBrowser(150))
        assertTrue(watchErrorOffersBrowser(152))
    }

    @Test fun refusedEmbed_otherCodesKeepRetry() {
        assertFalse(watchErrorOffersBrowser(-1)) // load stall — retry is right
        assertFalse(watchErrorOffersBrowser(100)) // gone/private — retry won't help, but no browser either
        assertFalse(watchErrorOffersBrowser(2))
        assertFalse(watchErrorOffersBrowser(5))
        assertFalse(watchErrorOffersBrowser(0))
    }

    @Test fun refusedEmbed_copyNamesTheBrowserWayOut() {
        for (code in listOf(101, 150, 152)) {
            assertTrue(
                "code $code must point at the in-app browser",
                watchPlayerErrorHint(code).contains("in-app browser"),
            )
            assertTrue(
                "code $code must be honest that the browser is not synced",
                watchPlayerErrorHint(code).contains("won't be synced"),
            )
        }
        // The embedding codes name the actual reason.
        assertTrue(watchPlayerErrorTitle(101).contains("doesn't allow embedding"))
        assertTrue(watchPlayerErrorTitle(150).contains("doesn't allow embedding"))
        // A plain stall keeps the R89 copy + retry hint.
        assertEquals("Couldn't load the video.", watchPlayerErrorTitle(-1))
        assertEquals(
            "Check your connection and try again.", watchPlayerErrorHint(-1),
        )
    }

    @Test fun watchUrl_isTheCanonicalWatchPage() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            youtubeWatchUrl("dQw4w9WgXcQ"),
        )
    }

    // ---------- banner visibility (bug 2) ----------

    private fun session(state: String, id: String = "s1") = WatchSession(
        id = id, kind = "youtube", state = state, inviteMsgId = "m1",
    )

    @Test fun banner_liveSessionPaneClosedShows() {
        assertTrue(
            watchBannerShouldShow(
                session("playing"), paneOpen = false,
                hasInvite = true, dismissedSessionId = null,
            ),
        )
        assertTrue(
            watchBannerShouldShow(
                session("paused"), paneOpen = false,
                hasInvite = true, dismissedSessionId = null,
            ),
        )
    }

    @Test fun banner_dismissedSessionNeverShows() {
        // The exact owner case: Back out of the pane (which parks
        // the session paused — still "active"), and the banner
        // must NOT be left on the chat.
        assertFalse(
            watchBannerShouldShow(
                session("paused"), paneOpen = false,
                hasInvite = true, dismissedSessionId = "s1",
            ),
        )
    }

    @Test fun banner_newSessionShowsAgain() {
        assertTrue(
            watchBannerShouldShow(
                session("waiting", id = "s2"), paneOpen = false,
                hasInvite = true, dismissedSessionId = "s1",
            ),
        )
    }

    @Test fun banner_hiddenWhilePaneOpenOrSessionEndedOrNoInvite() {
        assertFalse(
            watchBannerShouldShow(
                session("playing"), paneOpen = true,
                hasInvite = true, dismissedSessionId = null,
            ),
        )
        assertFalse(
            watchBannerShouldShow(
                session("ended"), paneOpen = false,
                hasInvite = true, dismissedSessionId = null,
            ),
        )
        assertFalse(
            watchBannerShouldShow(
                session("playing"), paneOpen = false,
                hasInvite = false, dismissedSessionId = null,
            ),
        )
        assertFalse(
            watchBannerShouldShow(
                null, paneOpen = false,
                hasInvite = true, dismissedSessionId = null,
            ),
        )
    }
}
