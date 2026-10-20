package com.tiku.app

import android.content.Context
import android.media.AudioManager
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
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
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
    }

    var events: Events? = null

    private val appCtx = ctx.applicationContext
    private val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var captureHelper: SurfaceTextureHelper? = null
    var remoteVideoTrack: VideoTrack? = null
        private set
    val eglContext: EglBase.Context get() = RtcFactory.egl.eglBaseContext
    private var disposed = false

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
        // local video (video calls)
        if (hasVideo) {
            val capturer = createCapturer(appCtx)
            if (capturer != null) {
                videoCapturer = capturer
                videoSource = RtcFactory.factory.createVideoSource(capturer.isScreencast)
                captureHelper = SurfaceTextureHelper.create("tiku-capture", eglContext)
                capturer.initialize(captureHelper, appCtx, videoSource!!.capturerObserver)
                runCatching { capturer.startCapture(960, 720, 24) }
                videoTrack = RtcFactory.factory.createVideoTrack("tiku-video", videoSource)
                videoTrack?.setEnabled(cameraOn)
                pc?.addTrack(videoTrack, listOf("tiku-stream"))
            }
        }
        // audio route for the call
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = hasVideo && speakerOn
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
                    onDone = { if (cont.isActive) cont.resume(offer) },
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
                conn.createAnswer(sdpObserver(
                    onCreated = { answer ->
                        conn.setLocalDescription(sdpObserver(
                            onCreated = {},
                            onDone = { if (cont.isActive) cont.resume(answer) },
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
            onDone = { if (cont.isActive) cont.resume(Unit) },
            onError = { if (cont.isActive) cont.resume(Unit) }, // best-effort: ICE state drives failure anyway
        ), answer)
    }

    fun addRemoteCandidate(c: IceCandidate) { runCatching { pc?.addIceCandidate(c) } }
    fun setMicEnabled(on: Boolean) { runCatching { audioTrack?.setEnabled(on) } }
    fun setCameraEnabled(on: Boolean) { runCatching { videoTrack?.setEnabled(on) } }
    fun switchCamera() { runCatching { videoCapturer?.switchCamera(null) } }
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
        state.value = CallUiState.Idle
        answerApplied = false; processedCallerCands = 0; processedCalleeCands = 0
        curCallerUid = ""; eventRecordedCallId = null
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
        curCallerUid = FirebaseEngine.uid
        eventRecordedCallId = null
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
    fun onIncoming(chatId: String, callId: String, kind: String, person: Person) {
        if (state.value !is CallUiState.Idle) return
        val video = kind == "video"
        curCallerUid = person.uid
        eventRecordedCallId = null
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
                val offer = CallSignal.readOffer(st.threadId, st.callId)
                    ?: throw IllegalStateException("Offer missing")
                val s = RtcSession(ctx, hasVideo = st.video,
                    cameraOn = settings.cameraDefaultOn, speakerOn = settings.speakerDefaultOn)
                wireEvents(s, st.threadId, st.callId, role = "callee")
                session = s
                val answer = s.acceptOffer(offer)
                CallSignal.acceptCall(st.threadId, st.callId, answer)
                attachDocListener(st.threadId, st.callId, role = "callee")
                state.value = CallUiState.Connected(st.threadId, st.callId, st.video, st.person,
                    System.currentTimeMillis(), speakerOn = settings.speakerDefaultOn,
                    cameraOn = settings.cameraDefaultOn)
                ringJob?.cancel(); ringJob = null
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
        s.events = object : RtcSession.Events {
            override fun onIceCandidate(c: IceCandidate) {
                scope.launch { CallSignal.pushCandidate(threadId, callId, role, c) }
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
        val callId = ids(st)!!.second
        ringJob?.cancel(); ringJob = null
        callDocReg?.remove(); callDocReg = null
        session?.dispose(); session = null
        answerApplied = false; processedCallerCands = 0; processedCalleeCands = 0
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
        state.value = CallUiState.Idle
        answerApplied = false; processedCallerCands = 0; processedCalleeCands = 0
        curCallerUid = ""; eventRecordedCallId = null
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
    }

    fun switchCamera() { session?.switchCamera() }

    /** Access for the call screen's renderers. */
    fun currentSession(): RtcSession? = session
}
