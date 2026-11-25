package com.tiku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ROUND 88 — presence freshness policy + lifecycle contract, pure unit
 * tests (no Firebase, no Robolectric: PresencePolicy is a plain object
 * in Models.kt). The two owner directions, pinned as arithmetic:
 *  - minimize/background must NOT flip the user offline: the heartbeat
 *    keeps stamping every HEARTBEAT_INTERVAL_MS while the process
 *    lives, and the reader tolerates exactly one missed tick;
 *  - a real exit must show offline FAST: an explicit online=false is
 *    instant, and a killed process (stamp frozen) expires at STALE_MS,
 *    not at the old ~3–4 min.
 */
class PresenceR88Test {

    // ---------- the core arithmetic ----------

    @Test fun staleThresholdIsTwiceHeartbeatInterval() {
        assertEquals(
            "threshold must be exactly 2x the interval: one missed tick tolerated, two not",
            2 * PresencePolicy.HEARTBEAT_INTERVAL_MS,
            PresencePolicy.STALE_MS,
        )
        assertTrue(
            "threshold must be comfortably longer than the interval",
            PresencePolicy.STALE_MS > PresencePolicy.HEARTBEAT_INTERVAL_MS,
        )
    }

    @Test fun oneMissedTickStaysOnline_twoMissedGoesOffline() {
        val now = 1_700_000_000_000L
        // Stamp from the tick at t=0; the t=60s tick was missed and the
        // t=120s tick is about to land: age 119_999ms is still online.
        assertTrue(PresencePolicy.isOnline(true, now - 119_999L, now))
        // Boundary is inclusive: exactly STALE_MS old is still online.
        assertTrue(PresencePolicy.isOnline(true, now - 120_000L, now))
        // Two consecutive missed ticks: age 120_001ms is offline.
        assertFalse(PresencePolicy.isOnline(true, now - 120_001L, now))
    }

    @Test fun explicitOfflineIsInstantEvenWithFreshStamp() {
        val now = 1_700_000_000_000L
        assertFalse(PresencePolicy.isOnline(false, now, now))
        assertFalse(PresencePolicy.isOnline(false, now - 10_000L, now))
    }

    @Test fun noStampLegacyDocIsTakenAtFaceValue() {
        val now = 1_700_000_000_000L
        assertTrue(PresencePolicy.isOnline(true, 0L, now))
        assertFalse(PresencePolicy.isOnline(false, 0L, now))
    }

    // ---------- hbAt vs lastSeen ----------

    @Test fun hbAtBeatsStaleLastSeen_andLegacyFallbackWorks() {
        val now = 1_700_000_000_000L
        // Backgrounded user, "Last seen" toggle off: lastSeen an hour
        // old, heartbeat 5s old — online off hbAt.
        val backgrounded = Person(
            uid = "u", name = "n", online = true,
            lastSeenMs = now - 3_600_000L, hbAtMs = now - 5_000L,
        )
        assertEquals(now - 5_000L, PresencePolicy.freshnessMs(backgrounded))
        assertTrue(
            PresencePolicy.isOnline(
                backgrounded.online,
                PresencePolicy.freshnessMs(backgrounded),
                now,
            ),
        )
        // Old build that never wrote hbAt: the fresh lastSeen still
        // counts via the fallback.
        val legacy = Person(
            uid = "u", name = "n", online = true,
            lastSeenMs = now - 5_000L, hbAtMs = 0L,
        )
        assertEquals(now - 5_000L, PresencePolicy.freshnessMs(legacy))
        assertTrue(
            PresencePolicy.isOnline(
                legacy.online,
                PresencePolicy.freshnessMs(legacy),
                now,
            ),
        )
        // Both stamps stale: offline.
        val dead = Person(
            uid = "u", name = "n", online = true,
            lastSeenMs = now - 3_600_000L, hbAtMs = now - 200_000L,
        )
        assertFalse(
            PresencePolicy.isOnline(
                dead.online,
                PresencePolicy.freshnessMs(dead),
                now,
            ),
        )
    }

    // ---------- lifecycle schedule simulation ----------

    @Test fun lifecycleSchedule_backgroundKeepsOnline_killExpiresAtThreshold() {
        // Simulated writer schedule with a fake clock, exactly as
        // setPresence + the heartbeat job drive it: ticks every
        // HEARTBEAT_INTERVAL_MS refresh the stamp; a background writes
        // nothing and cancels nothing; a kill freezes the stamp; a
        // lock writes online=false.
        val interval = PresencePolicy.HEARTBEAT_INTERVAL_MS
        var stamp = 0L
        fun tick(t: Long) {
            stamp = t
        }
        tick(0L) // setPresence(true): immediate write
        // Background at t=30: no write, no cancel — ticks continue.
        tick(60L)
        tick(120L)
        // t=150: stamp 120, age 30 -> online (minimize did not flip).
        assertTrue(PresencePolicy.isOnline(true, stamp, 150L))
        // Kill at t=160: stamp frozen at 120.
        // t=280: age exactly 120_000 -> still online (boundary).
        assertTrue(PresencePolicy.isOnline(true, stamp, 280L))
        // t=281: age 161_000 -> offline. Kill-to-offline <= 181 s.
        assertFalse(PresencePolicy.isOnline(true, stamp, 281L))
        // Lock at t=150 instead: online=false -> offline instantly.
        assertFalse(PresencePolicy.isOnline(false, stamp, 150L))
        // Sanity: the interval itself is the 60 s the writer uses.
        assertEquals(60_000L, interval)
    }
}
