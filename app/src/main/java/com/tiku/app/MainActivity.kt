package com.tiku.app

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TikuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        OwnerPrefs.init(this)
        // ROUND 23: warm both halves of the iOS emoji font on a background
        // thread so the first transient emoji surface (reaction-bar popup,
        // picker) never paints with the device fallback mid-load.
        EmojiArt.preload(this)
        // ROUND 58: the two FCM alert channels (messages + incoming
        // calls, both IMPORTANCE_HIGH) are created at app start so a
        // push landing in a cold FCM-only process always finds them
        // ready (PushAlerts). Creating a channel posts nothing — a
        // notification still exists only while a real event stands,
        // and the display gate lives in TikuFcmService (ROUND 63:
        // signed-in + local identity + the user's Notifications
        // preference, default ON).
        PushAlerts.ensureChannels(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.appCtx = applicationContext
        // The APK always opens to the K-drama facade; ChatRoot is reachable only via the gate.
        AppState.repo = if (Config.PREVIEW_UI_ONLY) PreviewRepo else FirebaseEngine
        DramaContent.load(this)
        AppState.instantGo(Screen.Facade)
        // ROUND 57 (Fix 5) — OWNER DIRECT ENTRY: with "Skip home
        // screen" on (OwnerPrefs.skipHome, default ON) and the
        // signed-in account being the owner, opening the app goes
        // straight to the episode gate — through the SAME
        // SecretEntry route the facade double-tap uses, so every
        // build-92 entry invariant holds: App lock OFF still skips
        // the password via its unlockFlow, otherwise the gate shows
        // exactly as it does from the double-tap (password required;
        // Google auth alone never unlocks; the gate's Cancel drops
        // back to this facade). Non-owners never take this branch —
        // their entry is the facade, unchanged.
        if (OwnerState.isOwner && OwnerPrefs.skipHome) {
            SecretEntry.openGate(this, lifecycleScope)
        }
        // ROUND 29 → ROUND 58: a call or message alert may have
        // launched us — same handling as a redelivered intent (see
        // onNewIntent). (The Round-15 NotifyGate.sync reconcile that
        // used to sit here died with the alert service: FCM needs no
        // start-of-app reconcile.)
        handleCallAlertIntent(intent)
        handleChatAlertIntent(intent)
        // ROUND 18: in-app update check (throttled to one fetch per 6h,
        // silent on failure; sets UpdateState + a one-per-build
        // notification when a newer build is published).
        UpdateCheck.maybeCheck(this)
        setContent {
            TikuRoot(onGoogleClick = ::onGoogleClick)
        }
    }

    /** ROUND 29 → ROUND 58: a tap on an incoming-call alert (PushAlerts) brings
     *  the app forward carrying the ringing chat's id. When the
     *  session is live, re-running the engine start re-attaches the
     *  call watchers, whose fresh snapshot hands the still-ringing
     *  call to the in-app ringing flow (the same re-attach the
     *  engine's own error retries perform). Locked, there is nothing
     *  to hand over — the tap simply lands on the facade/gate, and
     *  the unlock's own engine start attaches the watchers to the
     *  same end. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallAlertIntent(intent)
        handleChatAlertIntent(intent)
    }

    private fun handleCallAlertIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra(PushAlerts.EXTRA_CALL_THREAD) == null) return
        if (Config.PREVIEW_UI_ONLY) return
        if (ChatSession.unlocked && FirebaseEngine.signedIn) {
            FirebaseEngine.start(this)
        }
    }

    /** ROUND 58: a tap on a message alert (PushAlerts) carries the
     *  chat's id — with a live unlocked session, open that exact chat
     *  (the same openThread + go pair the chat list's own tap uses).
     *  Locked, there is nothing to open into — the tap simply lands
     *  on the facade/gate, and the chat is one tap away after unlock. */
    private fun handleChatAlertIntent(intent: android.content.Intent?) {
        val threadId = intent?.getStringExtra(PushAlerts.EXTRA_CHAT_THREAD) ?: return
        if (Config.PREVIEW_UI_ONLY) return
        if (ChatSession.unlocked && FirebaseEngine.signedIn) {
            AppState.repo.openThread(threadId)
            AppState.go(Screen.Chat(threadId))
        }
    }

    override fun onResume() {
        super.onResume()
        ChatSession.foreground = true
        // ROUND 15: the symmetric half of onPause's Watch pause — an
        // onPause()d WebView that is never onResume()d keeps a frozen,
        // black video surface while its page lives on. This only
        // unfreezes the renderer; it never forces playback (expanding
        // the pane is what plays — see WatchControl.requestResume).
        WatchControl.requestResume()
        // ROUND 58: returning to the app means any standing push
        // alerts have been seen — clear them (the old judge's
        // foreground clear, kept), and take the one-time Android 13+
        // permission ask if this phone's display gate could show an
        // alert and it hasn't been asked yet.
        PushAlerts.clearStanding(this)
        PushAlerts.maybeRequestPermission(this)
        // presence reflects being INSIDE ChatRoot only — never the facade
        if (!Config.PREVIEW_UI_ONLY && ChatSession.unlocked && FirebaseEngine.signedIn && isOnboarded()) {
            FirebaseEngine.setPresence(true)
            // ROUND 53 (item 2): NO call reconcile here any more. The
            // owner reversed the auto-popup half of Round 52 item 5:
            // merely returning to the foreground must never throw a
            // ringing screen up. An incoming call becomes visible only
            // when he opens that chat — openThread's reconcile is the
            // one presentation point that remains.
        }
    }

    override fun onPause() {
        super.onPause()
        ChatSession.foreground = false
        // ROUND 10 (Watch): backgrounding WITHOUT a lock (e.g. the owner's
        // relaxed mode, or a transient system surface) pauses the in-vault
        // video — playback never continues invisibly. The lock path itself
        // (onStop → ChatSession.lock) destroys the WebView outright.
        // ROUND 13: the pause goes through WatchControl to the ACTIVE pane's
        // WebView; no pane composed = nothing playing (view destroyed).
        WatchControl.requestPause()
        // ROUND 58: no notify reconcile on pause any more — FCM
        // delivery is the platform's job while the app is away; the
        // old foreground service that needed a legal start window here
        // is deleted.
        if (!Config.PREVIEW_UI_ONLY && ChatSession.unlocked && FirebaseEngine.signedIn) {
            FirebaseEngine.setPresence(false)
        }
    }

    override fun onStop() {
        super.onStop()
        // INSTANT RE-LOCK ON EXIT: backgrounding drops the unlocked session entirely —
        // keys zeroed, decrypted state dropped, facade restored, password required again.
        if (isChangingConfigurations) return
        if (ChatSession.expectExternalActivity) {
            // photo pickers etc. launched from inside ChatRoot don't count as "exit"
            ChatSession.expectExternalActivity = false
            return
        }
        // …but NOT during the first-run / gate flow: backgrounding mid-onboarding
        // (e.g. the system avatar picker) yanked setup back to the facade and
        // desynced password creation (owner Bug 0). There is no unlocked
        // ChatRoot session to protect yet.
        if (AppState.isSetupFlow(AppState.screen)) return
        // ROUND 9: the owner with App lock OFF keeps his session across
        // backgrounding (his phone, his choice — the relaxed mode exists only
        // for his account; OwnerGate is the single check). Everyone else,
        // and the owner with the toggle ON: instant re-lock, as always.
        if (ChatSession.unlocked && !OwnerGate.lockOffActive()) ChatSession.lock(this)
    }

    private fun isOnboarded(): Boolean =
        getSharedPreferences("tiku_prefs", MODE_PRIVATE).getBoolean("onboarded", false)

    /** Welcome Google button: 1.1s spinner in preview; real Credential Manager + Firebase otherwise. */
    private fun onGoogleClick() {
        if (Config.PREVIEW_UI_ONLY) {
            lifecycleScope.launch { delay(1100); AppState.authBusy = false; AppState.go(Screen.Password) }
            return
        }
        lifecycleScope.launch {
            val result = FirebaseEngine.googleSignIn(this@MainActivity)
            AppState.authBusy = false
            if (result.isFailure) {
                AppState.toast("Sign-in failed. Please try again.")
                return@launch
            }
            routeAfterGoogleAuth()
        }
    }

    /** Where a completed Google auth lands.
     *
     *  ROUND 55 (Part A) — THE SECURITY RULE, superseding the Round-54
     *  routing: GOOGLE AUTH ALONE MUST NEVER UNLOCK TIK. Round 54 put
     *  an account chip on the facade home and let a signed-in tap
     *  re-run this routing, whose returning-user branch walked
     *  straight into Screen.Home whenever a local identity existed —
     *  the owner caught it live: one tap on his own profile avatar
     *  and he was inside, no password asked. Auth proves WHO is
     *  holding the phone; it says nothing about whether the journal
     *  may open. So a RETURNING account (a journal/keyBackup exists)
     *  now lands back on the FACADE from every auth completion — the
     *  Welcome button and the episode gate's sign-in alike. Entry
     *  from there happens ONLY through the episode box: the journal
     *  password (verified locally, or unwrapped against the cloud
     *  backup on a device that holds no local identity yet) or a
     *  recovery code — see episodeAttempt in Facade.kt. Only a
     *  genuinely NEW account (no key material on its doc) proceeds
     *  into first-run setup, where creating the journal password IS
     *  the unlock act.
     *
     *  ROUND 5's tri-state still holds: a failed account check must
     *  never fall through to the create-password branch (that is how
     *  a returning user could be walked into overwriting their own
     *  journal) — it toasts and stays where it was. */
    private suspend fun routeAfterGoogleAuth() {
        val returning = FirebaseEngine.returningUserCheck()
        when {
            returning == null ->
                AppState.toast("Couldn't check your account — check your connection and try again.")
            returning == true -> AppState.go(Screen.Facade)
            else -> AppState.go(Screen.Password)
        }
    }
}

@Composable
fun TikuRoot(onGoogleClick: () -> Unit) {
    // app-wide light gradient (the design's .s background)
    val bgBrush = Brush.verticalGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom))
    val screen = AppState.screen
    val rootSettings by SettingsStore.state.collectAsState()
    // ROUND 13 (A4): the Tik palette + Haze state for the whole tree. The
    // facade provides its own LIGHT palette inside FacadeRouter; gate screens
    // do not read LocalPalette (their dark password field only renders inside
    // Tik sheets). PREVIEW_UI_ONLY provides NO HazeState — every glass
    // surface then falls back to its gradient form (GlassDesign), so the
    // Roborazzi suite never touches a real blur pipeline.
    val tikPalette = remember(rootSettings.darkMode, rootSettings.accentIdx) {
        buildPalette(if (rootSettings.darkMode) "dark" else "light", rootSettings.accentIdx)
    }
    val hazeState = rememberHazeState()
    // EDGE-TO-EDGE (owner bug fix): the gradient stays full-bleed, but EVERY piece of
    // interactive content lives in an inner box padded by the system-bar insets, so
    // nothing renders under the status bar or the gesture nav bar on any screen.
    Box(Modifier.fillMaxSize().background(bgBrush)) {
     CompositionLocalProvider(
        LocalPalette provides tikPalette,
        LocalHazeState provides if (Config.PREVIEW_UI_ONLY) null else hazeState,
     ) {
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        val kind = when (screen) {
            is Screen.Welcome, is Screen.Password, is Screen.Restore, is Screen.ProfileSetup,
            is Screen.PasswordReset -> "gate"
            is Screen.Facade, is Screen.TikGate -> "facade"
            else -> "home"
        }
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                if (AppState.instantNav) EnterTransition.None togetherWith ExitTransition.None
                else (slideInHorizontally(tween(550, easing = TikuEase)) { it / 3 } + fadeIn(tween(450, easing = TikuEase)))
                    .togetherWith(slideOutHorizontally(tween(550, easing = TikuEase)) { -it / 4 } + fadeOut(tween(300, easing = TikuEase)))
            },
            label = "root",
        ) { k ->
            when (k) {
                "gate" -> when (screen) {
                    is Screen.Welcome -> WelcomeScreen(onGoogleClick)
                    is Screen.Password -> CreatePasswordScreen()
                    is Screen.Restore -> RestorePasswordScreen()
                    is Screen.PasswordReset -> ResetPasswordScreen()
                    is Screen.ProfileSetup -> ProfileSetupScreen()
                    else -> WelcomeScreen(onGoogleClick)
                }
                "facade" -> FacadeRouter(onGoogleClick)
                else -> {
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen()
                        // ROUND 8 (animations pass): the chat overlay slides
                        // in from the right and the call overlay fades/scales
                        // in — previously both snapped in instantly. The last
                        // non-null target is remembered so the EXIT animation
                        // still has content to animate after navigation flips.
                        var lastChat by remember { mutableStateOf<Screen.Chat?>(null) }
                        var lastCall by remember { mutableStateOf<Screen.Call?>(null) }
                        var lastDetails by remember { mutableStateOf<Screen.ChatDetails?>(null) }
                        LaunchedEffect(screen) {
                            (screen as? Screen.Chat)?.let { lastChat = it }
                            (screen as? Screen.Call)?.let { lastCall = it }
                            (screen as? Screen.ChatDetails)?.let { lastDetails = it }
                        }
                        // ROUND 12: Tik overlays enter with the design's
                        // screen motion (`in` keyframes: fade + 8px rise,
                        // .26s, cubic-bezier(.22,.8,.22,1)) instead of the
                        // old horizontal slide.
                        AnimatedVisibility(
                            visible = screen is Screen.Chat,
                            enter = slideInVertically(tween(260, easing = TikEase)) { 24 } +
                                fadeIn(tween(260, easing = TikEase)),
                            exit = slideOutVertically(tween(200, easing = TikEase)) { 24 } +
                                fadeOut(tween(200, easing = TikEase)),
                        ) {
                            (screen as? Screen.Chat ?: lastChat)?.let { ChatScreen(it.threadId) }
                        }
                        // Chat details rides the same overlay pattern
                        // (header tap in the chat opens it; Back returns
                        // to the chat).
                        AnimatedVisibility(
                            visible = screen is Screen.ChatDetails,
                            enter = slideInVertically(tween(260, easing = TikEase)) { 24 } +
                                fadeIn(tween(260, easing = TikEase)),
                            exit = slideOutVertically(tween(200, easing = TikEase)) { 24 } +
                                fadeOut(tween(200, easing = TikEase)),
                        ) {
                            (screen as? Screen.ChatDetails ?: lastDetails)?.let { ChatDetailsScreen(it.threadId) }
                        }
                        // ROUND 14 (owner order): the Moments web surface
                        // rides the same overlay pattern as Chat/Details —
                        // full-screen above Home (so the NavPill, gated on
                        // screen is Screen.Home, hides itself), and leaving
                        // composition on close/lock destroys its WebView.
                        AnimatedVisibility(
                            visible = screen is Screen.Moments,
                            enter = slideInVertically(tween(260, easing = TikEase)) { 24 } +
                                fadeIn(tween(260, easing = TikEase)),
                            exit = slideOutVertically(tween(200, easing = TikEase)) { 24 } +
                                fadeOut(tween(200, easing = TikEase)),
                        ) {
                            MomentsScreen()
                        }
                        // ROUND 54 (Part B): the Tika companion chat
                        // rides the same overlay pattern — full-screen
                        // above Home (NavPill hides itself, gated on
                        // Screen.Home); leaving on lock destroys it
                        // with the session, like every Tik surface.
                        AnimatedVisibility(
                            visible = screen is Screen.TikaBot,
                            enter = slideInVertically(tween(260, easing = TikEase)) { 24 } +
                                fadeIn(tween(260, easing = TikEase)),
                            exit = slideOutVertically(tween(200, easing = TikEase)) { 24 } +
                                fadeOut(tween(200, easing = TikEase)),
                        ) {
                            TikaBotScreen()
                        }
                        // ROUND 15 (owner device test): the Watch host
                        // is hoisted HERE from HomeScreen — above the
                        // Chat / ChatDetails / Moments overlays, so the
                        // floating mini player stays visible and usable
                        // over an open chat; below the call UI, the
                        // story viewer, the sheets (BottomSheetHost,
                        // composed after this subtree) and the toast.
                        // It still dies with the Tik session: locking
                        // flips this AnimatedContent branch away, which
                        // uncomposes the host and destroys the WebView.
                        // The host pauses + hides the mini itself while
                        // a call is active (CallController state).
                        WatchHost()
                        WatchFullscreenLayer()
                        AnimatedVisibility(
                            visible = screen is Screen.Call,
                            enter = fadeIn(tween(300, easing = TikuEase)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(300, easing = TikuEase)),
                            exit = fadeOut(tween(220, easing = TikuEase)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(220, easing = TikuEase)),
                        ) {
                            (screen as? Screen.Call ?: lastCall)?.let { CallScreen(it.threadId, it.video) }
                        }
                        if (screen is Screen.Story) StoryScreen()
                        // ROUND 13: the "Now watching" glass bar floats
                        // above the glass nav. It self-hides unless a Watch
                        // session exists on a non-Watch Home tab.
                        // ROUND 14 (Addendum 2 §5): the floating mini
                        // player (WatchHost, hoisted to this root in
                        // ROUND 15) rests directly above this bar; the
                        // bar's bottom inset is NowWatchingBarBottomInset
                        // (94.dp — 8.dp above the redesign NavPill's
                        // 86.dp block).
                        NowWatchingBar(
                            Modifier.align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = NowWatchingBarBottomInset),
                        )
                        // ROUND 20: the floating call window (Call.kt) is
                        // composed HERE again, as this Box's last child —
                        // above HomeScreen, the Chat / Details / Moments
                        // overlays and WatchHost, so a minimized VIDEO
                        // call keeps the peer's live video floating while
                        // the owner messages (Round 14's CallPill lives
                        // inside HomeScreen and can never appear over a
                        // chat). The window self-gates: it renders only
                        // for a minimized live call, and yields the Home
                        // pane tabs to the CallPill, so exactly one
                        // minimized-call affordance is ever visible.
                        // The Watch mini never coexists with it —
                        // WatchHost hides + pauses its mini for the whole
                        // call (Round 15). Within this Box the window is
                        // the topmost layer (it floats over the story
                        // viewer too, IG-style); only the sheets and the
                        // toast, composed after this whole subtree, sit
                        // above it.
                        FloatingCallWindow()
                    }
                }
            }
        }
        DesignToast()
        BottomSheetHost()
        // ROUND 18: the update prompt — self-gating (renders only on the
        // Tik home / facade, only for an un-dismissed newer build).
        UpdatePromptDialog()
      }
     }
    }

    // FLAG_SECURE hook: set while ANY ChatRoot destination is visible (blocks screenshots +
    // the Android recents preview of gated content); cleared on the drama facade.
    // ROUND 3 — single source of truth: Privacy ▸ "Block screenshots"
    // (SettingsStore key "bs", default ON). This effect reads the LIVE settings
    // state (not just the destination), so flipping the switch while standing in
    // the Me pane applies/lifts FLAG_SECURE immediately, with no navigation.
    val activity = LocalContext.current as? ComponentActivity
    val innerSettings by SettingsStore.state.collectAsState()
    LaunchedEffect(screen, innerSettings.blockScreenshots) {
        AppState.instantNav = false
        val w = activity?.window
        if (AppState.isChatRoot(screen) && innerSettings.blockScreenshots) {
            w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    // ROUND 52 (item 3) → ROUND 54 (Part C): STATUS BAR VISIBILITY.
    // Round 52's per-screen rule misread the geometry: EVERY screen's
    // content is inset-padded BELOW the status bar (see the
    // windowInsetsPadding(systemBars) box above), so the strip the
    // system draws the clock / signal / battery over is ALWAYS this
    // root's fixed pale gradient (TikuTokens BgTop #FFF4EE → BgBottom
    // #FCD9C6) — never the screen's own background. Round 52 keyed the
    // icon appearance on each screen's CONTENT theme and gave the
    // facade, the gate/auth flow, Story, Call and ChatDetails WHITE
    // icons — white on the pale strip reads as a permanently hidden
    // status bar (the owner's Round-54 report: the bar stays "hidden"
    // from app open, which lands on the facade). A full audit found
    // NO hide() call, no SYSTEM_UI flag and no window flag anywhere
    // in the app — the bars were never hidden, only camouflaged.
    // The fix matches the strip that actually exists: DARK icons on
    // every screen, re-asserted on every navigation, plus an
    // explicit show() of the status bars so no transient state (a
    // player fullscreen session included) can leave them hidden
    // behind it. Player exits restore them too (FacadePlayer /
    // WatchFullscreenLayer call restoreStatusBars on dispose).
    LaunchedEffect(screen, rootSettings.darkMode) {
        val w = activity?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowInsetsControllerCompat(w, w.decorView)
        controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        controller.isAppearanceLightStatusBars = true
    }
}

/** ROUND 54 (Part C): the one status-bar restore the player surfaces
 *  call when they close — leaving a fullscreen video must hand the
 *  system bars back exactly as every other screen keeps them: shown,
 *  with dark icons over the root's pale strip. Safe from any context:
 *  a non-activity context is a no-op. */
internal fun restoreStatusBars(ctx: android.content.Context) {
    val window = (ctx as? ComponentActivity)?.window ?: return
    val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
    controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
    controller.isAppearanceLightStatusBars = true
}

/** Facade sub-navigation: fade transitions; instant snaps when re-locking.
 *  ROUND 55: the facade home takes NO account wiring any more (the
 *  Round-54 home chip is deleted — home is a plain drama app). The
 *  one callback is the episode gate's signed-out Google sign-in,
 *  which runs the same [MainActivity.onGoogleClick] flow as the
 *  Welcome screen. */
@Composable
fun FacadeRouter(onGoogleSignIn: () -> Unit = {}) {
    val screen = AppState.screen
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (AppState.instantNav) EnterTransition.None togetherWith ExitTransition.None
            else fadeIn(tween(280, easing = TikuEase)).togetherWith(fadeOut(tween(180, easing = TikuEase)))
        },
        label = "facade",
    ) { s ->
        when (s) {
            is Screen.Facade -> FacadeHome()
            is Screen.TikGate -> TikGateScreen(onGoogleSignIn)
            else -> FacadeHome()
        }
    }
}
