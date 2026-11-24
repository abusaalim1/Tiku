package com.tiku.app

/**
 * ROUND 85 (WS-A / WS-B) — the PENDING-vs-PERMANENT gate for message
 * decryption. Pure and side-effect free so the harness can pin the
 * contract without Firestore.
 *
 * The bug this fixes: a message doc lands BEFORE its payload is
 * decryptable — the sender writes the doc with `mediaChunks = 0`,
 * uploads the chunks, and only then updates the count; likewise a
 * cold-opened chat can be attempted before the conversation key is
 * derivable. The old code ran the decrypt anyway, it threw, and the
 * message was cached as an [TikuMessage.undecryptable] stub — the
 * "This message can't be opened on this phone" flash. Worse, when
 * the failure was a chunk that simply hadn't landed yet, NOTHING
 * ever re-attempted it: chunk writes are subcollection writes and
 * produce no change event on the message doc, so the stub latched.
 *
 * The contract now:
 *  - key not ready, payload count not landed, or a chunk still
 *    missing mid-download  ->  PENDING (loading treatment, retried);
 *  - key ready AND the decrypt genuinely failed (wrong key / corrupt
 *    ciphertext)           ->  PERMANENT (the honest placeholder);
 *  - no failure             ->  READY.
 */
object DecryptGate {

    enum class Verdict { READY, PENDING, PERMANENT }

    /** Media kinds whose payload rides the chunks subcollection. A
     *  doc for one of these with no chunk count yet is a doc whose
     *  sender is still uploading — pending by definition, because
     *  there is no ciphertext on the doc itself that could fail. */
    fun mediaPayloadPending(kindRaw: String, mediaChunks: Int?): Boolean =
        (mediaChunks ?: 0) <= 0 &&
            (kindRaw == "image" || kindRaw == "voice" ||
                kindRaw == "video" || kindRaw == "audio")

    /** A download that stopped because a chunk doc is not there yet
     *  is a pending state, not corruption: the sender commits chunks
     *  in batches and the count field can become visible a beat
     *  before a straggler chunk reads back. */
    fun isChunkMissing(error: Throwable?): Boolean =
        error is MediaStore.ChunkMissingException

    /** The can't-open placeholder may render ONLY for a permanent
     *  failure: the key was ready and the decrypt genuinely failed. */
    fun placeholderAllowed(keyReady: Boolean, verdict: Verdict): Boolean =
        keyReady && verdict == Verdict.PERMANENT

    /** Classify one decrypt attempt. [error] is the failure the
     *  attempt produced (null when it succeeded). */
    fun verdict(
        keyReady: Boolean,
        kindRaw: String,
        mediaChunks: Int?,
        error: Throwable?,
    ): Verdict = when {
        !keyReady -> Verdict.PENDING
        mediaPayloadPending(kindRaw, mediaChunks) -> Verdict.PENDING
        error == null -> Verdict.READY
        isChunkMissing(error) -> Verdict.PENDING
        else -> Verdict.PERMANENT
    }
}
