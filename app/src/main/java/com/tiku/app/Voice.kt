package com.tiku.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Voice-note recording + playback. Recording captures live maxAmplitude samples
 * for the waveform bars; files live only in app-private cache.
 */
class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var outFile: File? = null
    val amps = mutableListOf<Int>()
    var startedAt = 0L
    private set

    /** Starts recording. Throws on ANY failure — the caller surfaces the
     *  reason (ROUND 20: a silently-dead mic button shipped because every
     *  failure on this path used to be swallowed). */
    fun start(): File {
        stopRecorder()
        val f = File(ctx.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // ROUND 20: explicit channel count + bit rate. The bare
            // defaults left prepare() at the mercy of OEM AAC encoders —
            // several refuse a channel/bitrate-less 44.1 kHz profile and
            // prepare() throws, which the old call site then swallowed
            // into a dead button. Fully specified = deterministic.
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(64_000)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
        } catch (e: Exception) {
            runCatching { r.release() }
            runCatching { f.delete() }
            throw e
        }
        recorder = r; outFile = f; amps.clear(); startedAt = System.currentTimeMillis()
        return f
    }

    /** Called ~5x/sec by the UI while recording to sample the waveform. */
    fun sample() {
        val a = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
        amps += (a / 1200).coerceIn(2, 24)
    }

    /** Stops recording. With [send] true, returns the captured file ONLY
     *  when the recorder stopped cleanly AND the file actually holds
     *  audio — a failed stop (too-short clip, encoder error state) used
     *  to return the husk anyway, so a corrupt/empty note was "sent".
     *  Now the husk is deleted and null comes back, and the UI says so. */
    fun stop(send: Boolean): File? {
        val f = outFile
        val stoppedCleanly = stopRecorder()
        outFile = null
        if (!send) { f?.delete(); return null }
        if (!stoppedCleanly || f == null || !f.exists() || f.length() == 0L) {
            f?.delete(); return null
        }
        return f
    }

    /** Stops + releases the current recorder (if any). Returns true when
     *  stop() did not throw — a throw means the capture is unusable. */
    private fun stopRecorder(): Boolean {
        val r = recorder ?: return true
        recorder = null
        var ok = true
        try { r.stop() } catch (e: Exception) { ok = false /* too-short clip / error state */ }
        try { r.release() } catch (e: Exception) { /* noop */ }
        return ok
    }

    fun normalizedAmps(): List<Int> {
        if (amps.isEmpty()) return List(22) { 8 }
        if (amps.size <= 22) return amps + List(22 - amps.size) { 6 }
        val chunk = amps.size / 22.0
        return (0 until 22).map { i -> amps.subList((i * chunk).toInt(), ((i + 1) * chunk).toInt().coerceAtMost(amps.size)).maxOrNull() ?: 8 }
    }
}

/** Plays a local decrypted voice file; reports completion. */
class VoicePlayer {
    private var mp: MediaPlayer? = null
    var playingKey: String? = null
    private set

    fun toggle(key: String, file: File, onDone: () -> Unit) {
        if (playingKey == key) { stop(); onDone(); return }
        stop()
        mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { stop(); onDone() }
            prepare(); start()
        }
        playingKey = key
    }

    fun stop() {
        try { mp?.stop() } catch (e: Exception) { /* noop */ }
        try { mp?.release() } catch (e: Exception) { /* noop */ }
        mp = null; playingKey = null
    }
}
