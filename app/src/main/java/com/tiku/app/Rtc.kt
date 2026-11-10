package com.tiku.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * REAL audio + video calls over WebRTC (org.webrtc API via Stream's maintained
 * Maven Central fork). Media is pure P2P (DTLS-SRTP) — it NEVER touches Firebase.
 *
 * Signaling rides Firestore only, and is designed to leave ZERO traces:
 *   chats/{chatId}/calls/{callId} {
 *     kind: "audio"|"video", caller, callee, state: ringing|connected|ended,
 *     offer, answer, callerCands: [String], calleeCands: [String], createdAt }
 * ICE candidates are array fields ON the call doc (no subcollection), so ending
 * a call is a single document delete that takes every trace with it atomically.
 * On end / reject / cancel / timeout BOTH clients delete the doc (idempotent),
 * and a sweep on session start removes crash residue (state=ended or >2h old).
 */

// ======================= SDP / candidate codecs =======================
fun encodeSdp(s: SessionDescription): String = s.type.canonicalForm() + "\n" + s.description

fun decodeSdp(enc: String): SessionDescription {
    val type = enc.substringBefore("\n")
    val body = enc.substringAfter("\n", "")
    return SessionDescription(SessionDescription.Type.fromCanonicalForm(type), body)
}

fun encodeCandidate(c: IceCandidate): String = "${c.sdpMid}|${c.sdpMLineIndex}|${c.sdp}"

fun decodeCandidate(enc: String): IceCandidate? {
    val parts = enc.split("|", limit = 3)
    if (parts.size < 3) return null
    return IceCandidate(parts[0], parts[1].toIntOrNull() ?: 0, parts[2])
}

// ======================= Firestore signaling =======================
object CallSignal {
    private val db get() = FirebaseFirestore.getInstance()
    private fun callRef(chatId: String, callId: String) =
        db.collection(Schema.Chats.COLL).document(chatId).collection("calls").document(callId)

    suspend fun createCall(chatId: String, callId: String, kind: String, caller: String, callee: String, offer: SessionDescription) {
        callRef(chatId, callId).set(mapOf(
            "kind" to kind, "caller" to caller, "callee" to callee,
            "state" to "ringing", "offer" to encodeSdp(offer), "answer" to null,
            "callerCands" to emptyList<String>(), "calleeCands" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
        )).await()
    }

    suspend fun readOffer(chatId: String, callId: String): SessionDescription? {
        val s = callRef(chatId, callId).get().await().getString("offer") ?: return null
        return decodeSdp(s)
    }

    suspend fun acceptCall(chatId: String, callId: String, answer: SessionDescription) {
        callRef(chatId, callId).update(mapOf(
            "answer" to encodeSdp(answer), "state" to "connected",
        )).await()
    }

    suspend fun markEnded(chatId: String, callId: String) {
        runCatching { callRef(chatId, callId).update("state", "ended").await() }
    }

    /**
     * ROUND 32 — screen-share presence, one field per role on the same
     * ephemeral call doc (deleted whole at call end, like everything
     * else on it). The peer's listener reads the OTHER role's field to
     * learn "the stream coming at me is a screen, not a camera" — its
     * only product use is rendering that stream letterboxed (FIT) and
     * labelling it; the media itself needs no signaling help, since
     * the share rides the already-negotiated video track.
     */
    suspend fun setSharing(chatId: String, callId: String, role: String, on: Boolean) {
        val field = if (role == "caller") "sharingCaller" else "sharingCallee"
        runCatching { callRef(chatId, callId).update(field, on).await() }
    }

    /**
     * ROUND 47 — camera on/off presence, one field per role on the
     * same ephemeral call doc (the Round-32 sharing pattern). A
     * disabled video track gives the receiving side NO usable signal
     * on this stack — the remote renderer just freezes on its last
     * frame — so the toggle is published explicitly: the peer's
     * listener reads the OTHER role's field and swaps its frozen
     * surface for the avatar/placeholder, Instagram-style. The field
     * stores camera ON; absent means on (every pre-47 call).
     */
    suspend fun setCameraOn(chatId: String, callId: String, role: String, on: Boolean) {
        val field = if (role == "caller") "cameraCaller" else "cameraCallee"
        runCatching { callRef(chatId, callId).update(field, on).await() }
    }

    suspend fun pushCandidate(chatId: String, callId: String, role: String, cand: IceCandidate) {
        val field = if (role == "caller") "callerCands" else "calleeCands"
        runCatching { callRef(chatId, callId).update(field, FieldValue.arrayUnion(encodeCandidate(cand))).await() }
    }

    /**
     * Delete the ENTIRE call record (the doc is the whole tree: offer, answer and
     * both candidate arrays are fields on it). Verified in code: after the delete
     * we re-read and confirm the document no longer exists (and retry once).
     */
    suspend fun deleteCallTree(chatId: String, callId: String) {
        runCatching {
            val ref = callRef(chatId, callId)
            ref.delete().await()
            val gone = runCatching { !ref.get().await().exists() }.getOrDefault(false)
            if (!gone) ref.delete().await()
        }
    }

    fun listen(chatId: String, callId: String, onDoc: (Map<String, Any?>?) -> Unit): ListenerRegistration =
        callRef(chatId, callId).addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            onDoc(if (snap != null && snap.exists()) snap.data as? Map<String, Any?> else null)
        }
}

// ======================= WebRTC plumbing =======================
object RtcFactory {
    @Volatile private var ready = false
    lateinit var egl: EglBase private set
    lateinit var factory: PeerConnectionFactory private set

    @Synchronized
    fun ensure(ctx: Context) {
        if (ready) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        egl = EglBase.create()
        // No custom audio device module: the factory's default IS JavaAudioDeviceModule
        // (with the platform's echo cancellation / noise suppression).
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        ready = true
    }
}

class RtcSession(
    ctx: Context,
    val hasVideo: Boolean,
    cameraOn: Boolean,
    speakerOn: Boolean,
) {
    interface Events {
        fun onIceCandidate(c: IceCandidate)
        fun onMediaConnected()
        fun onFailed()
        fun onRemoteVideo()
        /** ROUND 32: the system revoked the screen-share projection
         *  (the user stopped it from the system share affordance).
         *  The session has already torn its capture pipeline down by
         *  the time this fires; the controller syncs UI + signaling. */
        fun onScreenShareStopped() {}
    }

    var events: Events? = null

    // ROUND 68 (WS7c) — HIGH 1:1 video-call quality profile. Supersedes
    // the Round 61 profile (720p@30, 2.0 Mbps cap, MAINTAIN_RESOLUTION):
    // capture up to 1080p (1920x1080) at 30 fps, an ADAPTIVE encoder
    // bitrate capped at 4.0 Mbps (floor 500 kbps so adaptation has room
    // to breathe instead of a hard fixed rate), framerate capped at the
    // capture rate, and MAINTAIN_RESOLUTION so framerate is sacrificed
    // before resolution under bandwidth pressure. No simulcast (a 1:1
    // call has one viewer). The camera capturer negotiates the closest
    // format the device actually supports at or below the request, so
    // a camera without a 1080p mode gracefully falls back on its own
    // — startCapture does not fail for a lower-capability camera, and
    // the encoder cap simply never binds there. Shipped values:
    // 1920x1080 @ 30 fps, max 4.0 Mbps / min 0.5 Mbps, MAINTAIN_RESOLUTION.
    private companion object {
        const val CAMERA_CAPTURE_WIDTH = 1920
        const val CAMERA_CAPTURE_HEIGHT = 1080
        const val CAMERA_CAPTURE_FPS = 30
        const val VIDEO_MAX_BITRATE_BPS = 4_000_000
        const val VIDEO_MIN_BITRATE_BPS = 500_000
        const val VIDEO_MAX_FRAMERATE = 30
    }

    private val appCtx = ctx.applicationContext
    private val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    /** ROUND 61: the video track's sender — kept so the quality
     *  profile (see [configureVideoSender]) can be applied at session
     *  setup and re-applied once the local description is set. */
    private var videoSender: RtpSender? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var captureHelper: SurfaceTextureHelper? = null
    // ROUND 32 — screen share state. The share feeds the SAME
    // videoSource from a ScreenCapturerAndroid instead of the camera
    // capturer: the track, its sender and the negotiated m-line never
    // change, so starting/stopping a share needs no renegotiation on
    // this once-negotiated stack. [cameraPref] is the camera on/off
    // the user asked for, remembered while the screen owns the track
    // and re-applied when the share ends.
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenHelper: SurfaceTextureHelper? = null
    private var sharingScreen = false
    private var cameraPref = cameraOn
    /** ROUND 47: the camera on/off the video track currently obeys
     *  (the user's preference — while a screen share owns the track
     *  this is what the camera returns to when the share ends). The
     *  call UI reads it to keep the self-view PiP honest: camera off
     *  must show the placeholder, never a stale frozen frame. */
    val cameraEnabled: Boolean get() = cameraPref
    var remoteVideoTrack: VideoTrack? = null
        private set
    val eglContext: EglBase.Context get() = RtcFactory.egl.eglBaseContext
    private var disposed = false

    // ROUND 7: remote candidates that arrive before the remote description
    // is set must QUEUE, not drop — addIceCandidate against a peer connection
    // with no remote description is a classic silent connect-killer. The
    // queue flushes the moment setRemoteDescription succeeds (either role).
    private val candLock = Any()
    private var remoteDescReady = false
    private val queuedRemoteCands = mutableListOf<IceCandidate>()

    private fun markRemoteDescReady() {
        val drain: List<IceCandidate>
        synchronized(candLock) {
            remoteDescReady = true
            drain = queuedRemoteCands.toList()
            queuedRemoteCands.clear()
        }
        drain.forEach { c -> runCatching { pc?.addIceCandidate(c) } }
    }

    init {
        RtcFactory.ensure(ctx)
        val iceServers = Config.ICE_SERVERS.map { cfg ->
            PeerConnection.IceServer.builder(cfg.urls).apply {
                if (cfg.username.isNotEmpty()) { setUsername(cfg.username); setPassword(cfg.credential) }
            }.createIceServer()
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = RtcFactory.factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) { events?.onIceCandidate(c) }
            override fun onIceCandidatesRemoved(c: Array<IceCandidate>) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                when (s) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> events?.onMediaConnected()
                    PeerConnection.IceConnectionState.FAILED -> events?.onFailed()
                    else -> {}
                }
            }
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onAddStream(stream: MediaStream) {
                stream.videoTracks.firstOrNull()?.let { vt ->
                    if (remoteVideoTrack !== vt) { remoteVideoTrack = vt; events?.onRemoteVideo() }
                }
            }
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, streams: Array<MediaStream>) {
                (r.track() as? VideoTrack)?.let { vt ->
                    if (remoteVideoTrack !== vt) { remoteVideoTrack = vt; events?.onRemoteVideo() }
                }
            }
            override fun onTrack(t: RtpTransceiver) {}
        })
        // local audio (always)
        audioSource = RtcFactory.factory.createAudioSource(MediaConstraints())
        audioTrack = RtcFactory.factory.createAudioTrack("tiku-audio", audioSource)
        pc?.addTrack(audioTrack, listOf("tiku-stream"))
        // Local video. ROUND 32: the video source + track now exist in
        // EVERY session — an audio call starts with a blank, disabled
        // track and no capturer at all. That is what lets screen share
        // light the track up mid-call on either kind of call: the
        // negotiated video m-line is already there from the offer, so
        // feeding the source (camera or screen) is all it takes — no
        // renegotiation, which this stack does not do. Video calls are
        // unchanged: the camera capturer feeds the source from the
        // start, exactly as before.
        val capturer = if (hasVideo) createCapturer(appCtx) else null
        videoCapturer = capturer
        videoSource = RtcFactory.factory.createVideoSource(capturer?.isScreencast ?: false)
        videoTrack = RtcFactory.factory.createVideoTrack("tiku-video", videoSource)
        if (capturer != null) {
            captureHelper = SurfaceTextureHelper.create("tiku-capture", eglContext)
            capturer.initialize(captureHelper, appCtx, videoSource!!.capturerObserver)
            // ROUND 68 (WS7c): request 1080p (1920x1080) at 30 fps (was
            // 720p under Round 61). The camera capturer negotiates the
            // closest format the device actually supports at or below
            // the request, so a camera without a 1080p mode gracefully
            // falls back on its own — startCapture does not fail for a
            // lower-capability camera.
            runCatching { capturer.startCapture(CAMERA_CAPTURE_WIDTH, CAMERA_CAPTURE_HEIGHT, CAMERA_CAPTURE_FPS) }
            videoTrack?.setEnabled(cameraOn)
        } else {
            videoTrack?.setEnabled(false)
        }
        videoSender = pc?.addTrack(videoTrack, listOf("tiku-stream"))
        configureVideoSender()
        // audio route for the call
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn
        }
    }

    /**
     * ROUND 68 (WS7c) — apply the HIGH 1:1 video quality profile to the
     * video sender: adaptive bitrate with a 4.0 Mbps cap (floor
     * 500 kbps so adaptation has room to breathe instead of a hard
     * fixed rate), framerate capped at the capture rate, and
     * MAINTAIN_RESOLUTION degradation — under bandwidth pressure the
     * encoder drops frames before it drops pixels. A single encoding
     * only: no simulcast layers are added or enabled. Applied to
     * EVERY session's video sender (a voice call's blank track
     * included) so a mid-call screen share — which rides this same
     * track/sender — gets the same profile. Best-effort by design:
     * any failure leaves the sender exactly as it was and can never
     * break call setup. Audio is not touched here or anywhere in
     * this round. (Round 61's profile was 720p / 2.0 Mbps / 300 kbps
     * floor; the constants above are the shipped Round-68 values.)
    private fun configureVideoSender() {
        val sender = videoSender ?: return
        runCatching {
            val params = sender.parameters ?: return@runCatching
            params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
            val encoding = params.encodings.firstOrNull()
            if (encoding != null) {
                encoding.active = true
                encoding.maxBitrateBps = VIDEO_MAX_BITRATE_BPS
                encoding.minBitrateBps = VIDEO_MIN_BITRATE_BPS
                encoding.maxFramerate = VIDEO_MAX_FRAMERATE
            }
            sender.setParameters(params)
        }
    }

    private fun createCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context)
            else Camera1Enumerator(false)
        val names = enumerator.deviceNames
        val front = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
        return front?.let { enumerator.createCapturer(it, null) }
    }

    private fun sdpObserver(
        onCreated: (SessionDescription) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) = object : SdpObserver {
        override fun onCreateSuccess(s: SessionDescription) = onCreated(s)
        override fun onSetSuccess() = onDone()
        override fun onCreateFailure(e: String) = onError(e)
        override fun onSetFailure(e: String) = onError(e)
    }

    suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        val conn = pc ?: return@suspendCancellableCoroutine cont.resumeWithException(IllegalStateException("No peer connection"))
        conn.createOffer(sdpObserver(
            onCreated = { offer ->
                conn.setLocalDescription(sdpObserver(
                    onCreated = {},
                    onDone = {
                        // ROUND 61: re-apply the video quality profile
                        // now that the local description is set — some
                        // stacks only honour sender parameters from
                        // this point on; re-applying is idempotent.
                        configureVideoSender()
                        if (cont.isActive) cont.resume(offer)
                    },
                    onError = { e -> if (cont.isActive) cont.resumeWithException(RuntimeException(e)) },
                ), offer)
            },
            onDone = {},
            onError = { e -> if (cont.isActive) cont.resumeWithException(RuntimeException(e)) },
        ), MediaConstraints())
    }

    /** Callee side: set the caller's offer, create + set the answer, return it. */
    suspend fun acceptOffer(offer: SessionDescription): SessionDescription = suspendCancellableCoroutine { cont ->
        val conn = pc ?: return@suspendCancellableCoroutine cont.resumeWithException(IllegalStateException("No peer connection"))
        conn.setRemoteDescription(sdpObserver(
            onCreated = {},
            onDone = {
                markRemoteDescReady()
                conn.createAnswer(sdpObserver(
                    onCreated = { answer ->
                        conn.setLocalDescription(sdpObserver(
                            onCreated = {},
                            onDone = {
                                // ROUND 61: same post-description
                                // re-application as the caller side.
                                configureVideoSender()
                                if (cont.isActive) cont.resume(answer)
                            },
                            onError = { e -> if (cont.isActive) cont.resumeWithException(RuntimeException(e)) },
                        ), answer)
                    },
                    onDone = {},
                    onError = { e -> if (cont.isActive) cont.resumeWithException(RuntimeException(e)) },
                ), MediaConstraints())
            },
            onError = { e -> if (cont.isActive) cont.resumeWithException(RuntimeException(e)) },
        ), offer)
    }

    suspend fun setRemoteAnswer(answer: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        val conn = pc
        if (conn == null) { if (cont.isActive) cont.resume(Unit); return@suspendCancellableCoroutine }
        conn.setRemoteDescription(sdpObserver(
            onCreated = {},
            onDone = { markRemoteDescReady(); if (cont.isActive) cont.resume(Unit) },
            onError = { if (cont.isActive) cont.resume(Unit) }, // best-effort: ICE state drives failure anyway
        ), answer)
    }

    fun addRemoteCandidate(c: IceCandidate) {
        // ROUND 7: queue until the remote description is actually set —
        // applying a candidate earlier silently fails inside WebRTC and the
        // call then never connects even though signaling looked perfect.
        val applyNow = synchronized(candLock) {
            if (remoteDescReady) true else { queuedRemoteCands += c; false }
        }
        if (applyNow) runCatching { pc?.addIceCandidate(c) }
    }
    fun setMicEnabled(on: Boolean) { runCatching { audioTrack?.setEnabled(on) } }
    fun setCameraEnabled(on: Boolean) {
        // ROUND 32: while the screen owns the track this only records
        // the preference — the share keeps flowing, and the preference
        // is applied to the camera the moment the share ends.
        cameraPref = on
        if (!sharingScreen) runCatching { videoTrack?.setEnabled(on) }
    }
    fun switchCamera() {
        if (sharingScreen) return // the camera is not the live source right now
        runCatching { videoCapturer?.switchCamera(null) }
    }

    /**
     * ROUND 32 — start feeding the video source from the screen.
     * [data] is the result intent of the system MediaProjection
     * consent dialog (fresh every time — a projection grant is
     * single-use). The caller (CallController) has ALREADY started
     * [ScreenShareService]: on Android 14+ the mediaProjection
     * foreground service must be running before the projection is
     * created, which happens inside the capturer's startCapture.
     * Returns false (with the camera restored) if anything fails.
     */
    fun startScreenShare(data: Intent): Boolean {
        if (disposed || sharingScreen) return false
        val source = videoSource ?: return false
        // The camera steps aside (it restarts when the share ends);
        // stopping capture also kills the camera indicator honestly —
        // while sharing, the camera truly sends nothing.
        runCatching { videoCapturer?.stopCapture() }
        val helper = SurfaceTextureHelper.create("tiku-screen", eglContext)
        val capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() { onProjectionRevoked() }
        })
        return try {
            capturer.initialize(helper, appCtx, source.capturerObserver)
            // Capture at the display's real size (even dimensions,
            // long side capped at 1920 — plenty for readable text,
            // and the encoder adapts from there).
            val dm = appCtx.resources.displayMetrics
            var w = dm.widthPixels
            var h = dm.heightPixels
            val longest = maxOf(w, h)
            if (longest > 1920) {
                val f = 1920f / longest
                w = (w * f).toInt()
                h = (h * f).toInt()
            }
            capturer.startCapture(w - w % 2, h - h % 2, 24)
            screenCapturer = capturer
            screenHelper = helper
            sharingScreen = true
            // The track may be disabled (audio call, or camera was
            // off): the share is an explicit "send video" act.
            runCatching { videoTrack?.setEnabled(true) }
            true
        } catch (e: Exception) {
            Log.e("TikuCall", "screen share start failed", e)
            runCatching { capturer.dispose() }
            runCatching { helper.dispose() }
            restoreCameraAfterShare()
            false
        }
    }

    /** User-initiated stop (the in-call button / indicator). */
    fun stopScreenShare() {
        if (!sharingScreen && screenCapturer == null) return
        endScreenShare()
    }

    /** The system revoked the projection (the user stopped it from
     *  the system share affordance, or the OS did): tear the capture
     *  down, stop the service, and let the controller sync the rest. */
    private fun onProjectionRevoked() {
        if (!sharingScreen) return
        endScreenShare()
        ScreenShareService.stop(appCtx)
        events?.onScreenShareStopped()
    }

    private fun endScreenShare() {
        sharingScreen = false
        runCatching { screenCapturer?.stopCapture() }
        runCatching { screenCapturer?.dispose() }
        screenCapturer = null
        runCatching { screenHelper?.dispose() }
        screenHelper = null
        restoreCameraAfterShare()
    }

    /** After a share: video calls go back to the camera at the
     *  user's remembered on/off preference; audio calls go back to
     *  sending nothing. */
    private fun restoreCameraAfterShare() {
        if (disposed) return
        if (hasVideo && videoCapturer != null) {
            runCatching { videoCapturer?.startCapture(CAMERA_CAPTURE_WIDTH, CAMERA_CAPTURE_HEIGHT, CAMERA_CAPTURE_FPS) }
            runCatching { videoTrack?.setEnabled(cameraPref) }
        } else {
            runCatching { videoTrack?.setEnabled(false) }
        }
    }
    fun setSpeaker(on: Boolean) {
        @Suppress("DEPRECATION")
        runCatching { audioManager.isSpeakerphoneOn = on }
    }
    /** Local preview renderer hookup (Compose AndroidView calls these). */
    fun bindLocalView(v: SurfaceViewRenderer) { runCatching { videoTrack?.addSink(v) } }
    fun unbindLocalView(v: SurfaceViewRenderer) { runCatching { videoTrack?.removeSink(v) } }
    fun bindRemoteView(v: SurfaceViewRenderer) { runCatching { remoteVideoTrack?.addSink(v) } }
    fun unbindRemoteView(v: SurfaceViewRenderer) { runCatching { remoteVideoTrack?.removeSink(v) } }

    fun dispose() {
        if (disposed) return
        disposed = true
        // ROUND 32: a live share dies with the session — capture
        // pipeline and its foreground service both stop here, so no
        // teardown path (hangup, failure, remote end, session lock)
        // can leak a projection or a running service. (disposed is
        // already set, so restoreCameraAfterShare correctly no-ops.)
        if (sharingScreen || screenCapturer != null) {
            endScreenShare()
            ScreenShareService.stop(appCtx)
        }
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { captureHelper?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }
}

// ======================= Call controller (single active call) =======================
sealed interface CallUiState {
    data object Idle : CallUiState
    data class Outgoing(
        val threadId: String, val callId: String, val video: Boolean,
        val person: Person, val atMs: Long,
    ) : CallUiState
    data class Incoming(
        val threadId: String, val callId: String, val video: Boolean,
        val person: Person, val atMs: Long,
    ) : CallUiState
    data class Connected(
        val threadId: String, val callId: String, val video: Boolean,
        val person: Person, val atMs: Long,
        val muted: Boolean = false, val speakerOn: Boolean = true, val cameraOn: Boolean = true,
        /** bumped when the remote video track arrives (forces the UI to rebind sinks) */
        val tick: Int = 0,
        /** ROUND 32: true while MY screen is the thing my video track
         *  is sending (set only by the share start/stop paths). */
        val sharingScreen: Boolean = false,
    ) : CallUiState
    /** Round 3: a failed call is a VISIBLE state ("Call failed — <reason>"),
     *  shown before the signaling trace is deleted — never a silent hangup. */
    data class Failed(
        val threadId: String, val callId: String, val video: Boolean,
        val person: Person, val reason: String,
    ) : CallUiState
}

object CallController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + TikuEngineHandler)
    val state = MutableStateFlow<CallUiState>(CallUiState.Idle)

    private var session: RtcSession? = null
        set(value) { field = value; sessionState.value = value }
    /** Observable session handle so the call screen rebinds its renderers when
     *  the (async-created) WebRTC session lands. */
    val sessionState = MutableStateFlow<RtcSession?>(null)

    /** ROUND 8 — IG-style floating call window (PiP). True while a live call
     *  (Outgoing/Connected) is shrunk to the draggable mini window over the
     *  chat UI. Purely a UI state: the call itself keeps running untouched.
     *  Minimizing NEVER ends the call, records nothing and deletes nothing;
     *  hanging up from the mini window goes through the same hangup() path
     *  (event recorded, signaling deleted). Session lock still ends the call
     *  (endSilently), exactly as before. */
    val pipMinimized = MutableStateFlow(false)

    /** ROUND 32 — true while the PEER's video stream is their screen
     *  (learned from their sharing field on the call doc). The call UI
     *  uses it to letterbox the remote stream (a screen must never be
     *  cropped) and to show the remote surface at all on audio calls. */
    val remoteSharing = MutableStateFlow(false)

    /** ROUND 47 — true while the PEER's camera is off (learned from
     *  their camera field on the call doc, see CallSignal.setCameraOn).
     *  The call UI and the floating window read it to swap the peer's
     *  (otherwise merely frozen) video surface for the avatar tile. */
    val remoteCameraOff = MutableStateFlow(false)

    /** My signaling role in the live call ("caller" | "callee"),
     *  captured where the session events are wired. Screen-share
     *  writes pick their doc field by it. */
    private var myRole: String = ""

    /** applicationContext captured when a share starts, so the share
     *  service can be stopped from teardown paths that hold no
     *  Context of their own. */
    private var shareAppCtx: Context? = null

    fun minimizeCall() {
        val st = state.value
        if (st is CallUiState.Connected || st is CallUiState.Outgoing) pipMinimized.value = true
    }

    /** Tap on the floating window: back to the full call screen. */
    fun restoreCall() {
        val st = state.value
        val (tid, video) = when (st) {
            is CallUiState.Connected -> st.threadId to st.video
            is CallUiState.Outgoing -> st.threadId to st.video
            else -> return
        }
        pipMinimized.value = false
        AppState.go(Screen.Call(tid, video))
    }
    private var callDocReg: ListenerRegistration? = null
    private var ringJob: Job? = null
    private var answerApplied = false
    private var processedCallerCands = 0
    private var processedCalleeCands = 0
    val realMode get() = !Config.PREVIEW_UI_ONLY

    // ---- round 4: call-event bookkeeping ----
    /** Uid of whoever placed the current call (mine on outgoing, the peer's
     *  on incoming) — stored in the encrypted chat event so each side renders
     *  perspective-correct copy. */
    private var curCallerUid: String = ""
    /** The callId whose chat event has already been written by THIS client
     *  (both sides write the same deterministic doc; this just avoids
     *  redundant local rewrites across multiple resolution paths). */
    private var eventRecordedCallId: String? = null
    /** ROUND 7 — the offer SDP captured from the ringing snapshot itself.
     *  The watcher doc ALREADY carries the offer; caching it here means
     *  accept() never depends on re-reading a doc that later signaling
     *  updates (ICE arrayUnion writes) can disturb. Cleared on teardown. */
    private var pendingOffer: SessionDescription? = null
    private var pendingOfferCallId: String? = null

    /** ROUND 7 — ICE egress gate (caller side). The caller's PeerConnection
     *  starts gathering the instant its local description is set — inside
     *  createOffer(), BEFORE CallSignal.createCall() has written the call
     *  doc. pushCandidate() update()s that doc; an update on a doc that does
     *  not exist yet fails (NOT_FOUND) and was swallowed by runCatching —
     *  the caller's earliest candidates (often the host/srflx pair that
     *  would have connected) vanished silently. While the gate is closed,
     *  outgoing candidates buffer as deferred pushes and flush the moment
     *  the doc exists. The callee's doc provably exists (its watcher saw
     *  it), so accept() opens the gate immediately. */
    private var iceGateOpen = false
    private val iceBuffer = mutableListOf<suspend () -> Unit>()

    private fun openIceGate() {
        iceGateOpen = true
        val pending = iceBuffer.toList(); iceBuffer.clear()
        pending.forEach { push -> scope.launch { push() } }
    }

    /** The callId of the currently active/ringing call, if any — used by the
     *  incoming-call watcher to recognise re-fires for the SAME call and
     *  leave that call's signaling doc completely alone. */
    fun activeCallId(): String? = ids(state.value)?.second

    /**
     * Record the call's outcome as an encrypted event inside the chat
     * (round 4, item 3). Must be called while the call state is still
     * readable (before finish/teardown resets it). Duration derives from
     * the Connected state's connect timestamp. Preview mode appends the
     * same pill locally so the UI path is identical.
     */
    private fun recordOutcome(outcome: String) {
        val st = state.value
        val (threadId, callId) = ids(st) ?: return
        if (eventRecordedCallId == callId) return
        eventRecordedCallId = callId
        val video = when (st) {
            is CallUiState.Outgoing -> st.video
            is CallUiState.Incoming -> st.video
            is CallUiState.Connected -> st.video
            is CallUiState.Failed -> st.video
            CallUiState.Idle -> false
        }
        val durSec = if (st is CallUiState.Connected)
            ((System.currentTimeMillis() - st.atMs) / 1000).toInt().coerceAtLeast(0) else 0
        val caller = curCallerUid
        scope.launch {
            if (realMode) FirebaseEngine.recordCallEvent(threadId, callId, caller, video, outcome, durSec)
            else PreviewRepo.appendCallEvent(threadId, caller, video, outcome, durSec)
        }
    }

    private fun ids(st: CallUiState): Pair<String, String>? = when (st) {
        is CallUiState.Outgoing -> st.threadId to st.callId
        is CallUiState.Incoming -> st.threadId to st.callId
        is CallUiState.Connected -> st.threadId to st.callId
        is CallUiState.Failed -> st.threadId to st.callId
        CallUiState.Idle -> null
    }

    /** Reset for preview/test hygiene. */
    fun endSilently() {
        val st = state.value
        // round 4: locking mid-call still leaves the chat event (written
        // BEFORE FirebaseEngine.lock zeroizes the keys — it calls us first)
        if (st is CallUiState.Connected) recordOutcome("ended")
        val cur = ids(st)
        ringJob?.cancel(); ringJob = null
        callDocReg?.remove(); callDocReg = null
        session?.dispose(); session = null
        clearScreenShareState()
        runCatching { CallFeedbackManager.stopAll() }
        state.value = CallUiState.Idle
        pipMinimized.value = false
        answerApplied = false; processedCallerCands = 0; processedCalleeCands = 0
        curCallerUid = ""; eventRecordedCallId = null
        pendingOffer = null; pendingOfferCallId = null
        iceGateOpen = false; iceBuffer.clear()
        if (realMode && cur != null) {
            scope.launch { CallSignal.deleteCallTree(cur.first, cur.second) }
        }
    }

    // ---------- outgoing ----------
    fun startCall(ctx: Context, threadId: String, video: Boolean) {
        if (state.value !is CallUiState.Idle) return
        val person = AppState.thread(threadId)?.person ?: run {
            AppState.toast("Chat unavailable"); return
        }
        if (!realMode) { startFakeCall(threadId, video, person); return }
        val settings = SettingsStore.state.value
        val callId = UUID.randomUUID().toString()
        // guarded: in the Robolectric screenshot process FirebaseApp is not
        // initialized and a bare FirebaseEngine.uid would throw synchronously
        curCallerUid = runCatching { FirebaseEngine.uid }.getOrDefault("")
        eventRecordedCallId = null
        iceGateOpen = false; iceBuffer.clear()
        pipMinimized.value = false
        state.value = CallUiState.Outgoing(threadId, callId, video, person, System.currentTimeMillis())
        armRingTimeout(threadId, callId)
        scope.launch {
            try {
                val peer = AppState.thread(threadId)?.person ?: person
                // signaling lives UNDER the chat doc (rules gate it on the
                // parent's members) — the doc must exist first, AWAITED. In
                // round 2 this create raced the thread bootstrap and the rules
                // refusal was invisible (generic toast, instant teardown).
                FirebaseEngine.ensureChatDoc(threadId, peer.uid)
                val s = RtcSession(ctx, hasVideo = video,
                    cameraOn = settings.cameraDefaultOn, speakerOn = settings.speakerDefaultOn)
                wireEvents(s, threadId, callId, role = "caller")
                session = s
                val offer = s.createOffer()
                CallSignal.createCall(threadId, callId, if (video) "video" else "audio",
                    FirebaseEngine.uid, peer.uid, offer)
                // ROUND 58: the call doc exists NOW — fire the peer's
                // FCM call push (best-effort, background, failures
                // swallowed; this is what replaced the deleted alert
                // service's closed-app ringing).
                PushSender.notifyCall(threadId, peer.uid, callId, video)
                // the call doc exists NOW — release any ICE candidates that
                // gathered while the offer was being created (see ice gate)
                openIceGate()
                attachDocListener(threadId, callId, role = "caller")
            } catch (e: Exception) {
                Log.e("TikuCall", "startCall failed for chat $threadId", e)
                failCall(threadId, FirebaseEngine.sendFailureReason(e))
            }
        }
    }

    private fun startFakeCall(threadId: String, video: Boolean, person: Person) {
        val callId = "preview-call"
        curCallerUid = "me"
        eventRecordedCallId = null
        pipMinimized.value = false
        state.value = CallUiState.Outgoing(threadId, callId, video, person, System.currentTimeMillis())
        scope.launch {
            delay(1400)
            val st = state.value
            if (st is CallUiState.Outgoing && st.callId == callId) {
                state.value = CallUiState.Connected(
                    threadId, callId, video, person, System.currentTimeMillis(),
                    speakerOn = video, cameraOn = true,
                )
            }
        }
    }

    // ---------- incoming ----------
    fun onIncoming(chatId: String, callId: String, kind: String, person: Person, offerEnc: String? = null) {
        if (state.value !is CallUiState.Idle) return
        val video = kind == "video"
        curCallerUid = person.uid
        eventRecordedCallId = null
        // ROUND 7: cache the offer straight from the ringing snapshot — the
        // single most reliable copy (server-confirmed, already in hand).
        pendingOffer = offerEnc?.let { runCatching { decodeSdp(it) }.getOrNull() }
        pendingOfferCallId = if (pendingOffer != null) callId else null
        pipMinimized.value = false
        state.value = CallUiState.Incoming(chatId, callId, video, person, System.currentTimeMillis())
        armRingTimeout(chatId, callId)
        AppState.go(Screen.Call(chatId, video))
    }

    fun accept(ctx: Context) {
        val st = state.value as? CallUiState.Incoming ?: return
        if (!realMode) {
            state.value = CallUiState.Connected(st.threadId, st.callId, st.video, st.person,
                System.currentTimeMillis(), speakerOn = st.video, cameraOn = true)
            ringJob?.cancel(); ringJob = null
            return
        }
        val settings = SettingsStore.state.value
        scope.launch {
            try {
                // ROUND 7: prefer the offer cached from the ringing snapshot.
                // Fallback: re-read with bounded retries instead of failing
                // on the first null — a one-shot get() raced the caller's
                // ICE-candidate writes and produced "Offer missing" on
                // receive even though the call was perfectly valid. The wait
                // also stops the moment this call resolves any other way.
                var offer = pendingOffer?.takeIf { pendingOfferCallId == st.callId }
                var attempt = 0
                while (offer == null && attempt < 15) {
                    val now = state.value
                    if (now !is CallUiState.Incoming || now.callId != st.callId) return@launch
                    offer = CallSignal.readOffer(st.threadId, st.callId)
                    if (offer == null) { attempt++; delay(400) }
                }
                val offerSdp = offer
                if (offerSdp == null) {
                    // ROUND 7 (b)+(d): the offer never materialised. With the
                    // atomic createCall, a ringing doc either carries its
                    // offer or is gone entirely (the caller vanished
                    // mid-ring) — that is a NO-ANSWER, not a failure: record
                    // it as one and leave quietly. The user must never see a
                    // fake "Call failed — Offer missing" again.
                    recordOutcome("noanswer")
                    CallSignal.markEnded(st.threadId, st.callId)
                    finish(st.threadId, deleteDocs = true, quiet = true)
                } else {
                    // the callee's call doc provably exists (the watcher saw
                    // it ringing) — ICE egress needs no gate on this side.
                    iceGateOpen = true; iceBuffer.clear()
                    val s = RtcSession(ctx, hasVideo = st.video,
                        cameraOn = settings.cameraDefaultOn, speakerOn = settings.speakerDefaultOn)
                    wireEvents(s, st.threadId, st.callId, role = "callee")
                    session = s
                    val answer = s.acceptOffer(offerSdp)
                    CallSignal.acceptCall(st.threadId, st.callId, answer)
                    attachDocListener(st.threadId, st.callId, role = "callee")
                    state.value = CallUiState.Connected(st.threadId, st.callId, st.video, st.person,
                        System.currentTimeMillis(), speakerOn = settings.speakerDefaultOn,
                        cameraOn = settings.cameraDefaultOn)
                    ringJob?.cancel(); ringJob = null
                    (state.value as? CallUiState.Connected)?.let { publishInitialCameraOff(it) }
                }
            } catch (e: Exception) {
                Log.e("TikuCall", "accept failed for call ${st.callId}", e)
                failCall(st.threadId, FirebaseEngine.sendFailureReason(e))
            }
        }
    }

    fun reject() {
        val st = state.value as? CallUiState.Incoming ?: return
        // round 4: the decline is a chat event both sides can see (the caller
        // records the same event when its doc listener sees the end mark)
        recordOutcome("declined")
        scope.launch { CallSignal.markEnded(st.threadId, st.callId) }
        finish(st.threadId, deleteDocs = true, quiet = true)
    }

    /** Local user hangs up (from any state). */
    fun hangup() {
        val st = state.value
        val cur = ids(st) ?: return
        // round 4: connected → "ended" (+duration); still ringing out →
        // "noanswer" (the callee renders it as a missed call)
        when (st) {
            is CallUiState.Connected -> recordOutcome("ended")
            is CallUiState.Outgoing -> recordOutcome("noanswer")
            is CallUiState.Incoming -> recordOutcome("declined")
            else -> {}
        }
        scope.launch { CallSignal.markEnded(cur.first, cur.second) }
        finish(cur.first, deleteDocs = true, quiet = false)
    }

    // ---------- shared session plumbing ----------
    private fun wireEvents(s: RtcSession, threadId: String, callId: String, role: String) {
        myRole = role
        s.events = object : RtcSession.Events {
            override fun onIceCandidate(c: IceCandidate) {
                val push: suspend () -> Unit = { CallSignal.pushCandidate(threadId, callId, role, c) }
                scope.launch { if (iceGateOpen) push() else iceBuffer += push }
            }
            override fun onMediaConnected() {
                // media is flowing — if we are still "Outgoing" (caller side),
                // signaling marks connected; nothing extra needed here.
            }
            override fun onFailed() {
                scope.launch {
                    Log.e("TikuCall", "ICE connection failed for call $callId")
                    failCall(threadId, "the connection failed")
                }
            }
            override fun onRemoteVideo() {
                // tick bump forces CallScreen recomposition so it rebinds the remote sink
                val st = state.value
                if (st is CallUiState.Connected) state.value = st.copy(tick = st.tick + 1)
            }
            override fun onScreenShareStopped() {
                // The system revoked the projection; the session has
                // already ended its capture. Run the ordinary stop so
                // UI state, the signaling field and the service all
                // settle on "not sharing".
                scope.launch { stopScreenShare() }
            }
        }
    }

    private fun attachDocListener(threadId: String, callId: String, role: String) {
        callDocReg?.remove()
        callDocReg = CallSignal.listen(threadId, callId) { data ->
            val st = state.value
            val mine = ids(st)?.second == callId
            if (!mine) return@listen
            if (data == null || data["state"] == "ended") {
                // remote side ended / deleted the record. Round 4 — record
                // the chat event from MY perspective before teardown:
                // connected → the call ended; I was ringing out and the doc
                // ended → the callee declined; I was being rung and the doc
                // vanished → missed (caller cancelled / timed out / busy).
                when (st) {
                    is CallUiState.Connected -> recordOutcome("ended")
                    is CallUiState.Outgoing -> recordOutcome("declined")
                    is CallUiState.Incoming -> recordOutcome("noanswer")
                    else -> {}
                }
                if (st !is CallUiState.Idle) finish(threadId, deleteDocs = true, quiet = false, remoteEnded = true)
                return@listen
            }
            // ROUND 32: the peer's screen-share flag (their role's
            // field on this same doc). A flip re-keys the remote
            // renderer via a tick bump so it re-creates letterboxed
            // (or back to full-bleed camera framing).
            val peerSharing = data[if (role == "caller") "sharingCallee" else "sharingCaller"] == true
            if (peerSharing != remoteSharing.value) {
                remoteSharing.value = peerSharing
                val cur = state.value
                if (cur is CallUiState.Connected) state.value = cur.copy(tick = cur.tick + 1)
            }
            // ROUND 47: the peer's camera on/off (their role's field;
            // absent = on). No tick bump: the surfaces collect
            // remoteCameraOff directly and re-derive from it.
            val peerCameraOff = (data[if (role == "caller") "cameraCallee" else "cameraCaller"] as? Boolean) == false &&
                data.containsKey(if (role == "caller") "cameraCallee" else "cameraCaller")
            if (peerCameraOff != remoteCameraOff.value) remoteCameraOff.value = peerCameraOff
            scope.launch {
                runCatching {
                    if (role == "caller") {
                        val ans = data["answer"] as? String
                        if (ans != null && !answerApplied) {
                            answerApplied = true
                            session?.setRemoteAnswer(decodeSdp(ans))
                            val cur = state.value
                            if (cur is CallUiState.Outgoing && cur.callId == callId) {
                                state.value = CallUiState.Connected(
                                    cur.threadId, cur.callId, cur.video, cur.person, System.currentTimeMillis(),
                                    speakerOn = SettingsStore.state.value.speakerDefaultOn,
                                    cameraOn = SettingsStore.state.value.cameraDefaultOn,
                                )
                                ringJob?.cancel(); ringJob = null
                                (state.value as? CallUiState.Connected)?.let { publishInitialCameraOff(it) }
                            }
                        }
                        @Suppress("UNCHECKED_CAST")
                        val cands = (data["calleeCands"] as? List<String>) ?: emptyList()
                        while (processedCalleeCands < cands.size) {
                            decodeCandidate(cands[processedCalleeCands])?.let { session?.addRemoteCandidate(it) }
                            processedCalleeCands++
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val cands = (data["callerCands"] as? List<String>) ?: emptyList()
                        while (processedCallerCands < cands.size) {
                            decodeCandidate(cands[processedCallerCands])?.let { session?.addRemoteCandidate(it) }
                            processedCallerCands++
                        }
                    }
                }
            }
        }
    }

    private fun armRingTimeout(threadId: String, callId: String) {
        ringJob?.cancel()
        ringJob = scope.launch {
            delay(Config.CALL_RING_TIMEOUT_MS)
            val st = state.value
            val cur = ids(st)
            if (cur != null && cur.second == callId && st !is CallUiState.Connected) {
                // round 4: ring timeout is a resolved call attempt — the
                // caller records "noanswer"; a callee whose ring timed out
                // records the same event (rendered there as a missed call)
                recordOutcome("noanswer")
                CallSignal.markEnded(threadId, callId)
                AppState.toast("No answer")
                finish(threadId, deleteDocs = true, quiet = true)
            }
        }
    }

    /**
     * Failure path (round 3): surface a plain "Call failed — <reason>" state and
     * hold it on screen briefly; ONLY THEN tear down and delete the signaling
     * trace (finish). A call must never fail silently again.
     */
    private fun failCall(threadId: String, reason: String) {
        val st = state.value
        if (st is CallUiState.Failed) return
        ids(st) ?: return
        // round 4: a call that HAD connected still leaves its "ended" event
        // even when it dies by ICE failure; a call that never connected
        // leaves none here (the peer's own resolution path records theirs)
        if (st is CallUiState.Connected) recordOutcome("ended")
        val person = when (st) {
            is CallUiState.Outgoing -> st.person
            is CallUiState.Incoming -> st.person
            is CallUiState.Connected -> st.person
            else -> AppState.thread(threadId)?.person ?: return
        }
        val video = when (st) {
            is CallUiState.Outgoing -> st.video
            is CallUiState.Incoming -> st.video
            is CallUiState.Connected -> st.video
            else -> false
        }
        val callId = ids(st)?.second ?: ""
        ringJob?.cancel(); ringJob = null
        callDocReg?.remove(); callDocReg = null
        session?.dispose(); session = null
        answerApplied = false; processedCallerCands = 0; processedCalleeCands = 0
        pendingOffer = null; pendingOfferCallId = null
        iceGateOpen = false; iceBuffer.clear()
        clearScreenShareState()
        state.value = CallUiState.Failed(threadId, callId, video, person, reason)
        AppState.go(Screen.Call(threadId, video))
        scope.launch {
            delay(2600)
            if (state.value is CallUiState.Failed) {
                finish(threadId, deleteDocs = true, quiet = true)
            }
        }
    }

    /** Common teardown: dispose media, delete the signaling doc (both sides do this —
     *  idempotent), reset state, and navigate back to the chat (or Home). */
    private fun finish(threadId: String, deleteDocs: Boolean, quiet: Boolean, remoteEnded: Boolean = false) {
        val st = state.value
        val callId = ids(st)?.second
        ringJob?.cancel(); ringJob = null
        callDocReg?.remove(); callDocReg = null
        session?.dispose(); session = null
        clearScreenShareState()
        runCatching { CallFeedbackManager.stopAll() }
        state.value = CallUiState.Idle
        pipMinimized.value = false
        answerApplied = false; processedCallerCands = 0; processedCalleeCands = 0
        curCallerUid = ""; eventRecordedCallId = null
        pendingOffer = null; pendingOfferCallId = null
        iceGateOpen = false; iceBuffer.clear()
        if (deleteDocs && realMode && callId != null) {
            scope.launch { CallSignal.deleteCallTree(threadId, callId) }
        }
        if (remoteEnded && !quiet) AppState.toast("Call ended")
        if (AppState.screen is Screen.Call) {
            if (AppState.thread(threadId) != null) AppState.go(Screen.Chat(threadId))
            else AppState.go(Screen.Home)
        }
    }

    // ---------- in-call controls ----------
    fun toggleMute() {
        val st = state.value as? CallUiState.Connected ?: return
        val next = !st.muted
        session?.setMicEnabled(!next)
        state.value = st.copy(muted = next)
    }

    fun toggleSpeaker() {
        val st = state.value as? CallUiState.Connected ?: return
        val next = !st.speakerOn
        session?.setSpeaker(next)
        state.value = st.copy(speakerOn = next)
    }

    fun toggleCamera() {
        val st = state.value as? CallUiState.Connected ?: return
        val next = !st.cameraOn
        session?.setCameraEnabled(next)
        state.value = st.copy(cameraOn = next)
        // ROUND 47: publish the toggle so the peer swaps its frozen
        // surface for the avatar (and back) — see setCameraOn.
        if (realMode && myRole.isNotEmpty()) {
            scope.launch { CallSignal.setCameraOn(st.threadId, st.callId, myRole, next) }
        }
    }

    /** ROUND 47: when a call connects with the camera already off
     *  (the camera-default setting), the absent doc field would read
     *  as "on" on the peer's side — publish the off state once, at
     *  Connected entry, from both roles' connect paths. */
    private fun publishInitialCameraOff(st: CallUiState.Connected) {
        if (!realMode || st.cameraOn || myRole.isEmpty()) return
        scope.launch { CallSignal.setCameraOn(st.threadId, st.callId, myRole, false) }
    }

    fun switchCamera() { session?.switchCamera() }

    // ---------- ROUND 32: screen sharing ----------
    /**
     * Begin sharing my screen. [data] is the fresh result intent of
     * the system consent dialog (the call screen owns that launcher).
     * Order matters on Android 14+: the mediaProjection foreground
     * service goes up FIRST, the projection (created inside the
     * session's capturer start) second. The mic track is never
     * touched — the call's audio flows throughout.
     */
    fun beginScreenShare(ctx: Context, data: Intent) {
        val st = state.value as? CallUiState.Connected ?: return
        if (st.sharingScreen) return
        val s = session ?: return
        val appCtx = ctx.applicationContext
        ScreenShareService.start(appCtx)
        shareAppCtx = appCtx
        if (s.startScreenShare(data)) {
            state.value = st.copy(sharingScreen = true)
            scope.launch { CallSignal.setSharing(st.threadId, st.callId, myRole, true) }
        } else {
            ScreenShareService.stop(appCtx)
            shareAppCtx = null
            AppState.toast("Couldn't start screen sharing")
        }
    }

    /** Stop sharing (button, indicator, or the session's revocation
     *  callback). Idempotent: safe from every path, in any order. */
    fun stopScreenShare() {
        val st = state.value
        session?.stopScreenShare()
        shareAppCtx?.let { ScreenShareService.stop(it) }
        shareAppCtx = null
        if (st is CallUiState.Connected && st.sharingScreen) {
            state.value = st.copy(sharingScreen = false)
            scope.launch { CallSignal.setSharing(st.threadId, st.callId, myRole, false) }
        }
    }

    /** Call teardown bookkeeping for shares: the session's dispose()
     *  has already stopped the capturer and the service; this drops
     *  the controller's own share state so nothing outlives the call. */
    private fun clearScreenShareState() {
        shareAppCtx?.let { ScreenShareService.stop(it) }
        shareAppCtx = null
        remoteSharing.value = false
        // ROUND 47: the peer-camera flag dies with the call too (same
        // teardown pass as remoteSharing; the doc itself is deleted).
        remoteCameraOff.value = false
        myRole = ""
    }

    /** Access for the call screen's renderers. */
    fun currentSession(): RtcSession? = session
}
