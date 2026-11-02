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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TikuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        OwnerPrefs.init(this)
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
        // The APK always opens to the study facade; ChatRoot is reachable only via the gate.
        AppState.repo = if (Config.PREVIEW_UI_ONLY) PreviewRepo else FirebaseEngine
        StudyContent.load(this)
        AppState.instantGo(Screen.Facade)
        setContent { TikuRoot(onGoogleClick = ::onGoogleClick) }
    }

    override fun onResume() {
        super.onResume()
        ChatSession.foreground = true
        // presence reflects being INSIDE ChatRoot only — never the facade
        if (!Config.PREVIEW_UI_ONLY && ChatSession.unlocked && FirebaseEngine.signedIn && isOnboarded()) {
            FirebaseEngine.setPresence(true)
        }
    }

    override fun onPause() {
        super.onPause()
        ChatSession.foreground = false
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
                val err = result.exceptionOrNull()
                val isCancelled = err is androidx.credentials.exceptions.GetCredentialCancellationException ||
                        err?.javaClass?.simpleName?.contains("Cancel", ignoreCase = true) == true
                if (!isCancelled) {
                    val msg = err?.localizedMessage ?: err?.message ?: "Sign-in failed."
                    if (msg.contains("10") || msg.contains("DEVELOPER_ERROR", ignoreCase = true)) {
                        AppState.toast("Google Sign-In SHA-1 mismatch. Please add the debug SHA-1 to Firebase Console.")
                    } else {
                        AppState.toast("Sign-in failed: $msg")
                    }
                }
                return@launch
            }
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
}

@Composable
fun TikuRoot(onGoogleClick: () -> Unit) {
    // app-wide light gradient (the design's .s background)
    val bgBrush = Brush.verticalGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom))
    val screen = AppState.screen
    // EDGE-TO-EDGE (owner bug fix): the gradient stays full-bleed, but EVERY piece of
    // interactive content lives in an inner box padded by the system-bar insets, so
    // nothing renders under the status bar or the gesture nav bar on any screen.
    Box(Modifier.fillMaxSize().background(bgBrush)) {
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        val kind = when (screen) {
            is Screen.Welcome, is Screen.Password, is Screen.Restore, is Screen.ProfileSetup,
            is Screen.PasswordReset -> "gate"
            is Screen.Facade, is Screen.StudySubject, is Screen.StudyChapter, is Screen.StudyNotes,
            is Screen.StudyTimer, is Screen.StudyRevision, is Screen.StudyFormulas, is Screen.TikGate -> "facade"
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
                "facade" -> FacadeRouter()
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
                        LaunchedEffect(screen) {
                            (screen as? Screen.Chat)?.let { lastChat = it }
                            (screen as? Screen.Call)?.let { lastCall = it }
                        }
                        AnimatedVisibility(
                            visible = screen is Screen.Chat,
                            enter = slideInHorizontally(tween(420, easing = TikuEase)) { it } +
                                fadeIn(tween(300, easing = TikuEase)),
                            exit = slideOutHorizontally(tween(320, easing = TikuEase)) { it } +
                                fadeOut(tween(220, easing = TikuEase)),
                        ) {
                            (screen as? Screen.Chat ?: lastChat)?.let { ChatScreen(it.threadId) }
                        }
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
                        // ROUND 8: the floating call window lives above the
                        // chat surfaces (below sheets + toast, which render
                        // after this Box).
                        FloatingCallWindow()
                    }
                }
            }
        }
        DesignToast()
        BottomSheetHost()
      }
    }

    // FLAG_SECURE hook: set while ANY ChatRoot destination is visible (blocks screenshots +
    // the Android recents preview of gated content); cleared on the study facade.
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
}

/** Facade sub-navigation: fade transitions; instant snaps when re-locking. */
@Composable
fun FacadeRouter() {
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
            is Screen.StudySubject -> SubjectScreen(s.name)
            is Screen.StudyChapter -> ChapterScreen(s.subject, s.index)
            is Screen.StudyNotes -> NotesScreen()
            is Screen.StudyTimer -> TimerScreen()
            is Screen.StudyRevision -> RevisionScreen()
            is Screen.StudyFormulas -> FormulasScreen()
            is Screen.TikGate -> TikGateScreen()
            else -> FacadeHome()
        }
    }
}
