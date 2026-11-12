package com.tiku.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
// ROUND 70: the in-call dock glyphs/buttons are the owner's premium
// kit in CallControls.kt (CallIcons stroke vectors + CallControlButton
// / EndCallButton). The minimize / 3-dot / ringing glyphs below stay
// Material ImageVectors (material-icons-extended, already a
// dependency).
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/** ROUND 61 (Bug 4): walk a context chain to its host Activity (the
 *  same lookup MainActivity's restoreStatusBars does by direct cast,
 *  made wrapper-proof). Null on any non-activity context — e.g. the
 *  Robolectric harness — so callers can no-op safely. */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Manages phone vibration during calls.
 *  ROUND 52 (item 4, owner's explicit order): calls are SILENT — the
 *  incoming ringing screen is a visual popup only and the outgoing
 *  side plays no ringback. This manager used to play the device's
 *  default ringtone (RingtoneManager) on Incoming — audibly the
 *  phone's SIM-style ringtone the owner complained about — and a
 *  ToneGenerator ringback on Outgoing; both players are GONE. The
 *  vibration behaviour is byte-for-byte what it was. */
object CallFeedbackManager {
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
            }
            is CallUiState.Outgoing, is CallUiState.Connected,
            is CallUiState.Failed, CallUiState.Idle -> {
                stopAll()
            }
        }
    }

    fun stopAll() {
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
 *  Camera streams use the designed FILL scaling. (ROUND 69: the
 *  former FIT/letterbox mode existed only for shared screens;
 *  screen sharing is removed.) */
@Composable
private fun RtcVideoView(local: Boolean, modifier: Modifier) {
    val session = CallController.currentSession() ?: return
    AndroidView(
        factory = { c ->
            SurfaceViewRenderer(c).apply {
                init(session.eglContext, null)
                setMirror(local)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
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

/** ROUND 45: the call action discs (end / accept / floating end) share
 *  one glass finish — a vertical colour gradient (the function colour:
 *  red stays red, accept stays green), a bright glass edge and a soft
 *  white gloss pooled across the top half, clipped by the disc itself.
 *  Pure skin: geometry and clicks are the call sites' own. */
@Composable
private fun CallGlassDisc(
    sizeDp: Dp,
    gradient: List<Color>,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.size(sizeDp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(sizeDp / 2)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                    ),
                ),
        )
        content()
    }
}

/** End call: the red glass disc with Material's CallEnd glyph —
 *  the standard phone-down handset, already in hang-up orientation
 *  (no manual rotation). Red stays unmistakably red. ROUND 69 (#6):
 *  replaces the rotated hand-drawn "call" path that rendered as a
 *  white mask-like blob on this disc. */
@Composable
private fun CallEndButton(onClick: () -> Unit) {
    CallGlassDisc(58.dp, listOf(Color(0xFFFF7A85), Color(0xFFE5313F)), onClick) {
        Icon(Icons.Filled.CallEnd, contentDescription = null, modifier = Modifier.size(26.dp), tint = Color.White)
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
    // ROUND 47: the peer's camera is off (their doc field) — their
    // surface steps aside for the avatar instead of freezing.
    val remoteCameraOffNow by CallController.remoteCameraOff.collectAsState()

    // vibration feedback only — calls never play a sound (Round 52)
    LaunchedEffect(callState) {
        CallFeedbackManager.onCallStateChanged(ctx, callState)
    }
    DisposableEffect(Unit) {
        onDispose {
            CallFeedbackManager.stopAll()
        }
    }

    // ROUND 61 (Bug 4) — IMMERSIVE SYSTEM BARS on the call surface.
    // The owner: on Instagram the system navigation bar hides during
    // calls; here it stayed visible. CallScreen is the ONE surface
    // for every call presentation — Outgoing, Incoming (ringing),
    // Connected and Failed all render here, inside the single
    // MainActivity — so hiding here covers the ringing presentation
    // too, and nowhere else: no other screen composes this effect,
    // and there is no global/app-wide behaviour change. The bars
    // hide with BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE (an edge swipe
    // reveals them briefly, then they sink again). Restore happens
    // in this same effect's onDispose — the exact moment CallScreen
    // leaves composition (call ended / rejected / minimized to the
    // floating window / session locked) — showing systemBars() back
    // and re-asserting the app-wide light status-bar icons that
    // TikuRoot keeps everywhere else. Hiding also collapses
    // TikuRoot's windowInsetsPadding(systemBars) around this screen
    // to zero, so the call UI (remote video included) goes truly
    // full-bleed while the bars are gone and re-insets on restore.
    // Best-effort (runCatching + null-safe activity lookup): in the
    // preview harness there is no real window to drive and the
    // effect simply no-ops.
    DisposableEffect(Unit) {
        val controller = ctx.findActivity()?.window?.let { w ->
            WindowInsetsControllerCompat(w, w.decorView)
        }
        runCatching {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            runCatching {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                controller?.isAppearanceLightStatusBars = true
            }
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

    // ROUND 69 (#6): screen sharing removed — no consent launcher remains.

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

    // ROUND 68 (WS7b) — connected-call controls auto-hide. Three
    // seconds after the call connects (and three seconds after the
    // controls are last re-shown or touched), every control sinks
    // away and only the 3-dot button stays, on the right edge; tapping
    // it brings the controls back and restarts the timer. Ringing /
    // outgoing never hide: the accept/reject and the outgoing End
    // button are always on screen. [controlsHideKey] is bumped by
    // every control tap and every re-show, restarting the timer.
    var controlsVisible by remember(connected?.callId) { mutableStateOf(true) }
    var controlsHideKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(connected?.callId, controlsHideKey) {
        if (connected == null) return@LaunchedEffect
        delay(3000)
        controlsVisible = false
    }
    fun pokeControls() {
        controlsVisible = true
        controlsHideKey++
    }

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

    // Call stage (ROUND 13 skin, ROUND 45 token pass): the palette's own
    // premium-dark bg as the single source (was a hardcoded near-black),
    // with the theme pattern and accent glows pooled top/bottom (audio
    // stage; the remote video, when live, covers it as before).
    val pal = LocalPalette.current
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize()
            .background(pal.bg)
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
        // Remote video: video calls as before, or whenever a remote
        // video track actually exists. ROUND 47: a peer whose camera
        // is OFF loses the surface to the avatar presentation below
        // (their disabled track would only freeze on its last frame).
        val showRemoteVideo = connected != null && !Config.PREVIEW_UI_ONLY &&
            (isVideo || rtcSession?.remoteVideoTrack != null) &&
            !remoteCameraOffNow
        if (showRemoteVideo) {
            key(connected!!.tick) {
                RtcVideoView(local = false, modifier = Modifier.fillMaxSize())
            }
            // ROUND 45: video-stage scrims in the new language — soft
            // dark pools top and bottom so the glass chrome (minimize,
            // identity pill, dock) stays legible over any camera feed.
            // Pure overlay: the renderer beneath is untouched.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.36f),
                            0.22f to Color.Transparent,
                            0.60f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                ),
            )
        }
        // self-view PiP (98x138, r24, white .4 edge, resting top-end).
        // ROUND 68 (WS7d): the PiP is DRAGGABLE — the same
        // detectDragGestures + clamp pattern FloatingCallWindow uses
        // (drag deltas in px, clamped to the screen with an 8.dp
        // margin, position kept across recompositions). Dragging moves
        // only this window; the renderers underneath are untouched.
        if (isVideo) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pipW = 98.dp
                val pipH = 138.dp
                var pipX by remember { mutableStateOf(maxWidth - pipW - 20.dp) }
                var pipY by remember { mutableStateOf(24.dp) }
                LaunchedEffect(maxWidth, maxHeight) {
                    pipX = pipX.coerceIn(8.dp, (maxWidth - pipW - 8.dp).coerceAtLeast(8.dp))
                    pipY = pipY.coerceIn(8.dp, (maxHeight - pipH - 8.dp).coerceAtLeast(8.dp))
                }
                Box(
                    Modifier.offset(x = pipX, y = pipY).size(pipW, pipH)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                pipX = (pipX + with(density) { drag.x.toDp() })
                                    .coerceIn(8.dp, (maxWidth - pipW - 8.dp).coerceAtLeast(8.dp))
                                pipY = (pipY + with(density) { drag.y.toDp() })
                                    .coerceIn(8.dp, (maxHeight - pipH - 8.dp).coerceAtLeast(8.dp))
                            }
                        },
                ) {
                    // ROUND 47: the live self-view only while the camera
                    // is actually sending. Camera off falls through to the
                    // "You" glass placeholder: the disabled track's stale
                    // last frame must never pose as a live camera.
                    if (!Config.PREVIEW_UI_ONLY && rtcSession != null && (connected != null || callState is CallUiState.Outgoing) &&
                        rtcSession?.cameraEnabled == true) {
                        RtcVideoView(
                            local = true,
                            modifier = Modifier.fillMaxSize()
                                .shadow(12.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(24.dp)),
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .shadow(12.dp, RoundedCornerShape(24.dp))
                                .glassSurface(RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("You", fontSize = 12.sp, color = Color.White, fontFamily = InterTight) }
                    }
                }
            }
        }
        // ROUND 8: explicit minimize control (same action as Back) — shrinks
        // the call to the floating window so messaging can continue.
        // ROUND 69 (#6): the glyph is Material KeyboardArrowDown (the
        // call screen's one icon language now), and the control hides
        // with the rest of the dock once the call connects (it stays
        // on screen while outgoing, when it is the only minimize path
        // besides Back).
        if ((connected != null && controlsVisible) || callState is CallUiState.Outgoing) {
            Box(
                Modifier.align(Alignment.TopStart).padding(16.dp).size(40.dp)
                    .glassSurface(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        CallController.minimizeCall()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White) }
        }
        // ROUND 68 (WS7b): the 3-dot button — the ONE control that
        // stays once the call connects. It sits on the RIGHT edge,
        // vertically centred (clear of the top-end PiP and the bottom
        // dock), in the same glass as the dock. Tapping it brings the
        // controls back (and restarts the 3-second timer); tapping it
        // while the controls are showing hides them at once.
        if (connected != null) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(44.dp)
                    .glassSurface(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (controlsVisible) controlsVisible = false else pokeControls()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MoreHoriz, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White) }
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
                // .cltop — the identity pill (name + status). ROUND 45:
                // the hand-rolled white-alpha gradient is now the app's
                // large liquid glass (glassSurface useLargeGlass), the
                // same card skin as the rest of the app; text rides the
                // palette's tx token.
                Column(
                    Modifier
                        .glassSurface(RoundedCornerShape(26.dp), useLargeGlass = true)
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(person.name, fontFamily = InterTight, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = 19.sp, color = pal.tx)
                    Text(statusText, fontSize = 12.sp, color = pal.tx.copy(alpha = 0.78f), fontFamily = PoppinsFamily)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // ROUND 68 (WS7b): the encryption line rides with the
                // dock — it hides with the controls once connected
                // (ringing/outgoing always show it, like the dock).
                if (connected == null || controlsVisible) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TikuLockIcon(Modifier.size(12.dp), tint = pal.tx.copy(alpha = 0.6f))
                        Spacer(Modifier.width(5.dp))
                        Text("End-to-end encrypted", fontSize = 12.sp, color = pal.tx.copy(alpha = 0.6f), fontFamily = PoppinsFamily)
                    }
                    Spacer(Modifier.height(18.dp))
                }
                if (incoming != null) {
                    // ringing: accept / reject — rejecting deletes the call record entirely.
                    // ROUND 68 (WS7b): ringing NEVER auto-hides (the
                    // timer only runs while connected), so this row is
                    // unconditional.
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        CallEndButton { CallController.reject() }
                        // accept: green stays green, finished as glass
                        // (ROUND 45 CallGlassDisc, same skin as End).
                        CallGlassDisc(
                            58.dp, listOf(Color(0xFF3ED97E), Color(0xFF1F9D55)),
                            onClick = { acceptWithPerms() },
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(26.dp), tint = Color.White)
                        }
                    }
                } else if (connected != null) {
                    // ROUND 68 (WS7b): the connected dock exists only
                    // while the controls are showing — 3 seconds after
                    // connect (and after every re-show/touch) it sinks
                    // away, leaving the 3-dot button. Every dock tap
                    // pokes the timer first, so a second tap still
                    // lands before the dock hides again.
                    if (controlsVisible) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // ROUND 70: the dock is the owner's premium
                            // kit (CallControls.kt) wired to the same
                            // CallController actions — mic / speaker /
                            // camera / flip / end on video calls, mic /
                            // speaker / end on audio calls. NO Share
                            // (screen sharing was removed in build 118)
                            // and NO Keypad (the app has no in-call
                            // keypad or audio→video upgrade action).
                            CallControlButton(if (connected.muted) CallIcons.MicOff else CallIcons.Mic, "Mute", active = connected.muted) { pokeControls(); CallController.toggleMute() }
                            CallControlButton(CallIcons.Speaker, "Speaker", active = connected.speakerOn) { pokeControls(); CallController.toggleSpeaker() }
                            if (connected.video) {
                                CallControlButton(if (connected.cameraOn) CallIcons.Video else CallIcons.VideoOff, "Camera", active = !connected.cameraOn) { pokeControls(); CallController.toggleCamera() }
                                CallControlButton(CallIcons.FlipCamera, "Flip", active = false) { pokeControls(); CallController.switchCamera() }
                            }
                            EndCallButton(onClick = { CallController.hangup() })
                        }
                    }
                } else {
                    // Outgoing: only the End button — always on screen
                    // (the auto-hide timer never runs before connect).
                    // ROUND 70: the kit's EndCallButton, the same end
                    // button as the connected dock.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EndCallButton(onClick = { CallController.hangup() })
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
 *
 * ROUND 68 (WS7e) — VERIFIED, no change: Back once during a live
 * call already lands here (CallScreen's BackHandler calls
 * minimizeCall() for Connected/Outgoing, which flips pipMinimized
 * and navigates to the chat while the RTC session keeps running
 * untouched); this window then plays the PARTNER's live remote video
 * tile over the chat, and tapping it restores the full call via
 * restoreCall(). Voice calls show the avatar tile + clock instead.
 * The signalling/FCM path and the build-90 "incoming presents only
 * when that chat is opened" behaviour are untouched by this round.
 */
@Composable
fun FloatingCallWindow() {
    val callState by CallController.state.collectAsState()
    val pip by CallController.pipMinimized.collectAsState()
    val rtcSession by CallController.sessionState.collectAsState()
    // ROUND 47: the peer's camera-off flag — the tile falls back to
    // the avatar row instead of a frozen last frame.
    val remoteCameraOffNow by CallController.remoteCameraOff.collectAsState()
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
    // ROUND 47: peer camera off collapses the video tile to the
    // avatar row. (ROUND 69: the tile is always the peer's remote
    // track now — the former local shared-screen tile is gone with
    // screen sharing.)
    val showVideo = video &&
        connected != null && !Config.PREVIEW_UI_ONLY && rtcSession != null &&
        !remoteCameraOffNow
    val winW = if (showVideo) 108.dp else 184.dp
    val winH = if (showVideo) 156.dp else 64.dp
    val pal = LocalPalette.current
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
                // ROUND 45: tile skin rides the palette (sheet → bg
                // premium-dark gradient, hi edge) instead of the old
                // hardcoded near-blacks.
                .background(Brush.verticalGradient(listOf(pal.sheet.copy(alpha = 0.92f), pal.bg)))
                .border(1.dp, pal.hi, RoundedCornerShape(20.dp))
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
                key(connected?.tick) {
                    RtcVideoView(local = false, modifier = Modifier.fillMaxSize())
                }
                // ROUND 20: his own tiny self-preview rides the tile's
                // top-end corner — the same local renderer binding the
                // full call screen uses (a second sink on the local
                // track, bound and released with this window only).
                // Hidden while his camera is off: a black inset would
                // lie about a camera that is not sending anything.
                if (connected?.cameraOn == true) {
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
                    Text(statusText, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), fontFamily = PoppinsFamily)
                }
            } else {
                Row(
                    Modifier.fillMaxSize().padding(start = 10.dp, end = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarBox(person, 40.dp, 14.dp, 16f)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, fontSize = 13.sp, color = pal.tx, fontFamily = InterTight,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(statusText, fontSize = 11.5.sp, color = pal.tx.copy(alpha = 0.75f), fontFamily = PoppinsFamily)
                    }
                }
            }
            // end call — a real hangup (event recorded + signaling deleted),
            // never a mere dismiss of the window. ROUND 45: same red
            // glass-disc finish as the full screen's End button.
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                CallGlassDisc(
                    30.dp, listOf(Color(0xFFFF7A85), Color(0xFFE5313F)),
                    onClick = { CallController.hangup() },
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                }
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
                        fontSize = 16.sp, color = Color.White, fontFamily = InterTight)
                    Text("2h ago", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = PoppinsFamily)
                }
            }
            Spacer(Modifier.weight(1f))
            SerifTitle(quote, 52.sp, Color.White)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Tap to continue · ", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = PoppinsFamily)
                TikuLockIcon(Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.8f))
                Text(" Encrypted", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = PoppinsFamily)
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
