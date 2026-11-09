package com.tiku.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * MediaStore — media WITHOUT Firebase Storage (free Spark plan).
 *
 * Encrypted media bytes live inside Firestore: the ciphertext is split into
 * chunks of at most [CHUNK_BYTES]; chunk i becomes a doc at
 * chats/{chatId}/messages/{msgId}/chunks/{i} with field `d` = Base64(chunk).
 * The message doc carries `mediaChunks` (chunk count) and `iv` (the whole-blob
 * AES-GCM IV). Downloads reassemble the chunks, then decrypt with the
 * conversation key exactly like text payloads.
 *
 * Free-plan math: Firestore free tier = 1 GiB stored + 50k reads/day — for a
 * 2-person private chat this is plenty. Photos are downscaled to <=1280px and
 * JPEG q80 before encryption (typically 150–350 KB). Voice notes cap at
 * 3 minutes. No video messages in v1 (the design has none).
 */
object MediaStore {
    const val CHUNK_BYTES = 700 * 1024

    /** Upload an already-encrypted blob, chunked. Returns the chunk count. */
    suspend fun upload(db: FirebaseFirestore, chatId: String, msgId: String, ciphertext: ByteArray): Int {
        val chunksRef = db.collection(Schema.Chats.COLL).document(chatId)
            .collection(Schema.Chats.MESSAGES).document(msgId)
            .collection("chunks")
        val count = (ciphertext.size + CHUNK_BYTES - 1) / CHUNK_BYTES
        var batch = db.batch()
        var inBatch = 0
        for (i in 0 until count) {
            val from = i * CHUNK_BYTES
            val slice = ciphertext.copyOfRange(from, minOf(from + CHUNK_BYTES, ciphertext.size))
            batch.set(chunksRef.document(i.toString()), mapOf("d" to Base64.encodeToString(slice, Base64.NO_WRAP)))
            if (++inBatch >= 400) { batch.commit().await(); batch = db.batch(); inBatch = 0 }
        }
        if (inBatch > 0) batch.commit().await()
        return count
    }

    /** Download and concatenate all chunks of a media blob (still encrypted). */
    suspend fun download(db: FirebaseFirestore, chatId: String, msgId: String, chunkCount: Int): ByteArray {
        val snap = db.collection(Schema.Chats.COLL).document(chatId)
            .collection(Schema.Chats.MESSAGES).document(msgId)
            .collection("chunks")
            .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.ASCENDING)
            .get().await()
        val out = ByteArrayOutputStream()
        val byIndex = snap.documents.associate { (it.id.toIntOrNull() ?: -1) to (it.getString("d") ?: "") }
        for (i in 0 until chunkCount) {
            // ROUND 34: a missing chunk is a hard, retryable failure —
            // silently skipping it used to hand the decrypt a truncated
            // blob, whose GCM failure surfaced far from the real cause
            // (and read as a permanently broken message). Throwing here
            // lets fetchMedia's re-fetch + error path do its job.
            val d = byIndex[i]
                ?: throw IllegalStateException("media chunk $i of $chunkCount missing for message $msgId")
            out.write(Base64.decode(d, Base64.NO_WRAP))
        }
        return out.toByteArray()
    }

    /** Photo pipeline: decode bytes -> downscale longest side <= 1280 -> JPEG q80. */
    fun compressPhoto(bytes: ByteArray): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        return compressPhoto(bmp)
    }

    fun compressPhoto(bmp: Bitmap, maxSide: Int = 1280, quality: Int = 80): ByteArray {
        val w = bmp.width; val h = bmp.height
        val scaled = if (maxOf(w, h) > maxSide) {
            val s = maxSide / maxOf(w, h).toFloat()
            Bitmap.createScaledBitmap(bmp, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    /** Small avatar (profile photo), fits a single Firestore field as Base64. */
    fun compressAvatar(bmp: Bitmap): String {
        val bytes = compressPhoto(bmp, maxSide = 512, quality = 70)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeBitmap(b64: String): Bitmap? = try {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}

/**
 * MediaSaver — the ONLY way chat media leaves the app (owner rule, round 2):
 * NOTHING auto-saves to the gallery / public storage. Received photos live in
 * memory; received voice notes live in app-private cache. Only an explicit
 * "Save to device" tap on a message copies the DECRYPTED bytes out:
 * photos -> Pictures/Tiku, voice -> Music/Tiku (MediaStore on API 29+).
 */
object MediaSaver {
    fun saveToDevice(ctx: android.content.Context, m: TikuMessage): Boolean = when (m.kind) {
        MsgKind.IMAGE -> {
            val bmp = m.photo ?: return false
            writeImage(ctx, bmp, "tiku_${m.id}.jpg")
        }
        MsgKind.VOICE -> {
            val path = m.localPath ?: return false
            val f = java.io.File(path)
            if (!f.exists()) false else writeAudio(ctx, f, "tiku_voice_${m.id}.m4a", "audio/mp4")
        }
        // ROUND 15: the file kinds save from their decrypted cache file,
        // under their real mime type — video to Movies/Tiku, audio files
        // to Music/Tiku. Unloaded payloads (localPath null) can't save.
        MsgKind.VIDEO -> {
            val path = m.localPath ?: return false
            val f = java.io.File(path)
            if (!f.exists()) false else writeVideo(ctx, f, "tiku_video_${m.id}.${f.extension.ifEmpty { "mp4" }}",
                m.mimeType.ifEmpty { "video/mp4" })
        }
        MsgKind.AUDIO -> {
            val path = m.localPath ?: return false
            val f = java.io.File(path)
            if (!f.exists()) false else writeAudio(ctx, f, "tiku_audio_${m.id}.${f.extension.ifEmpty { "mp3" }}",
                m.mimeType.ifEmpty { "audio/mpeg" })
        }
        else -> false
    }

    private fun writeImage(ctx: android.content.Context, bmp: Bitmap, name: String): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/Tiku")
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: return false
            true
        } else {
            val dir = java.io.File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "Tiku").apply { mkdirs() }
            java.io.File(dir, name).outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            true
        }
    }.getOrDefault(false)

    private fun writeAudio(ctx: android.content.Context, src: java.io.File, name: String, mime: String): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mime)
                put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_MUSIC + "/Tiku")
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return false
            true
        } else {
            val dir = java.io.File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "Tiku").apply { mkdirs() }
            src.copyTo(java.io.File(dir, name), overwrite = true)
            true
        }
    }.getOrDefault(false)

    /** ROUND 66 (amendment): the Nikah certificate PNG — the same
     *  explicit-save discipline as chat media, but the destination is
     *  the device Downloads folder (the owner asked for a Download,
     *  not a gallery save). API 29+: a MediaStore Downloads entry
     *  written under IS_PENDING and only published once the PNG bytes
     *  are fully written — any failure deletes the pending entry, so
     *  no partial file is ever left behind. Below 29: the
     *  app-specific Downloads folder, the same fallback shape as the
     *  writers above. */
    fun saveCertificatePng(ctx: android.content.Context, bmp: Bitmap, name: String): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS)
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
            ) ?: return false
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IllegalStateException("certificate PNG encode failed")
                    }
                } ?: throw IllegalStateException("certificate output stream unavailable")
                val done = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                }
                ctx.contentResolver.update(uri, done, null, null)
                true
            } catch (e: Exception) {
                ctx.contentResolver.delete(uri, null, null)
                false
            }
        } else {
            val dir = java.io.File(
                ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Tiku",
            ).apply { mkdirs() }
            java.io.File(dir, name).outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }.getOrDefault(false)

    /** ROUND 15: video save — same MediaStore pattern as audio, into
     *  Movies/Tiku on API 29+ (app-specific Movies/Tiku below that). */
    private fun writeVideo(ctx: android.content.Context, src: java.io.File, name: String, mime: String): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, mime)
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_MOVIES + "/Tiku")
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return false
            true
        } else {
            val dir = java.io.File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "Tiku").apply { mkdirs() }
            src.copyTo(java.io.File(dir, name), overwrite = true)
            true
        }
    }.getOrDefault(false)
}
