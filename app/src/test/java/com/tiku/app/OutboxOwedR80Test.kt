package com.tiku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ROUND 80 (WS-Q2) — the OWED-NOTIFY queue on the OutboxStore rails,
 * tested the way R78/R79 tested the send outbox's planners: the pure
 * encode/decode + drain-planning functions directly, and the store
 * operations against the store itself. Robolectric runner (like
 * ScreenshotsTest, which hosts the R78 store tests) so org.json and
 * SharedPreferences behave as they do on device.
 *
 * Pinned:
 *  - owed encode/decode round-trips byte-faithfully, INCLUDING the
 *    null ct/iv case (media types carry no ciphertext) and the
 *    present ct/iv case (text types carry the message's own);
 *  - planOwed drops entries strictly older than maxAge and keeps
 *    fresher ones (an entry exactly maxAge old still delivers);
 *  - enqueueOwed dedupes by msgId — re-enqueueing REPLACES, never
 *    duplicates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxOwedR80Test {

    // ---------- (a) encode / decode round-trip ----------

    @Test
    fun owedRoundTrip_withCtIv() {
        val e = OutboxStore.OwedNotify(
            msgId = "m1", chatId = "chatA", peerUid = "peer1",
            type = "text", ct = "CIPHERTEXT==", iv = "IVIVIV==",
            queuedAtMs = 1_700_000_000_000L,
        )
        val back = OutboxStore.decodeOwed(OutboxStore.encodeOwed(listOf(e)))
        assertEquals(1, back.size)
        assertEquals(e, back[0])
    }

    @Test
    fun owedRoundTrip_nullCtIv() {
        // Media pushes carry no ct/iv: the keys are omitted from the
        // JSON entirely and must decode back as null, not "".
        val e = OutboxStore.OwedNotify(
            msgId = "m2", chatId = "chatB", peerUid = "peer2",
            type = "photo", ct = null, iv = null, queuedAtMs = 42L,
        )
        val raw = OutboxStore.encodeOwed(listOf(e))
        assertTrue("ct key must be omitted for null ct", !raw.contains("\"ct\""))
        assertTrue("iv key must be omitted for null iv", !raw.contains("\"iv\""))
        val back = OutboxStore.decodeOwed(raw)
        assertEquals(1, back.size)
        assertEquals(e, back[0])
        assertNull(back[0].ct)
        assertNull(back[0].iv)
    }

    @Test
    fun owedRoundTrip_mixedListAndEmptyInputs() {
        val list = listOf(
            OutboxStore.OwedNotify("a", "c1", "p1", "text", "ct1", "iv1", 1L),
            OutboxStore.OwedNotify("b", "c2", "p2", "voice", null, null, 2L),
            OutboxStore.OwedNotify("c", "c3", "p3", "ai_text", "ct3", "iv3", 3L),
        )
        assertEquals(list, OutboxStore.decodeOwed(OutboxStore.encodeOwed(list)))
        assertTrue(OutboxStore.decodeOwed("").isEmpty())
        assertTrue(OutboxStore.decodeOwed("not json").isEmpty())
    }

    // ---------- (b) planOwed age planning ----------

    @Test
    fun planOwed_dropsOlderThanMaxAge_keepsFresher() {
        val now = 10_000_000L
        val entries = listOf(
            // queued 1h ago — fresh, delivers
            OutboxStore.OwedNotify("fresh", "c", "p", "text", queuedAtMs = now - 3_600_000L),
            // queued 7h ago against a 6h limit — stale, drops
            OutboxStore.OwedNotify("stale", "c", "p", "text", queuedAtMs = now - 7 * 3_600_000L),
        )
        val (deliver, drop) = OutboxStore.planOwed(entries, now, 6L * 3600 * 1000)
        assertEquals(listOf("fresh"), deliver.map { it.msgId })
        assertEquals(listOf("stale"), drop.map { it.msgId })
        // The 6h constant itself is the contract the engine drains with.
        assertEquals(6L * 3600 * 1000, OutboxStore.OWED_MAX_AGE_MS)
    }

    @Test
    fun planOwed_boundary_exactlyMaxAgeStillDelivers() {
        val now = 100_000_000L
        val maxAge = 6L * 3600 * 1000
        val edge = OutboxStore.OwedNotify(
            "edge", "c", "p", "photo", queuedAtMs = now - maxAge,
        )
        val justOver = OutboxStore.OwedNotify(
            "over", "c", "p", "photo", queuedAtMs = now - maxAge - 1L,
        )
        val (deliver, drop) = OutboxStore.planOwed(listOf(edge, justOver), now, maxAge)
        assertEquals(listOf("edge"), deliver.map { it.msgId })
        assertEquals(listOf("over"), drop.map { it.msgId })
    }

    @Test
    fun planOwed_emptyAndAllFresh() {
        val (d0, x0) = OutboxStore.planOwed(emptyList(), 123L, 1000L)
        assertTrue(d0.isEmpty()); assertTrue(x0.isEmpty())
        val e = OutboxStore.OwedNotify("n", "c", "p", "video", queuedAtMs = 100L)
        val (d1, x1) = OutboxStore.planOwed(listOf(e), 150L, 1000L)
        assertEquals(listOf("n"), d1.map { it.msgId })
        assertTrue(x1.isEmpty())
    }

    // ---------- (c) enqueueOwed dedupe by msgId ----------

    @Test
    fun enqueueOwed_dedupesByMsgId() {
        val ids = listOf("r80d1", "r80d2")
        try {
            OutboxStore.enqueueOwed(
                OutboxStore.OwedNotify("r80d1", "chatA", "peer1", "text", "ct-a", "iv-a", 100L),
            )
            OutboxStore.enqueueOwed(
                OutboxStore.OwedNotify("r80d2", "chatA", "peer1", "photo", null, null, 200L),
            )
            // Same msgId again (a second failed attempt for the same
            // message): REPLACES the entry — new queuedAtMs wins,
            // the queue does not grow.
            OutboxStore.enqueueOwed(
                OutboxStore.OwedNotify("r80d1", "chatA", "peer1", "text", "ct-a", "iv-a", 300L),
            )
            val mine = OutboxStore.owedAll().filter { it.msgId in ids }
            assertEquals(2, mine.size)
            assertEquals(300L, mine.first { it.msgId == "r80d1" }.queuedAtMs)
            // removeOwed drops exactly the one entry.
            OutboxStore.removeOwed("r80d1")
            assertEquals(
                listOf("r80d2"),
                OutboxStore.owedAll().filter { it.msgId in ids }.map { it.msgId },
            )
        } finally {
            ids.forEach { OutboxStore.removeOwed(it) }
        }
    }
}
