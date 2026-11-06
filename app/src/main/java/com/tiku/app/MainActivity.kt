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
        // NOTE: zero chat notifications by design — no notification channel is
        // created at startup, ever. ROUND 9 exception (owner account only, his
        // own phone, opt-in): OwnerNotify creates ONE channel lazily at the
        // first actual alert when — and only when — the owner enabled it.
        // For every other account that code path never runs.
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
        // ROUND 15: app start is one of the notify service's choke points
        // (OwnerPrefs was initialized in TikuApp.onCreate, before this).
        NotifyGate.sync(this)
        // ROUND 29: a call alert may have launched us — same handling
        // as a redelivered intent (see onNewIntent).
        handleCallAlertIntent(intent)
        // ROUND 18: in-app update check (throttled to one fetch per 6h,
        // silent on failure; sets UpdateState + a one-per-build
        // notification when a newer build is published).
        UpdateCheck.maybeCheck(this)
        setContent {
            TikuRoot(onGoogleClick = ::onGoogleClick, onAccountClick = ::onFacadeAccountClick)
        }
    }

    /** ROUND 29: a tap on an incoming-call alert (OwnerNotify) brings
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
    }

    private fun handleCallAlertIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra(TikuAlertService.EXTRA_CALL_THREAD) == null) return
        if (Config.PREVIEW_UI_ONLY) return
        if (ChatSession.unlocked && FirebaseEngine.signedIn) {
            FirebaseEngine.start(this)
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
        // ROUND 15: returning to the app re-reconciles the notify service
        // with its run gate (settings may have changed while we were away).
        NotifyGate.sync(this)
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
        // ROUND 15: leaving the app is the moment background alerts start
        // to matter — sync starts the notify service now if its gate
        // holds (e.g. an in-app unlock just consumed a Lock-now one-shot),
        // and stops it if the gate broke. Called while still foreground,
        // so a foreground-service start is always legal here.
        NotifyGate.sync(this)
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

    /** ROUND 54 (Part A): the facade home's account chip. Signed out,
     *  tapping it IS the Google sign-in — the same flow the Welcome
     *  screen ran. Signed in, it re-runs the post-auth routing below
     *  (a locked, onboarded session lands back in Tik through the
     *  returning-user path; a signed-in session that still owes setup
     *  lands on exactly the setup screen it owes). */
    private fun onFacadeAccountClick() {
        if (Config.PREVIEW_UI_ONLY) return
        if (FirebaseEngine.signedIn) {
            lifecycleScope.launch { routeAfterGoogleAuth() }
        } else {
            onGoogleClick()
        }
    }

    /** Where a completed Google auth lands — the journal create /
     *  restore routing, unchanged since Round 5: it is post-sign-in
     *  setup, shared by the Welcome screen and the facade chip. */
    private suspend fun routeAfterGoogleAuth() {
        // ROUND 5: tri-state — a failed account check must never fall
        // through to the create-password branch (that is how a returning
        // user could be walked into overwriting their own journal).
        val returning = FirebaseEngine.returningUserCheck()
        when {
            returning == null ->
                AppState.toast("Couldn't check your account — check your connection and try again.")
            returning == true && FirebaseEngine.loadLocalIdentity(this@MainActivity) -> {
                getSharedPreferences("tiku_prefs", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
                FirebaseEngine.start(this@MainActivity)
                FirebaseEngine.loadMyProfile()
                FirebaseEngine.setPresence(true)
                AppState.go(Screen.Home)
            }
            returning == true -> AppState.go(Screen.Restore)
            else -> AppState.go(Screen.Password)
        }
    }
}

@Composable
fun TikuRoot(onGoogleClick: () -> Unit, onAccountClick: () -> Unit = {}) {
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
                "facade" -> FacadeRouter(onAccountClick)
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

/** Facade sub-navigation: fade transitions; instant snaps when re-locking. */
@Composable
fun FacadeRouter(onAccountClick: () -> Unit = {}) {
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
            is Screen.Facade -> FacadeHome(onAccountClick = onAccountClick)
            is Screen.TikGate -> TikGateScreen()
            else -> FacadeHome(onAccountClick = onAccountClick)
        }
    }
}
