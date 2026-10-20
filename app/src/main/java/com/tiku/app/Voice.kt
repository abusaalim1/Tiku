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

    fun start(): File {
        stopQuietly()
        val f = File(ctx.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44100)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        recorder = r; outFile = f; amps.clear(); startedAt = System.currentTimeMillis()
        return f
    }

    /** Called ~5x/sec by the UI while recording to sample the waveform. */
    fun sample() {
        val a = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
        amps += (a / 1200).coerceIn(2, 24)
    }

    fun stop(send: Boolean): File? {
        val f = outFile
        stopQuietly()
        if (!send) { f?.delete(); return null }
        return f
    }

    private fun stopQuietly() {
        try { recorder?.stop() } catch (e: Exception) { /* too-short clip */ }
        try { recorder?.release() } catch (e: Exception) { /* noop */ }
        recorder = null; outFile = null
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
