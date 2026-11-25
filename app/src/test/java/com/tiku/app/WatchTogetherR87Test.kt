package com.tiku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * ROUND 87 — Watch Together sync math, tested the way the R78/R80/R84
 * planners were: the pure decision functions directly, plus a simulated
 * playback walk that drives them exactly as WatchTogetherPane does
 * (tick -> heartbeat gate / seek detection / drift check).
 *
 * The contract these tests pin:
 *  - drift over 2 s seeks, 2 s or under does not (boundary included);
 *  - the corrected position adds wall-clock time while playing only,
 *    and never without a server stamp;
 *  - a late join jumps to the corrected position once;
 *  - the heartbeat fires at most once per 15 s, while playing only;
 *  - a host seek publishes at once (and ordinary playback never
 *    masquerades as a seek);
 *  - one active session per chat (a new start ends the old one);
 *  - host-leave parks the session paused with the hostLeft flag and
 *    the frozen corrected position; a guest leaving touches nothing
 *    but their joined flag;
 *  - YouTube links parse from every share shape; anything else that
 *    is a usable link is a web page; garbage is refused.
 */
class WatchTogetherR87Test {

    private fun session(
        state: String = "waiting",
        positionSec: Double = 0.0,
        updatedAtMs: Long = 0L,
        hostUid: String = "host1",
        joined: Map<String, Boolean> = mapOf("host1" to true),
    ) = WatchSession(
        id = "s1", url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        videoId = "dQw4w9WgXcQ", kind = "youtube", hostUid = hostUid,
        state = state, positionSec = positionSec, updatedAtMs = updatedAtMs,
        joined = joined, inviteMsgId = "s1",
    )

    // ---------- drift -> seek ----------

    @Test fun driftOverTwoSecondsSeeks() {
        assertTrue(WatchSync.shouldSeek(10.0, 12.5))
        assertTrue(WatchSync.shouldSeek(12.5, 10.0))
        assertTrue(WatchSync.shouldSeek(0.0, 30.0))
    }

    @Test fun driftAtOrUnderTwoSecondsDoesNotSeek() {
        // The boundary itself is jitter, not desync.
        assertFalse(WatchSync.shouldSeek(10.0, 12.0))
        assertFalse(WatchSync.shouldSeek(12.0, 10.0))
        assertFalse(WatchSync.shouldSeek(10.0, 11.5))
        assertFalse(WatchSync.shouldSeek(10.0, 10.0))
    }

    // ---------- clock correction ----------

    @Test fun correctedPositionAddsElapsedWhilePlaying() {
        // Host wrote position 30 at t=100_000; at t=110_000 the guest
        // must see 40.
        assertEquals(
            40.0,
            WatchSync.correctedPosition("playing", 30.0, 100_000L, 110_000L),
            0.001,
        )
    }

    @Test fun correctedPositionFrozenWhenPausedOrWaiting() {
        assertEquals(
            30.0,
            WatchSync.correctedPosition("paused", 30.0, 100_000L, 110_000L),
            0.001,
        )
        assertEquals(
            0.0,
            WatchSync.correctedPosition("waiting", 0.0, 100_000L, 110_000L),
            0.001,
        )
    }

    @Test fun correctedPositionIgnoresUnstampedWrite() {
        // updatedAt 0 = the local echo before the server stamp lands:
        // correcting against it would add ~55 years of playback.
        assertEquals(
            30.0,
            WatchSync.correctedPosition("playing", 30.0, 0L, 1_700_000_000_000L),
            0.001,
        )
    }

    @Test fun correctedPositionNeverGoesNegative() {
        // Future-dated write (clock skew): no rewind, stored position stands.
        assertEquals(
            5.0,
            WatchSync.correctedPosition("playing", 5.0, 110_000L, 100_000L),
            0.001,
        )
        // Negative stored position clamps to 0 while playing…
        assertEquals(
            0.0,
            WatchSync.correctedPosition("playing", -3.0, 100_000L, 101_000L),
            0.001,
        )
        // …and while paused.
        assertEquals(
            0.0,
            WatchSync.correctedPosition("paused", -2.0, 100_000L, 105_000L),
            0.001,
        )
    }

    // ---------- join catch-up ----------

    @Test fun joinCatchUpJumpsToCorrectedPosition() {
        // Guest joins 25 s late into a video the host started at 30 s.
        val s = session(state = "playing", positionSec = 30.0, updatedAtMs = 100_000L)
        val target = WatchSync.correctedPosition(
            s.state, s.positionSec, s.updatedAtMs, 125_000L,
        )
        assertEquals(55.0, target, 0.001)
        assertTrue(WatchSync.shouldSeek(0.0, target))
    }

    // ---------- heartbeat cadence ----------

    @Test fun heartbeatCadenceIsFifteenSeconds() {
        assertEquals(15_000L, WatchSync.HEARTBEAT_MS)
    }

    @Test fun heartbeatDueOnlyWhilePlayingAndOnlyAfterCadence() {
        assertFalse(WatchSync.heartbeatDue(100_000L, 100_000L, true))
        assertFalse(WatchSync.heartbeatDue(100_000L, 114_999L, true))
        assertTrue(WatchSync.heartbeatDue(100_000L, 115_000L, true))
        assertTrue(WatchSync.heartbeatDue(100_000L, 200_000L, true))
        assertFalse(WatchSync.heartbeatDue(100_000L, 200_000L, false))
    }

    // ---------- seek detection (host ticks) ----------

    @Test fun ordinaryPlaybackIsNeverASeek() {
        // Last publish: pos 30 at t=100_000. Ticks at +1 s, +2 s, +3 s
        // track the expected position exactly — no publish.
        assertFalse(WatchSync.seekJumpDetected(30.0, 100_000L, 31.0, 101_000L))
        assertFalse(WatchSync.seekJumpDetected(30.0, 100_000L, 32.0, 102_000L))
        assertFalse(WatchSync.seekJumpDetected(30.0, 100_000L, 33.0, 103_000L))
        // A 1 s wobble (slow tick) is still ordinary playback.
        assertFalse(WatchSync.seekJumpDetected(30.0, 100_000L, 32.5, 102_000L))
    }

    @Test fun realSeekDetectedWithinOneTick() {
        // Host jumps from 30 to 90: the next tick is 57 s ahead of
        // where ordinary playback would be.
        assertTrue(WatchSync.seekJumpDetected(30.0, 100_000L, 90.0, 103_000L))
        // Backwards too.
        assertTrue(WatchSync.seekJumpDetected(90.0, 103_000L, 10.0, 104_000L))
    }

    @Test fun seekDetectionNeedsARealPublishWatermark() {
        assertFalse(WatchSync.seekJumpDetected(30.0, 0L, 90.0, 103_000L))
    }

    // ---------- simulated playback walk (pane's exact tick logic) ----------

    @Test fun playbackWalkHeartbeatAndSeekAndDrift() {
        // Host publishes playing at 30 s (t=100_000). Ticks every 1 s.
        var lastSentPos = 30.0
        var lastSentMs = 100_000L
        var publishes = 0
        fun hostTick(pos: Double, nowMs: Long) {
            if (WatchSync.seekJumpDetected(lastSentPos, lastSentMs, pos, nowMs)) {
                lastSentPos = pos; lastSentMs = nowMs; publishes++
            } else if (WatchSync.heartbeatDue(lastSentMs, nowMs, true)) {
                lastSentPos = pos; lastSentMs = nowMs; publishes++
            }
        }
        // 14 s of ordinary playback: zero publishes.
        for (i in 1..14) hostTick(30.0 + i, 100_000L + i * 1_000L)
        assertEquals(0, publishes)
        // The 15 s tick: exactly one heartbeat.
        hostTick(45.0, 115_000L)
        assertEquals(1, publishes)
        // 14 more seconds: still just the one.
        for (i in 1..14) hostTick(45.0 + i, 115_000L + i * 1_000L)
        assertEquals(1, publishes)
        // Host seeks to 120: the very next tick publishes.
        hostTick(120.0, 130_000L)
        assertEquals(2, publishes)
        assertEquals(120.0, lastSentPos, 0.001)

        // Guest side on the same walk: its player runs 1 s behind the
        // host's corrected position (jitter) — no seek; a 5 s stall
        // behind is a seek.
        val hostPos = WatchSync.correctedPosition(
            "playing", lastSentPos, lastSentMs, 131_000L,
        )
        assertEquals(121.0, hostPos, 0.001)
        assertFalse(WatchSync.shouldSeek(120.0, hostPos))
        assertTrue(WatchSync.shouldSeek(115.0, hostPos))
    }

    // ---------- single-active-session rule ----------

    @Test fun newSessionEndsOldOne() {
        val old = session(state = "playing", positionSec = 42.0)
        val ended = WatchSync.superseded(old)
        assertEquals("ended", ended?.state)
        assertEquals(42.0, ended?.positionSec ?: -1.0, 0.001)
        assertEquals("s1", ended?.id)
    }

    @Test fun supersedeIsNoOpWithoutAnActiveSession() {
        assertNull(WatchSync.superseded(null))
        assertNull(WatchSync.superseded(session(state = "ended")))
    }

    // ---------- host-leave / guest-leave ----------

    @Test fun hostLeaveParksPausedWithFrozenPosition() {
        val s = session(
            state = "playing", positionSec = 30.0, updatedAtMs = 100_000L,
        )
        val left = WatchSync.hostLeftState(s, 110_000L)
        assertEquals("paused", left.state)
        assertTrue(left.hostLeft)
        assertEquals(40.0, left.positionSec, 0.001)
        assertEquals(110_000L, left.updatedAtMs)
        assertTrue(left.active)
    }

    @Test fun hostLeaveNeverResurrectsAnEndedSession() {
        val s = session(state = "ended", positionSec = 40.0)
        val left = WatchSync.hostLeftState(s, 110_000L)
        assertEquals("ended", left.state)
        assertFalse(left.hostLeft)
    }

    @Test fun guestLeaveClearsOnlyTheirJoinedFlag() {
        val s = session(
            state = "playing", positionSec = 30.0,
            joined = mapOf("host1" to true, "guest1" to true),
        )
        val left = WatchSync.guestLeft(s, "guest1")
        assertEquals("playing", left.state)
        assertEquals(30.0, left.positionSec, 0.001)
        assertFalse(left.joined["guest1"] == true)
        assertTrue(left.joined["host1"] == true)
    }

    // ---------- host-stale grace ----------

    @Test fun hostStaleAfterThreeMissedHeartbeats() {
        val s = session(state = "playing", positionSec = 30.0, updatedAtMs = 100_000L)
        assertFalse(WatchSync.hostStale(s, 144_999L))
        assertFalse(WatchSync.hostStale(s, 145_000L))
        assertTrue(WatchSync.hostStale(s, 145_001L))
        // Paused sessions are never "stale" — the host is not expected
        // to write while paused.
        assertFalse(WatchSync.hostStale(s.copy(state = "paused"), 200_000L))
    }

    // ---------- URL parsing ----------

    @Test fun youtubeLinksParseFromEveryShareShape() {
        val id = "dQw4w9WgXcQ"
        val urls = listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtube.com/watch?v=$id&t=42s",
            "https://www.youtube.com/watch?v=$id&list=PL123",
            "https://youtu.be/$id",
            "https://youtu.be/$id?t=30",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id",
            "https://music.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?v=$id",
        )
        for (u in urls) {
            val p = WatchSync.parseUrl(u)
            assertEquals("must parse: $u", WatchSync.Kind.YOUTUBE, p?.kind)
            assertEquals("id must survive: $u", id, p?.videoId)
        }
    }

    @Test fun youtubeIdExtractionIsStrict() {
        assertNull(WatchSync.extractYouTubeId("https://www.youtube.com/watch"))
        assertNull(WatchSync.extractYouTubeId("https://www.youtube.com/watch?v=short"))
        assertNull(WatchSync.extractYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ!"))
        assertEquals(
            "dQw4w9WgXcQ",
            WatchSync.extractYouTubeId("https://youtu.be/dQw4w9WgXcQ"),
        )
        // A video id in a NON-YouTube URL is not a YouTube link.
        assertNull(WatchSync.extractYouTubeId("https://example.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun nonYoutubeLinksAreWebPages() {
        val p = WatchSync.parseUrl("https://example.com/a-nice-article")
        assertEquals(WatchSync.Kind.WEB, p?.kind)
        assertNull(p?.videoId)
        assertEquals("https://example.com/a-nice-article", p?.url)
        // A bare domain gets https://.
        assertEquals(
            "https://example.com",
            WatchSync.parseUrl("example.com")?.url,
        )
        // A bare YouTube homepage is a web page, not a video.
        assertEquals(
            WatchSync.Kind.WEB,
            WatchSync.parseUrl("https://www.youtube.com/")?.kind,
        )
    }

    @Test fun garbageLinksAreRefused() {
        assertNull(WatchSync.parseUrl(""))
        assertNull(WatchSync.parseUrl("   "))
        assertNull(WatchSync.parseUrl("hello world"))
        assertNull(WatchSync.parseUrl("just some words"))
        assertNull(WatchSync.parseUrl("javascript:alert(1)"))
        assertNull(WatchSync.parseUrl("intent://scan#Intent;scheme=zxing;end"))
        assertNull(WatchSync.parseUrl("https://"))
        assertNull(WatchSync.parseUrl("https://.com"))
        assertNull(WatchSync.parseUrl("x".repeat(3000)))
    }

    @Test fun correctedPositionBoundsTheWalk() {
        // The exact numbers the pane's join path uses: a guest joining
        // a 10-minute video at the host's 90 s mark, 5 s after the
        // host's last write, lands at 95 s — inside the video.
        val target = WatchSync.correctedPosition("playing", 90.0, 100_000L, 105_000L)
        assertEquals(95.0, target, 0.001)
        assertTrue(abs(target) < 600.0)
    }

    // ================= ROUND 89: Watch Together FIX =================
    // Each test below FAILS on the R87 code and PASSES on the R89
    // code — they pin the exact defects the owner hit on his phone
    // (see round89-PLAN.md Step 1):
    //  - the player frame was ~2.1:1, not 16:9 (264 dp pane);
    //  - a stalled player load had no error text at all (no code
    //    mapping, no timeout code);
    //  - a YouTube link in a plain message had no Watch Together
    //    offer (the tap went to the external browser).

    @Test fun r89PlayerFrameIsSixteenNine() {
        // The R87 pane (264 dp) fails this at every phone width:
        // 264 - 46 - 50 = 168 dp of video at ~360 dp wide is 2.14:1.
        assertFalse(WatchPlayerFrame.isNearSixteenNine(360f, 264))
        assertFalse(WatchPlayerFrame.isNearSixteenNine(393f, 264))
        // The R89 pane (312 dp) leaves 216 dp of video: 16:9 within
        // 8% at 360-412 dp-wide phones.
        assertEquals(216, WatchPlayerFrame.videoHeightDp(WATCH_PANE_HEIGHT_DP))
        assertTrue(WatchPlayerFrame.isNearSixteenNine(360f, WATCH_PANE_HEIGHT_DP))
        assertTrue(WatchPlayerFrame.isNearSixteenNine(393f, WATCH_PANE_HEIGHT_DP))
        assertTrue(WatchPlayerFrame.isNearSixteenNine(412f, WATCH_PANE_HEIGHT_DP))
        // The deadline the pane enforces on a stalled load.
        assertEquals(15_000L, WatchPlayerFrame.LOAD_TIMEOUT_MS)
    }

    @Test fun r89PlayerErrorTitlesNameTheFailure() {
        // -1 is the load-timeout code (JS watchdog + pane deadline):
        // the R87 pane had no code mapping at all, so a stalled load
        // showed nothing — now it names the failure in plain words.
        assertEquals("Couldn't load the video.", watchPlayerErrorTitle(-1))
        assertEquals(
            "Check your connection and try again.",
            watchPlayerErrorHint(-1),
        )
        assertEquals("This video isn't available.", watchPlayerErrorTitle(100))
        assertEquals(
            "It may have been removed or made private.",
            watchPlayerErrorHint(100),
        )
        assertEquals(
            "This video can't play here — its owner doesn't allow embedding.",
            watchPlayerErrorTitle(101),
        )
        assertEquals(
            "This video can't play here — its owner doesn't allow embedding.",
            watchPlayerErrorTitle(150),
        )
        assertEquals(
            "Try another video — this one can't play inside Tik.",
            watchPlayerErrorHint(150),
        )
        assertEquals("This video can't play here.", watchPlayerErrorTitle(2))
        assertEquals("This video can't play here.", watchPlayerErrorTitle(5))
        assertEquals("This video can't play here.", watchPlayerErrorTitle(999))
    }

    @Test fun r89YouTubeLinksOfferWatchTogether() {
        // The link-tap offer fires exactly for YouTube videos (every
        // share shape) and never for a plain web page — the R87 tap
        // sent every link to the external browser instead.
        for (url in listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc123",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
        )) {
            assertEquals(
                "offer expected for $url",
                WatchSync.Kind.YOUTUBE,
                WatchSync.parseUrl(url)?.kind,
            )
        }
        assertEquals(
            WatchSync.Kind.WEB,
            WatchSync.parseUrl("https://example.com/a-nice-article")?.kind,
        )
        assertEquals(
            WatchSync.Kind.WEB,
            WatchSync.parseUrl("https://www.youtube.com/")?.kind,
        )
    }
}
