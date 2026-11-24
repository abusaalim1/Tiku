package com.tiku.app

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File

/**
 * ROUND 85 (WS-F) — STAGED sending (WhatsApp / Instagram shape): a
 * picked or captured photo/video no longer sends the moment it is
 * chosen. It lands in the composer's staging strip (thumbnails above
 * the input box, each removable with ✕) and only the composer's Send
 * button commits it — through the EXACT existing send pipeline
 * (sendPhotoVo / sendVideoVo: encryption, chunking, OutboxStore
 * persistence, guarded sends), exactly as the pickers used to call
 * them directly. Staging itself is LOCAL UI state only: an unsent
 * staged item is NOT an outbox entry and touches no schema.
 *
 * Leave-chat behaviour follows the per-chat draft rule (DraftStore):
 * staged items are kept PER CHAT — leaving a chat and coming back
 * restores its staged media exactly like its draft text. The store
 * is in-memory (a staged item holds a live Bitmap / a cache File;
 * drafts persist to prefs because they are plain text — staged media
 * does not survive process death, and its cache files are
 * OS-purgeable by design).
 */
sealed class StagedMedia {
    /** Stable identity for the strip's ✕ and list keys. */
    abstract val key: String
    /** The view-once allowance chosen for THIS item (the composer's
     *  arm at pick time, or the camera confirm screen's selector for
     *  a capture): 1 / 2, null = Keep (unlimited). Rides the existing
     *  send functions' `vo` parameter — no schema change. */
    abstract val vo: Int?
    /** What the staging strip draws for this item. */
    abstract val thumb: Bitmap?

    data class Photo(
        override val key: String,
        val bmp: Bitmap,
        override val vo: Int?,
    ) : StagedMedia() {
        override val thumb: Bitmap get() = bmp
    }

    data class Video(
        override val key: String,
        val file: File,
        val mime: String,
        val durSec: Int,
        val poster: Bitmap?,
        override val vo: Int?,
    ) : StagedMedia() {
        override val thumb: Bitmap? get() = poster
    }
}

/** Which existing pipeline entry a staged item commits through. Pure
 *  so the harness can pin the mapping: a staged item must NEVER grow
 *  a send path of its own. */
enum class StagedSendRoute { PHOTO_VO, VIDEO_VO }

fun stagedSendRoute(item: StagedMedia): StagedSendRoute = when (item) {
    is StagedMedia.Photo -> StagedSendRoute.PHOTO_VO
    is StagedMedia.Video -> StagedSendRoute.VIDEO_VO
}

/** Per-chat staging lists (see the file header for the draft rule
 *  this follows). Snapshot state: the open composer recomposes when
 *  its chat's list changes. */
object StagedMediaStore {
    private val byChat = mutableMapOf<String, SnapshotStateList<StagedMedia>>()

    fun listFor(chatId: String): SnapshotStateList<StagedMedia> =
        byChat.getOrPut(chatId) { mutableStateListOf() }

    fun add(chatId: String, item: StagedMedia) {
        listFor(chatId).add(item)
    }

    /** Remove one staged item (the strip's ✕, or a commit consuming
     *  it). Returns the removed item so the caller can clean up a
     *  cache file it owns, or null when it was already gone. */
    fun remove(chatId: String, key: String): StagedMedia? {
        val list = byChat[chatId] ?: return null
        val idx = list.indexOfFirst { it.key == key }
        return if (idx >= 0) list.removeAt(idx) else null
    }

    fun clear(chatId: String) {
        byChat[chatId]?.clear()
    }

    fun count(chatId: String): Int = byChat[chatId]?.size ?: 0
}
