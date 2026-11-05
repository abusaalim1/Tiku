package com.tiku.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/** Manages ringing sounds, ringback tones, and phone vibration during calls. */
object CallFeedbackManager {
    private var ringtone: Ringtone? = null
    private var toneGen: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    fun onCallStateChanged(context: android.content.Context, state: CallUiState) {
        val settings = SettingsStore.state.value
        when (state) {
            is CallUiState.Incoming -> {
                stopAll()
                if (settings.vibration) {
                    runCatching {
                        val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                        if (vib != null && vib.hasVibrator()) {
                            vibrator = vib
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
                            } else {
                                @Suppress("DEPRECATION")
                                vib.vibrate(longArrayOf(0, 1000, 1000), 0)
                            }
                        }
                    }
                }
                if (settings.sounds) {
                    runCatching {
                        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                isLooping = true
                            }
                            play()
                        }
                    }
                }
            }
            is CallUiState.Outgoing -> {
                stopAll()
                if (settings.sounds) {
                    runCatching {
                        toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                        toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
                    }
                }
            }
            is CallUiState.Connected, is CallUiState.Failed, CallUiState.Idle -> {
                stopAll()
            }
        }
    }

    fun stopAll() {
        runCatching { toneGen?.stopTone(); toneGen?.release() }
        toneGen = null
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }
}

/**
 * Call entry points ask for mic/camera ONLY at the moment a call starts
 * (runtime permissions at point of use — never at install or app start).
 */
@Composable
fun rememberCallStarter(): (threadId: String, video: Boolean) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val req = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val needed = if (req.second) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else listOf(Manifest.permission.RECORD_AUDIO)
        if (needed.all { grants[it] == true }) {
            CallController.startCall(ctx, req.first, req.second)
            AppState.go(Screen.Call(req.first, req.second))
        } else {
            AppState.toast(if (req.second) "Microphone and camera are needed for calls" else "Microphone is needed for calls")
        }
    }
    return { threadId, video ->
        if (Config.PREVIEW_UI_ONLY) {
            CallController.startCall(ctx, threadId, video)
            AppState.go(Screen.Call(threadId, video))
        } else {
            val needed = (if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                else arrayOf(Manifest.permission.RECORD_AUDIO))
                .filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isEmpty()) {
                CallController.startCall(ctx, threadId, video)
                AppState.go(Screen.Call(threadId, video))
            } else {
                pending = threadId to video
                launcher.launch(needed.toTypedArray())
            }
        }
    }
}

/** WebRTC video surface (local PiP or remote full-screen). Preview mode never
 *  reaches here — org.webrtc classes must not load under Robolectric.
 *  ROUND 32: [fit] letterboxes instead of cropping — used when the
 *  remote stream is a shared SCREEN, whose edges carry content that
 *  must never be cut away. Camera streams keep the designed FILL. */
@Composable
private fun RtcVideoView(local: Boolean, modifier: Modifier, fit: Boolean = false) {
    val session = CallController.currentSession() ?: return
    AndroidView(
        factory = { c ->
            SurfaceViewRenderer(c).apply {
                init(session.eglContext, null)
                setMirror(local)
                setScalingType(
                    if (fit) RendererCommon.ScalingType.SCALE_ASPECT_FIT
                    else RendererCommon.ScalingType.SCALE_ASPECT_FILL
                )
                setEnableHardwareScaler(true)
                if (local) { setZOrderMediaOverlay(true); session.bindLocalView(this) }
                else session.bindRemoteView(this)
            }
        },
        onRelease = { v ->
            if (local) session.unbindLocalView(v) else session.unbindRemoteView(v)
            runCatching { v.release() }
        },
        modifier = modifier,
    )
}

/** Design call-dock button: 58dp glass disc (white .14 + white .2 edge);
 *  the ACTIVE state flips to near-white with a dark glyph. Icons are the
 *  owner design's drawn set (TikuIcon names). */
@Composable
private fun CallToggle(icon: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(58.dp).clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.14f))
            .then(
                if (active) Modifier
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TikuIcon(icon, size = 24.dp, tint = if (active) Color(0xFF16141C) else Color.White)
    }
}

/** End call: the design's red-gradient disc with the phone glyph rotated
 *  135° (the classic hang-up pose). */
@Composable
private fun CallEndButton(onClick: () -> Unit) {
    Box(
        Modifier.size(58.dp).clip(CircleShape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFF6A77), Color(0xEE3148))),
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TikuIcon(
            "call", size = 24.dp, tint = Color.White,
            modifier = Modifier.graphicsLayer { rotationZ = 135f },
        )
    }
}

/**
 * Call screen — the designed dark-gradient UI, now wired to the REAL WebRTC
 * engine (Rtc.kt). Ringing only ever happens here: inside ChatRoot, unlocked,
 * app foregrounded — there are no call notifications anywhere by design.
 */
@Composable
fun CallScreen(threadId: String, video: Boolean) {
    val ctx = LocalContext.current
    val callState by CallController.state.collectAsState()
    val rtcSession by CallController.sessionState.collectAsState()
    // ROUND 32: the peer is sharing their screen (their video stream
    // is a screen: show it even on voice calls, and letterboxed).
    val remoteSharingNow by CallController.remoteSharing.collectAsState()

    // manage ringtones and vibration feedback
    LaunchedEffect(callState) {
        CallFeedbackManager.onCallStateChanged(ctx, callState)
    }
    DisposableEffect(Unit) {
        onDispose {
            CallFeedbackManager.stopAll()
        }
    }

    // accept-side permission gate (mic, plus camera for video calls)
    var pendingAccept by remember { mutableStateOf<CallUiState.Incoming?>(null) }
    val acceptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val inc = pendingAccept ?: return@rememberLauncherForActivityResult
        pendingAccept = null
        val needed = if (inc.video) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else listOf(Manifest.permission.RECORD_AUDIO)
        if (needed.all { grants[it] == true }) CallController.accept(ctx)
        else { AppState.toast("Permission needed to answer"); CallController.reject() }
    }
    fun acceptWithPerms() {
        val inc = callState as? CallUiState.Incoming ?: return
        if (Config.PREVIEW_UI_ONLY) { CallController.accept(ctx); return }
        val needed = (if (inc.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else arrayOf(Manifest.permission.RECORD_AUDIO))
            .filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) CallController.accept(ctx)
        else { pendingAccept = inc; acceptLauncher.launch(needed.toTypedArray()) }
    }

    // ROUND 32 — screen share consent. The system dialog is the ONLY
    // way to a projection grant, and the grant is single-use: every
    // share start asks afresh. Grant → the controller starts the
    // projection service and swaps the session's video source to the
    // screen; denial simply leaves the call untouched.
    val shareConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null) {
            CallController.beginScreenShare(ctx, data)
        }
    }
    fun toggleShare() {
        val c = callState as? CallUiState.Connected ?: return
        if (c.sharingScreen) { CallController.stopScreenShare(); return }
        if (Config.PREVIEW_UI_ONLY) return
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
        shareConsentLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // ROUND 8: Back during a live call MINIMIZES to the floating window
    // (IG behaviour) — it must never hang up by accident. Only the explicit
    // end button ends a call. Incoming-call Back still rejects; the session
    // lock / quick exit still ends everything (ChatSession → endSilently).
    androidx.activity.compose.BackHandler {
        when (callState) {
            is CallUiState.Incoming -> CallController.reject()
            is CallUiState.Connected, is CallUiState.Outgoing -> CallController.minimizeCall()
            else -> CallController.hangup()
        }
    }
    val pipMinimized by CallController.pipMinimized.collectAsState()
    LaunchedEffect(pipMinimized) {
        if (pipMinimized) {
            if (AppState.thread(threadId) != null) AppState.go(Screen.Chat(threadId)) else AppState.go(Screen.Home)
        }
    }

    // no active call for this screen -> back to the chat / home
    LaunchedEffect(callState) {
        if (callState is CallUiState.Idle) {
            if (AppState.thread(threadId) != null) AppState.go(Screen.Chat(threadId)) else AppState.go(Screen.Home)
        }
    }

    val (person, status, isVideo) = when (val s = callState) {
        is CallUiState.Outgoing -> Triple(s.person, "Calling…", s.video)
        is CallUiState.Incoming -> Triple(s.person, if (s.video) "Incoming video call…" else "Incoming voice call…", s.video)
        is CallUiState.Connected -> Triple(s.person, "", s.video)
        is CallUiState.Failed -> Triple(s.person, "Call failed — ${s.reason}", s.video)
        CallUiState.Idle -> Triple(Person(name = ""), "", video)
    }
    val connected = callState as? CallUiState.Connected
    val incoming = callState as? CallUiState.Incoming

    // connected-call clock
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connected?.callId) {
        if (connected == null) return@LaunchedEffect
        while (true) { delay(500); nowMs = System.currentTimeMillis() }
    }
    val statusText = if (connected != null) {
        val secs = ((nowMs - connected.atMs) / 1000).coerceAtLeast(0).toInt()
        "%d:%02d".format(secs / 60, secs % 60)
    } else status

    val inf = rememberInfiniteTransition(label = "callfloat")
    val fy by inf.animateFloat(0f, -8f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "fy")
    val pScale1 by inf.animateFloat(1f, 1.42f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ps1")
    val pAlpha1 by inf.animateFloat(0.55f, 0f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pa1")
    val pScale2 by inf.animateFloat(1f, 1.72f, infiniteRepeatable(tween(1800, delayMillis = 450, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ps2")
    val pAlpha2 by inf.animateFloat(0.4f, 0f, infiniteRepeatable(tween(1800, delayMillis = 450, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pa2")

    // ROUND 13 skin: the design's call stage — near-black #0B0B0F with the
    // theme pattern and accent glows pooled top/bottom (audio stage; the
    // remote video, when live, covers it as before).
    val pal = LocalPalette.current
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(pal.ac.copy(alpha = 0.50f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(pal.ac2.copy(alpha = 0.34f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                        radius = size.width * 0.9f,
                    ),
                )
                with(density) { drawThemePattern(pal.pat, pal.ac, true, 160.dp.toPx()) }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Remote video: video calls as before; ROUND 32 also shows
        // the surface on a VOICE call when the peer starts sharing
        // their screen (their blank-by-default video track lights
        // up), or whenever a remote video track actually exists.
        val showRemoteVideo = connected != null && !Config.PREVIEW_UI_ONLY &&
            (isVideo || remoteSharingNow || rtcSession?.remoteVideoTrack != null)
        if (showRemoteVideo) {
            // re-keyed on the share flag too: FIT vs FILL is chosen
            // at renderer creation, so a share flip rebuilds the view.
            key(connected!!.tick to remoteSharingNow) {
                RtcVideoView(local = false, modifier = Modifier.fillMaxSize(), fit = remoteSharingNow)
            }
        }
        // self-view PiP (top-end, as designed: 98x138, r24, white .4 edge).
        // ROUND 32: also composed while I share my screen on a voice
        // call — the PiP binds the same video track, so it previews
        // exactly what the peer is seeing.
        if (isVideo || connected?.sharingScreen == true) {
            Box(Modifier.fillMaxSize().padding(top = 24.dp, end = 20.dp)) {
                if (!Config.PREVIEW_UI_ONLY && rtcSession != null && (connected != null || callState is CallUiState.Outgoing)) {
                    RtcVideoView(
                        local = true,
                        modifier = Modifier.size(98.dp, 138.dp).align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                    )
                } else {
                    Box(
                        Modifier.size(98.dp, 138.dp).align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0x1FFFFFFF))
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("You", fontSize = 12.sp, color = Color.White, fontFamily = InterTight) }
                }
            }
        }
        // ROUND 8: explicit minimize control (same action as Back) — shrinks
        // the call to the floating window so messaging can continue.
        if (connected != null || callState is CallUiState.Outgoing) {
            Box(
                Modifier.align(Alignment.TopStart).padding(16.dp).size(40.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        CallController.minimizeCall()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(22.dp), tint = Color.White) }
        }
        Column(
            Modifier.fillMaxSize().padding(top = 70.dp, bottom = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!showRemoteVideo) {
                    Box(
                        Modifier.offset(y = fy.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (connected == null && callState !is CallUiState.Failed) {
                            // the design's pulse: expanding RINGS stroked in
                            // white lerped toward the accent (was: filled
                            // orange/pink discs)
                            val ring = lerp(Color.White, pal.ac, 0.65f)
                            Box(
                                Modifier.size((120 * pScale2).dp).alpha(pAlpha2)
                                    .border(1.5.dp, ring, CircleShape)
                            )
                            Box(
                                Modifier.size((120 * pScale1).dp).alpha(pAlpha1)
                                    .border(1.5.dp, ring, CircleShape)
                            )
                        }
                        AvatarBox(person, 120.dp, 44.dp, 48f)
                    }
                    Spacer(Modifier.height(18.dp))
                }
                // .cltop — the design's glass identity pill (name + status)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.07f)),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(person.name, fontFamily = InterTight, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = 19.sp, color = Color.White)
                    Text(statusText, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = InterTight)
                }
                // ROUND 32 — screen-share indicator, same glass-pill
                // language as .cltop. Mine carries the Stop action;
                // the peer's is a plain label (their stream is already
                // letterboxed full-screen behind this column).
                if (connected?.sharingScreen == true) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3DD68C)))
                        Spacer(Modifier.width(8.dp))
                        Text("You're sharing your screen", fontSize = 12.5.sp, color = Color.White, fontFamily = InterTight)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Stop", fontSize = 12.5.sp, color = Color(0xFFFF8A94), fontFamily = InterTight,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                            ) { CallController.stopScreenShare() },
                        )
                    }
                } else if (remoteSharingNow && connected != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3DD68C)))
                        Spacer(Modifier.width(8.dp))
                        Text("You're watching their screen", fontSize = 12.5.sp, color = Color.White, fontFamily = InterTight)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TikuLockIcon(Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.width(5.dp))
                    Text("End-to-end encrypted", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), fontFamily = InterTight)
                }
                Spacer(Modifier.height(18.dp))
                if (incoming != null) {
                    // ringing: accept / reject — rejecting deletes the call record entirely
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        CallEndButton { CallController.reject() }
                        Box(
                            Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF2E9E5B))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { acceptWithPerms() },
                            contentAlignment = Alignment.Center,
                        ) {
                            TikuIcon("call", size = 24.dp, tint = Color.White)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (connected != null) {
                            CallToggle(if (connected.muted) "micOff" else "mic", connected.muted) { CallController.toggleMute() }
                            CallToggle("spk", connected.speakerOn) { CallController.toggleSpeaker() }
                            // ROUND 32: share screen — on every connected
                            // call (voice calls included: sharing lights
                            // up the blank video track). While sharing,
                            // the camera controls step aside (the camera
                            // is not the live source; its on/off is kept
                            // and returns with the camera).
                            CallToggle("shareScr", connected.sharingScreen) { toggleShare() }
                            if (connected.video && !connected.sharingScreen) {
                                CallToggle(if (connected.cameraOn) "vid" else "vidOff", !connected.cameraOn) { CallController.toggleCamera() }
                                CallToggle("flip", false) { CallController.switchCamera() }
                            }
                        }
                        CallEndButton { CallController.hangup() }
                    }
                }
            }
        }
    }
}

/**
 * ROUND 8 — FLOATING CALL WINDOW (IG-style PiP). Rendered by TikuRoot over
 * the ChatRoot surfaces whenever a live call is minimized. Video calls show
 * the live REMOTE tile (the same renderer binding the full screen uses);
 * audio calls (and outgoing calls) show an avatar tile with the call clock.
 * Draggable anywhere over the chat UI; tap restores the full call screen;
 * the red button ends the call through CallController.hangup() — the call
 * event is recorded and the signaling doc deleted, exactly as when ending
 * from the full screen. If the peer ends, or the session locks, the
 * controller leaves Connected/Outgoing (or pipMinimized clears) and the
 * window disappears on its own.
 *
 * ROUND 20 (owner device test, build 63): the window is composed by
 * TikuRoot again. Round 14 had replaced it with the redesign CallPill
 * inside HomeScreen — a name/duration pill with NO video, rendered only
 * on the Home pane tabs and structurally unable to appear over an open
 * chat (Chat is a TikuRoot overlay ABOVE HomeScreen), so a minimized
 * video call vanished exactly where the owner wants it: over the chat
 * he is typing in. Surface seam (exactly one minimized-call affordance
 * per surface): on the Home pane tabs (Messages / Calls / Watch /
 * Settings, dest 0..3) the CallPill remains the affordance and this
 * window stays out; on every other surface — an open chat above all —
 * this window renders. Renderer handover is safe by construction:
 * the full screen and this window each own a private
 * SurfaceViewRenderer and bind/unbind ONLY their own view as a sink
 * on the shared VideoTrack (WebRTC tracks fan out to many sinks; a
 * release touches nothing shared — the EglBase context is
 * factory-owned), so the brief overlap while the call screen's exit
 * animation runs can neither crash nor steal the peer's frames, and
 * the RTC session itself is never paused or renegotiated.
 */
@Composable
fun FloatingCallWindow() {
    val callState by CallController.state.collectAsState()
    val pip by CallController.pipMinimized.collectAsState()
    val rtcSession by CallController.sessionState.collectAsState()
    val remoteSharingNow by CallController.remoteSharing.collectAsState()
    val connected = callState as? CallUiState.Connected
    // connected-call clock (same cadence as the full call screen)
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connected?.callId) {
        if (connected == null) return@LaunchedEffect
        while (true) { delay(500); nowMs = System.currentTimeMillis() }
    }
    val active = callState is CallUiState.Connected || callState is CallUiState.Outgoing
    if (!pip || !active) return
    // ROUND 20 — surface seam with the shell's CallPill (Home.kt): on
    // the Home pane tabs the pill above the NavPill is the minimized
    // affordance; everywhere else (an open chat, chat details, the
    // Requests pane, Moments) this window is. Reading AppState here
    // subscribes the window to navigation, so the hand-over between
    // the two is a recomposition, never a double render.
    if (AppState.screen is Screen.Home && AppState.tikDestination in 0..3) return
    val (person, video) = when (val s = callState) {
        is CallUiState.Connected -> s.person to s.video
        is CallUiState.Outgoing -> s.person to s.video
        else -> return
    }
    val statusText = if (connected != null) {
        val secs = ((nowMs - connected.atMs) / 1000).coerceAtLeast(0).toInt()
        "%d:%02d".format(secs / 60, secs % 60)
    } else "Calling…"
    // ROUND 32: the tile also becomes a video tile when a screen is
    // being shared on a voice call — the peer's shared screen, or (if
    // the peer sends no video) my own share preview.
    val tileLocal = connected?.sharingScreen == true && rtcSession?.remoteVideoTrack == null
    val showVideo = (video || remoteSharingNow || connected?.sharingScreen == true) &&
        connected != null && !Config.PREVIEW_UI_ONLY && rtcSession != null
    val winW = if (showVideo) 108.dp else 184.dp
    val winH = if (showVideo) 156.dp else 64.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var offX by remember { mutableStateOf(maxWidth - winW - 14.dp) }
        var offY by remember { mutableStateOf(96.dp) }
        LaunchedEffect(maxWidth, maxHeight, winW, winH) {
            offX = offX.coerceIn(8.dp, (maxWidth - winW - 8.dp).coerceAtLeast(8.dp))
            offY = offY.coerceIn(8.dp, (maxHeight - winH - 8.dp).coerceAtLeast(8.dp))
        }
        Box(
            Modifier.offset(x = offX, y = offY).size(winW, winH)
                .shadow(16.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1C1C1F), Color(0xFF000000))))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offX = (offX + with(density) { drag.x.toDp() })
                            .coerceIn(8.dp, (maxWidth - winW - 8.dp).coerceAtLeast(8.dp))
                        offY = (offY + with(density) { drag.y.toDp() })
                            .coerceIn(8.dp, (maxHeight - winH - 8.dp).coerceAtLeast(8.dp))
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    CallController.restoreCall()
                },
        ) {
            if (showVideo) {
                key(connected?.tick to remoteSharingNow) {
                    RtcVideoView(
                        local = tileLocal, modifier = Modifier.fillMaxSize(),
                        fit = remoteSharingNow && !tileLocal,
                    )
                }
                // ROUND 20: his own tiny self-preview rides the tile's
                // top-end corner — the same local renderer binding the
                // full call screen uses (a second sink on the local
                // track, bound and released with this window only).
                // Hidden while his camera is off: a black inset would
                // lie about a camera that is not sending anything.
                // ROUND 32: also skipped when the tile itself is already
                // showing the local (shared-screen) track.
                if (connected?.cameraOn == true && !tileLocal) {
                    RtcVideoView(
                        local = true,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .size(27.dp, 38.dp).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                    )
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(person.name, fontSize = 11.sp, color = Color.White, fontFamily = InterTight,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(statusText, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), fontFamily = InterTight)
                }
            } else {
                Row(
                    Modifier.fillMaxSize().padding(start = 10.dp, end = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(person, 40.dp, 14.dp, 16f)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, fontSize = 13.sp, color = Color.White, fontFamily = InterTight,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(statusText, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.75f), fontFamily = InterTight)
                    }
                }
            }
            // end call — a real hangup (event recorded + signaling deleted),
            // never a mere dismiss of the window.
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF6A77), Color(0xEE3148))))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        CallController.hangup()
                    },
                contentAlignment = Alignment.Center,
            ) {
                TikuIcon(
                    "call", size = 15.dp, tint = Color.White,
                    modifier = Modifier.graphicsLayer { rotationZ = 135f },
                )
            }
        }
    }
}

/**
 * Story viewer — progress segments, gradient backgrounds, serif quote text,
 * tap left/right to navigate. Fed by the Moments data model.
 */
@Composable
fun StoryScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Home) }
    val content = AppState.story
    if (content == null) { LaunchedEffect(Unit) { AppState.go(Screen.Home) }; return }

    var sl by remember { mutableIntStateOf(0) }
    val segments = minOf(3, content.quotes.size.coerceAtLeast(1))
    val grad = content.grads[sl % content.grads.size]
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun next() { if (sl >= segments - 1) AppState.go(Screen.Home) else sl++ }
    fun prev() { if (sl > 0) sl-- }

    // auto-advance ~3.6s per segment (1.4% per 50ms like the prototype)
    LaunchedEffect(sl) {
        delay(3570)
        if (sl < segments - 1) sl++ else AppState.go(Screen.Home)
    }

    val quote = content.quotes[(content.personIndex + sl) % content.quotes.size]

    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(grad.first, grad.second)))
            .onGloballyPositioned { size = it.size },
    ) {
        // tap zones
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null) { prev() })
            Box(Modifier.weight(2f).fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null) { next() })
        }
        Column(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 40.dp, start = 24.dp, end = 24.dp)) {
            // progress segments
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(segments) { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(3.dp))
                            .background(Color(0x55FFFFFF)),
                    ) {
                        Box(
                            Modifier.fillMaxHeight()
                                .fillMaxWidth(if (i < sl) 1f else if (i == sl) segmentProgress(sl) else 0f)
                                .background(Color.White),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBox(content.person, 42.dp, 15.dp, 18f)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(content.person.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 16.sp, color = Color.White, fontFamily = Manrope)
                    Text("2h ago", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = Manrope)
                }
            }
            Spacer(Modifier.weight(1f))
            SerifTitle(quote, 52.sp, Color.White)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Tap to continue · ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = Manrope)
                TikuLockIcon(Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.8f))
                Text(" Encrypted", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = Manrope)
            }
        }
    }
}

@Composable
private fun segmentProgress(sl: Int): Float {
    val inf = rememberInfiniteTransition(label = "seg")
    val w by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3570), RepeatMode.Restart), label = "segw")
    @Suppress("UNUSED_PARAMETER") val unused = sl
    return w
}
